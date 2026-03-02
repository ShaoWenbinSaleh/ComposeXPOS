package com.cofopt.orderingmachine.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.Screen
import com.cofopt.orderingmachine.network.WecrConfig
import com.cofopt.orderingmachine.network.WecrHttpsClient
import com.cofopt.orderingmachine.network.WecrTcpClient
import com.cofopt.orderingmachine.serial.DebugLog
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentProcessResult
import com.cofopt.orderingmachine.ui.PaymentScreen.CardPaymentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainViewModel.setPaymentMethodForFlowImpl(method: PaymentMethod) {
    paymentMethod = method
    paymentError = null
    if (method == PaymentMethod.CARD) {
        markCardPaymentFailedImpl(false)
    }
}

internal suspend fun MainViewModel.pollWecrStatusWithDetailsImpl(
    httpsClient: WecrHttpsClient,
    keyIndex: String,
    transactionRef: String,
    maxAttempts: Int = 60,
    delayMillis: Long = 500
): WecrHttpsClient.TransactionStatus? {
    repeat(maxAttempts) { attempt ->
        if (attempt > 0) {
            kotlinx.coroutines.delay(delayMillis)
        }
        val status = runCatching { httpsClient.getTransactionStatus(keyIndex, transactionRef) }.getOrNull()
        DebugLog.add("WECR: Poll status (details) attempt ${attempt + 1}/$maxAttempts result=$status")

        val statusCode = status?.status?.trim()
        val resultCode = status?.transactionResult?.trim()

        val isSuccess = isWecrSuccessStatus(statusCode, resultCode)
        val isFailure = isWecrFailureStatus(statusCode, resultCode)

        if (isSuccess) return status
        if (isFailure && attempt >= 2) return null
    }
    return null
}

internal fun MainViewModel.markCardPaymentFailedImpl(failed: Boolean) {
    cardPaymentFailed = failed
}

internal suspend fun MainViewModel.processCounterPaymentImpl(context: Context): Int {
    return withContext(Dispatchers.IO) {
        Log.d("MainViewModel", "processCounterPayment: start")
        Log.d("MainViewModel", "Payment Event: COUNTER_PAYMENT_STARTED | total_amount=$totalAmount | cart_items_count=${cartItems.size}")
        
        val callNumber = postOrderToCashRegisterIfConfiguredImpl(
            context,
            paymentMethod = "CASH",
            paymentStatus = "UNPAID"
        )
        val createFailed = callNumber == null
        val effectiveCallNumber = callNumber ?: fallbackCallNumberImpl()

        Log.d(
            "MainViewModel",
            "processCounterPayment: postOrder result callNumber=$callNumber effectiveCallNumber=$effectiveCallNumber createFailed=$createFailed"
        )

        if (createFailed) {
            Log.w("MainViewModel", "Payment Event: COUNTER_PAYMENT_ORDER_CREATE_FAILED | fallback_call_number=$effectiveCallNumber")
        } else {
            Log.d("MainViewModel", "Payment Event: COUNTER_PAYMENT_ORDER_CREATED | call_number=$callNumber")
        }

        withContext(Dispatchers.Main) {
            lastCallNumber = effectiveCallNumber
            cashRegisterCreateFailed = createFailed
            cashRegisterCreateFailedWasPaid = false
            val printCallNumber = if (createFailed) {
                formatFallbackCallNumberImpl(effectiveCallNumber)
            } else {
                effectiveCallNumber.toString()
            }
            Log.d("MainViewModel", "processCounterPayment: printing unpaid order callNumber=$printCallNumber")
            printUnpaidOrderImpl(context, printCallNumber)
        }

        Log.d("MainViewModel", "processCounterPayment: done")
        Log.d("MainViewModel", "Payment Event: COUNTER_PAYMENT_COMPLETED | effective_call_number=$effectiveCallNumber | create_failed=$createFailed")
        effectiveCallNumber
    }
}

internal suspend fun MainViewModel.processCounterPaymentOfflineImpl(context: Context): Int {
    return withContext(Dispatchers.IO) {
        Log.d("MainViewModel", "processCounterPaymentOffline: start")

        val effectiveCallNumber = fallbackCallNumberImpl()

        withContext(Dispatchers.Main) {
            lastCallNumber = effectiveCallNumber
            cashRegisterCreateFailed = true
            cashRegisterCreateFailedWasPaid = false
            val printCallNumber = formatFallbackCallNumberImpl(effectiveCallNumber)
            Log.d("MainViewModel", "processCounterPaymentOffline: printing unpaid order callNumber=$printCallNumber")
            printUnpaidOrderImpl(context, printCallNumber)
        }

        Log.d("MainViewModel", "processCounterPaymentOffline: done")
        effectiveCallNumber
    }
}

internal suspend fun MainViewModel.debugSimulateCardPaidImpl(context: Context): Int {
    return withContext(Dispatchers.IO) {
        Log.d("MainViewModel", "debugSimulateCardPaid: start")
        val callNumber = postOrderToCashRegisterIfConfiguredImpl(
            context,
            paymentMethod = "CARD",
            paymentStatus = "PAID"
        )
        val createFailed = callNumber == null
        val effectiveCallNumber = callNumber ?: fallbackCallNumberImpl()

        Log.d(
            "MainViewModel",
            "debugSimulateCardPaid: postOrder result callNumber=$callNumber effectiveCallNumber=$effectiveCallNumber createFailed=$createFailed"
        )

        withContext(Dispatchers.Main) {
            lastCallNumber = effectiveCallNumber
            cashRegisterCreateFailed = createFailed
            cashRegisterCreateFailedWasPaid = true
            val printCallNumber = if (createFailed) {
                formatFallbackCallNumberImpl(effectiveCallNumber)
            } else {
                effectiveCallNumber.toString()
            }
            Log.d("MainViewModel", "debugSimulateCardPaid: printing paid order callNumber=$printCallNumber")
            printPaidOrderImpl(context, printCallNumber)
        }

        Log.d("MainViewModel", "debugSimulateCardPaid: done")
        effectiveCallNumber
    }
}

internal suspend fun MainViewModel.beginCardPaymentImpl(context: Context): CardPaymentResult? {
    return withContext(Dispatchers.IO) {
        try {
            val httpsClient = WecrHttpsClient(
                apiUrl = WecrConfig.API_URL,
                login = WecrConfig.login(context),
                sid = WecrConfig.sid(context),
                privateKeyPem = WecrConfig.getPrivateKey(context),
                version = WecrConfig.VERSION
            )

            val amount = totalAmount
            val merchantRef = "ORDER_${System.currentTimeMillis()}"
            val transactionRef = "TXN_${System.currentTimeMillis()}"

            val transactionResult = httpsClient.startTransaction(
                amount = amount,
                merchantRef = merchantRef,
                transactionRef = transactionRef,
                keyIndex = 0,
                useSignature = true
            )

            if (transactionResult != null && transactionResult.status == "00") {
                val keyIndex = transactionResult.keyIndex?.toIntOrNull()
                val transactionRefActual = transactionResult.transactionRef

                if (keyIndex != null && transactionRefActual != null) {
                    setCurrentTransactionInfoImpl(transactionRefActual, keyIndex)
                    return@withContext CardPaymentResult(
                        transactionRef = transactionRefActual,
                        keyIndex = keyIndex
                    )
                }
            }

            null
        } catch (e: Exception) {
            DebugLog.add("Card payment start failed: ${e.message}")
            null
        }
    }
}

internal suspend fun MainViewModel.processCardPaymentImpl(context: Context): CardPaymentProcessResult {
    return withContext(Dispatchers.IO) {
        try {
            val transactionRef = currentTransactionRef
            val keyIndex = currentKeyIndex

            if (transactionRef == null || keyIndex == null) {
                return@withContext CardPaymentProcessResult(
                    success = false,
                    timeout = false,
                    errorMessage = "Transaction info missing"
                )
            }

            val httpsClient = WecrHttpsClient(
                apiUrl = WecrConfig.API_URL,
                login = WecrConfig.login(context),
                sid = WecrConfig.sid(context),
                privateKeyPem = WecrConfig.getPrivateKey(context),
                version = WecrConfig.VERSION
            )

            triggerPosMachineImpl(context)

            val status = pollWecrStatusWithDetailsImpl(
                httpsClient = httpsClient,
                keyIndex = keyIndex.toString(),
                transactionRef = transactionRef
            )

            if (status != null) {
                val callNumber = postOrderToCashRegisterIfConfiguredImpl(context, "CARD", "PAID")
                val createFailed = callNumber == null
                val effectiveCallNumber = callNumber ?: fallbackCallNumberImpl()
                withContext(Dispatchers.Main) {
                    lastCallNumber = effectiveCallNumber
                    cashRegisterCreateFailed = createFailed
                    cashRegisterCreateFailedWasPaid = true
                    val printCallNumber = if (createFailed) {
                        formatFallbackCallNumberImpl(effectiveCallNumber)
                    } else {
                        effectiveCallNumber.toString()
                    }
                    printPaidOrderImpl(
                        context = context,
                        callNumber = printCallNumber,
                        transactionRef = transactionRef,
                        wecrStatus = status
                    )
                }
                CardPaymentProcessResult(
                    success = true,
                    timeout = false,
                    callNumber = effectiveCallNumber
                )
            } else {
                CardPaymentProcessResult(
                    success = false,
                    timeout = false,
                    errorMessage = "Payment not completed"
                )
            }
        } catch (e: Exception) {
            CardPaymentProcessResult(
                success = false,
                timeout = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
}

internal suspend fun MainViewModel.triggerPosMachineImpl(context: Context): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val tcpClient = WecrTcpClient(WecrConfig.posIp(context), WecrConfig.posPort(context))
            val connected = tcpClient.connect(timeoutMillis = 5000)

            if (connected) {
                val triggerSent = tcpClient.sendTrigger()
                tcpClient.close()
                withContext(Dispatchers.Main) {
                    posTriggerFailed = !triggerSent
                }
                triggerSent
            } else {
                withContext(Dispatchers.Main) {
                    posTriggerFailed = true
                }
                false
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                posTriggerFailed = true
            }
            false
        }
    }
}

internal suspend fun MainViewModel.pollWecrStatusImpl(
    httpsClient: WecrHttpsClient,
    keyIndex: Int,
    transactionRef: String
): Boolean {
    return pollWecrStatusStringImpl(
        httpsClient = httpsClient,
        keyIndex = keyIndex.toString(),
        transactionRef = transactionRef
    )
}

internal suspend fun MainViewModel.pollWecrStatusStringImpl(
    httpsClient: WecrHttpsClient,
    keyIndex: String,
    transactionRef: String,
    maxAttempts: Int = 60,
    delayMillis: Long = 500
): Boolean {
    repeat(maxAttempts) { attempt ->
        if (attempt > 0) {
            kotlinx.coroutines.delay(delayMillis)
        }
        val status = runCatching { httpsClient.getTransactionStatus(keyIndex, transactionRef) }.getOrNull()
        DebugLog.add("WECR: Poll status attempt ${attempt + 1}/$maxAttempts result=$status")

        val statusCode = status?.status?.trim()
        val resultCode = status?.transactionResult?.trim()

        val isSuccess = isWecrSuccessStatus(statusCode, resultCode)
        val isFailure = isWecrFailureStatus(statusCode, resultCode)

        if (isSuccess) return true
        if (isFailure && attempt >= 2) return false
    }
    return false
}

internal fun MainViewModel.setCurrentTransactionInfoImpl(transactionRef: String?, keyIndex: Int?) {
    dispatch(
        MainViewModelAction.SetCurrentTransactionInfo(
            transactionRef = transactionRef,
            keyIndex = keyIndex
        )
    )
}

internal fun MainViewModel.cancelCurrentTransactionImpl(context: Context) {
    val transactionRef = currentTransactionRef
    val keyIndex = currentKeyIndex

    if (transactionRef != null && keyIndex != null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wecrClient = WecrHttpsClient(
                    apiUrl = WecrConfig.API_URL,
                    login = WecrConfig.login(context),
                    sid = WecrConfig.sid(context),
                    privateKeyPem = WecrConfig.getPrivateKey(context),
                    version = WecrConfig.VERSION
                )

                val result = wecrClient.cancelTransaction(
                    keyIndex = keyIndex,
                    transactionRef = transactionRef,
                    force = false
                )

                DebugLog.add("Cancel transaction result: $result")
            } catch (e: Exception) {
                DebugLog.add("Cancel transaction failed: ${e.message}")
            }
        }
    }

    setCurrentTransactionInfoImpl(null, null)

    paymentJob?.cancel()
    paymentJob = null
}

internal fun MainViewModel.handlePaymentTimeoutImpl() {
    paymentError = "Payment timeout"
    setCurrentTransactionInfoImpl(null, null)
    paymentJob?.cancel()
    paymentJob = null
    navigateTo(Screen.PAYMENT_SELECTION)
}

internal fun MainViewModel.clearPaymentErrorImpl() {
    paymentError = null
}
