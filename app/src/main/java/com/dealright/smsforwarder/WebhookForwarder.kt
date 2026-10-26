package com.dealright.smsforwarder

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
     * Queues the SMS for delivery. The send runs in [ForwardJob], an expedited
     * JobScheduler job that survives the receiver returning, the process being frozen or
     * killed, and Doze: it waits for network and retries with backoff.
     */
    fun forward(smsData: SmsData, webhookUrl: String, template: String, headerConfig: HeaderConfig) {
        if (webhookUrl.isBlank()) {
            SmsForwarderApp.instance.logRepository.addLog(
                sender = smsData.sender,
                body = smsData.body,
                webhookUrl = "(not set)",
                error = "No webhook URL configured",
            )
            return
        }

        val effectiveTemplate = template.ifBlank { DEFAULT_TEMPLATE }
        val rendered = renderTemplate(effectiveTemplate, smsData.toTemplateMap())
        ForwardJob.enqueue(SmsForwarderApp.instance, smsData, webhookUrl, rendered, headerConfig)
    }

    /** True when the send succeeded or must not be retried. */
    fun send(
        sender: String,
        smsBody: String,
        webhookUrl: String,
        rendered: String,
        headerConfig: HeaderConfig,
        willRetry: Boolean,
        logFailure: Boolean = true,
    ): Boolean {
        val logRepo = SmsForwarderApp.instance.logRepository

        val reqHeaders = buildString {
            appendLine("${headerConfig.httpMethod} $webhookUrl")
            if (headerConfig.httpMethod != "GET") {
                appendLine("Content-Type: ${headerConfig.contentType}")
            }
            for ((key, value) in headerConfig.extraHeaders) {
                appendLine("$key: $value")
            }
        }.trim()

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

            client.newCall(requestBuilder.build()).execute().use { response ->
                val respHeaders = buildString {
                    appendLine("HTTP ${response.code} ${response.message}")
                    for (name in response.headers.names()) {
                        appendLine("$name: ${response.header(name)}")
                    }
                }.trim()

                // Server-side trouble is worth another try; a 4xx will fail the same way again.
                val retryable = response.code == 429 || response.code >= 500
                if (retryable && !logFailure) return false
                logRepo.addLog(
                    sender = sender,
                    body = smsBody,
                    webhookUrl = webhookUrl,
                    requestHeaders = reqHeaders,
                    requestJson = rendered,
                    responseCode = response.code,
                    responseHeaders = respHeaders,
                    responseBody = response.body?.string()?.take(1000),
                    error = if (retryable && willRetry) "Will retry" else null,
                )
                return !retryable
            }
        } catch (e: Exception) {
            if (!logFailure) return false
            logRepo.addLog(
                sender = sender,
                body = smsBody,
                webhookUrl = webhookUrl,
                requestHeaders = reqHeaders,
                requestJson = rendered,
                error = "${e.javaClass.simpleName}: ${e.message}" + if (willRetry) " (will retry)" else "",
            )
            return false
        }
    }
}
