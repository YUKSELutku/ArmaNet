package com.shieldblock.shieldblock.traffic

import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import com.shieldblock.shieldblock.dns.UdpPacket
import java.net.InetSocketAddress

class ConnectionOwnerResolver(private val connectivityManager: ConnectivityManager) {
    fun resolveUid(packet: UdpPacket): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            connectivityManager.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(packet.sourceAddress, packet.sourcePort),
                InetSocketAddress(packet.destinationAddress, packet.destinationPort),
            ).takeIf { it >= 0 }
        }.getOrNull()
    }
}
