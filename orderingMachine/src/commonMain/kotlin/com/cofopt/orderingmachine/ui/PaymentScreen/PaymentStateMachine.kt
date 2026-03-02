package com.cofopt.orderingmachine.ui.PaymentScreen

import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.tr

/*
Idle → SelectingPayment → ProcessingCardRequest → ProcessingPayment → PaymentSuccess
                            ↓                      ↓                    ↓
                        PaymentFailed ← PaymentTimeout ← PaymentFailed ← PaymentCancelled
                            ↓                      ↓
                        RetryPayment → SelectingPayment
 */
sealed class PaymentState {
    object Idle : PaymentState()

    data class SelectingPayment(
        val language: Language,
        val paymentError: PaymentError? = null
    ) : PaymentState()

    data class ProcessingCardRequest(
        val language: Language
    ) : PaymentState()

    data class ProcessingPayment(
        val language: Language,
        val paymentMethod: PaymentMethod,
        val posTriggerFailed: Boolean = false
    ) : PaymentState()

    data class ProcessingCounterPayment(
        val language: Language
    ) : PaymentState()

    data class PaymentSuccess(
        val language: Language,
        val paymentMethod: PaymentMethod,
        val callNumber: Int?
    ) : PaymentState()

    data class PaymentFailed(
        val language: Language,
        val error: PaymentError,
        val canRetry: Boolean = true
    ) : PaymentState()

    data class PaymentTimeout(
        val language: Language
    ) : PaymentState()
}

sealed class PaymentError {
    object RequestFailed : PaymentError()
    object PosTriggerFailed : PaymentError()
    object PaymentTimeout : PaymentError()
    object PaymentCancelled : PaymentError()
    data class UnknownError(val message: String) : PaymentError()
}

sealed class PaymentEvent {
    object StartPaymentFlow : PaymentEvent()
    data class SelectPaymentMethod(val method: PaymentMethod) : PaymentEvent()
    object CardRequestSuccess : PaymentEvent()
    data class CardRequestFailed(val error: PaymentError) : PaymentEvent()
    data class PosTriggerResult(val success: Boolean) : PaymentEvent()
    data class PaymentCompleted(val callNumber: Int?) : PaymentEvent()
    data class PaymentFailed(val error: PaymentError) : PaymentEvent()
    object PaymentTimeout : PaymentEvent()
    object PaymentCancelled : PaymentEvent()
    object RetryPayment : PaymentEvent()
    object BackToSelection : PaymentEvent()
}

class PaymentStateMachine(initialLanguage: Language) {
    private var language: Language = initialLanguage
    private var currentState: PaymentState = PaymentState.SelectingPayment(language = initialLanguage)
    private var stateChangeListener: ((PaymentState) -> Unit)? = null

    fun updateLanguage(newLanguage: Language) {
        language = newLanguage
        val state = currentState
        val updated = when (state) {
            is PaymentState.SelectingPayment -> state.copy(language = newLanguage)
            is PaymentState.ProcessingCardRequest -> state.copy(language = newLanguage)
            is PaymentState.ProcessingPayment -> state.copy(language = newLanguage)
            is PaymentState.ProcessingCounterPayment -> state.copy(language = newLanguage)
            is PaymentState.PaymentSuccess -> state.copy(language = newLanguage)
            is PaymentState.PaymentFailed -> state.copy(language = newLanguage)
            is PaymentState.PaymentTimeout -> state.copy(language = newLanguage)
            is PaymentState.Idle -> state
        }
        if (updated != state) {
            setState(updated)
        }
    }

    fun setStateChangeListener(listener: (PaymentState) -> Unit) {
        stateChangeListener = listener
    }

    fun getCurrentState(): PaymentState = currentState

    private fun setState(newState: PaymentState) {
        currentState = newState
        stateChangeListener?.invoke(newState)
    }

    fun handleEvent(event: PaymentEvent) {
        val newState = when (val state = currentState) {
            is PaymentState.Idle -> {
                when (event) {
                    is PaymentEvent.StartPaymentFlow -> PaymentState.SelectingPayment(language = language)
                    else -> state
                }
            }

            is PaymentState.SelectingPayment -> {
                when (event) {
                    is PaymentEvent.SelectPaymentMethod -> {
                        when (event.method) {
                            PaymentMethod.CARD -> PaymentState.ProcessingCardRequest(language = state.language)
                            PaymentMethod.CASH -> PaymentState.ProcessingCounterPayment(language = state.language)
                            PaymentMethod.COUNTER -> PaymentState.ProcessingCounterPayment(language = state.language)
                        }
                    }

                    is PaymentEvent.RetryPayment -> PaymentState.SelectingPayment(language = state.language, paymentError = null)
                    is PaymentEvent.BackToSelection -> PaymentState.SelectingPayment(language = state.language, paymentError = state.paymentError)
                    else -> state
                }
            }

            is PaymentState.ProcessingCardRequest -> {
                when (event) {
                    is PaymentEvent.CardRequestSuccess -> PaymentState.ProcessingPayment(
                        language = state.language,
                        paymentMethod = PaymentMethod.CARD
                    )

                    is PaymentEvent.CardRequestFailed -> PaymentState.SelectingPayment(
                        language = state.language,
                        paymentError = event.error
                    )

                    else -> state
                }
            }

            is PaymentState.ProcessingPayment -> {
                when (event) {
                    is PaymentEvent.PosTriggerResult -> state.copy(posTriggerFailed = !event.success)
                    is PaymentEvent.PaymentCompleted -> PaymentState.PaymentSuccess(
                        language = state.language,
                        paymentMethod = state.paymentMethod,
                        callNumber = event.callNumber
                    )

                    is PaymentEvent.PaymentFailed -> PaymentState.PaymentFailed(
                        language = state.language,
                        error = event.error
                    )

                    is PaymentEvent.PaymentTimeout -> PaymentState.PaymentTimeout(language = state.language)
                    is PaymentEvent.PaymentCancelled -> PaymentState.SelectingPayment(
                        language = state.language,
                        paymentError = PaymentError.PaymentCancelled
                    )

                    else -> state
                }
            }

            is PaymentState.ProcessingCounterPayment -> {
                when (event) {
                    is PaymentEvent.PaymentCompleted -> PaymentState.PaymentSuccess(
                        language = state.language,
                        paymentMethod = PaymentMethod.COUNTER,
                        callNumber = event.callNumber
                    )

                    is PaymentEvent.PaymentFailed -> PaymentState.PaymentFailed(
                        language = state.language,
                        error = event.error
                    )

                    is PaymentEvent.PaymentTimeout -> PaymentState.PaymentTimeout(language = state.language)
                    is PaymentEvent.PaymentCancelled -> PaymentState.SelectingPayment(
                        language = state.language,
                        paymentError = PaymentError.PaymentCancelled
                    )

                    else -> state
                }
            }

            is PaymentState.PaymentSuccess -> state

            is PaymentState.PaymentFailed -> {
                when (event) {
                    is PaymentEvent.SelectPaymentMethod -> {
                        when (event.method) {
                            PaymentMethod.CARD -> PaymentState.ProcessingCardRequest(language = state.language)
                            PaymentMethod.CASH -> PaymentState.ProcessingCounterPayment(language = state.language)
                            PaymentMethod.COUNTER -> PaymentState.ProcessingCounterPayment(language = state.language)
                        }
                    }

                    is PaymentEvent.RetryPayment -> PaymentState.SelectingPayment(language = state.language)
                    is PaymentEvent.BackToSelection -> PaymentState.SelectingPayment(language = state.language)
                    else -> state
                }
            }

            is PaymentState.PaymentTimeout -> {
                when (event) {
                    is PaymentEvent.SelectPaymentMethod -> {
                        when (event.method) {
                            PaymentMethod.CARD -> PaymentState.ProcessingCardRequest(language = state.language)
                            PaymentMethod.CASH -> PaymentState.ProcessingCounterPayment(language = state.language)
                            PaymentMethod.COUNTER -> PaymentState.ProcessingCounterPayment(language = state.language)
                        }
                    }

                    is PaymentEvent.RetryPayment -> PaymentState.SelectingPayment(language = state.language)
                    is PaymentEvent.BackToSelection -> PaymentState.SelectingPayment(language = state.language)
                    else -> state
                }
            }
        }

        if (newState != currentState) {
            setState(newState)
        }
    }

    fun getErrorMessage(error: PaymentError, language: Language): String {
        return when (error) {
            is PaymentError.RequestFailed -> tr(
                language,
                "Request failed. Please retry or select payment at counter.",
                "发送请求失败。请重试或选择在收银台支付。",
                "Verzoek mislukt. Probeer het opnieuw of betaal bij de kassa.",
                ja = "リクエストに失敗しました。再試行するか、レジでお支払いください。",
                tr = "İstek başarısız oldu. Lütfen tekrar deneyin veya kasada ödeyin."
            )

            is PaymentError.PosTriggerFailed -> tr(
                language,
                "Sorry, POS wake-up failed. Please click any number key on the POS machine and then pay.",
                "抱歉，POS机唤醒失败。请在POS机上点击任意数字键后支付。",
                "Sorry, POS wake-up mislukt. Klik op een willekeurige cijfertoets op de POS-machine en betaal daarna.",
                ja = "申し訳ありません。POS端末の起動に失敗しました。POSで任意の数字キーを押してからお支払いください。",
                tr = "Üzgünüz, POS uyandırma başarısız oldu. POS cihazında herhangi bir rakam tuşuna basıp ardından ödeme yapın."
            )

            is PaymentError.PaymentTimeout -> tr(
                language,
                "Payment timeout. Please retry or select payment at counter.",
                "支付超时。请重试或选择在收银台支付。",
                "Betaling time-out. Probeer het opnieuw of betaal bij de kassa.",
                ja = "支払いがタイムアウトしました。再試行するか、レジでお支払いください。",
                tr = "Ödeme zaman aşımına uğradı. Lütfen tekrar deneyin veya kasada ödeyin."
            )

            is PaymentError.PaymentCancelled -> tr(
                language,
                "Payment cancelled. Please try again.",
                "支付已取消。请重试。",
                "Betaling geannuleerd. Probeer het opnieuw.",
                ja = "支払いがキャンセルされました。もう一度お試しください。",
                tr = "Ödeme iptal edildi. Lütfen tekrar deneyin."
            )

            is PaymentError.UnknownError -> tr(
                language,
                "Payment failed: ${error.message}. Please retry or select payment at counter.",
                "支付失败：${error.message}。请重试或选择在收银台支付。",
                "Betaling mislukt: ${error.message}. Probeer het opnieuw of betaal bij de kassa.",
                ja = "支払いに失敗しました: ${error.message}。再試行するか、レジでお支払いください。",
                tr = "Ödeme başarısız: ${error.message}. Lütfen tekrar deneyin veya kasada ödeyin."
            )
        }
    }
}
