package com.cofopt.orderingmachine.network

import com.cofopt.shared.payment.wecr.WecrDefaults

object WecrConfig {
    const val API_URL = WecrDefaults.API_URL
    const val LOGIN = WecrDefaults.LOGIN
    const val SID = WecrDefaults.SID
    const val VERSION = WecrDefaults.VERSION

    const val POS_IP = WecrDefaults.DEFAULT_POS_IP
    const val POS_PORT = WecrDefaults.DEFAULT_POS_PORT

    private const val PREFS_NAME = "wecr_config"
    private const val KEY_POS_IP = "pos_ip"
    private const val KEY_POS_PORT = "pos_port"
    private const val KEY_ENABLE_CARD_PAYMENT = "enable_card_payment"
    private const val KEY_AUTO_CARD_SUCCESS = "auto_card_success"
    private const val KEY_FORCE_TEST_AMOUNT = "force_test_amount"
    private const val KEY_DEBUG_CARD_SM_ENABLED = "debug_card_sm_enabled"
    private const val KEY_DEBUG_POS_REQUEST_SUCCESS = "debug_pos_request_success"
    private const val KEY_DEBUG_POS_TRIGGER_SUCCESS = "debug_pos_trigger_success"

    fun login(context: OrderingPlatformContext): String = LOGIN

    fun sid(context: OrderingPlatformContext): String = SID

    fun posIp(context: OrderingPlatformContext): String {
        return OrderingPlatformPrefs.getString(context, PREFS_NAME, KEY_POS_IP, POS_IP)
    }

    fun posPort(context: OrderingPlatformContext): Int {
        return OrderingPlatformPrefs.getInt(context, PREFS_NAME, KEY_POS_PORT, POS_PORT)
    }

    fun enableCardPayment(context: OrderingPlatformContext): Boolean {
        return OrderingPlatformPrefs.getBoolean(context, PREFS_NAME, KEY_ENABLE_CARD_PAYMENT, true)
    }

    fun autoCardSuccess(context: OrderingPlatformContext): Boolean {
        return OrderingPlatformPrefs.getBoolean(context, PREFS_NAME, KEY_AUTO_CARD_SUCCESS, false)
    }

    fun forceTestAmount(context: OrderingPlatformContext): Boolean {
        return OrderingPlatformPrefs.getBoolean(context, PREFS_NAME, KEY_FORCE_TEST_AMOUNT, false)
    }

    fun debugCardSmEnabled(context: OrderingPlatformContext): Boolean {
        return OrderingPlatformPrefs.getBoolean(context, PREFS_NAME, KEY_DEBUG_CARD_SM_ENABLED, false)
    }

    fun debugPosRequestSuccess(context: OrderingPlatformContext): Boolean {
        return OrderingPlatformPrefs.getBoolean(context, PREFS_NAME, KEY_DEBUG_POS_REQUEST_SUCCESS, true)
    }

    fun debugPosTriggerSuccess(context: OrderingPlatformContext): Boolean {
        return OrderingPlatformPrefs.getBoolean(context, PREFS_NAME, KEY_DEBUG_POS_TRIGGER_SUCCESS, true)
    }

    fun useNewCertificate(context: OrderingPlatformContext): Boolean = false

    fun setEnableCardPayment(context: OrderingPlatformContext, enabled: Boolean) {
        OrderingPlatformPrefs.putBoolean(context, PREFS_NAME, KEY_ENABLE_CARD_PAYMENT, enabled)
    }

    fun setAutoCardSuccess(context: OrderingPlatformContext, enabled: Boolean) {
        OrderingPlatformPrefs.putBoolean(context, PREFS_NAME, KEY_AUTO_CARD_SUCCESS, enabled)
    }

    fun setForceTestAmount(context: OrderingPlatformContext, enabled: Boolean) {
        OrderingPlatformPrefs.putBoolean(context, PREFS_NAME, KEY_FORCE_TEST_AMOUNT, enabled)
    }

    fun setDebugCardSmEnabled(context: OrderingPlatformContext, enabled: Boolean) {
        OrderingPlatformPrefs.putBoolean(context, PREFS_NAME, KEY_DEBUG_CARD_SM_ENABLED, enabled)
    }

    fun setDebugPosRequestSuccess(context: OrderingPlatformContext, enabled: Boolean) {
        OrderingPlatformPrefs.putBoolean(context, PREFS_NAME, KEY_DEBUG_POS_REQUEST_SUCCESS, enabled)
    }

    fun setDebugPosTriggerSuccess(context: OrderingPlatformContext, enabled: Boolean) {
        OrderingPlatformPrefs.putBoolean(context, PREFS_NAME, KEY_DEBUG_POS_TRIGGER_SUCCESS, enabled)
    }

    fun setUseNewCertificate(context: OrderingPlatformContext, enabled: Boolean) {
        // No-op in open-source mock mode.
    }

    fun getPrivateKey(context: OrderingPlatformContext): String = ""

    fun preferredKeyIndices(context: OrderingPlatformContext): List<Int> {
        return listOf(WecrDefaults.LEGACY_KEY_INDEX)
    }

    fun savePaymentSettings(
        context: OrderingPlatformContext,
        login: String,
        sid: String,
        posIp: String,
        posPort: Int,
    ) {
        OrderingPlatformPrefs.putString(context, PREFS_NAME, KEY_POS_IP, posIp)
        OrderingPlatformPrefs.putInt(context, PREFS_NAME, KEY_POS_PORT, posPort)
    }
}
