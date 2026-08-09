package com.shieldblock.shieldblock.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketParserTest {
    @Test
    fun `parses normalized A question fields`() {
        val packet = query("Example.COM", DnsQuery.TYPE_A)

        val parsed = DnsPacketParser.parseQuery(packet)!!

        assertEquals(0x1234, parsed.transactionId)
        assertEquals("Example.COM", parsed.domain)
        assertEquals(DnsQuery.TYPE_A, parsed.type)
        assertTrue(parsed.isAddressQuery)
        assertTrue(parsed.recursionDesired)
    }

    @Test
    fun `parses AAAA question`() {
        val parsed = DnsPacketParser.parseQuery(query("ipv6.example", DnsQuery.TYPE_AAAA))!!

        assertEquals("ipv6.example", parsed.domain)
        assertEquals(DnsQuery.TYPE_AAAA, parsed.type)
        assertTrue(parsed.isAddressQuery)
    }

    @Test
    fun `non-address DNS type is preserved but not classified as address query`() {
        val parsed = DnsPacketParser.parseQuery(query("example.com", 15))!!

        assertEquals(15, parsed.type)
        assertFalse(parsed.isAddressQuery)
    }

    @Test
    fun `compression pointer loop is rejected`() {
        val packet = ByteArray(18)
        packet[2] = 0x01
        packet[5] = 0x01
        packet[12] = 0xc0.toByte()
        packet[13] = 0x0c

        assertNull(DnsPacketParser.parseQuery(packet))
    }

    @Test
    fun `NXDOMAIN copies question and sets response flags`() {
        val packet = query("ads.example", DnsQuery.TYPE_A)
        val parsed = DnsPacketParser.parseQuery(packet)!!

        val response = DnsResponseBuilder.nxdomain(packet, parsed)

        assertEquals(packet.size, response.size)
        assertEquals(0x81, response[2].toInt() and 0xff)
        assertEquals(0x83, response[3].toInt() and 0xff)
        assertEquals(0, response[6].toInt())
        assertEquals(0, response[7].toInt())
        assertEquals(packet.copyOfRange(12, packet.size).toList(), response.copyOfRange(12, response.size).toList())
    }

    private fun query(domain: String, type: Int): ByteArray {
        val labels = domain.split('.')
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val result = ByteArray(size)
        result[0] = 0x12
        result[1] = 0x34
        result[2] = 0x01
        result[5] = 0x01
        var offset = 12
        labels.forEach { label ->
            result[offset++] = label.length.toByte()
            label.toByteArray(Charsets.US_ASCII).copyInto(result, offset)
            offset += label.length
        }
        result[offset++] = 0
        result[offset++] = (type ushr 8).toByte()
        result[offset++] = type.toByte()
        result[offset++] = 0
        result[offset] = 1
        return result
    }
}
