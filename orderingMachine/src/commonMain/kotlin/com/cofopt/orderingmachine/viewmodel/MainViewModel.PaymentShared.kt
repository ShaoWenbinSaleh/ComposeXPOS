package com.cofopt.orderingmachine.viewmodel

internal fun isWecrSuccessStatus(statusCode: String?, resultCode: String?): Boolean {
    return statusCode == "00" || resultCode == "0" || resultCode.equals("OK", ignoreCase = true)
}

internal fun isWecrFailureStatus(statusCode: String?, resultCode: String?): Boolean {
    return statusCode == null || statusCode.startsWith("9") || resultCode == "1"
}
