package com.cofopt.orderingmachine.network

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit

private external class AbortController {
    val signal: dynamic
    fun abort()
}

actual object CashRegisterNetworkTransport {
    actual suspend fun testConnection(host: String, port: Int, timeoutMillis: Int): Boolean {
        val healthUrl = cashRegisterUrl(host, port, "/health") ?: return false
        return try {
            val response = request(
                method = "GET",
                url = healthUrl,
                requestBody = null,
                connectTimeoutMillis = timeoutMillis,
                readTimeoutMillis = timeoutMillis,
            )
            isCashRegisterHealthResponse(response)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
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
        val totalTimeoutMillis = (
            connectTimeoutMillis.toLong().coerceAtLeast(0) +
                readTimeoutMillis.toLong().coerceAtLeast(0)
            ).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        val timeoutId = window.setTimeout(
            handler = {
                runCatching { controller.abort() }
            },
            timeout = totalTimeoutMillis,
        )

        try {
            val init = RequestInit(
                method = method,
                headers = headers,
                body = requestBody,
            )
            init.asDynamic().signal = controller.signal

            val response = window.fetch(url, init).await()
            val body = try {
                response.text().await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                ""
            }
            return CashRegisterHttpResponse(
                statusCode = response.status.toInt(),
                body = body,
            )
        } finally {
            window.clearTimeout(timeoutId)
        }
    }
}
