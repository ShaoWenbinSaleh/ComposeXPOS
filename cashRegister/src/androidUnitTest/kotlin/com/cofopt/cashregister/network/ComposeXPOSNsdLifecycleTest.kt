package com.cofopt.cashregister.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ComposeXPOSNsdLifecycleTest {
    @Test
    fun stopRejectsLateCallbacksAndRestartUsesANewGeneration() {
        val lifecycle = NsdDiscoveryLifecycle()

        val firstGeneration = lifecycle.start()
        assertTrue(lifecycle.isCurrent(firstGeneration))
        assertEquals(firstGeneration, lifecycle.start())

        lifecycle.stop()
        assertFalse(lifecycle.isCurrent(firstGeneration))

        val replacementGeneration = lifecycle.start()
        assertNotEquals(firstGeneration, replacementGeneration)
        assertTrue(lifecycle.isCurrent(replacementGeneration))
        assertFalse(lifecycle.isCurrent(firstGeneration))
    }

    @Test
    fun retryDelayIsPerTypeCappedAndResetAfterDiscoveryStarts() {
        val lifecycle = NsdDiscoveryLifecycle()
        lifecycle.start()

        assertEquals(1_000L, lifecycle.nextRetryDelayMillis("ordering"))
        assertEquals(2_000L, lifecycle.nextRetryDelayMillis("ordering"))
        assertEquals(4_000L, lifecycle.nextRetryDelayMillis("ordering"))
        assertEquals(8_000L, lifecycle.nextRetryDelayMillis("ordering"))
        assertEquals(8_000L, lifecycle.nextRetryDelayMillis("ordering"))
        assertEquals(1_000L, lifecycle.nextRetryDelayMillis("calling"))

        lifecycle.markDiscoveryStarted("ordering")
        assertEquals(1_000L, lifecycle.nextRetryDelayMillis("ordering"))
    }
}
