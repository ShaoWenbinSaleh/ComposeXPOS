package com.cofopt.orderingmachine.network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        } catch (_: Exception) {
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
        return withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            try {
                conn.requestMethod = method
                conn.connectTimeout = connectTimeoutMillis.coerceAtLeast(1)
                conn.readTimeout = readTimeoutMillis.coerceAtLeast(1)
                conn.setRequestProperty("Accept", "application/json, text/plain")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                if (requestBody != null) {
                    conn.doOutput = true
                    conn.outputStream.use { os ->
                        os.write(requestBody.toByteArray(Charsets.UTF_8))
                    }
                }

                val code = conn.responseCode
                val body = runCatching {
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }.getOrDefault("")

                CashRegisterHttpResponse(
                    statusCode = code,
                    body = body,
                )
            } finally {
                conn.disconnect()
            }
        }
    }
}
