package com.shieldblock.shieldblock.filter

import org.json.JSONException
import org.json.JSONObject

class FilterLoader(private val source: FilterSource) {
    fun load(): FilterRuleset {
        val adDomains = parseDomainList("ads.txt")
        val trackerDomains = parseDomainList("trackers.txt")
        val overlap = adDomains intersect trackerDomains
        if (overlap.isNotEmpty()) {
            throw FilterLoadException("Domains cannot be both advertisement and tracker rules: $overlap")
        }

        val metadataDocument = parseMetadata(source.readText("networks.json"))
        val allDomains = adDomains + trackerDomains
        val missingMetadata = allDomains - metadataDocument.metadata.keys
        if (missingMetadata.isNotEmpty()) {
            throw FilterLoadException("Missing network metadata for: $missingMetadata")
        }

        validateCategories(adDomains, trackerDomains, metadataDocument.metadata)

        return FilterRuleset(
            version = metadataDocument.version,
            updatedAt = metadataDocument.updatedAt,
            adDomains = adDomains,
            trackerDomains = trackerDomains,
            metadata = metadataDocument.metadata.filterKeys { it in allDomains },
        )
    }

    private fun parseDomainList(path: String): Set<String> =
        source.readText(path)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') && !it.startsWith('!') }
            .map { raw ->
                DomainNormalizer.normalize(raw)
                    ?: throw FilterLoadException("Invalid domain in $path: $raw")
            }
            .toCollection(linkedSetOf())

    private fun parseMetadata(json: String): MetadataDocument {
        try {
            val root = JSONObject(json)
            val version = root.optInt("filterVersion", 0)
            if (version <= 0) throw FilterLoadException("filterVersion must be a positive integer")

            val updatedAt = root.optString("updatedAt").trim()
            if (updatedAt.isEmpty()) throw FilterLoadException("updatedAt is required")

            val networks = root.optJSONObject("networks")
                ?: throw FilterLoadException("networks object is required")
            val metadata = linkedMapOf<String, NetworkMetadata>()
            for (rawDomain in networks.keys()) {
                val domain = DomainNormalizer.normalize(rawDomain)
                    ?: throw FilterLoadException("Invalid metadata domain: $rawDomain")
                if (domain != rawDomain) {
                    throw FilterLoadException("Metadata domain must be canonical: $rawDomain")
                }

                val value = networks.getJSONObject(rawDomain)
                val network = value.getString("network").trim()
                if (network.isEmpty()) throw FilterLoadException("Empty network name for $domain")
                val category = FilterCategory.fromWireValue(value.getString("category"))
                if (category == FilterCategory.UNKNOWN) {
                    throw FilterLoadException("Unknown is not a blocklist category: $domain")
                }
                metadata[domain] = NetworkMetadata(network, category)
            }
            return MetadataDocument(version, updatedAt, metadata)
        } catch (error: JSONException) {
            throw FilterLoadException("Invalid networks.json", error)
        }
    }

    private fun validateCategories(
        adDomains: Set<String>,
        trackerDomains: Set<String>,
        metadata: Map<String, NetworkMetadata>,
    ) {
        adDomains.forEach { domain ->
            if (metadata.getValue(domain).category != FilterCategory.ADVERTISEMENT) {
                throw FilterLoadException("ads.txt metadata category mismatch: $domain")
            }
        }
        trackerDomains.forEach { domain ->
            if (metadata.getValue(domain).category != FilterCategory.TRACKER) {
                throw FilterLoadException("trackers.txt metadata category mismatch: $domain")
            }
        }
    }

    private data class MetadataDocument(
        val version: Int,
        val updatedAt: String,
        val metadata: Map<String, NetworkMetadata>,
    )
}
