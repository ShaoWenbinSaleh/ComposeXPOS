@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cofopt.orderingmachine.network

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import kotlin.js.JsAny

private external class AbortController : JsAny {
    val signal: JsAny
    fun abort()
}

private fun requestInit(
    method: String,
    headers: Headers,
    body: String?,
    signal: JsAny,
): RequestInit = js("({ method: method, headers: headers, body: body, signal: signal })")

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
                null
            },
            timeout = totalTimeoutMillis,
        )

        try {
            val init = requestInit(
                method = method,
                headers = headers,
                body = requestBody,
                signal = controller.signal,
            )

            val response: Response = window.fetch(url, init).await()
            val body: String = try {
                response.text().await<String>()
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
