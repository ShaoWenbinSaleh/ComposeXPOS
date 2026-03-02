package com.cofopt.cashregister.network.wecr

import android.content.Context
import com.cofopt.cashregister.CashRegisterDebugConfig
import com.cofopt.shared.payment.wecr.WecrDefaults

object WecrConfig {
    const val API_URL = WecrDefaults.API_URL
    const val LOGIN = WecrDefaults.LOGIN
    const val SID = WecrDefaults.SID
    const val VERSION = WecrDefaults.VERSION

    fun login(context: Context): String = LOGIN

    fun sid(context: Context): String = SID

    fun useNewCertificate(context: Context): Boolean = false

    fun forceTestAmount(context: Context): Boolean {
        return CashRegisterDebugConfig.wecrForceTestAmount(context)
    }

    fun getPrivateKey(context: Context): String = ""

    fun preferredKeyIndices(context: Context): List<Int> = listOf(WecrDefaults.LEGACY_KEY_INDEX)

    fun posIp(context: Context): String {
        val v = CashRegisterDebugConfig.posIp(context).trim()
        return if (v.isNotBlank()) v else WecrDefaults.DEFAULT_POS_IP
    }

    fun posPort(context: Context): Int {
        val v = CashRegisterDebugConfig.posPort(context)
        return if (v > 0) v else WecrDefaults.DEFAULT_POS_PORT
    }
}
