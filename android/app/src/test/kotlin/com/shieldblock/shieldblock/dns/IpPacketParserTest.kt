package com.shieldblock.shieldblock.dns

import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpPacketParserTest {
    @Test
    fun `parses IPv4 UDP DNS payload`() {
        val raw = ipv4Packet(byteArrayOf(1, 2, 3, 4))

        val packet = IpPacketParser.parseUdp(raw)!!

        assertEquals(4, packet.ipVersion)
        assertEquals("10.111.222.1", packet.sourceAddress.hostAddress)
        assertEquals("10.111.222.2", packet.destinationAddress.hostAddress)
        assertEquals(53000, packet.sourcePort)
        assertEquals(53, packet.destinationPort)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), packet.payload)
    }

    @Test
    fun `parses IPv6 UDP payload without extension headers`() {
        val payload = byteArrayOf(8, 9)
        val source = InetAddress.getByName("2001:db8::1").address
        val destination = InetAddress.getByName("2001:db8::2").address
        val raw = ByteArray(48 + payload.size)
        raw[0] = 0x60
        writeShort(raw, 4, 8 + payload.size)
        raw[6] = 17
        raw[7] = 64
        source.copyInto(raw, 8)
        destination.copyInto(raw, 24)
        writeShort(raw, 40, 54000)
        writeShort(raw, 42, 53)
        writeShort(raw, 44, 8 + payload.size)
        payload.copyInto(raw, 48)

        val packet = IpPacketParser.parseUdp(raw)!!

        assertEquals(6, packet.ipVersion)
        assertEquals(54000, packet.sourcePort)
        assertArrayEquals(payload, packet.payload)
    }

    @Test
    fun `rejects fragmented IPv4 UDP`() {
        val raw = ipv4Packet(byteArrayOf(1))
        raw[6] = 0x20

        assertNull(IpPacketParser.parseUdp(raw))
    }

    @Test
    fun `response swaps endpoints and ports`() {
        val request = IpPacketParser.parseUdp(ipv4Packet(byteArrayOf(1)))!!

        val response = IpPacketParser.parseUdp(
            UdpResponsePacketBuilder.buildResponse(request, byteArrayOf(5, 6)),
        )!!

        assertEquals(request.destinationAddress, response.sourceAddress)
        assertEquals(request.sourceAddress, response.destinationAddress)
        assertEquals(53, response.sourcePort)
        assertEquals(53000, response.destinationPort)
        assertArrayEquals(byteArrayOf(5, 6), response.payload)
    }

    private fun ipv4Packet(payload: ByteArray): ByteArray {
        val result = ByteArray(28 + payload.size)
        result[0] = 0x45
        writeShort(result, 2, result.size)
        result[8] = 64
        result[9] = 17
        InetAddress.getByName("10.111.222.1").address.copyInto(result, 12)
        InetAddress.getByName("10.111.222.2").address.copyInto(result, 16)
        writeShort(result, 20, 53000)
        writeShort(result, 22, 53)
        writeShort(result, 24, 8 + payload.size)
        payload.copyInto(result, 28)
        return result
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }
}
