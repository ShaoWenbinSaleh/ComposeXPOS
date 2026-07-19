package com.cofopt.cashregister.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkUtilsTest {
    @Test
    fun activeWifiWinsOverEnumerationOrderAndNonWifiDefaultRoute() {
        val candidates = listOf(
            LocalIpv4Candidate("rmnet_data0", "10.20.30.40"),
            LocalIpv4Candidate("tun0", "10.8.0.2"),
            LocalIpv4Candidate("wlan0", "192.168.50.9"),
        )

        assertEquals(
            "192.168.50.9",
            selectPreferredLocalIpv4Address(candidates, currentNetworkAddress = "10.20.30.40"),
        )
    }

    @Test
    fun currentNetworkWinsWhenWifiIsUnavailable() {
        val candidates = listOf(
            LocalIpv4Candidate("tun0", "10.8.0.2"),
            LocalIpv4Candidate("rmnet_data0", "10.20.30.40"),
        )

        assertEquals(
            "10.20.30.40",
            selectPreferredLocalIpv4Address(candidates, currentNetworkAddress = "10.20.30.40"),
        )
    }

    @Test
    fun unusableAddressesAreSkippedBeforeSafeFallback() {
        val candidates = listOf(
            LocalIpv4Candidate("wlan0", "169.254.1.8"),
            LocalIpv4Candidate("lo", "127.0.0.1"),
            LocalIpv4Candidate("eth0", "192.168.1.20"),
        )

        assertEquals(
            "192.168.1.20",
            selectPreferredLocalIpv4Address(candidates, currentNetworkAddress = null),
        )
        assertNull(
            selectPreferredLocalIpv4Address(
                candidates.take(2),
                currentNetworkAddress = null,
            )
        )
    }

    @Test
    fun addressValidationRejectsNonLanEndpointShapes() {
        assertTrue(isUsableIpv4Address("192.168.1.5"))
        assertTrue(isUsableIpv4Address("10.0.0.5"))
        assertFalse(isUsableIpv4Address("0.0.0.0"))
        assertFalse(isUsableIpv4Address("127.0.0.1"))
        assertFalse(isUsableIpv4Address("169.254.1.5"))
        assertFalse(isUsableIpv4Address("224.0.0.251"))
        assertFalse(isUsableIpv4Address("not-an-address"))
    }
}
