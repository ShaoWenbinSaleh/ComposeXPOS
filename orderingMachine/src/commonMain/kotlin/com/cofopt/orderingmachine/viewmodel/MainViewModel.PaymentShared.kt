package com.cofopt.orderingmachine.viewmodel

internal fun isWecrSuccessStatus(statusCode: String?, resultCode: String?): Boolean {
    if (isWecrFailureStatus(statusCode, resultCode)) return false
    return statusCode == "00" || resultCode == "0" || resultCode.equals("OK", ignoreCase = true)
}

internal fun isWecrFailureStatus(statusCode: String?, resultCode: String?): Boolean {
    // A missing/temporarily unreadable response is ambiguous, not a declined
    // payment. Only explicit terminal failure codes may unlock another charge.
    return statusCode?.startsWith("9") == true || resultCode == "1"
}
