package com.dealright.smsforwarder

/**
 * All fields extractable from an incoming SMS.
 * Each field becomes a {{placeholder}} in the JSON template.
 */
data class SmsData(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val serviceCenter: String,
    val displayAddress: String,
    val displayBody: String,
    val isEmail: Boolean,
    val protocolId: Int,
    val status: Int,
    val simSlot: Int,
    val simName: String,
    val carrierName: String,
    val countryIso: String,
    val format: String,
) {
    /** Returns a map of all available template variables and their values. */
    fun toTemplateMap(): Map<String, String> = mapOf(
        "sender" to sender,
        "body" to body,
        "timestamp" to timestamp.toString(),
        "service_center" to serviceCenter,
        "display_address" to displayAddress,
        "display_body" to displayBody,
        "is_email" to isEmail.toString(),
        "protocol_id" to protocolId.toString(),
        "status" to status.toString(),
        "sim_slot" to simSlot.toString(),
        "sim_name" to simName,
        "carrier" to carrierName,
        "country" to countryIso,
        "format" to format,
    )

    companion object {
        /** All available placeholder names with descriptions. */
        val VARIABLE_DOCS: List<Pair<String, String>> = listOf(
            "sender" to "Sender phone number",
            "body" to "Message text",
            "timestamp" to "Unix timestamp (ms)",
            "service_center" to "SMSC address",
            "display_address" to "Formatted sender",
            "display_body" to "Formatted message",
            "is_email" to "true if email gateway",
            "protocol_id" to "Protocol identifier",
            "status" to "Message status code",
            "sim_slot" to "SIM slot index (0, 1, ...)",
            "sim_name" to "SIM display name",
            "carrier" to "Carrier name",
            "country" to "Country ISO code",
            "format" to "3gpp or 3gpp2",
        )
    }
}
