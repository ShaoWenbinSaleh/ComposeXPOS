package com.cofopt.orderingmachine.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume

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
        val nsUrl = NSURL.URLWithString(url)
            ?: return CashRegisterHttpResponse(statusCode = 0, body = "Invalid URL")
        val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setHTTPMethod(method)
            if (requestBody != null) {
                setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
                setHTTPBody(requestBody.encodeToByteArray().toNSData())
            }
        }

        return suspendCancellableCoroutine { cont ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(
                request = request as NSURLRequest,
                completionHandler = { data, response, error ->
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
