package com.cofopt.shared.mock

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object MockFeatureNotice {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun showPayment(context: Context, extra: String? = null) {
        val suffix = extra?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val message = "Mock payment mode$suffix: no real payment request is sent."
        showToast(context, message)
    }

    fun showPrint(context: Context, extra: String? = null) {
        val suffix = extra?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val message = "Mock print mode$suffix: no real printer job is sent."
        showToast(context, message)
    }

    private fun showToast(context: Context, message: String) {
        val appContext = context.applicationContext
        mainHandler.post {
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
