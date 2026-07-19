package com.cofopt.orderingmachine.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume

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
        val nsUrl = NSURL.URLWithString(url)
            ?: return CashRegisterHttpResponse(statusCode = 0, body = "Invalid URL")
        val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setHTTPMethod(method)
            val totalTimeoutMillis =
                connectTimeoutMillis.toLong().coerceAtLeast(0) + readTimeoutMillis.toLong().coerceAtLeast(0)
            setTimeoutInterval(totalTimeoutMillis.coerceAtLeast(1).toDouble() / 1000.0)
            setValue("application/json, text/plain", forHTTPHeaderField = "Accept")
            if (requestBody != null) {
                setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
                setHTTPBody(requestBody.encodeToByteArray().toNSData())
            }
        }

        return suspendCancellableCoroutine { cont ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(
                request = request as NSURLRequest,
                completionHandler = { data, response, error ->
                    if (!cont.isActive) {
                        return@dataTaskWithRequest
                    }
                    if (error != null) {
                        cont.resume(
                            CashRegisterHttpResponse(
                                statusCode = 0,
                                body = error.localizedDescription ?: "network_error",
                            )
                        )
                        return@dataTaskWithRequest
                    }

                    val statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    val body = if (data != null) {
                        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
                    } else {
                        ""
                    }

                    cont.resume(CashRegisterHttpResponse(statusCode = statusCode, body = body))
                },
            )

            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}
