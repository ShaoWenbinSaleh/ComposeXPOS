package com.cofopt.shared.payment.wecr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Open-source mock implementation.
 *
 * To integrate a real provider:
 * 1) Replace these methods with provider SDK/API calls.
 * 2) Load credentials from secure runtime config (never from source code).
 * 3) Add signature verification, retry policy, and audit logging.
 */
class WecrHttpsClient(
    private val apiUrl: String,
    private val login: String,
    private val sid: String,
    private val privateKeyPem: String,
    private val version: String = WecrDefaults.VERSION,
    private val soapNamespace: String = "https://example.invalid/payment",
    private val cancelSoapNamespace: String = soapNamespace,
    private val logger: ((String) -> Unit)? = null,
) {
    data class TransactionResult(
        val keyIndex: String?,
        val transactionRef: String?,
        val merchantRef: String?,
        val amount: String?,
        val status: String?,
        val message: String?,
        val terminalIp: String?,
        val terminalPort: String?,
    )

    data class TransactionStatus(
        val keyIndex: String?,
        val transactionResult: String?,
        val status: String?,
        val message: String?,
        val amount: String?,
        val brand: String?,
        val ticket: String?,
        val extras: Map<String, String?> = emptyMap(),
    )

    data class CancelTransactionResult(
        val keyIndex: String?,
        val version: String?,
        val login: String?,
        val sid: String?,
        val transactionRef: String?,
        val status: String?,
        val message: String?,
        val signature: String?,
    )

    private fun log(message: String) {
        logger?.invoke(message)
    }

    suspend fun startTransaction(
        amount: Double,
        merchantRef: String = "",
        transactionRef: String = "",
        keyIndex: Int = 0,
        useSignature: Boolean = true,
    ): TransactionResult? = withContext(Dispatchers.Default) {
        delay(250)

        val nonce = nextMockNonce()
        val resolvedTransactionRef = transactionRef.ifBlank { "MOCK_TXN_$nonce" }
        val resolvedMerchantRef = merchantRef.ifBlank { "MOCK_ORDER_$nonce" }

        log("MOCK startTransaction amount=$amount keyIndex=$keyIndex apiUrl=$apiUrl")

        TransactionResult(
            keyIndex = keyIndex.toString(),
            transactionRef = resolvedTransactionRef,
            merchantRef = resolvedMerchantRef,
            amount = formatAmount2(amount),
            status = "00",
            message = "MOCK: transaction accepted",
            terminalIp = WecrDefaults.DEFAULT_POS_IP,
            terminalPort = WecrDefaults.DEFAULT_POS_PORT.toString(),
        )
    }

    suspend fun getTransactionStatus(
        keyIndex: String,
        transactionRef: String = "",
    ): TransactionStatus? = withContext(Dispatchers.Default) {
        delay(250)

        log("MOCK getTransactionStatus keyIndex=$keyIndex transactionRef=$transactionRef")

        TransactionStatus(
            keyIndex = keyIndex,
            transactionResult = "0",
            status = "00",
            message = "MOCK: payment approved",
            amount = null,
            brand = "MOCK_CARD",
            ticket = "MOCK_TICKET_${nextMockNonce()}",
            extras = mapOf(
                "integration_mode" to "mock",
                "api_url" to apiUrl,
                "namespace" to soapNamespace,
                "version" to version,
                "login" to login,
                "sid" to sid,
                "has_private_key" to privateKeyPem.isNotBlank().toString(),
            ),
        )
    }

    suspend fun cancelTransaction(
        keyIndex: Int,
        transactionRef: String,
        force: Boolean = false,
    ): CancelTransactionResult? = withContext(Dispatchers.Default) {
        delay(150)

        log("MOCK cancelTransaction keyIndex=$keyIndex transactionRef=$transactionRef force=$force")

        CancelTransactionResult(
            keyIndex = keyIndex.toString(),
            version = version,
            login = login,
            sid = sid,
            transactionRef = transactionRef,
            status = "00",
            message = "MOCK: transaction cancelled",
            signature = null,
        )
    }

    private fun formatAmount2(amount: Double): String {
        val scaled = (amount * 100.0).roundToLong()
        val absScaled = abs(scaled)
        val whole = absScaled / 100
        val fraction = (absScaled % 100).toString().padStart(2, '0')
        val sign = if (scaled < 0) "-" else ""
        return "$sign$whole.$fraction"
    }

    private fun nextMockNonce(): String {
        return Random.nextLong(100_000_000_000L, 999_999_999_999L).toString()
    }
}
