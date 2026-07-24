package com.dealright.smsforwarder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    // A manifest BroadcastReceiver using goAsync() has roughly a 10s budget before
    // the system may kill/ANR it, so bound the whole call well under that. callTimeout
    // caps the entire request (connect + write + read) regardless of the sub-timeouts.
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
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

    /**
     * Kicks off the webhook delivery on the IO dispatcher and returns the [Job] so the
     * caller (the SMS receiver) can await completion before releasing its wakelock and
     * finishing the broadcast. Returns an already-completed Job when there is nothing to send.
     */
    fun forward(smsData: SmsData, webhookUrl: String, template: String, headerConfig: HeaderConfig): Job {
        val logRepo = SmsForwarderApp.instance.logRepository

        if (webhookUrl.isBlank()) {
            logRepo.addLog(
                sender = smsData.sender,
                body = smsData.body,
                webhookUrl = "(not set)",
                error = "No webhook URL configured",
            )
            return Job().apply { complete() }
        }

        val effectiveTemplate = template.ifBlank { DEFAULT_TEMPLATE }
        val vars = smsData.toTemplateMap()
        val rendered = renderTemplate(effectiveTemplate, vars)

        return scope.launch {
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
