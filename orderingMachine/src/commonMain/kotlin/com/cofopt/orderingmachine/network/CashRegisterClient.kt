package com.cofopt.orderingmachine.network

import com.cofopt.orderingmachine.currentEpochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface CashRegisterOrderSubmissionResult {
    data class Accepted(val callNumber: Int) : CashRegisterOrderSubmissionResult
    data class Pending(val orderId: String, val message: String? = null) : CashRegisterOrderSubmissionResult
    data class Indeterminate(val orderId: String, val message: String) : CashRegisterOrderSubmissionResult
    data class Rejected(val statusCode: Int, val message: String) : CashRegisterOrderSubmissionResult
}

object CashRegisterClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isConfigured(context: OrderingPlatformContext): Boolean {
        val endpoint = CashRegisterConfig.endpoint(context) ?: return false
        return cashRegisterUrl(endpoint.host, endpoint.port, "/health") != null
    }

    suspend fun testConnection(context: OrderingPlatformContext): Boolean {
        val endpoint = CashRegisterConfig.endpoint(context) ?: return false
        if (cashRegisterUrl(endpoint.host, endpoint.port, "/health") == null) {
            return false
        }
        return try {
            CashRegisterNetworkTransport.testConnection(
                endpoint.host,
                endpoint.port,
                timeoutMillis = 5000,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun testConnectionWithRetry(
        context: OrderingPlatformContext,
        timeoutMillis: Long = 15_000,
        retryDelayMillis: Long = 400,
    ): Boolean {
        if (timeoutMillis <= 0) return false
        return withTimeoutOrNull(timeoutMillis) {
            while (!testConnection(context)) {
                delay(retryDelayMillis.coerceAtLeast(1))
            }
            true
        } ?: false
    }

    suspend fun postOrder(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
        maxRetries: Int = 3,
    ): Int? {
        return when (
            val result = submitOrderInternal(
                context = context,
                order = order,
                maxRetries = maxRetries,
                persistUntilAcknowledged = false,
            )
        ) {
            is CashRegisterOrderSubmissionResult.Accepted -> result.callNumber
            is CashRegisterOrderSubmissionResult.Pending,
            is CashRegisterOrderSubmissionResult.Indeterminate,
            is CashRegisterOrderSubmissionResult.Rejected -> null
        }
    }

    /**
     * Persists a card order before any payment request is sent, but keeps it
     * held so the background retry loop cannot mark it paid prematurely.
     */
    suspend fun reserveOrder(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
    ): Boolean {
        val existing = CashRegisterOrderOutbox.find(context, order.orderId)
        val target = existing?.target ?: configuredTarget(context) ?: return false
        val staged = CashRegisterOrderOutbox.stage(
            context = context,
            order = order,
            requestedTarget = target,
            deliverable = false,
            paymentPhase = CashRegisterPaymentPhase.PREPARED,
        ) ?: return false
        return staged.durability == CashRegisterOutboxDurability.CONFIRMED
    }

    suspend fun discardReservedOrder(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = CashRegisterOrderOutbox.discardHeld(context, orderId)

    /** Releases and delivers the exact held order using its original target. */
    suspend fun submitReservedOrder(
        context: OrderingPlatformContext,
        orderId: String,
        maxRetries: Int = 5,
        retainUntilCallerCommits: Boolean = false,
    ): CashRegisterOrderSubmissionResult {
        if (!CashRegisterOrderOutbox.isReadable(context)) {
            return CashRegisterOrderSubmissionResult.Indeterminate(
                orderId = orderId,
                message = "reserved_order_outbox_unreadable",
            )
        }
        val entry = CashRegisterOrderOutbox.find(context, orderId)
            ?: return CashRegisterOrderSubmissionResult.Rejected(0, "reserved_order_missing")
        entry.blockedReason?.let { reason ->
            return CashRegisterOrderSubmissionResult.Rejected(409, "order_outbox_blocked:$reason")
        }
        if (retainUntilCallerCommits) {
            if (entry.paymentPhase != CashRegisterPaymentPhase.PAID_COMMIT) {
                return CashRegisterOrderSubmissionResult.Rejected(0, "payment_not_committed")
            }
            // Send the held entry directly. It stays invisible to the global
            // retry loop until the payment journal is durably cleared.
            return submitStoredEntry(
                context = context,
                entry = entry,
                maxRetries = maxRetries,
                persistUntilAcknowledged = true,
                removeOnAccepted = false,
            )
        }
        return submitOrderInternal(
            context = context,
            order = entry.order,
            maxRetries = maxRetries,
            persistUntilAcknowledged = true,
            removeOnAccepted = !retainUntilCallerCommits,
        )
    }

    suspend fun acknowledgeReservedOrder(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = CashRegisterOrderOutbox.remove(context, orderId)

    suspend fun releaseReservedOrderForRetry(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = CashRegisterOrderOutbox.releasePaymentCommit(context, orderId)

    suspend fun markReservedOrderPaid(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = CashRegisterOrderOutbox.markPaymentPaid(context, orderId)

    suspend fun releaseOrphanedPaymentCommits(
        context: OrderingPlatformContext,
        protectedOrderId: String?,
    ): Boolean = CashRegisterOrderOutbox.releaseOrphanedPaymentCommits(context, protectedOrderId)

    /** Exact, fail-closed proof used before treating an exception as safely queued. */
    suspend fun isOrderDurablyRecorded(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
    ): Boolean = CashRegisterOrderOutbox.isOrderDurablyRecorded(context, order)

    /**
     * Fail-closed proof that a paid card order remains durably retained for
     * automatic retry or manual resolution after a permanent server rejection.
     */
    suspend fun isPaidOrderDurablyRecorded(
        context: OrderingPlatformContext,
        orderId: String,
    ): Boolean = CashRegisterOrderOutbox.isPaidOrderDurablyRecorded(context, orderId)

    /**
     * Durably records the order before sending it. A Pending result must never
     * be replaced with a locally generated call number: the server may already
     * have accepted this exact order and lost only the acknowledgement.
     */
    suspend fun submitOrder(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
        maxRetries: Int = 1,
    ): CashRegisterOrderSubmissionResult {
        return submitOrderInternal(
            context = context,
            order = order,
            maxRetries = maxRetries,
            persistUntilAcknowledged = true,
            fastPending = true,
        )
    }

    /** Retries durable entries without creating a second order or call number. */
    suspend fun retryPendingOrders(
        context: OrderingPlatformContext,
        maxOrders: Int = 10,
    ): Int = coroutineScope {
        val claimed = CashRegisterOrderOutbox.claimDeliverable(
            context = context,
            maxOrders = maxOrders,
            nowMillis = currentEpochMillis(),
        )
        val endpointSemaphore = Semaphore(MAX_PARALLEL_RETRY_ENDPOINTS)
        claimed.groupBy { it.target }
            .values
            .map { endpointEntries ->
                async {
                    endpointSemaphore.withPermit {
                        var accepted = 0
                        for (entry in endpointEntries) {
                            if (submitStoredEntry(
                                context = context,
                                entry = entry,
                                maxRetries = 1,
                                persistUntilAcknowledged = true,
                            ) is CashRegisterOrderSubmissionResult.Accepted) {
                                accepted++
                            }
                        }
                        accepted
                    }
                }
            }
            .awaitAll()
            .sum()
    }

    private suspend fun submitOrderInternal(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
        maxRetries: Int,
        persistUntilAcknowledged: Boolean,
        removeOnAccepted: Boolean = true,
        fastPending: Boolean = false,
    ): CashRegisterOrderSubmissionResult {
        if (persistUntilAcknowledged && !CashRegisterOrderOutbox.isReadable(context)) {
            return CashRegisterOrderSubmissionResult.Indeterminate(
                orderId = order.orderId,
                message = "order_outbox_unreadable",
            )
        }
        val existing = if (persistUntilAcknowledged) {
            CashRegisterOrderOutbox.find(context, order.orderId)
        } else {
            null
        }
        if (existing != null) {
            normalCashRegisterSubmissionBlockReason(existing, order)?.let { reason ->
                return CashRegisterOrderSubmissionResult.Rejected(409, reason)
            }
        }
        val target = existing?.target ?: configuredTarget(context)
            ?: return CashRegisterOrderSubmissionResult.Rejected(0, "cashregister_not_configured")
        val entry = if (persistUntilAcknowledged) {
            val staged = CashRegisterOrderOutbox.stage(
                context = context,
                order = order,
                requestedTarget = target,
                deliverable = true,
            ) ?: return CashRegisterOrderSubmissionResult.Indeterminate(
                orderId = order.orderId,
                message = "order_outbox_persistence_failed",
            )
            if (staged.durability == CashRegisterOutboxDurability.UNCERTAIN) {
                return CashRegisterOrderSubmissionResult.Indeterminate(
                    orderId = order.orderId,
                    message = "order_outbox_persistence_uncertain",
                )
            }
            staged.entry
        } else {
            CashRegisterOutboxEntry(order = order, target = target, deliverable = true)
        }
        normalCashRegisterSubmissionBlockReason(entry, order)?.let { reason ->
            return CashRegisterOrderSubmissionResult.Rejected(409, reason)
        }
        entry.blockedReason?.let { reason ->
            return CashRegisterOrderSubmissionResult.Rejected(409, "order_outbox_blocked:$reason")
        }

        return submitStoredEntry(
            context = context,
            entry = entry,
            maxRetries = maxRetries,
            persistUntilAcknowledged = persistUntilAcknowledged,
            removeOnAccepted = removeOnAccepted,
            fastPending = fastPending,
        )
    }

    private suspend fun submitStoredEntry(
        context: OrderingPlatformContext,
        entry: CashRegisterOutboxEntry,
        maxRetries: Int,
        persistUntilAcknowledged: Boolean,
        removeOnAccepted: Boolean = true,
        fastPending: Boolean = false,
    ): CashRegisterOrderSubmissionResult {
        val order = entry.order
        val orderUrl = cashRegisterUrl(entry.target.host, entry.target.port, "/orders")
            ?: return CashRegisterOrderSubmissionResult.Rejected(0, "invalid_bound_cashregister_endpoint")
        if (maxRetries <= 0) {
            return CashRegisterOrderSubmissionResult.Pending(order.orderId, "no_submission_attempts")
        }

        val attempts = maxRetries.coerceAtMost(MAX_RETRY_ATTEMPTS)
        val payload = json.encodeToString(CashRegisterOrderPayload.serializer(), order)
        var lastError: String? = null
        repeat(attempts) { attempt ->
            try {
                val response = CashRegisterNetworkTransport.request(
                    method = "POST",
                    url = orderUrl,
                    requestBody = payload,
                    connectTimeoutMillis = if (fastPending) {
                        FOREGROUND_CONNECT_TIMEOUT_MILLIS
                    } else {
                        5000 + (attempt * 2000)
                    },
                    readTimeoutMillis = if (fastPending) {
                        FOREGROUND_READ_TIMEOUT_MILLIS
                    } else {
                        8000 + (attempt * 2000)
                    },
                )

                if (response.statusCode !in 200..299) {
                    if (response.statusCode in PERMANENT_CLIENT_ERRORS) {
                        val message = responseMessage(response.body) ?: "HTTP ${response.statusCode}"
                        if (
                            persistUntilAcknowledged &&
                            !CashRegisterOrderOutbox.markBlocked(context, order.orderId, message)
                        ) {
                            return CashRegisterOrderSubmissionResult.Indeterminate(
                                orderId = order.orderId,
                                message = "server_rejected_but_outbox_block_failed:$message",
                            )
                        }
                        return CashRegisterOrderSubmissionResult.Rejected(
                            statusCode = response.statusCode,
                            message = message,
                        )
                    }
                    throw IllegalStateException("HTTP ${response.statusCode}: ${response.body}")
                }

                val obj = json.parseToJsonElement(response.body).jsonObject
                val responseStatus = obj["status"]?.jsonPrimitive?.contentOrNull?.trim()
                if (!responseStatus.equals("ok", ignoreCase = true)) {
                    throw IllegalStateException("CashRegister returned a non-success response")
                }
                val callNumberElement = obj["callNumber"]
                    ?: throw IllegalStateException("CashRegister response is missing callNumber")
                if (callNumberElement is JsonNull) {
                    throw IllegalStateException("CashRegister response has no callNumber")
                }
                val callNumber = callNumberElement.jsonPrimitive.let { primitive ->
                    primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
                } ?: throw IllegalStateException("CashRegister returned an invalid callNumber")
                if (persistUntilAcknowledged && removeOnAccepted) {
                    CashRegisterOrderOutbox.remove(context, order.orderId)
                }
                return CashRegisterOrderSubmissionResult.Accepted(callNumber)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error.message ?: error::class.simpleName
                if (attempt < attempts - 1) {
                    delay(exponentialRetryDelay(baseMillis = 500, attempt = attempt, maxMillis = 3000))
                }
            }
        }
        return CashRegisterOrderSubmissionResult.Pending(order.orderId, lastError)
    }

    private fun configuredTarget(context: OrderingPlatformContext): CashRegisterOrderTarget? {
        val endpoint = CashRegisterConfig.endpoint(context) ?: return null
        if (cashRegisterUrl(endpoint.host, endpoint.port, "/orders") == null) return null
        return CashRegisterOrderTarget(host = endpoint.host, port = endpoint.port)
    }

    suspend fun getDishes(context: OrderingPlatformContext, maxRetries: Int = 3): List<DishesSyncItem>? {
        val endpoint = CashRegisterConfig.endpoint(context) ?: return null
        val dishesUrl = cashRegisterUrl(endpoint.host, endpoint.port, "/dishes") ?: return null
        if (maxRetries <= 0) return null

        val attempts = maxRetries.coerceAtMost(MAX_RETRY_ATTEMPTS)
        repeat(attempts) { attempt ->
            try {
                val response = CashRegisterNetworkTransport.request(
                    method = "GET",
                    url = dishesUrl,
                    requestBody = null,
                    connectTimeoutMillis = 6000 + (attempt * 2000),
                    readTimeoutMillis = 20000 + (attempt * 5000),
                )

                if (response.statusCode !in 200..299) {
                    if (response.statusCode in 400..499) return null
                    throw IllegalStateException("HTTP ${response.statusCode}")
                }

                return json.decodeFromString<List<DishesSyncItem>>(response.body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (attempt < attempts - 1) {
                    delay(exponentialRetryDelay(baseMillis = 400, attempt = attempt, maxMillis = 2000))
                }
            }
        }

        return null
    }

    suspend fun getMenu(context: OrderingPlatformContext, maxRetries: Int = 3): List<MenuSyncItem>? {
        val endpoint = CashRegisterConfig.endpoint(context) ?: return null
        val menuUrl = cashRegisterUrl(endpoint.host, endpoint.port, "/menu") ?: return null
        if (maxRetries <= 0) return null

        val attempts = maxRetries.coerceAtMost(MAX_RETRY_ATTEMPTS)
        repeat(attempts) { attempt ->
            try {
                val response = CashRegisterNetworkTransport.request(
                    method = "GET",
                    url = menuUrl,
                    requestBody = null,
                    connectTimeoutMillis = 4000 + (attempt * 1500),
                    readTimeoutMillis = 6000 + (attempt * 1500),
                )

                if (response.statusCode !in 200..299) {
                    if (response.statusCode in 400..499) return null
                    throw IllegalStateException("HTTP ${response.statusCode}")
                }

                return json.decodeFromString<List<MenuSyncItem>>(response.body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (attempt < attempts - 1) {
                    delay(exponentialRetryDelay(baseMillis = 400, attempt = attempt, maxMillis = 2000))
                }
            }
        }

        return null
    }

    private fun exponentialRetryDelay(baseMillis: Long, attempt: Int, maxMillis: Long): Long {
        var result = baseMillis.coerceAtMost(maxMillis)
        repeat(attempt.coerceIn(0, 30)) {
            result = (result * 2).coerceAtMost(maxMillis)
        }
        return result
    }

    private fun responseMessage(body: String): String? {
        return runCatching {
            json.parseToJsonElement(body)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private const val MAX_RETRY_ATTEMPTS = 10
    private const val MAX_PARALLEL_RETRY_ENDPOINTS = 4
    private const val FOREGROUND_CONNECT_TIMEOUT_MILLIS = 2_000
    private const val FOREGROUND_READ_TIMEOUT_MILLIS = 3_000
    private val PERMANENT_CLIENT_ERRORS = (400..499).toSet() - setOf(408, 425, 429)
}
