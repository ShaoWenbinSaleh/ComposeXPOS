package com.cofopt.orderingmachine.viewmodel

import android.content.Context

/**
 * Minimal durable recovery record for an in-flight WECR transaction. The
 * matching order payload is held separately in CashRegisterOrderOutbox.
 */
internal enum class CardPaymentJournalPhase {
    /** The order is durable, but startTransaction has definitely not been called yet. */
    PREPARED,

    /** The start intent is durable; the request or its acknowledgement may be in flight. */
    START_REQUESTED,

    /** WECR acknowledged the start request and returned the persisted correlation data. */
    ACTIVE,
}

internal data class CardPaymentJournalEntry(
    val orderId: String,
    val transactionRef: String,
    val keyIndex: Int,
    val phase: CardPaymentJournalPhase,
)

internal object CardPaymentJournal {
    private const val PREFS_NAME = "card_payment_recovery_v1"
    private const val KEY_ORDER_ID = "order_id"
    private const val KEY_TRANSACTION_REF = "transaction_ref"
    private const val KEY_INDEX = "key_index"
    private const val KEY_PHASE = "phase"

    fun load(context: Context): CardPaymentJournalEntry? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val orderId = prefs.getString(KEY_ORDER_ID, null)?.trim().orEmpty()
        val transactionRef = prefs.getString(KEY_TRANSACTION_REF, null)?.trim().orEmpty()
        val keyIndex = prefs.getInt(KEY_INDEX, Int.MIN_VALUE)
        if (orderId.isEmpty() || transactionRef.isEmpty() || keyIndex == Int.MIN_VALUE) return null
        // Records written by v1 had no phase and were persisted immediately
        // before startTransaction. They are therefore ambiguous, never safely
        // discardable, and must be treated as START_REQUESTED.
        val phase = prefs.getString(KEY_PHASE, null)
            ?.let { raw -> CardPaymentJournalPhase.entries.firstOrNull { it.name == raw } }
            ?: CardPaymentJournalPhase.START_REQUESTED
        return CardPaymentJournalEntry(orderId, transactionRef, keyIndex, phase)
    }

    fun save(context: Context, entry: CardPaymentJournalEntry): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER_ID, entry.orderId)
            .putString(KEY_TRANSACTION_REF, entry.transactionRef)
            .putInt(KEY_INDEX, entry.keyIndex)
            .putString(KEY_PHASE, entry.phase.name)
            .commit()
    }

    fun clear(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
