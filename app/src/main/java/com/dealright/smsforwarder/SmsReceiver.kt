package com.dealright.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val format = intent.getStringExtra("format") ?: ""

        // Get subscription/SIM info
        val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
        } else {
            intent.getIntExtra("subscription", -1)
        }

        var simSlot = -1
        var simName = ""
        var carrierName = ""
        var countryIso = ""

        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subInfo = if (subId >= 0) {
                subManager?.getActiveSubscriptionInfo(subId)
            } else {
                subManager?.activeSubscriptionInfoList?.firstOrNull()
            }
            if (subInfo != null) {
                simSlot = subInfo.simSlotIndex
                simName = subInfo.displayName?.toString() ?: ""
                carrierName = subInfo.carrierName?.toString() ?: ""
                countryIso = subInfo.countryIso ?: ""
            }
        } catch (_: SecurityException) {
            // READ_PHONE_STATE not granted — sim fields stay empty
        }

        // Group message parts by sender (multi-part SMS)
        val grouped = messages.groupBy { it.originatingAddress ?: "Unknown" }

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

        for ((sender, parts) in grouped) {
            val first = parts.first()
            val body = parts.joinToString("") { it.messageBody ?: "" }

            val smsData = SmsData(
                sender = sender,
                body = body,
                timestamp = first.timestampMillis,
                serviceCenter = first.serviceCenterAddress ?: "",
                displayAddress = first.displayOriginatingAddress ?: sender,
                displayBody = first.displayMessageBody ?: body,
                isEmail = first.isEmail,
                protocolId = first.protocolIdentifier,
                status = first.status,
                simSlot = simSlot,
                simName = simName,
                carrierName = carrierName,
                countryIso = countryIso,
                format = format,
            )

            WebhookForwarder.forward(smsData, webhookUrl, template, headerConfig)
        }
    }
}
