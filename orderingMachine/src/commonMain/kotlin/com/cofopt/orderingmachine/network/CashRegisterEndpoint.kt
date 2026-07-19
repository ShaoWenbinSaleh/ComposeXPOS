package com.cofopt.orderingmachine.network

private data class ParsedCashRegisterEndpoint(
    val scheme: String,
    val host: String,
    val hadExplicitScheme: Boolean,
)

internal fun normalizeCashRegisterHost(rawHost: String, port: Int): String? {
    val endpoint = parseCashRegisterEndpoint(rawHost, port) ?: return null
    if (!endpoint.hadExplicitScheme) return endpoint.host.toStorageAuthorityHost()

    val authorityHost = endpoint.host.toStorageAuthorityHost()
    return "${endpoint.scheme}://$authorityHost"
}

private fun parseCashRegisterEndpoint(rawHost: String, port: Int): ParsedCashRegisterEndpoint? {
    if (port !in 1..65535) return null

    var candidate = rawHost.trim()
    val scheme: String
    val hadExplicitScheme: Boolean
    when {
        candidate.startsWith("http://", ignoreCase = true) -> {
            scheme = "http"
            hadExplicitScheme = true
            candidate = candidate.substring(7)
        }

        candidate.startsWith("https://", ignoreCase = true) -> {
            scheme = "https"
            hadExplicitScheme = true
            candidate = candidate.substring(8)
        }

        candidate.contains("://") -> return null
        else -> {
            scheme = "http"
            hadExplicitScheme = false
        }
    }

    candidate = candidate.trim().trimEnd('/')
    if (candidate.isEmpty()) return null
    if (candidate.any { it.isWhitespace() || it.isISOControl() }) return null
    if (candidate.any { it == '/' || it == '?' || it == '#' || it == '@' }) return null

    val normalized = if (candidate.startsWith('[')) {
        val closingBracket = candidate.indexOf(']')
        if (closingBracket <= 1) return null

        val host = candidate.substring(1, closingBracket)
        val suffix = candidate.substring(closingBracket + 1)
        if (suffix.isNotEmpty()) {
            val embeddedPort = suffix.removePrefix(":").toIntOrNull() ?: return null
            if (!suffix.startsWith(':') || embeddedPort != port) return null
        }
        host
    } else {
        if (candidate.contains('[') || candidate.contains(']')) return null
        val firstColon = candidate.indexOf(':')
        val lastColon = candidate.lastIndexOf(':')
        if (firstColon >= 0 && firstColon == lastColon) {
            val embeddedPort = candidate.substring(firstColon + 1).toIntOrNull() ?: return null
            if (embeddedPort != port) return null
            candidate.substring(0, firstColon)
        } else {
            candidate
        }
    }.trim()

    if (normalized.isEmpty()) return null
    return ParsedCashRegisterEndpoint(
        scheme = scheme,
        host = normalized.replace("%25", "%", ignoreCase = true),
        hadExplicitScheme = hadExplicitScheme,
    )
}

internal fun cashRegisterUrl(rawHost: String, port: Int, path: String): String? {
    if (!path.startsWith('/') || path.startsWith("//")) return null
    val endpoint = parseCashRegisterEndpoint(rawHost, port) ?: return null
    return "${endpoint.scheme}://${endpoint.host.toUrlAuthorityHost()}:$port$path"
}

private fun String.toUrlAuthorityHost(): String {
    if (!contains(':')) return this
    // RFC 3986 requires brackets around an IPv6 literal in a URL. A zone
    // identifier uses an escaped percent sign inside the authority.
    return "[${replace("%", "%25")}]"
}

private fun String.toStorageAuthorityHost(): String {
    return if (contains(':')) "[$this]" else this
}

internal fun isCashRegisterHealthResponse(response: CashRegisterHttpResponse): Boolean {
    return response.statusCode in 200..299 && response.body.trim().equals("ok", ignoreCase = true)
}
