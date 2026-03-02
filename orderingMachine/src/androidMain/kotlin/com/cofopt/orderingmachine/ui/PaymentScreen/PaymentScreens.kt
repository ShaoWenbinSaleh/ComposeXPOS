package com.cofopt.orderingmachine.ui.PaymentScreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cofopt.orderingmachine.OrderMode
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.WecrConfig
import com.cofopt.orderingmachine.viewmodel.MainViewModel
import com.cofopt.shared.mock.MockFeatureNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PaymentFlowScreen(
    viewModel: MainViewModel,
    onBackToOrdering: () -> Unit,
    onNextCustomer: () -> Unit,
) {
    val context = LocalContext.current
    val debugCardSmEnabled = WecrConfig.debugCardSmEnabled(context)
    val language = viewModel.language
    val dineIn = viewModel.orderMode == OrderMode.DINE_IN
    val cartItems = viewModel.cartItems
    val total = viewModel.totalAmount
    val coroutineScope = rememberCoroutineScope()

    val stateMachine = remember { PaymentStateMachine(language) }
    var currentState by remember { mutableStateOf(stateMachine.getCurrentState()) }

    LaunchedEffect(language) {
        stateMachine.updateLanguage(language)
    }

    DisposableEffect(stateMachine) {
        stateMachine.setStateChangeListener { newState ->
            currentState = newState
        }
        onDispose {
            stateMachine.setStateChangeListener { }
            viewModel.paymentJob?.cancel()
            viewModel.paymentJob = null
        }
    }

    val s = currentState

    fun launchManagedPaymentJob(block: suspend () -> Unit) {
        viewModel.paymentJob?.cancel()
        val job = coroutineScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                if (viewModel.paymentJob === coroutineContext[Job]) {
                    viewModel.paymentJob = null
                }
            }
        }
        viewModel.paymentJob = job
    }

    LaunchedEffect(s) {
        android.util.Log.d(
            "PaymentFlow",
            "state=${s::class.java.simpleName}, paymentMethod=${(s as? PaymentState.ProcessingPayment)?.paymentMethod}, debugCardSmEnabled=$debugCardSmEnabled"
        )
        when (s) {
            is PaymentState.ProcessingCounterPayment -> {
                launchManagedPaymentJob {
                    try {
                        android.util.Log.d("PaymentFlow", "Starting COUNTER payment")
                        viewModel.setPaymentMethodForFlow(PaymentMethod.COUNTER)
                        val connected = CashRegisterClient.testConnectionWithRetry(context)
                        val callNumber = if (connected) {
                            viewModel.processCounterPayment(context)
                        } else {
                            viewModel.processCounterPaymentOffline(context)
                        }
                        android.util.Log.d("PaymentFlow", "COUNTER payment completed: callNumber=$callNumber")
                        stateMachine.handleEvent(PaymentEvent.PaymentCompleted(callNumber))
                    } catch (e: Exception) {
                        android.util.Log.e("PaymentFlow", "COUNTER payment failed", e)
                        stateMachine.handleEvent(
                            PaymentEvent.PaymentFailed(
                                PaymentError.UnknownError(e.message ?: "Counter payment failed")
                            )
                        )
                    }
                }
            }

            is PaymentState.ProcessingCardRequest -> {
                MockFeatureNotice.showPayment(context, "OrderingMachine")
                if (debugCardSmEnabled) {
                    viewModel.paymentJob?.cancel()
                    viewModel.paymentJob = null
                    val requestSuccess = WecrConfig.debugPosRequestSuccess(context)
                    if (requestSuccess) {
                        stateMachine.handleEvent(PaymentEvent.CardRequestSuccess)
                    } else {
                        stateMachine.handleEvent(PaymentEvent.CardRequestFailed(PaymentError.RequestFailed))
                    }
                } else {
                    launchManagedPaymentJob {
                        try {
                            viewModel.setPaymentMethodForFlow(PaymentMethod.CARD)
                            val result = viewModel.beginCardPayment(context)
                            if (result != null) {
                                stateMachine.handleEvent(PaymentEvent.CardRequestSuccess)
                            } else {
                                stateMachine.handleEvent(PaymentEvent.CardRequestFailed(PaymentError.RequestFailed))
                            }
                        } catch (e: Exception) {
                            stateMachine.handleEvent(
                                PaymentEvent.CardRequestFailed(
                                    PaymentError.UnknownError(e.message ?: "Unknown error")
                                )
                            )
                        }
                    }
                }
            }

            is PaymentState.ProcessingPayment -> {
                if (debugCardSmEnabled) {
                    viewModel.paymentJob?.cancel()
                    viewModel.paymentJob = null
                    val triggerSuccess = WecrConfig.debugPosTriggerSuccess(context)
                    stateMachine.handleEvent(PaymentEvent.PosTriggerResult(success = triggerSuccess))
                } else {
                    launchManagedPaymentJob {
                        try {
                            viewModel.setPaymentMethodForFlow(PaymentMethod.CARD)
                            val result = viewModel.processCardPayment(context)
                            when {
                                result.success -> stateMachine.handleEvent(PaymentEvent.PaymentCompleted(result.callNumber))
                                result.timeout -> stateMachine.handleEvent(PaymentEvent.PaymentTimeout)
                                else -> stateMachine.handleEvent(
                                    PaymentEvent.PaymentFailed(
                                        PaymentError.UnknownError(result.errorMessage ?: "Payment failed")
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            stateMachine.handleEvent(
                                PaymentEvent.PaymentFailed(
                                    PaymentError.UnknownError(e.message ?: "Unknown error")
                                )
                            )
                        }
                    }
                }
            }

            else -> {
                viewModel.paymentJob?.cancel()
                viewModel.paymentJob = null
            }
        }
    }

    AnimatedContent(
        targetState = s,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(220)) + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = tween(220)
                )
            ).togetherWith(
                fadeOut(animationSpec = tween(180)) + scaleOut(
                    targetScale = 1.01f,
                    animationSpec = tween(180)
                )
            )
        },
        contentKey = { it::class },
        label = "payment_state_transition"
    ) { state ->
        when (state) {
            is PaymentState.SelectingPayment -> {
                val paymentError = state.paymentError?.let { stateMachine.getErrorMessage(it, state.language) }
                PaymentSelectionScreen(
                    language = state.language,
                    dineIn = dineIn,
                    cartItems = cartItems,
                    total = total,
                    paymentError = paymentError,
                    printError = viewModel.printError,
                    onSelect = { paymentMethod, _ ->
                        android.util.Log.d(
                            "PaymentFlow",
                            "PaymentSelection onSelect: method=$paymentMethod debugCardSmEnabled=$debugCardSmEnabled"
                        )
                        stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                    },
                    onBack = onBackToOrdering
                )
            }

            is PaymentState.ProcessingCardRequest -> {
                PaymentProcessingScreen(
                    language = state.language,
                    posTriggerFailed = if (debugCardSmEnabled) false else viewModel.posTriggerFailed,
                    onCancel = {
                        viewModel.cancelCurrentTransaction(context)
                        stateMachine.handleEvent(PaymentEvent.PaymentCancelled)
                    },
                    onTimeout = {
                        viewModel.handlePaymentTimeout()
                        stateMachine.handleEvent(PaymentEvent.PaymentTimeout)
                    },
                    onDebugSuccess = if (debugCardSmEnabled) {
                        { stateMachine.handleEvent(PaymentEvent.CardRequestSuccess) }
                    } else {
                        null
                    },
                    onDebugFail = if (debugCardSmEnabled) {
                        { stateMachine.handleEvent(PaymentEvent.CardRequestFailed(PaymentError.RequestFailed)) }
                    } else {
                        null
                    }
                )
            }

            is PaymentState.ProcessingCounterPayment -> {
                CounterPaymentProcessingScreen(language = language)
            }

            is PaymentState.ProcessingPayment -> {
                PaymentProcessingScreen(
                    language = state.language,
                    posTriggerFailed = if (debugCardSmEnabled) state.posTriggerFailed else viewModel.posTriggerFailed,
                    onCancel = {
                        viewModel.cancelCurrentTransaction(context)
                        stateMachine.handleEvent(PaymentEvent.PaymentCancelled)
                    },
                    onTimeout = {
                        viewModel.handlePaymentTimeout()
                        stateMachine.handleEvent(PaymentEvent.PaymentTimeout)
                    },
                    onDebugSuccess = if (debugCardSmEnabled && state.paymentMethod == PaymentMethod.CARD) {
                        {
                            launchManagedPaymentJob {
                                try {
                                    viewModel.setPaymentMethodForFlow(PaymentMethod.CARD)
                                    val callNumber = viewModel.debugSimulateCardPaid(context)
                                    stateMachine.handleEvent(PaymentEvent.PaymentCompleted(callNumber))
                                } catch (e: Exception) {
                                    stateMachine.handleEvent(
                                        PaymentEvent.PaymentFailed(
                                            PaymentError.UnknownError(e.message ?: "Debug card payment failed")
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                    onDebugFail = if (debugCardSmEnabled && state.paymentMethod == PaymentMethod.CARD) {
                        {
                            stateMachine.handleEvent(
                                PaymentEvent.PaymentFailed(
                                    PaymentError.UnknownError("Simulated payment failure")
                                )
                            )
                        }
                    } else {
                        null
                    }
                )
            }

            is PaymentState.PaymentSuccess -> {
                PaymentResultScreen(
                    language = state.language,
                    paymentMethod = state.paymentMethod,
                    callNumber = state.callNumber,
                    cardPaymentFailed = viewModel.cardPaymentFailed,
                    cashRegisterCreateFailed = viewModel.cashRegisterCreateFailed,
                    cashRegisterCreateFailedWasPaid = viewModel.cashRegisterCreateFailedWasPaid,
                    onNextCustomer = onNextCustomer
                )
            }

            is PaymentState.PaymentFailed -> {
                val paymentError = stateMachine.getErrorMessage(state.error, state.language)
                PaymentSelectionScreen(
                    language = state.language,
                    dineIn = dineIn,
                    cartItems = cartItems,
                    total = total,
                    paymentError = paymentError,
                    printError = viewModel.printError,
                    onSelect = { paymentMethod, _ ->
                        stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                    },
                    onBack = onBackToOrdering
                )
            }

            is PaymentState.PaymentTimeout -> {
                val paymentError = stateMachine.getErrorMessage(PaymentError.PaymentTimeout, state.language)
                PaymentSelectionScreen(
                    language = state.language,
                    dineIn = dineIn,
                    cartItems = cartItems,
                    total = total,
                    paymentError = paymentError,
                    printError = viewModel.printError,
                    onSelect = { paymentMethod, _ ->
                        stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                    },
                    onBack = onBackToOrdering
                )
            }

            is PaymentState.Idle -> {
                PaymentSelectionScreen(
                    language = language,
                    dineIn = dineIn,
                    cartItems = cartItems,
                    total = total,
                    paymentError = null,
                    printError = viewModel.printError,
                    onSelect = { paymentMethod, _ ->
                        stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                    },
                    onBack = onBackToOrdering
                )
            }
        }
    }
}
