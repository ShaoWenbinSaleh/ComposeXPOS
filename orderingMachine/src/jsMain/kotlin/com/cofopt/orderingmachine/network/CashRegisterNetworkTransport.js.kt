package com.cofopt.orderingmachine.network

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit

private external class AbortController {
    val signal: dynamic
    fun abort()
}

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

        val controller = AbortController()
        val timeoutId = window.setTimeout(
            handler = {
                runCatching { controller.abort() }
            },
            timeout = (connectTimeoutMillis + readTimeoutMillis),
        )

        try {
            val init = RequestInit(
                method = method,
                headers = headers,
                body = requestBody,
            )
            init.asDynamic().signal = controller.signal

            val response = window.fetch(url, init).await()
            val body = runCatching { response.text().await() }.getOrDefault("")
            return CashRegisterHttpResponse(
                statusCode = response.status.toInt(),
                body = body,
            )
        } finally {
            window.clearTimeout(timeoutId)
        }
    }
}
