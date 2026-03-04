package com.cofopt.shared.network

import kotlinx.serialization.Serializable

/**
 * Open-source defaults for LAN handshake keys.
 * Replace these values in deployment before using in production.
 */
const val COMPOSEXPOS_LINK_SHARED_KEY = "CHANGE_ME_COMPOSEXPOS_LINK_SHARED_KEY"
const val CALLING_WS_SHARED_KEY = "CHANGE_ME_CALLING_WS_SHARED_KEY"

@Serializable
data class OrderingCashRegisterConfigRequest(
    val host: String,
    val port: Int,
    val sharedKey: String? = null,
)

@Serializable
data class OrderingCashRegisterConfigResponse(
    val status: String,
    val message: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val configured: Boolean? = null,
)
