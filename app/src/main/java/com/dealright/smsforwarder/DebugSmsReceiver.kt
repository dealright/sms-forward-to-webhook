package com.dealright.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug receiver that simulates an incoming SMS via adb broadcast.
 *
 * Usage from adb:
 *   adb shell am broadcast \
 *     -a com.dealright.smsforwarder.DEBUG_SMS \
 *     --es sender "+15551234567" \
 *     --es body "Hello from adb" \
 *     -n com.dealright.smsforwarder/.DebugSmsReceiver
 */
class DebugSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: "+0000000000"
        val body = intent.getStringExtra("body") ?: "(empty)"

        val prefs = context.getSharedPreferences("sms_forwarder", Context.MODE_PRIVATE)
        val webhookUrl = prefs.getString("webhook_url", "") ?: ""
        val template = prefs.getString("json_template", "") ?: ""
        val contentType = prefs.getString("content_type", "application/json") ?: "application/json"
        val customHeadersRaw = prefs.getString("custom_headers", "") ?: ""

        val extraHeaders = mutableMapOf<String, String>()
        for (line in customHeadersRaw.lines()) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                extraHeaders[parts[0].trim()] = parts[1].trim()
            }
        }
        val headerConfig = HeaderConfig(contentType = contentType, extraHeaders = extraHeaders)

        val smsData = SmsData(
            sender = sender,
            body = body,
            timestamp = System.currentTimeMillis(),
            serviceCenter = "",
            displayAddress = sender,
            displayBody = body,
            isEmail = false,
            protocolId = 0,
            status = -1,
            simSlot = 0,
            simName = "DEBUG",
            carrierName = "adb",
            countryIso = "us",
            format = "debug",
        )

        WebhookForwarder.forward(smsData, webhookUrl, template, headerConfig)
    }
}
