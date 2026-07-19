package com.cofopt.orderingmachine.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.CashRegisterOrderSubmissionResult
import com.cofopt.orderingmachine.network.WecrConfig
import com.cofopt.orderingmachine.network.WecrHttpsClient
import com.cofopt.orderingmachine.network.WecrTcpClient
import com.cofopt.orderingmachine.serial.DebugLog
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentCancellationResult
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentProcessResult
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentRecoveryOutcome
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes the journal/outbox cross-store boundary inside this app process. */
private val cardPaymentPersistenceMutex = Mutex()

internal sealed interface WecrPaymentResolution {
    data class Paid(val status: WecrHttpsClient.TransactionStatus) : WecrPaymentResolution
    data class Failed(val status: WecrHttpsClient.TransactionStatus?) : WecrPaymentResolution
    data object Unresolved : WecrPaymentResolution
}

private sealed interface PaidCardFinalization {
    data class Completed(val callNumber: Int?) : PaidCardFinalization
    data object Unresolved : PaidCardFinalization
}

internal fun MainViewModel.setPaymentMethodForFlowImpl(method: PaymentMethod) {
    val journal = CardPaymentJournal.load(getApplication())
    val active = currentActiveOrderPayloadImpl()
    if (journal != null && (method != PaymentMethod.CARD || active?.orderId != journal.orderId)) {
        throw IllegalStateException("card_payment_resolution_required")
    }

    val protocolMethod = if (method == PaymentMethod.CARD) "CARD" else "CASH"
    active?.let { pending ->
        if (!pending.paymentMethod.equals(protocolMethod, ignoreCase = true)) {
            throw IllegalStateException("pending_order_uses_${pending.paymentMethod.lowercase()}_payment")
        }
    }
    paymentMethod = method
    paymentError = null
    if (method == PaymentMethod.CARD) markCardPaymentFailedImpl(false)
}

internal suspend fun resolveWecrPaymentImpl(
    httpsClient: WecrHttpsClient,
    keyIndex: String,
    transactionRef: String,
    maxAttempts: Int = 60,
    delayMillis: Long = 500,
): WecrPaymentResolution {
    if (maxAttempts <= 0) return WecrPaymentResolution.Unresolved
    var consecutiveExplicitFailures = 0

    repeat(maxAttempts) { attempt ->
        if (attempt > 0) delay(delayMillis.coerceAtLeast(1))
        val status = try {
            httpsClient.getTransactionStatus(keyIndex, transactionRef)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        DebugLog.add("WECR: Poll status attempt ${attempt + 1}/$maxAttempts result=$status")

        if (status == null) {
            // Network/parser failures say nothing about whether money moved.
            consecutiveExplicitFailures = 0
            return@repeat
        }

        val statusCode = status.status?.trim()
        val resultCode = status.transactionResult?.trim()
        if (isWecrSuccessStatus(statusCode, resultCode)) {
            return WecrPaymentResolution.Paid(status)
        }
        if (isWecrFailureStatus(statusCode, resultCode)) {
            consecutiveExplicitFailures++
            if (consecutiveExplicitFailures >= 3) {
                return WecrPaymentResolution.Failed(status)
            }
        } else {
            consecutiveExplicitFailures = 0
        }
    }
    return WecrPaymentResolution.Unresolved
}

internal suspend fun MainViewModel.pollWecrStatusWithDetailsImpl(
    httpsClient: WecrHttpsClient,
    keyIndex: String,
    transactionRef: String,
    maxAttempts: Int = 60,
    delayMillis: Long = 500,
): WecrHttpsClient.TransactionStatus? {
    return (resolveWecrPaymentImpl(
        httpsClient = httpsClient,
        keyIndex = keyIndex,
        transactionRef = transactionRef,
        maxAttempts = maxAttempts,
        delayMillis = delayMillis,
    ) as? WecrPaymentResolution.Paid)?.status
}

internal fun MainViewModel.markCardPaymentFailedImpl(failed: Boolean) {
    cardPaymentFailed = failed
}

internal suspend fun MainViewModel.processCounterPaymentImpl(context: Context): Int? {
    return withContext(Dispatchers.IO) {
        Log.d("MainViewModel", "processCounterPayment: start")
        val submission = postOrderToCashRegisterIfConfiguredImpl(
            context = context,
            paymentMethod = "CASH",
            paymentStatus = "UNPAID",
        )
        if (submission is CashRegisterOrderSubmissionResult.Rejected) {
            clearActiveOrderPayloadImpl()
            throw IllegalStateException(submission.message)
        }
        if (submission is CashRegisterOrderSubmissionResult.Indeterminate) {
            // Keep the frozen payload/orderId. A retry of the same identity is
            // safe; creating a fresh identity is not.
            throw IllegalStateException(submission.message)
        }

        val callNumber = (submission as? CashRegisterOrderSubmissionResult.Accepted)?.callNumber
        val orderPayload = currentActiveOrderPayloadImpl()
            ?: throw IllegalStateException("active_order_missing_after_submission")
        val pending = submission is CashRegisterOrderSubmissionResult.Pending

        withContext(Dispatchers.Main) {
            lastCallNumber = callNumber
            cashRegisterCreateFailed = pending
            cashRegisterCreateFailedWasPaid = false
            printUnpaidOrderImpl(context, callNumber?.toString() ?: "----", orderPayload)
        }
        Log.d("MainViewModel", "processCounterPayment: done callNumber=$callNumber pending=$pending")
        callNumber
    }
}

/** Offline delivery now uses the same durable, endpoint-bound outbox path. */
internal suspend fun MainViewModel.processCounterPaymentOfflineImpl(context: Context): Int? =
    processCounterPaymentImpl(context)

internal suspend fun MainViewModel.debugSimulateCardPaidImpl(context: Context): Int? {
    return withContext(Dispatchers.IO) {
        val submission = postOrderToCashRegisterIfConfiguredImpl(
            context = context,
            paymentMethod = "CARD",
            paymentStatus = "PAID",
        )
        if (submission is CashRegisterOrderSubmissionResult.Rejected) {
            clearActiveOrderPayloadImpl()
            throw IllegalStateException(submission.message)
        }
        if (submission is CashRegisterOrderSubmissionResult.Indeterminate) {
            throw IllegalStateException(submission.message)
        }
        val callNumber = (submission as? CashRegisterOrderSubmissionResult.Accepted)?.callNumber
        val orderPayload = currentActiveOrderPayloadImpl()
            ?: throw IllegalStateException("active_order_missing_after_submission")
        withContext(Dispatchers.Main) {
            lastCallNumber = callNumber
            cashRegisterCreateFailed = callNumber == null
            cashRegisterCreateFailedWasPaid = true
            printPaidOrderImpl(context, callNumber?.toString() ?: "----", orderPayload)
        }
        callNumber
    }
}

internal suspend fun MainViewModel.beginCardPaymentImpl(context: Context): CardPaymentResult? {
    return withContext(Dispatchers.IO) {
        var existing = CardPaymentJournal.load(context)
        if (existing?.phase == CardPaymentJournalPhase.PREPARED) {
            // PREPARED is written before START_REQUESTED and therefore proves
            // no external payment request was made. It is safe to discard.
            if (!cleanupFailedCardIntentImpl(context, existing)) return@withContext null
            existing = null
        }
        existing?.let { pending ->
            val active = currentActiveOrderPayloadImpl()
            if (active?.orderId != pending.orderId) return@withContext null
            withContext(Dispatchers.Main) {
                setCurrentTransactionInfoImpl(pending.transactionRef, pending.keyIndex)
            }
            return@withContext CardPaymentResult(pending.transactionRef, pending.keyIndex)
        }

        val orderPayload = activeOrderPayloadForImpl(context, "CARD", "PAID")
        val transactionRef = "TXN_${System.currentTimeMillis()}_${orderPayload.orderId.takeLast(8)}"
        val requestedKeyIndex = 0
        val recovery = CardPaymentJournalEntry(
            orderId = orderPayload.orderId,
            transactionRef = transactionRef,
            keyIndex = requestedKeyIndex,
            phase = CardPaymentJournalPhase.PREPARED,
        )
        val prepared = cardPaymentPersistenceMutex.withLock {
            CashRegisterClient.reserveOrder(context, orderPayload) &&
                CardPaymentJournal.save(context, recovery)
        }
        if (!prepared) {
            // No external request exists yet. Only release the frozen identity
            // when both durable records can be removed; a failed commit may
            // still have changed the in-process SharedPreferences view.
            val orderDiscarded = CashRegisterClient.discardReservedOrder(context, orderPayload.orderId)
            val journalCleared = CardPaymentJournal.clear(context)
            if (orderDiscarded && journalCleared) {
                clearActiveOrderPayloadImpl()
                return@withContext null
            }
            val readable = CardPaymentJournal.load(context) ?: return@withContext null
            withContext(Dispatchers.Main) {
                setCurrentTransactionInfoImpl(readable.transactionRef, readable.keyIndex)
            }
            return@withContext CardPaymentResult(readable.transactionRef, readable.keyIndex)
        }

        // This phase is committed before the external call. A crash from this
        // point onwards is ambiguous and must be resolved with the same
        // transaction reference; it must never open a second charge.
        val requestedRecovery = recovery.copy(phase = CardPaymentJournalPhase.START_REQUESTED)
        if (!CardPaymentJournal.save(context, requestedRecovery)) {
            // No request has been sent yet, so a complete cleanup is safe. If
            // storage itself is failing, retain/recover any readable journal
            // rather than allowing another payment attempt.
            if (cleanupFailedCardIntentImpl(context, requestedRecovery)) return@withContext null
            val readable = CardPaymentJournal.load(context) ?: return@withContext null
            withContext(Dispatchers.Main) {
                setCurrentTransactionInfoImpl(readable.transactionRef, readable.keyIndex)
            }
            return@withContext CardPaymentResult(readable.transactionRef, readable.keyIndex)
        }
        withContext(Dispatchers.Main) {
            setCurrentTransactionInfoImpl(transactionRef, requestedKeyIndex)
        }

        val httpsClient = newWecrClient(context)
        try {
            val result = httpsClient.startTransaction(
                amount = totalAmount,
                merchantRef = orderPayload.orderId,
                transactionRef = transactionRef,
                keyIndex = requestedKeyIndex,
                useSignature = true,
            )
            if (result != null && isWecrSuccessStatus(result.status?.trim(), null)) {
                val actualRef = result.transactionRef?.trim().takeUnless { it.isNullOrEmpty() } ?: transactionRef
                val actualKey = result.keyIndex?.toIntOrNull() ?: requestedKeyIndex
                val actualRecovery = requestedRecovery.copy(
                    transactionRef = actualRef,
                    keyIndex = actualKey,
                    phase = CardPaymentJournalPhase.ACTIVE,
                )
                if (CardPaymentJournal.save(context, actualRecovery)) {
                    withContext(Dispatchers.Main) { setCurrentTransactionInfoImpl(actualRef, actualKey) }
                    return@withContext CardPaymentResult(actualRef, actualKey)
                }
                // The start was accepted, so this is an unresolved persistence
                // failure, never a declined request. Continue with the returned
                // correlation data and keep the recovery guard engaged.
                withContext(Dispatchers.Main) { setCurrentTransactionInfoImpl(actualRef, actualKey) }
                return@withContext CardPaymentResult(actualRef, actualKey)
            } else if (result != null && isWecrFailureStatus(result.status?.trim(), null)) {
                cleanupFailedCardIntentImpl(context, requestedRecovery)
                return@withContext null
            }

            // No response is ambiguous. Continue to status polling using the
            // client-generated reference instead of opening another payment.
            CardPaymentResult(transactionRef, requestedKeyIndex)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DebugLog.add("Card payment start response unresolved: ${error.message}")
            CardPaymentResult(transactionRef, requestedKeyIndex)
        }
    }
}

internal suspend fun MainViewModel.processCardPaymentImpl(context: Context): CardPaymentProcessResult {
    return withContext(Dispatchers.IO) {
        val transactionRef = currentTransactionRef
        val keyIndex = currentKeyIndex
        if (transactionRef == null || keyIndex == null) {
            return@withContext CardPaymentProcessResult(
                success = false,
                timeout = false,
                errorMessage = "Transaction info missing",
                unresolved = CardPaymentJournal.load(context) != null,
            )
        }

        try {
            triggerPosMachineImpl(context)
            when (
                val resolution = resolveWecrPaymentImpl(
                    httpsClient = newWecrClient(context),
                    keyIndex = keyIndex.toString(),
                    transactionRef = transactionRef,
                )
            ) {
                is WecrPaymentResolution.Paid -> {
                    when (
                        val finalized = finalizePaidCardOrderImpl(
                            context = context,
                            transactionRef = transactionRef,
                            status = resolution.status,
                        )
                    ) {
                        is PaidCardFinalization.Completed -> CardPaymentProcessResult(
                            success = true,
                            timeout = false,
                            callNumber = finalized.callNumber,
                        )

                        PaidCardFinalization.Unresolved -> CardPaymentProcessResult(
                            success = false,
                            timeout = false,
                            errorMessage = "Paid order delivery is still being recovered",
                            unresolved = true,
                        )
                    }
                }

                is WecrPaymentResolution.Failed -> {
                    val recovery = CardPaymentJournal.load(context)
                    val cleaned = recovery == null || cleanupFailedCardIntentImpl(context, recovery)
                    CardPaymentProcessResult(
                        success = false,
                        timeout = false,
                        errorMessage = if (cleaned) "Payment declined" else "Payment cleanup pending",
                        unresolved = !cleaned,
                    )
                }

                WecrPaymentResolution.Unresolved -> {
                    recoverPendingCardPaymentImpl(context, force = true)
                    CardPaymentProcessResult(
                        success = false,
                        timeout = false,
                        errorMessage = "Payment result unresolved",
                        unresolved = true,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            recoverPendingCardPaymentImpl(context, force = true)
            CardPaymentProcessResult(
                success = false,
                timeout = false,
                errorMessage = error.message ?: "Payment result unresolved",
                unresolved = CardPaymentJournal.load(context) != null,
            )
        }
    }
}

private suspend fun MainViewModel.finalizePaidCardOrderImpl(
    context: Context,
    transactionRef: String,
    status: WecrHttpsClient.TransactionStatus,
): PaidCardFinalization {
    val recovery = CardPaymentJournal.load(context)
        ?: return PaidCardFinalization.Unresolved
    val orderPayload = currentActiveOrderPayloadImpl()
    val submission = if (CashRegisterClient.markReservedOrderPaid(context, recovery.orderId)) {
        CashRegisterClient.submitReservedOrder(
            context = context,
            orderId = recovery.orderId,
            retainUntilCallerCommits = true,
        )
    } else {
        CashRegisterOrderSubmissionResult.Rejected(0, "paid_order_commit_persistence_failed")
    }
    val callNumber = (submission as? CashRegisterOrderSubmissionResult.Accepted)?.callNumber
    val deliveryIsDurable = when (submission) {
        is CashRegisterOrderSubmissionResult.Accepted,
        is CashRegisterOrderSubmissionResult.Pending -> true

        is CashRegisterOrderSubmissionResult.Rejected ->
            CashRegisterClient.isPaidOrderDurablyRecorded(context, recovery.orderId)

        is CashRegisterOrderSubmissionResult.Indeterminate -> false
    }

    if (!deliveryIsDurable || !CardPaymentJournal.clear(context)) {
        recoverPendingCardPaymentImpl(context, force = true)
        return PaidCardFinalization.Unresolved
    }

    when (submission) {
        is CashRegisterOrderSubmissionResult.Accepted -> {
            if (!CashRegisterClient.acknowledgeReservedOrder(context, recovery.orderId)) {
                recoverOrphanedPaymentCommitsImpl(context)
            }
        }

        is CashRegisterOrderSubmissionResult.Pending -> {
            if (!CashRegisterClient.releaseReservedOrderForRetry(context, recovery.orderId)) {
                recoverOrphanedPaymentCommitsImpl(context)
            }
        }

        is CashRegisterOrderSubmissionResult.Rejected,
        is CashRegisterOrderSubmissionResult.Indeterminate -> Unit
    }

    withContext(Dispatchers.Main) {
        setCurrentTransactionInfoImpl(null, null)
        if (orderPayload?.orderId == recovery.orderId) {
            lastCallNumber = callNumber
            cashRegisterCreateFailed = callNumber == null
            cashRegisterCreateFailedWasPaid = true
            printPaidOrderImpl(
                context = context,
                callNumber = callNumber?.toString() ?: "----",
                orderPayload = orderPayload,
                transactionRef = transactionRef,
                wecrStatus = status,
            )
        }
    }
    return PaidCardFinalization.Completed(callNumber)
}

private suspend fun MainViewModel.cleanupFailedCardIntentImpl(
    context: Context,
    recovery: CardPaymentJournalEntry,
): Boolean {
    if (!CashRegisterClient.discardReservedOrder(context, recovery.orderId)) return false
    if (!CardPaymentJournal.clear(context)) return false
    withContext(Dispatchers.Main) {
        setCurrentTransactionInfoImpl(null, null)
        if (currentActiveOrderPayloadImpl()?.orderId == recovery.orderId) clearActiveOrderPayloadImpl()
    }
    return true
}

/**
 * Once the payment journal is gone, an unremoved PAID_COMMIT must be promoted
 * back to the ordinary idempotent retry queue. Retry transient storage errors
 * without blocking the customer result screen.
 */
private fun MainViewModel.recoverOrphanedPaymentCommitsImpl(context: Context) {
    if (outboxRecoveryJob?.isActive == true) return
    outboxRecoveryJob = viewModelScope.launch(Dispatchers.IO) {
        while (isActive) {
            val released = cardPaymentPersistenceMutex.withLock {
                val protectedOrderId = CardPaymentJournal.load(context)?.orderId
                CashRegisterClient.releaseOrphanedPaymentCommits(
                    context = context,
                    protectedOrderId = protectedOrderId,
                )
            }
            if (released) {
                break
            }
            delay(15_000)
        }
    }
}

internal fun MainViewModel.recoverPendingCardPaymentImpl(context: Context, force: Boolean = false) {
    if (cardRecoveryJob?.isActive == true) return
    cardRecoveryJob = viewModelScope.launch(Dispatchers.IO) {
        val (initial, orphanReleaseSucceeded) = cardPaymentPersistenceMutex.withLock {
            val journal = CardPaymentJournal.load(context)
            val released = CashRegisterClient.releaseOrphanedPaymentCommits(
                context = context,
                protectedOrderId = journal?.orderId,
            )
            journal to released
        }
        if (!orphanReleaseSucceeded && initial == null) {
            recoverOrphanedPaymentCommitsImpl(context)
        }
        val pending = initial ?: return@launch
        if (pending.phase == CardPaymentJournalPhase.PREPARED) {
            val wasForeground = currentActiveOrderPayloadImpl()?.orderId == pending.orderId
            while (isActive) {
                if (cleanupFailedCardIntentImpl(context, pending)) {
                    if (wasForeground) {
                        withContext(Dispatchers.Main) {
                            cardPaymentRecoveryOutcome = CardPaymentRecoveryOutcome.NotPaid
                        }
                    }
                    break
                }
                delay(15_000)
                val stillPending = CardPaymentJournal.load(context) ?: break
                if (stillPending.orderId != pending.orderId || stillPending.phase != pending.phase) break
            }
            return@launch
        }
        if (!force && currentActiveOrderPayloadImpl()?.orderId == pending.orderId) return@launch
        var recovery: CardPaymentJournalEntry = pending
        while (isActive) {
            val foregroundOrder = currentActiveOrderPayloadImpl()
                ?.takeIf { it.orderId == recovery.orderId }
            val resolution = try {
                resolveWecrPaymentImpl(
                    httpsClient = newWecrClient(context),
                    keyIndex = recovery.keyIndex.toString(),
                    transactionRef = recovery.transactionRef,
                    maxAttempts = 3,
                    delayMillis = 1_000,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                WecrPaymentResolution.Unresolved
            }

            when (resolution) {
                is WecrPaymentResolution.Paid -> {
                    if (!CashRegisterClient.markReservedOrderPaid(context, recovery.orderId)) {
                        delay(15_000)
                        recovery = CardPaymentJournal.load(context) ?: break
                        continue
                    }
                    val submission = CashRegisterClient.submitReservedOrder(
                        context = context,
                        orderId = recovery.orderId,
                        retainUntilCallerCommits = true,
                    )
                    val deliveryIsDurable =
                        submission is CashRegisterOrderSubmissionResult.Accepted ||
                            submission is CashRegisterOrderSubmissionResult.Pending ||
                            (
                                submission is CashRegisterOrderSubmissionResult.Rejected &&
                                    CashRegisterClient.isPaidOrderDurablyRecorded(context, recovery.orderId)
                                )
                    if (deliveryIsDurable) {
                        if (CardPaymentJournal.clear(context)) {
                            when (submission) {
                                is CashRegisterOrderSubmissionResult.Accepted -> {
                                    if (!CashRegisterClient.acknowledgeReservedOrder(context, recovery.orderId)) {
                                        recoverOrphanedPaymentCommitsImpl(context)
                                    }
                                }

                                is CashRegisterOrderSubmissionResult.Pending -> {
                                    if (!CashRegisterClient.releaseReservedOrderForRetry(
                                            context,
                                            recovery.orderId,
                                        )
                                    ) {
                                        recoverOrphanedPaymentCommitsImpl(context)
                                    }
                                }

                                is CashRegisterOrderSubmissionResult.Rejected,
                                is CashRegisterOrderSubmissionResult.Indeterminate -> Unit
                            }
                            withContext(Dispatchers.Main) {
                                setCurrentTransactionInfoImpl(null, null)
                                if (foregroundOrder != null) {
                                    val callNumber =
                                        (submission as? CashRegisterOrderSubmissionResult.Accepted)?.callNumber
                                    lastCallNumber = callNumber
                                    cashRegisterCreateFailed = callNumber == null
                                    cashRegisterCreateFailedWasPaid = true
                                    printPaidOrderImpl(
                                        context = context,
                                        callNumber = callNumber?.toString() ?: "----",
                                        orderPayload = foregroundOrder,
                                        transactionRef = recovery.transactionRef,
                                        wecrStatus = resolution.status,
                                    )
                                    cardPaymentRecoveryOutcome = CardPaymentRecoveryOutcome.Paid(callNumber)
                                }
                            }
                            break
                        }
                    }
                }

                is WecrPaymentResolution.Failed -> {
                    val wasForeground = foregroundOrder != null
                    if (cleanupFailedCardIntentImpl(context, recovery)) {
                        if (wasForeground) {
                            withContext(Dispatchers.Main) {
                                cardPaymentRecoveryOutcome = CardPaymentRecoveryOutcome.NotPaid
                            }
                        }
                        break
                    }
                }

                WecrPaymentResolution.Unresolved -> Unit
            }
            delay(15_000)
            recovery = CardPaymentJournal.load(context) ?: break
        }
    }
}

internal suspend fun MainViewModel.cancelCurrentTransactionImpl(
    context: Context,
): CardPaymentCancellationResult = withContext(Dispatchers.IO) {
    val recovery = CardPaymentJournal.load(context)
        ?: return@withContext CardPaymentCancellationResult.Cancelled
    if (recovery.phase == CardPaymentJournalPhase.PREPARED) {
        return@withContext if (cleanupFailedCardIntentImpl(context, recovery)) {
            CardPaymentCancellationResult.Cancelled
        } else {
            recoverPendingCardPaymentImpl(context, force = true)
            CardPaymentCancellationResult.Unresolved("Payment cleanup pending")
        }
    }
    val client = newWecrClient(context)
    val cancelResult = try {
        client.cancelTransaction(
            keyIndex = recovery.keyIndex,
            transactionRef = recovery.transactionRef,
            force = false,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        DebugLog.add("Cancel transaction response unresolved: ${error.message}")
        null
    }
    val cancelConfirmed = isWecrSuccessStatus(cancelResult?.status?.trim(), null)

    when (
        val resolution = resolveWecrPaymentImpl(
            httpsClient = client,
            keyIndex = recovery.keyIndex.toString(),
            transactionRef = recovery.transactionRef,
            maxAttempts = 6,
            delayMillis = 500,
        )
    ) {
        is WecrPaymentResolution.Paid -> when (
            val finalized = finalizePaidCardOrderImpl(
                context,
                recovery.transactionRef,
                resolution.status,
            )
        ) {
            is PaidCardFinalization.Completed ->
                CardPaymentCancellationResult.Paid(finalized.callNumber)

            PaidCardFinalization.Unresolved -> {
                recoverPendingCardPaymentImpl(context, force = true)
                CardPaymentCancellationResult.Unresolved("Paid order delivery is still being recovered")
            }
        }

        is WecrPaymentResolution.Failed -> {
            if (cleanupFailedCardIntentImpl(context, recovery)) {
                CardPaymentCancellationResult.Cancelled
            } else {
                recoverPendingCardPaymentImpl(context, force = true)
                CardPaymentCancellationResult.Unresolved("Payment cleanup pending")
            }
        }

        WecrPaymentResolution.Unresolved -> {
            if (cancelConfirmed && cleanupFailedCardIntentImpl(context, recovery)) {
                CardPaymentCancellationResult.Cancelled
            } else {
                recoverPendingCardPaymentImpl(context, force = true)
                CardPaymentCancellationResult.Unresolved("Cancellation was not confirmed")
            }
        }
    }
}

internal suspend fun MainViewModel.handlePaymentTimeoutImpl(
    context: Context,
): CardPaymentCancellationResult {
    paymentError = "Payment timeout"
    return cancelCurrentTransactionImpl(context)
}

internal suspend fun MainViewModel.triggerPosMachineImpl(context: Context): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val tcpClient = WecrTcpClient(WecrConfig.posIp(context), WecrConfig.posPort(context))
            val connected = tcpClient.connect(timeoutMillis = 5000)
            val sent = if (connected) tcpClient.sendTrigger() else false
            tcpClient.close()
            withContext(Dispatchers.Main) { posTriggerFailed = !sent }
            sent
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.Main) { posTriggerFailed = true }
            false
        }
    }
}

internal suspend fun MainViewModel.pollWecrStatusImpl(
    httpsClient: WecrHttpsClient,
    keyIndex: Int,
    transactionRef: String,
): Boolean = pollWecrStatusStringImpl(httpsClient, keyIndex.toString(), transactionRef)

internal suspend fun MainViewModel.pollWecrStatusStringImpl(
    httpsClient: WecrHttpsClient,
    keyIndex: String,
    transactionRef: String,
    maxAttempts: Int = 60,
    delayMillis: Long = 500,
): Boolean {
    return resolveWecrPaymentImpl(
        httpsClient = httpsClient,
        keyIndex = keyIndex,
        transactionRef = transactionRef,
        maxAttempts = maxAttempts,
        delayMillis = delayMillis,
    ) is WecrPaymentResolution.Paid
}

internal fun MainViewModel.setCurrentTransactionInfoImpl(transactionRef: String?, keyIndex: Int?) {
    dispatch(MainViewModelAction.SetCurrentTransactionInfo(transactionRef, keyIndex))
}

internal fun MainViewModel.clearPaymentErrorImpl() {
    paymentError = null
}

private fun newWecrClient(context: Context): WecrHttpsClient {
    return WecrHttpsClient(
        apiUrl = WecrConfig.API_URL,
        login = WecrConfig.login(context),
        sid = WecrConfig.sid(context),
        privateKeyPem = WecrConfig.getPrivateKey(context),
        version = WecrConfig.VERSION,
    )
}
