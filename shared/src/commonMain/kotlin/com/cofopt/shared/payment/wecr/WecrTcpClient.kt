package com.cofopt.shared.payment.wecr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Open-source mock POS trigger client.
 * Replace with real TCP trigger logic when integrating a production terminal.
 */
class WecrTcpClient(
    private val posIpAddress: String,
    private val posPort: Int = WecrDefaults.DEFAULT_POS_PORT,
    private val logger: ((String) -> Unit)? = null,
) {
    private var connected: Boolean = false

    private fun log(message: String) {
        logger?.invoke(message)
    }

    suspend fun connect(timeoutMillis: Int = 5000): Boolean = withContext(Dispatchers.Default) {
        delay(80)
        connected = true
        log("MOCK TCP connect to $posIpAddress:$posPort timeout=${timeoutMillis}ms")
        true
    }

    fun close() {
        connected = false
        log("MOCK TCP close")
    }

    suspend fun sendTrigger(): Boolean = withContext(Dispatchers.Default) {
        delay(80)
        if (!connected) {
            log("MOCK TCP trigger failed: not connected")
            return@withContext false
        }
        log("MOCK TCP trigger sent")
        true
    }
}
