package com.dealright.smsforwarder

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("sms_forwarder", Context.MODE_PRIVATE)
    val logs = SmsForwarderApp.instance.logRepository.logs

    init {
        // Migrate: if webhook is blank from a previous install, apply new default
        if (prefs.getString("webhook_url", "").isNullOrBlank()) {
            prefs.edit().putString("webhook_url", DEFAULT_WEBHOOK_URL).apply()
        }
    }

    private val _webhookUrl = MutableStateFlow(prefs.getString("webhook_url", DEFAULT_WEBHOOK_URL) ?: DEFAULT_WEBHOOK_URL)
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    private val _jsonTemplate = MutableStateFlow(
        prefs.getString("json_template", DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE
    )
    val jsonTemplate: StateFlow<String> = _jsonTemplate.asStateFlow()

    private val _contentType = MutableStateFlow(
        prefs.getString("content_type", CONTENT_TYPES[0]) ?: CONTENT_TYPES[0]
    )
    val contentType: StateFlow<String> = _contentType.asStateFlow()

    private val _customHeaders = MutableStateFlow(
        prefs.getString("custom_headers", "") ?: ""
    )
    val customHeaders: StateFlow<String> = _customHeaders.asStateFlow()

    fun setWebhookUrl(url: String) {
        _webhookUrl.value = url
        prefs.edit().putString("webhook_url", url).apply()
    }

    fun setJsonTemplate(template: String) {
        _jsonTemplate.value = template
        prefs.edit().putString("json_template", template).apply()
    }

    fun setContentType(ct: String) {
        _contentType.value = ct
        prefs.edit().putString("content_type", ct).apply()
    }

    fun setCustomHeaders(headers: String) {
        _customHeaders.value = headers
        prefs.edit().putString("custom_headers", headers).apply()
    }

    fun buildHeaderConfig(): HeaderConfig {
        val extra = mutableMapOf<String, String>()
        for (line in _customHeaders.value.lines()) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                extra[parts[0].trim()] = parts[1].trim()
            }
        }
        return HeaderConfig(
            contentType = _contentType.value,
            extraHeaders = extra,
        )
    }

    fun clearLogs() {
        SmsForwarderApp.instance.logRepository.clear()
    }

    fun sendTestMessage() {
        val testData = SmsData(
            sender = "+1234567890",
            body = "This is a test message from sms-forward-to-webhook",
            timestamp = System.currentTimeMillis(),
            serviceCenter = "+15551234000",
            displayAddress = "+1234567890",
            displayBody = "This is a test message from sms-forward-to-webhook",
            isEmail = false,
            protocolId = 0,
            status = -1,
            simSlot = 0,
            simName = "SIM 1",
            carrierName = "Test Carrier",
            countryIso = "us",
            format = "3gpp",
        )
        WebhookForwarder.forward(testData, _webhookUrl.value, _jsonTemplate.value, buildHeaderConfig())
    }

    companion object {
        const val DEFAULT_WEBHOOK_URL = "https://chat.googleapis.com/v1/spaces/XXXXXXXXXXXXX/messages?key=YOUR_API_KEY&token=YOUR_TOKEN"
        const val DEFAULT_TEMPLATE = """{"text": "SMS from {{sender}}: {{body}}"}"""
        val CONTENT_TYPES = listOf(
            "application/json",
            "text/plain",
            "application/x-www-form-urlencoded",
        )
    }
}
