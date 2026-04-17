package com.dealright.smsforwarder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LogEntry(
    val timestamp: String,
    val sender: String,
    val body: String,
    val webhookUrl: String,
    val requestHeaders: String,
    val requestJson: String,
    val responseCode: Int?,
    val responseHeaders: String?,
    val responseBody: String?,
    val error: String?,
) {
    val success: Boolean get() = responseCode != null && responseCode in 200..299
}

class LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun addLog(
        sender: String,
        body: String,
        webhookUrl: String,
        requestHeaders: String = "",
        requestJson: String = "",
        responseCode: Int? = null,
        responseHeaders: String? = null,
        responseBody: String? = null,
        error: String? = null,
    ) {
        val entry = LogEntry(
            timestamp = LocalDateTime.now().format(formatter),
            sender = sender,
            body = body,
            webhookUrl = webhookUrl,
            requestHeaders = requestHeaders,
            requestJson = requestJson,
            responseCode = responseCode,
            responseHeaders = responseHeaders,
            responseBody = responseBody,
            error = error,
        )
        _logs.update { listOf(entry) + it }
    }

    fun clear() {
        _logs.update { emptyList() }
    }
}
