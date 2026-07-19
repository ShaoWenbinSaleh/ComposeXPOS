package com.cofopt.cashregister.persistence

import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryPersistenceStatusTest {
    @Test
    fun retryDelayBacksOffAndCapsAtThirtySeconds() {
        assertEquals(0L, persistenceRetryDelayMillis(0))
        assertEquals(100L, persistenceRetryDelayMillis(1))
        assertEquals(200L, persistenceRetryDelayMillis(2))
        assertEquals(25_600L, persistenceRetryDelayMillis(9))
        assertEquals(30_000L, persistenceRetryDelayMillis(10))
        assertEquals(30_000L, persistenceRetryDelayMillis(100))
    }
}
