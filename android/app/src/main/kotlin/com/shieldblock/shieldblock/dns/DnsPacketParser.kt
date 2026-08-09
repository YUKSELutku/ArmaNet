package com.shieldblock.shieldblock.dns

data class DnsQuery(
    val transactionId: Int,
    val domain: String,
    val type: Int,
    val questionCount: Int,
    val questionSectionEnd: Int,
    val recursionDesired: Boolean,
) {
    val isAddressQuery: Boolean get() = type == TYPE_A || type == TYPE_AAAA

    companion object {
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
    }
}

object DnsPacketParser {
    private const val HEADER_SIZE = 12
    private const val MAX_POINTER_DEPTH = 16

    fun parseQuery(packet: ByteArray): DnsQuery? {
        if (packet.size < HEADER_SIZE) return null
        val flags = readUnsignedShort(packet, 2)
        if (flags and 0x8000 != 0) return null
        val questionCount = readUnsignedShort(packet, 4)
        if (questionCount <= 0) return null

        var offset = HEADER_SIZE
        var firstDomain: String? = null
        var firstType = 0
        repeat(questionCount) { index ->
            val name = readName(packet, offset) ?: return null
            offset = name.nextOffset
            if (offset + 4 > packet.size) return null
            val type = readUnsignedShort(packet, offset)
            offset += 4 // QTYPE + QCLASS
            if (index == 0) {
                firstDomain = name.value
                firstType = type
            }
        }

        val domain = firstDomain?.takeIf(String::isNotBlank) ?: return null
        return DnsQuery(
            transactionId = readUnsignedShort(packet, 0),
            domain = domain,
            type = firstType,
            questionCount = questionCount,
            questionSectionEnd = offset,
            recursionDesired = flags and 0x0100 != 0,
        )
    }

    private fun readName(packet: ByteArray, start: Int): NameResult? {
        if (start !in packet.indices) return null
        val labels = mutableListOf<String>()
        var cursor = start
        var nextOffset = -1
        var pointerDepth = 0
        val visited = mutableSetOf<Int>()

        while (cursor in packet.indices) {
            val length = packet[cursor].toInt() and 0xff
            when {
                length == 0 -> {
                    if (nextOffset < 0) nextOffset = cursor + 1
                    return NameResult(labels.joinToString("."), nextOffset)
                }
                length and 0xc0 == 0xc0 -> {
                    if (cursor + 1 >= packet.size || pointerDepth++ >= MAX_POINTER_DEPTH) return null
                    val pointer = ((length and 0x3f) shl 8) or (packet[cursor + 1].toInt() and 0xff)
                    if (pointer !in packet.indices || !visited.add(pointer)) return null
                    if (nextOffset < 0) nextOffset = cursor + 2
                    cursor = pointer
                }
                length > 63 || cursor + 1 + length > packet.size -> return null
                else -> {
                    val label = packet.copyOfRange(cursor + 1, cursor + 1 + length)
                        .toString(Charsets.US_ASCII)
                    if (label.isBlank()) return null
                    labels += label
                    cursor += length + 1
                }
            }
        }
        return null
    }

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private data class NameResult(val value: String, val nextOffset: Int)
}
