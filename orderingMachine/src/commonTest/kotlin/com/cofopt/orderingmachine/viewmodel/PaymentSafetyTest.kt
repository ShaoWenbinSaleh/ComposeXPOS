package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentEvent
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentState
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentStateMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PaymentSafetyTest {
    @Test
    fun missingWecrResponseIsAmbiguousRatherThanDeclined() {
        assertFalse(isWecrFailureStatus(null, null))
        assertTrue(isWecrFailureStatus("91", null))
        assertTrue(isWecrFailureStatus("00", "1"))
        assertFalse(isWecrSuccessStatus("00", "1"))
        assertFalse(isWecrSuccessStatus("91", "0"))
        assertTrue(isWecrSuccessStatus("00", null))
        assertTrue(isWecrSuccessStatus(null, "0"))
    }

    @Test
    fun unresolvedPaymentStateCannotStartAnotherMethod() {
        val machine = PaymentStateMachine(Language.default)
        machine.handleEvent(PaymentEvent.SelectPaymentMethod(PaymentMethod.CARD))
        machine.handleEvent(PaymentEvent.CardRequestSuccess)
        machine.handleEvent(PaymentEvent.PaymentResolutionRequired())

        val locked = assertIs<PaymentState.PaymentFailed>(machine.getCurrentState())
        assertFalse(locked.canRetry)

        machine.handleEvent(PaymentEvent.SelectPaymentMethod(PaymentMethod.COUNTER))
        assertEquals(locked, machine.getCurrentState())
    }

    @Test
    fun durableRecoveryCanCompleteLockedPayment() {
        val machine = PaymentStateMachine(Language.default)
        machine.handleEvent(PaymentEvent.SelectPaymentMethod(PaymentMethod.CARD))
        machine.handleEvent(PaymentEvent.CardRequestSuccess)
        machine.handleEvent(PaymentEvent.PaymentResolutionRequired())

        machine.handleEvent(PaymentEvent.PaymentRecoveryCompleted(callNumber = 42))

        val success = assertIs<PaymentState.PaymentSuccess>(machine.getCurrentState())
        assertEquals(PaymentMethod.CARD, success.paymentMethod)
        assertEquals(42, success.callNumber)
    }

    @Test
    fun durableRecoveryCanUnlockExplicitlyFailedPayment() {
        val machine = PaymentStateMachine(Language.default)
        machine.handleEvent(PaymentEvent.SelectPaymentMethod(PaymentMethod.CARD))
        machine.handleEvent(PaymentEvent.CardRequestSuccess)
        machine.handleEvent(PaymentEvent.PaymentResolutionRequired())

        machine.handleEvent(PaymentEvent.PaymentRecoveryFailed)

        val selection = assertIs<PaymentState.SelectingPayment>(machine.getCurrentState())
        assertIs<com.cofopt.orderingmachine.ui.PaymentScreen.PaymentError.PaymentCancelled>(
            selection.paymentError
        )
    }
}
