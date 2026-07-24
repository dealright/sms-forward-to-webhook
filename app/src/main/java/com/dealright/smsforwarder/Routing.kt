package com.dealright.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SubscriptionManager

/** SharedPreferences store name and keys, plus per-SIM webhook routing. */
object Prefs {
    const val NAME = "sms_forwarder"
    const val WEBHOOK_URL = "webhook_url"

    /** Number of SIM slots the UI exposes per-webhook fields for (dual SIM). */
    const val MAX_SIM_SLOTS = 2

    /** Per-SIM override key for a given slot, e.g. "webhook_url_sim0". */
    fun simKey(slot: Int): String = "webhook_url_sim$slot"

    /**
     * Resolves the webhook URL for an incoming SMS by SIM slot.
     * A slot-specific URL overrides the global one; a blank slot URL
     * (or an undetermined slot, -1) falls back to the global webhook_url.
     */
    fun resolveWebhookUrl(prefs: SharedPreferences, slot: Int): String {
        val global = prefs.getString(WEBHOOK_URL, "") ?: ""
        if (slot < 0) return global
        val perSim = prefs.getString(simKey(slot), "") ?: ""
        return perSim.ifBlank { global }
    }
}

/** An active SIM detected on the device, used to label per-SIM webhook fields. */
data class SimInfo(
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String,
) {
    /** Human label like "SIM 1 · Verizon · +15551234567" (slot is 0-based, shown 1-based). */
    fun label(): String = buildList {
        add("SIM ${slotIndex + 1}")
        val name = displayName.ifBlank { carrierName }
        if (name.isNotBlank()) add(name)
        if (number.isNotBlank()) add(number)
    }.joinToString(" · ")
}

/**
 * Reads the device's active SIMs. Requires READ_PHONE_STATE; returns an
 * empty list if the permission is not granted or no SIMs are active.
 */
@Suppress("DEPRECATION") // SubscriptionInfo.getNumber(); the API 33 replacement needs READ_PHONE_NUMBERS.
fun detectSims(context: Context): List<SimInfo> {
    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
        as? SubscriptionManager ?: return emptyList()
    return try {
        sm.activeSubscriptionInfoList
            ?.sortedBy { it.simSlotIndex }
            ?.map { info ->
                SimInfo(
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString().orEmpty(),
                    carrierName = info.carrierName?.toString().orEmpty(),
                    number = runCatching { info.number.orEmpty() }.getOrDefault(""),
                )
            } ?: emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }
}
