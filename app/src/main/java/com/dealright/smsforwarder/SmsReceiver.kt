package com.dealright.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

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

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        // Per-SIM routing: a slot-specific webhook overrides the global one;
        // a blank slot URL or an undetermined slot falls back to the global default.
        val webhookUrl = Prefs.resolveWebhookUrl(prefs, simSlot)
        val template = prefs.getString("json_template", "") ?: ""
        val httpMethod = prefs.getString("http_method", "POST") ?: "POST"
        val contentType = prefs.getString("content_type", "application/json") ?: "application/json"
        val customHeadersRaw = prefs.getString("custom_headers", "") ?: ""

        val extraHeaders = mutableMapOf<String, String>()
        for (line in customHeadersRaw.lines()) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                extraHeaders[parts[0].trim()] = parts[1].trim()
            }
        }
        val headerConfig = HeaderConfig(httpMethod = httpMethod, contentType = contentType, extraHeaders = extraHeaders)

        // Keep the broadcast (and the process) alive until every POST finishes. Without
        // goAsync() the receiver is considered done the instant onReceive returns, and in
        // Doze the process is frozen before the fire-and-forget coroutines can complete —
        // which is why forwarding stopped after the phone had been idle ~30 minutes. The
        // wakelock guarantees CPU stays on for that window; both are released in finally.
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "com.dealright.smsforwarder:forward",
        ).apply { acquire(WAKELOCK_TIMEOUT_MS) }

        val jobs = mutableListOf<Job>()
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

            jobs += WebhookForwarder.forward(smsData, webhookUrl, template, headerConfig)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                jobs.joinAll()
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    private companion object {
        // Safety backstop; the POST itself is capped at 8s (callTimeout) in WebhookForwarder.
        private const val WAKELOCK_TIMEOUT_MS = 15_000L
    }
}
