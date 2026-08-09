package com.shieldblock.shieldblock.dns

object DnsResponseBuilder {
    fun nxdomain(queryPacket: ByteArray, query: DnsQuery): ByteArray =
        errorResponse(queryPacket, query, responseCode = 3)

    fun serverFailure(queryPacket: ByteArray, query: DnsQuery): ByteArray =
        errorResponse(queryPacket, query, responseCode = 2)

    private fun errorResponse(
        queryPacket: ByteArray,
        query: DnsQuery,
        responseCode: Int,
    ): ByteArray {
        val end = query.questionSectionEnd.coerceAtMost(queryPacket.size)
        val response = queryPacket.copyOfRange(0, end)
        val flags = 0x8000 or 0x0080 or responseCode or if (query.recursionDesired) 0x0100 else 0
        response[2] = (flags shr 8).toByte()
        response[3] = flags.toByte()
        writeUnsignedShort(response, 4, query.questionCount)
        writeUnsignedShort(response, 6, 0)
        writeUnsignedShort(response, 8, 0)
        writeUnsignedShort(response, 10, 0)
        return response
    }

    private fun writeUnsignedShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value shr 8).toByte()
        data[offset + 1] = value.toByte()
    }
}
