package com.cofopt.orderingmachine.network

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response

actual object CashRegisterNetworkTransport {
    actual suspend fun testConnection(host: String, port: Int, timeoutMillis: Int): Boolean {
        val healthUrl = "http://$host:$port/health"
        return runCatching {
            val response = request(
                method = "GET",
                url = healthUrl,
                requestBody = null,
                connectTimeoutMillis = timeoutMillis,
                readTimeoutMillis = timeoutMillis,
            )
            response.statusCode in 100..599
        }.getOrDefault(false)
    }

    actual suspend fun request(
        method: String,
        url: String,
        requestBody: String?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): CashRegisterHttpResponse {
        val headers = Headers()
        if (requestBody != null) {
            headers.append("Content-Type", "application/json; charset=utf-8")
        }

        val init = RequestInit(
            method = method,
            headers = headers,
        )

        val response: Response = window.fetch(url, init).await()
        val body: String = try {
            response.text().await<String>()
        } catch (_: Throwable) {
            ""
        }

        return CashRegisterHttpResponse(
            statusCode = response.status.toInt(),
            body = body,
        )
    }
}
