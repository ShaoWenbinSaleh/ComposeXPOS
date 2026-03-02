package com.cofopt.shared.payment.wecr

/**
 * Open-source defaults for payment mock mode.
 *
 * Real integrations should inject runtime credentials from secure storage and never hardcode
 * private keys or production account identifiers in source control.
 */
object WecrDefaults {
    const val API_URL = "https://example.invalid/payment-api"
    const val LOGIN = "OPEN_SOURCE_LOGIN_PLACEHOLDER"
    const val SID = "OPEN_SOURCE_SID_PLACEHOLDER"
    const val VERSION = "v1"

    const val PRIVATE_KEY_PEM = ""
    const val PRIVATE_KEY_PEM_NEW = ""

    const val DEFAULT_POS_IP = "127.0.0.1"
    const val DEFAULT_POS_PORT = 1234

    const val LEGACY_KEY_INDEX = 0
    const val NEW_CERT_KEY_INDEX = 1

    fun privateKey(useNewCertificate: Boolean): String {
        return if (useNewCertificate) PRIVATE_KEY_PEM_NEW else PRIVATE_KEY_PEM
    }

    fun preferredKeyIndices(useNewCertificate: Boolean): List<Int> {
        return if (useNewCertificate) listOf(NEW_CERT_KEY_INDEX, LEGACY_KEY_INDEX)
        else listOf(LEGACY_KEY_INDEX, NEW_CERT_KEY_INDEX)
    }
}
