package com.shieldblock.shieldblock.dns

object UdpResponsePacketBuilder {
    fun buildResponse(request: UdpPacket, responsePayload: ByteArray): ByteArray =
        if (request.ipVersion == 6) buildIpv6(request, responsePayload) else buildIpv4(request, responsePayload)

    private fun buildIpv4(request: UdpPacket, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val result = ByteArray(totalLength)
        result[0] = 0x45
        writeShort(result, 2, totalLength)
        result[6] = 0x40
        result[8] = 64
        result[9] = 17
        request.destinationAddress.address.copyInto(result, 12)
        request.sourceAddress.address.copyInto(result, 16)
        writeShort(result, 10, checksum(result, 0, 20))
        writeUdp(result, 20, request, payload)
        val udpChecksum = udpChecksumIpv4(result, 20, udpLength)
        writeShort(result, 26, if (udpChecksum == 0) 0xffff else udpChecksum)
        return result
    }

    private fun buildIpv6(request: UdpPacket, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        val result = ByteArray(40 + udpLength)
        result[0] = 0x60
        writeShort(result, 4, udpLength)
        result[6] = 17
        result[7] = 64
        request.destinationAddress.address.copyInto(result, 8)
        request.sourceAddress.address.copyInto(result, 24)
        writeUdp(result, 40, request, payload)
        val udpChecksum = udpChecksumIpv6(result, 40, udpLength)
        writeShort(result, 46, if (udpChecksum == 0) 0xffff else udpChecksum)
        return result
    }

    private fun writeUdp(result: ByteArray, offset: Int, request: UdpPacket, payload: ByteArray) {
        writeShort(result, offset, request.destinationPort)
        writeShort(result, offset + 2, request.sourcePort)
        writeShort(result, offset + 4, 8 + payload.size)
        payload.copyInto(result, offset + 8)
    }

    private fun udpChecksumIpv4(data: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        sum = addBytes(sum, data, 12, 8)
        sum += 17
        sum += udpLength
        sum = addBytes(sum, data, udpOffset, udpLength)
        return finishChecksum(sum)
    }

    private fun udpChecksumIpv6(data: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        sum = addBytes(sum, data, 8, 32)
        sum += (udpLength ushr 16) and 0xffff
        sum += udpLength and 0xffff
        sum += 17
        sum = addBytes(sum, data, udpOffset, udpLength)
        return finishChecksum(sum)
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int =
        finishChecksum(addBytes(0, data, offset, length))

    private fun addBytes(initial: Long, data: ByteArray, offset: Int, length: Int): Long {
        var sum = initial
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += ((data[index].toInt() and 0xff) shl 8) or (data[index + 1].toInt() and 0xff)
            index += 2
        }
        if (index < end) sum += (data[index].toInt() and 0xff) shl 8
        return sum
    }

    private fun finishChecksum(initial: Long): Int {
        var sum = initial
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }
}
