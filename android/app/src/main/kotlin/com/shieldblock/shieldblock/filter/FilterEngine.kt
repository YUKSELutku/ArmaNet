package com.shieldblock.shieldblock.filter

class FilterEngine(
    initialRuleset: FilterRuleset,
    allowlistDomains: Collection<String> = emptySet(),
    customBlocklistDomains: Collection<String> = emptySet(),
) {
    @Volatile
    private var ruleset: FilterRuleset = initialRuleset

    @Volatile
    private var allowlist: Set<String> = normalizeAllowlist(allowlistDomains)

    @Volatile
    private var customBlocklist: Set<String> = normalizeDomains(customBlocklistDomains)

    fun evaluate(domain: String?): FilterDecision {
        val normalized = DomainNormalizer.normalize(domain)
            ?: return FilterDecision(FilterAction.ALLOW, normalizedDomain = null)

        val allowMatch = findMatch(normalized, allowlist)
        if (allowMatch != null) {
            return FilterDecision(
                action = FilterAction.ALLOW,
                normalizedDomain = normalized,
                matchedDomain = allowMatch,
                allowlistOverride = true,
            )
        }

        val customBlockMatch = findMatch(normalized, customBlocklist)
        if (customBlockMatch != null) {
            return FilterDecision(
                action = FilterAction.BLOCK,
                normalizedDomain = normalized,
                matchedDomain = customBlockMatch,
                network = "User rule",
                category = FilterCategory.UNKNOWN,
            )
        }

        val currentRuleset = ruleset
        val blockMatch = findMatch(normalized, currentRuleset.blockedDomains)
            ?: return FilterDecision(FilterAction.ALLOW, normalizedDomain = normalized)
        val metadata = currentRuleset.metadata.getValue(blockMatch)
        return FilterDecision(
            action = FilterAction.BLOCK,
            normalizedDomain = normalized,
            matchedDomain = blockMatch,
            network = metadata.network,
            category = metadata.category,
        )
    }

    fun isBlocked(domain: String?): Boolean = evaluate(domain).blocked

    fun getCategory(domain: String?): FilterCategory = evaluate(domain).category

    fun getNetworkName(domain: String?): String? = evaluate(domain).network

    fun updateAllowlist(domains: Collection<String>) {
        allowlist = normalizeAllowlist(domains)
    }

    fun updateCustomBlocklist(domains: Collection<String>) {
        customBlocklist = normalizeDomains(domains)
    }

    /**
     * Atomic hook for a future verified remote source. Rollbacks and same-version
     * replacements are rejected so a failed update leaves the active rules intact.
     */
    fun replaceRuleset(candidate: FilterRuleset): Boolean {
        synchronized(this) {
            if (candidate.version <= ruleset.version) return false
            ruleset = candidate
            return true
        }
    }

    fun filterVersion(): Int = ruleset.version

    private fun normalizeAllowlist(domains: Collection<String>): Set<String> =
        normalizeDomains(domains)

    private fun normalizeDomains(domains: Collection<String>): Set<String> =
        domains.mapNotNull(DomainNormalizer::normalize).toSet()

    private fun findMatch(domain: String, candidates: Set<String>): String? =
        DomainNormalizer.suffixes(domain).firstOrNull(candidates::contains)
}
