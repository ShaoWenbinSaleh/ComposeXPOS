package com.cofopt.orderingmachine.network

data class CashRegisterHttpResponse(
    val statusCode: Int,
    val body: String,
)

expect object CashRegisterNetworkTransport {
    suspend fun testConnection(host: String, port: Int, timeoutMillis: Int): Boolean

    suspend fun request(
        method: String,
        url: String,
        requestBody: String?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): CashRegisterHttpResponse
}
