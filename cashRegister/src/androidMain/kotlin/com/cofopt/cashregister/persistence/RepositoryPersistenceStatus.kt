package com.cofopt.cashregister.persistence

/**
 * Observable health of a repository's latest in-memory snapshot.
 *
 * A non-null [lastError] is deliberately retained while a retry is pending so diagnostics do not
 * briefly report a healthy repository merely because another retry was scheduled.
 */
data class RepositoryPersistenceStatus(
    val pending: Boolean = false,
    val retryAttempt: Int = 0,
    val lastError: String? = null,
)

internal fun persistenceRetryDelayMillis(failedAttempt: Int): Long {
    if (failedAttempt <= 0) return 0L
    val exponent = (failedAttempt - 1).coerceAtMost(9)
    return (100L * (1L shl exponent)).coerceAtMost(30_000L)
}
