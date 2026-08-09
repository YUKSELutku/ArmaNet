package com.shieldblock.shieldblock.dns

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class UpstreamDnsResolver(
    private val vpnService: VpnService,
    private val connectivityManager: ConnectivityManager,
) {
    fun resolve(query: ByteArray): ResolutionResult {
        return try {
            val network = findUnderlyingNetwork()
            val servers = network?.let { connectivityManager.getLinkProperties(it)?.dnsServers }.orEmpty()
                .ifEmpty { listOf(InetAddress.getByName(FALLBACK_DNS)) }
            var lastError: Throwable? = null
            servers.take(MAX_SERVERS).forEach { server ->
                try {
                    return ResolutionResult.Success(queryServer(query, network, server), server.hostAddress)
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            ResolutionResult.Failure(lastError?.message ?: "No reachable upstream DNS server")
        } catch (error: Throwable) {
            ResolutionResult.Failure(error.message ?: "Could not inspect the underlying network")
        }
    }

    fun hasActivePrivateDns(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            findUnderlyingNetwork()
                ?.let(connectivityManager::getLinkProperties)
                ?.isPrivateDnsActive == true
        }.getOrDefault(false)
    }

    private fun queryServer(query: ByteArray, network: Network?, server: InetAddress): ByteArray {
        DatagramSocket(null).use { socket ->
            if (!vpnService.protect(socket)) error("Could not protect upstream DNS socket")
            network?.bindSocket(socket)
            socket.bind(InetSocketAddress(0))
            socket.soTimeout = TIMEOUT_MS
            socket.send(DatagramPacket(query, query.size, server, DNS_PORT))
            val buffer = ByteArray(MAX_DNS_PACKET)
            val response = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(response)
            } catch (timeout: SocketTimeoutException) {
                throw timeout
            }
            return buffer.copyOf(response.length)
        }
    }

    private fun findUnderlyingNetwork(): Network? {
        fun isUsable(network: Network): Boolean {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        connectivityManager.activeNetwork?.takeIf(::isUsable)?.let { return it }
        return connectivityManager.allNetworks.firstOrNull(::isUsable)
    }

    sealed interface ResolutionResult {
        data class Success(val payload: ByteArray, val serverAddress: String?) : ResolutionResult
        data class Failure(val reason: String) : ResolutionResult
    }

    companion object {
        private const val DNS_PORT = 53
        private const val TIMEOUT_MS = 3_000
        private const val MAX_DNS_PACKET = 8_192
        private const val MAX_SERVERS = 3
        private const val FALLBACK_DNS = "1.1.1.1"
    }
}
