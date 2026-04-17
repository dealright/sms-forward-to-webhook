package com.dealright.smsforwarder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class HeaderConfig(
    val httpMethod: String = "POST",
    val contentType: String = "application/json",
    val extraHeaders: Map<String, String> = emptyMap(),
)

object WebhookForwarder {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val DEFAULT_TEMPLATE = """{"text": "SMS from {{sender}}: {{body}}"}"""

    fun renderTemplate(template: String, vars: Map<String, String>): String {
        return Regex("""\{\{(\w+)\}\}""").replace(template) { match ->
            val key = match.groupValues[1]
            vars[key]?.let { value ->
                value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
            } ?: match.value
        }
    }

    fun forward(smsData: SmsData, webhookUrl: String, template: String, headerConfig: HeaderConfig) {
        val logRepo = SmsForwarderApp.instance.logRepository

        if (webhookUrl.isBlank()) {
            logRepo.addLog(
                sender = smsData.sender,
                body = smsData.body,
                webhookUrl = "(not set)",
                error = "No webhook URL configured",
            )
            return
        }

        val effectiveTemplate = template.ifBlank { DEFAULT_TEMPLATE }
        val vars = smsData.toTemplateMap()
        val rendered = renderTemplate(effectiveTemplate, vars)

        scope.launch {
            try {
                val body = rendered.toRequestBody(headerConfig.contentType.toMediaType())
                val requestBuilder = Request.Builder().url(webhookUrl)

                when (headerConfig.httpMethod) {
                    "GET" -> requestBuilder.get()
                    "PUT" -> requestBuilder.put(body)
                    "PATCH" -> requestBuilder.patch(body)
                    "DELETE" -> requestBuilder.delete(body)
                    else -> requestBuilder.post(body)
                }

                for ((key, value) in headerConfig.extraHeaders) {
                    requestBuilder.addHeader(key, value)
                }

                val request = requestBuilder.build()

                val reqHeaders = buildString {
                    appendLine("${headerConfig.httpMethod} $webhookUrl")
                    if (headerConfig.httpMethod != "GET") {
                        appendLine("Content-Type: ${headerConfig.contentType}")
                    }
                    for ((key, value) in headerConfig.extraHeaders) {
                        appendLine("$key: $value")
                    }
                }.trim()

                client.newCall(request).execute().use { response ->
                    // Format response headers
                    val respHeaders = buildString {
                        appendLine("HTTP ${response.code} ${response.message}")
                        for (name in response.headers.names()) {
                            appendLine("$name: ${response.header(name)}")
                        }
                    }.trim()

                    logRepo.addLog(
                        sender = smsData.sender,
                        body = smsData.body,
                        webhookUrl = webhookUrl,
                        requestHeaders = reqHeaders,
                        requestJson = rendered,
                        responseCode = response.code,
                        responseHeaders = respHeaders,
                        responseBody = response.body?.string()?.take(1000),
                    )
                }
            } catch (e: Exception) {
                val reqHeaders = buildString {
                    appendLine("${headerConfig.httpMethod} $webhookUrl")
                    if (headerConfig.httpMethod != "GET") {
                        appendLine("Content-Type: ${headerConfig.contentType}")
                    }
                    for ((key, value) in headerConfig.extraHeaders) {
                        appendLine("$key: $value")
                    }
                }.trim()

                logRepo.addLog(
                    sender = smsData.sender,
                    body = smsData.body,
                    webhookUrl = webhookUrl,
                    requestHeaders = reqHeaders,
                    requestJson = rendered,
                    error = "${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }
    }
}
