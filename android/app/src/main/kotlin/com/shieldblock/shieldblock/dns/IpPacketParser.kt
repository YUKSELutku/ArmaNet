package com.shieldblock.shieldblock.dns

import java.net.InetAddress

object IpPacketParser {
    private const val UDP_PROTOCOL = 17

    fun parseUdp(packet: ByteArray, length: Int = packet.size): UdpPacket? {
        if (length < 1) return null
        return when ((packet[0].toInt() ushr 4) and 0x0f) {
            4 -> parseIpv4(packet, length)
            6 -> parseIpv6(packet, length)
            else -> null
        }
    }

    private fun parseIpv4(data: ByteArray, length: Int): UdpPacket? {
        if (length < 28) return null
        val headerLength = (data[0].toInt() and 0x0f) * 4
        if (headerLength < 20 || length < headerLength + 8) return null
        val totalLength = readUnsignedShort(data, 2)
        if (totalLength < headerLength + 8 || totalLength > length) return null
        if ((data[9].toInt() and 0xff) != UDP_PROTOCOL) return null
        val fragment = readUnsignedShort(data, 6)
        if (fragment and 0x3fff != 0) return null
        return parseUdpPayload(
            data = data,
            udpOffset = headerLength,
            packetEnd = totalLength,
            ipVersion = 4,
            source = InetAddress.getByAddress(data.copyOfRange(12, 16)),
            destination = InetAddress.getByAddress(data.copyOfRange(16, 20)),
        )
    }

    private fun parseIpv6(data: ByteArray, length: Int): UdpPacket? {
        if (length < 48 || (data[6].toInt() and 0xff) != UDP_PROTOCOL) return null
        val payloadLength = readUnsignedShort(data, 4)
        val packetEnd = 40 + payloadLength
        if (packetEnd > length || payloadLength < 8) return null
        return parseUdpPayload(
            data = data,
            udpOffset = 40,
            packetEnd = packetEnd,
            ipVersion = 6,
            source = InetAddress.getByAddress(data.copyOfRange(8, 24)),
            destination = InetAddress.getByAddress(data.copyOfRange(24, 40)),
        )
    }

    private fun parseUdpPayload(
        data: ByteArray,
        udpOffset: Int,
        packetEnd: Int,
        ipVersion: Int,
        source: InetAddress,
        destination: InetAddress,
    ): UdpPacket? {
        val udpLength = readUnsignedShort(data, udpOffset + 4)
        if (udpLength < 8 || udpOffset + udpLength > packetEnd) return null
        return UdpPacket(
            ipVersion = ipVersion,
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = readUnsignedShort(data, udpOffset),
            destinationPort = readUnsignedShort(data, udpOffset + 2),
            payload = data.copyOfRange(udpOffset + 8, udpOffset + udpLength),
        )
    }

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
}
