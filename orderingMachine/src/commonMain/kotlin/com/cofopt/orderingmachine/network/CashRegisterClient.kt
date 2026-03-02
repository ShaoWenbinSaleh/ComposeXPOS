package com.cofopt.orderingmachine.network

import com.cofopt.orderingmachine.currentTimeMillis
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CashRegisterClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isConfigured(context: OrderingPlatformContext): Boolean {
        val host = CashRegisterConfig.host(context).trim()
        val port = CashRegisterConfig.port(context)
        return host.isNotEmpty() && port > 0
    }

    suspend fun testConnection(context: OrderingPlatformContext): Boolean {
        val host = CashRegisterConfig.host(context).trim()
        val port = CashRegisterConfig.port(context)
        if (host.isEmpty() || port <= 0) {
            return false
        }
        return runCatching {
            CashRegisterNetworkTransport.testConnection(host, port, timeoutMillis = 5000)
        }.getOrDefault(false)
    }

    suspend fun testConnectionWithRetry(
        context: OrderingPlatformContext,
        timeoutMillis: Long = 15_000,
        retryDelayMillis: Long = 400,
    ): Boolean {
        val startedAt = currentTimeMillis()
        while (currentTimeMillis() - startedAt < timeoutMillis) {
            if (testConnection(context)) return true
            val elapsed = currentTimeMillis() - startedAt
            val remaining = timeoutMillis - elapsed
            if (remaining <= 0) return false
            delay(min(retryDelayMillis, remaining))
        }
        return false
    }

    suspend fun postOrder(
        context: OrderingPlatformContext,
        order: CashRegisterOrderPayload,
        maxRetries: Int = 3,
    ): Int? {
        val host = CashRegisterConfig.host(context).trim()
        val port = CashRegisterConfig.port(context)
        if (host.isEmpty() || port <= 0) {
            return null
        }

        repeat(maxRetries) { attempt ->
            try {
                val url = "http://$host:$port/orders"
                val payload = json.encodeToString(CashRegisterOrderPayload.serializer(), order)
                val response = CashRegisterNetworkTransport.request(
                    method = "POST",
                    url = url,
                    requestBody = payload,
                    connectTimeoutMillis = 5000 + (attempt * 2000),
                    readTimeoutMillis = 8000 + (attempt * 2000),
                )

                if (response.statusCode !in 200..299) {
                    if (response.statusCode in 400..499) {
                        return null
                    }
                    throw IllegalStateException("HTTP ${response.statusCode}: ${response.body}")
                }

                return runCatching {
                    val obj = json.parseToJsonElement(response.body).jsonObject
                    val cn = obj["callNumber"]?.jsonPrimitive
                    cn?.intOrNull ?: cn?.contentOrNull?.trim()?.toIntOrNull()
                }.getOrNull()
            } catch (_: Exception) {
                if (attempt < maxRetries - 1) {
                    val delayMs = (500L * (1 shl attempt)).coerceAtMost(3000L)
                    delay(delayMs)
                }
            }
        }
        return null
    }

    suspend fun getDishes(context: OrderingPlatformContext, maxRetries: Int = 3): List<DishesSyncItem>? {
        val host = CashRegisterConfig.host(context).trim()
        val port = CashRegisterConfig.port(context)
        if (host.isEmpty() || port <= 0) return null

        repeat(maxRetries) { attempt ->
            try {
                val response = CashRegisterNetworkTransport.request(
                    method = "GET",
                    url = "http://$host:$port/dishes",
                    requestBody = null,
                    connectTimeoutMillis = 6000 + (attempt * 2000),
                    readTimeoutMillis = 20000 + (attempt * 5000),
                )

                if (response.statusCode !in 200..299) {
                    if (response.statusCode in 400..499) return null
                    throw IllegalStateException("HTTP ${response.statusCode}")
                }

                return runCatching {
                    json.decodeFromString<List<DishesSyncItem>>(response.body)
                }.getOrNull()
            } catch (_: Exception) {
                if (attempt < maxRetries - 1) {
                    val delayMs = (400L * (1 shl attempt)).coerceAtMost(2000L)
                    delay(delayMs)
                }
            }
        }

        return null
    }

    suspend fun getMenu(context: OrderingPlatformContext, maxRetries: Int = 3): List<MenuSyncItem>? {
        val host = CashRegisterConfig.host(context).trim()
        val port = CashRegisterConfig.port(context)
        if (host.isEmpty() || port <= 0) return null

        repeat(maxRetries) { attempt ->
            try {
                val response = CashRegisterNetworkTransport.request(
                    method = "GET",
                    url = "http://$host:$port/menu",
                    requestBody = null,
                    connectTimeoutMillis = 4000 + (attempt * 1500),
                    readTimeoutMillis = 6000 + (attempt * 1500),
                )

                if (response.statusCode !in 200..299) {
                    if (response.statusCode in 400..499) return null
                    throw IllegalStateException("HTTP ${response.statusCode}")
                }

                return runCatching {
                    json.decodeFromString<List<MenuSyncItem>>(response.body)
                }.getOrNull()
            } catch (_: Exception) {
                if (attempt < maxRetries - 1) {
                    val delayMs = (400L * (1 shl attempt)).coerceAtMost(2000L)
                    delay(delayMs)
                }
            }
        }

        return null
    }
}
