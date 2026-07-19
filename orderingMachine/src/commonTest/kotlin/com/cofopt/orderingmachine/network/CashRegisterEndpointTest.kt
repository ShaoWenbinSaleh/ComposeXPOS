package com.cofopt.orderingmachine.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashRegisterEndpointTest {
    @Test
    fun configSnapshotCanonicalizesHostAndPortAsOnePair() {
        assertEquals(
            CashRegisterConfigSnapshot("https://cash-register.example", 8443),
            canonicalCashRegisterConfigSnapshot(" HTTPS://cash-register.example:8443/ ", 8443),
        )
        assertEquals(
            CashRegisterConfigSnapshot("[2001:db8::42]", 8080),
            canonicalCashRegisterConfigSnapshot("2001:db8::42", 8080),
        )
        assertNull(canonicalCashRegisterConfigSnapshot("cash.local:9090", 8080))
    }

    @Test
    fun normalizesHttpSchemeAndMatchingEmbeddedPort() {
        assertEquals(
            "http://cash-register.local:8080/orders",
            cashRegisterUrl(" HTTP://cash-register.local:8080/ ", 8080, "/orders"),
        )
        assertEquals(
            "https://cash-register.example:8443/health",
            cashRegisterUrl("HTTPS://cash-register.example:8443/", 8443, "/health"),
        )
        assertEquals(
            "https://cash-register.example",
            normalizeCashRegisterHost("HTTPS://cash-register.example:8443/", 8443),
        )
    }

    @Test
    fun bracketsIpv6Literal() {
        assertEquals(
            "http://[2001:db8::42]:8080/menu",
            cashRegisterUrl("[2001:db8::42]", 8080, "/menu"),
        )
        assertEquals(
            "http://[2001:db8::42]:8080/menu",
            cashRegisterUrl("2001:db8::42", 8080, "/menu"),
        )
        assertEquals(
            "http://[fe80::42%25wlan0]:8080/health",
            cashRegisterUrl("http://[fe80::42%25wlan0]", 8080, "/health"),
        )
    }

    @Test
    fun rejectsUnsupportedOrAmbiguousEndpoint() {
        assertNull(cashRegisterUrl("ftp://cash-register.local", 8080, "/health"))
        assertNull(cashRegisterUrl("cash-register.local:9090", 8080, "/health"))
        assertNull(cashRegisterUrl("cash-register.local/path", 8080, "/health"))
        assertNull(cashRegisterUrl("cash-register.local", 0, "/health"))
        assertNull(cashRegisterUrl("cash-register.local", 8080, "//other-host/path"))
    }

    @Test
    fun healthRequiresSuccessfulCashRegisterMarker() {
        assertTrue(isCashRegisterHealthResponse(CashRegisterHttpResponse(200, " ok\n")))
        assertFalse(isCashRegisterHealthResponse(CashRegisterHttpResponse(503, "ok")))
        assertFalse(isCashRegisterHealthResponse(CashRegisterHttpResponse(200, "unrelated service")))
    }
}
