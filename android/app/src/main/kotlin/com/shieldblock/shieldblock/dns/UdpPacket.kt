package com.shieldblock.shieldblock.dns

import java.net.InetAddress

data class UdpPacket(
    val ipVersion: Int,
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)
