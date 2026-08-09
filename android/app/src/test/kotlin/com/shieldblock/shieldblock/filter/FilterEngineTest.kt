package com.shieldblock.shieldblock.filter

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilterEngineTest {
    private lateinit var ruleset: FilterRuleset
    private lateinit var engine: FilterEngine

    @Before
    fun setUp() {
        ruleset = FilterLoader(repositoryFilterSource()).load()
        engine = FilterEngine(ruleset)
    }

    @Test
    fun `exact advertisement domain is blocked with metadata`() {
        val decision = engine.evaluate("doubleclick.net")

        assertTrue(decision.blocked)
        assertEquals("doubleclick.net", decision.matchedDomain)
        assertEquals("Google DoubleClick", decision.network)
        assertEquals(FilterCategory.ADVERTISEMENT, decision.category)
    }

    @Test
    fun `advertisement subdomain matches its parent rule`() {
        val decision = engine.evaluate("googleads.g.doubleclick.net")

        assertTrue(decision.blocked)
        assertEquals("doubleclick.net", decision.matchedDomain)
        assertEquals(FilterCategory.ADVERTISEMENT, decision.category)
    }

    @Test
    fun `tracker domain is blocked and categorized`() {
        val decision = engine.evaluate("app-measurement.com")

        assertTrue(decision.blocked)
        assertEquals("Google Analytics / Firebase", decision.network)
        assertEquals(FilterCategory.TRACKER, decision.category)
    }

    @Test
    fun `observed advertising and telemetry endpoints use verified metadata`() {
        val expected = mapOf(
            "ms4.applvn.com" to Pair("AppLovin", FilterCategory.ADVERTISEMENT),
            "app.adjust.com" to Pair("Adjust", FilterCategory.TRACKER),
            "edge.safedk.com" to Pair("AppLovin SafeDK", FilterCategory.TRACKER),
            "api.gameanalytics.com" to Pair("GameAnalytics", FilterCategory.TRACKER),
            "config.uca.cloud.unity3d.com" to Pair("Unity Analytics", FilterCategory.TRACKER),
        )

        expected.forEach { (domain, metadata) ->
            val decision = engine.evaluate(domain)
            assertTrue("Expected $domain to be blocked", decision.blocked)
            assertEquals(metadata.first, decision.network)
            assertEquals(metadata.second, decision.category)
        }
    }

    @Test
    fun `specific allowlist entry overrides broader block rule`() {
        engine.updateAllowlist(listOf("googleads.g.doubleclick.net"))

        val allowed = engine.evaluate("googleads.g.doubleclick.net")
        val stillBlocked = engine.evaluate("other.doubleclick.net")

        assertFalse(allowed.blocked)
        assertTrue(allowed.allowlistOverride)
        assertEquals("googleads.g.doubleclick.net", allowed.matchedDomain)
        assertTrue(stillBlocked.blocked)
    }

    @Test
    fun `custom blocklist blocks unknown domains and their subdomains`() {
        engine.updateCustomBlocklist(listOf("functional.example.com"))

        val exact = engine.evaluate("functional.example.com")
        val subdomain = engine.evaluate("api.functional.example.com")

        listOf(exact, subdomain).forEach { decision ->
            assertTrue(decision.blocked)
            assertEquals("functional.example.com", decision.matchedDomain)
            assertEquals("User rule", decision.network)
            assertEquals(FilterCategory.UNKNOWN, decision.category)
        }
    }

    @Test
    fun `allowlist keeps precedence over a custom block`() {
        engine.updateCustomBlocklist(listOf("example.com"))
        engine.updateAllowlist(listOf("safe.example.com"))

        val allowed = engine.evaluate("safe.example.com")
        val blocked = engine.evaluate("other.example.com")

        assertFalse(allowed.blocked)
        assertTrue(allowed.allowlistOverride)
        assertTrue(blocked.blocked)
        assertEquals("example.com", blocked.matchedDomain)
    }

    @Test
    fun `removing a custom block immediately restores unknown allow behavior`() {
        engine.updateCustomBlocklist(listOf("functional.example.com"))
        assertTrue(engine.isBlocked("functional.example.com"))

        engine.updateCustomBlocklist(emptyList())

        assertFalse(engine.isBlocked("functional.example.com"))
    }

    @Test
    fun `unknown domain is allowed without invented metadata`() {
        val decision = engine.evaluate("api.example.com")

        assertFalse(decision.blocked)
        assertEquals(FilterAction.ALLOW, decision.action)
        assertEquals(FilterCategory.UNKNOWN, decision.category)
        assertNull(decision.network)
        assertNull(decision.matchedDomain)
    }

    @Test
    fun `broad legitimate service domains are not blocked`() {
        val legitimateDomains = listOf(
            "google.com",
            "mail.google.com",
            "facebook.com",
            "graph.facebook.com",
            "firebaseremoteconfig.googleapis.com",
            "p16-ttam-va.ibyteimg.com",
            "unity3d.com",
            "amazon.com",
            "yandex.com",
        )

        legitimateDomains.forEach { domain ->
            assertFalse("Expected $domain to remain allowed", engine.isBlocked(domain))
        }
    }

    @Test
    fun `domain normalization is case insensitive and removes terminal dots`() {
        listOf("DOUBLECLICK.NET", "doubleclick.net.", "DoubleClick.Net").forEach { domain ->
            val decision = engine.evaluate(domain)
            assertTrue("Expected normalized $domain to be blocked", decision.blocked)
            assertEquals("doubleclick.net", decision.normalizedDomain)
        }
    }

    @Test
    fun `bundled ruleset is versioned and internally consistent`() {
        assertEquals(2, ruleset.version)
        assertEquals(48, ruleset.adDomains.size)
        assertEquals(53, ruleset.trackerDomains.size)
        assertTrue(ruleset.adDomains.size >= 40)
        assertTrue(ruleset.trackerDomains.size >= 40)
        assertEquals(ruleset.blockedDomains, ruleset.metadata.keys)
        assertTrue(ruleset.adDomains.intersect(ruleset.trackerDomains).isEmpty())
    }

    private fun repositoryFilterSource(): FilterSource {
        val directory = sequenceOf(
            File("assets/filters"),
            File("../assets/filters"),
            File("../../assets/filters"),
        ).firstOrNull(File::isDirectory)
            ?: error("Could not locate repository assets/filters directory")

        return FilterSource { path -> File(directory, path).readText(Charsets.UTF_8) }
    }
}
