package com.shieldblock.shieldblock.filter

import java.util.Locale

enum class FilterCategory(val wireValue: String) {
    ADVERTISEMENT("advertisement"),
    TRACKER("tracker"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(value: String): FilterCategory =
            entries.firstOrNull { it.wireValue == value.lowercase(Locale.ROOT) }
                ?: throw FilterLoadException("Unsupported filter category: $value")
    }
}

enum class FilterAction {
    ALLOW,
    BLOCK,
}

data class NetworkMetadata(
    val network: String,
    val category: FilterCategory,
)

data class FilterDecision(
    val action: FilterAction,
    val normalizedDomain: String?,
    val matchedDomain: String? = null,
    val network: String? = null,
    val category: FilterCategory = FilterCategory.UNKNOWN,
    val allowlistOverride: Boolean = false,
) {
    val blocked: Boolean get() = action == FilterAction.BLOCK
}

data class FilterRuleset(
    val version: Int,
    val updatedAt: String,
    val adDomains: Set<String>,
    val trackerDomains: Set<String>,
    val metadata: Map<String, NetworkMetadata>,
) {
    val blockedDomains: Set<String> = adDomains + trackerDomains
}

class FilterLoadException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
