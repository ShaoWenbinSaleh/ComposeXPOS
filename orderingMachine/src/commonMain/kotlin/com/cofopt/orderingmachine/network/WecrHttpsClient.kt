package com.cofopt.orderingmachine.network

import com.cofopt.shared.payment.wecr.WecrDefaults

class WecrHttpsClient(
    apiUrl: String,
    login: String,
    sid: String,
    privateKeyPem: String,
    version: String = WecrDefaults.VERSION,
    soapNamespace: String = "https://example.invalid/payment",
    cancelSoapNamespace: String = soapNamespace,
    logger: ((String) -> Unit)? = null,
) {
    private val delegate = com.cofopt.shared.payment.wecr.WecrHttpsClient(
        apiUrl = apiUrl,
        login = login,
        sid = sid,
        privateKeyPem = privateKeyPem,
        version = version,
        soapNamespace = soapNamespace,
        cancelSoapNamespace = cancelSoapNamespace,
        logger = logger,
    )

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

    suspend fun startTransaction(
        amount: Double,
        merchantRef: String = "",
        transactionRef: String = "",
        keyIndex: Int = 0,
        useSignature: Boolean = true,
    ): TransactionResult? {
        return delegate.startTransaction(
            amount = amount,
            merchantRef = merchantRef,
            transactionRef = transactionRef,
            keyIndex = keyIndex,
            useSignature = useSignature,
        )?.toCompat()
    }

    suspend fun getTransactionStatus(
        keyIndex: String,
        transactionRef: String = "",
    ): TransactionStatus? {
        return delegate.getTransactionStatus(keyIndex = keyIndex, transactionRef = transactionRef)?.toCompat()
    }

    suspend fun cancelTransaction(
        keyIndex: Int,
        transactionRef: String,
        force: Boolean = false,
    ): CancelTransactionResult? {
        return delegate.cancelTransaction(
            keyIndex = keyIndex,
            transactionRef = transactionRef,
            force = force,
        )?.toCompat()
    }
}

private fun com.cofopt.shared.payment.wecr.WecrHttpsClient.TransactionResult.toCompat(): WecrHttpsClient.TransactionResult {
    return WecrHttpsClient.TransactionResult(
        keyIndex = keyIndex,
        transactionRef = transactionRef,
        merchantRef = merchantRef,
        amount = amount,
        status = status,
        message = message,
        terminalIp = terminalIp,
        terminalPort = terminalPort,
    )
}

private fun com.cofopt.shared.payment.wecr.WecrHttpsClient.TransactionStatus.toCompat(): WecrHttpsClient.TransactionStatus {
    return WecrHttpsClient.TransactionStatus(
        keyIndex = keyIndex,
        transactionResult = transactionResult,
        status = status,
        message = message,
        amount = amount,
        brand = brand,
        ticket = ticket,
        extras = extras,
    )
}

private fun com.cofopt.shared.payment.wecr.WecrHttpsClient.CancelTransactionResult.toCompat(): WecrHttpsClient.CancelTransactionResult {
    return WecrHttpsClient.CancelTransactionResult(
        keyIndex = keyIndex,
        version = version,
        login = login,
        sid = sid,
        transactionRef = transactionRef,
        status = status,
        message = message,
        signature = signature,
    )
}
