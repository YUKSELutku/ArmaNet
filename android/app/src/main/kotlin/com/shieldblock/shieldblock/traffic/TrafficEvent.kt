package com.shieldblock.shieldblock.traffic

data class TrafficEvent(
    val id: Long = 0,
    val uid: Int? = null,
    val packageName: String? = null,
    val appName: String? = null,
    val domain: String,
    val destinationIp: String? = null,
    val destinationPort: Int? = null,
    val protocol: String,
    val networkName: String? = null,
    val category: String = "unknown",
    val blocked: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val attributionConfidence: AttributionConfidence = AttributionConfidence.UNAVAILABLE,
)

enum class AttributionConfidence(val wireValue: String) {
    HIGH("high"),
    LOW("low"),
    UNAVAILABLE("unavailable");

    companion object {
        fun fromWireValue(value: String?): AttributionConfidence =
            entries.firstOrNull { it.wireValue == value } ?: UNAVAILABLE
    }
}

data class AppIdentity(
    val uid: Int? = null,
    val packageName: String? = null,
    val appName: String? = null,
    val confidence: AttributionConfidence = AttributionConfidence.UNAVAILABLE,
)
