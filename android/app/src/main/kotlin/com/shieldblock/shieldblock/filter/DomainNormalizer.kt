package com.shieldblock.shieldblock.filter

import java.net.IDN
import java.util.Locale

object DomainNormalizer {
    fun normalize(domain: String?): String? {
        val trimmed = domain?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT) ?: return null
        if (trimmed.isEmpty() || trimmed.length > 253) return null

        return try {
            val ascii = IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            if (ascii.isEmpty() || ascii.length > 253 || !hasValidLabels(ascii)) null else ascii
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun hasValidLabels(domain: String): Boolean =
        domain.split('.').all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                label.first() != '-' &&
                label.last() != '-'
        }

    fun suffixes(domain: String): Sequence<String> = sequence {
        var candidate = domain
        while (true) {
            yield(candidate)
            val dot = candidate.indexOf('.')
            if (dot < 0) break
            candidate = candidate.substring(dot + 1)
        }
    }
}
