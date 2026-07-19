package com.cofopt.cashregister.cmp.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallingSourceStateCodecTest {
    @Test
    fun roundTripNormalizesBucketsBeforeReconnect() {
        val raw = CallingSourceStateCodec.encode(
            preparing = listOf(1, 2, 2, 1000, 3),
            ready = listOf(3, 4, 4, -1),
        )

        assertEquals(
            PersistedCallingSourceState(
                preparing = listOf(1, 2),
                ready = listOf(3, 4),
            ),
            CallingSourceStateCodec.decode(raw),
        )
    }

    @Test
    fun missingCorruptOrUnknownStateCannotAuthorizeAnEmptyReconnect() {
        assertNull(CallingSourceStateCodec.decode(null))
        assertNull(CallingSourceStateCodec.decode("not-json"))
        assertNull(
            CallingSourceStateCodec.decode(
                """{"version":2,"preparing":[1],"ready":[]}"""
            )
        )
    }
}
