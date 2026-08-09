package com.shieldblock.shieldblock.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VpnRuntimeStateTest {
    @Test
    fun `health diagnostics count inspected and blocked DNS without changing VPN state`() {
        VpnRuntimeState.resetHealth(hasPrivateDns = true)
        VpnRuntimeState.update(VpnState.RUNNING, "DNS filtering is active.")

        VpnRuntimeState.recordDnsQuery(blocked = false)
        VpnRuntimeState.recordDnsQuery(blocked = true)
        VpnRuntimeState.recordPacketError(IllegalStateException("test packet error"))

        val status = VpnRuntimeState.run { snapshot().toMap() }
        assertEquals("running", status["state"])
        assertEquals(true, status["running"])
        assertEquals(2L, status["dnsQueries"])
        assertEquals(1L, status["blockedQueries"])
        assertEquals(1L, status["packetErrors"])
        assertEquals(true, status["privateDnsActive"])
        assertEquals("test packet error", status["lastError"])
        assertNotNull(status["lastDnsAt"])

        VpnRuntimeState.update(VpnState.STOPPED)
    }
}
