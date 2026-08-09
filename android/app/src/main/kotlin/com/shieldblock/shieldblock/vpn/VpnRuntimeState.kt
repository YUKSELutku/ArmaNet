package com.shieldblock.shieldblock.vpn

import com.shieldblock.shieldblock.bridge.NativeEventBus
import java.util.concurrent.atomic.AtomicLong

enum class VpnState(val wireValue: String) {
    STOPPED("stopped"),
    STARTING("starting"),
    RUNNING("running"),
    STOPPING("stopping"),
    ERROR("error");
}

data class VpnStatus(
    val state: VpnState,
    val detail: String? = null,
    val startedAt: Long? = null,
) {
    val running: Boolean get() = state == VpnState.RUNNING
}

object VpnRuntimeState {
    @Volatile
    private var current = VpnStatus(VpnState.STOPPED)

    private val dnsQueries = AtomicLong(0)
    private val blockedQueries = AtomicLong(0)
    private val packetErrors = AtomicLong(0)

    @Volatile
    private var lastDnsAt: Long? = null

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var privateDnsActive = false

    fun snapshot(): VpnStatus = current

    @Synchronized
    fun resetHealth(hasPrivateDns: Boolean) {
        dnsQueries.set(0)
        blockedQueries.set(0)
        packetErrors.set(0)
        lastDnsAt = null
        lastError = null
        privateDnsActive = hasPrivateDns
    }

    fun recordDnsQuery(blocked: Boolean) {
        dnsQueries.incrementAndGet()
        if (blocked) blockedQueries.incrementAndGet()
        lastDnsAt = System.currentTimeMillis()
    }

    fun recordPacketError(error: Throwable) {
        packetErrors.incrementAndGet()
        lastError = error.message ?: error.javaClass.simpleName
    }

    @Synchronized
    fun update(state: VpnState, detail: String? = null) {
        val previous = current
        val startedAt = when (state) {
            VpnState.RUNNING -> previous.startedAt ?: System.currentTimeMillis()
            else -> null
        }
        current = VpnStatus(state, detail, startedAt)
        NativeEventBus.emit(
            type = when (state) {
                VpnState.RUNNING -> "VPN_STARTED"
                VpnState.STOPPED -> "VPN_STOPPED"
                else -> "VPN_STATUS_CHANGED"
            },
            payload = mapOf("status" to current.toMap()),
        )
    }

    fun VpnStatus.toMap(): Map<String, Any?> = mapOf(
        "state" to state.wireValue,
        "running" to running,
        "detail" to detail,
        "startedAt" to startedAt,
        "dnsQueries" to dnsQueries.get(),
        "blockedQueries" to blockedQueries.get(),
        "packetErrors" to packetErrors.get(),
        "lastDnsAt" to lastDnsAt,
        "lastError" to lastError,
        "privateDnsActive" to privateDnsActive,
    )
}
