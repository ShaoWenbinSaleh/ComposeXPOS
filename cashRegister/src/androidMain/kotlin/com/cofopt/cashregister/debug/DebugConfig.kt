package com.cofopt.cashregister

import android.content.Context
import com.cofopt.cashregister.utils.AlertSoundOption

object CashRegisterDebugConfig {
    private const val PREF = "cashregister_debug"
    private const val KEY_POS_IP = "pos_ip"
    private const val KEY_POS_PORT = "pos_port"
    private const val KEY_WECR_LOGIN = "wecr_login"
    private const val KEY_WECR_SID = "wecr_sid"
    private const val KEY_WECR_USE_NEW_CERTIFICATE = "wecr_use_new_certificate"
    private const val KEY_WECR_FORCE_TEST_AMOUNT = "wecr_force_test_amount"
    private const val KEY_CALLING_MACHINE_IP = "calling_machine_ip"
    private const val KEY_CALLING_MACHINE_PORT = "calling_machine_port"
    private const val KEY_CALLING_MACHINE_DISPLAY_LANGUAGE = "calling_machine_display_language"
    private const val KEY_CALLING_MACHINE_VOICE_LANGUAGE = "calling_machine_voice_language"
    private const val KEY_ORDERING_MACHINE_IP = "ordering_machine_ip"
    private const val KEY_ORDERING_MACHINE_PORT = "ordering_machine_port"
    private const val KEY_ORDERING_MACHINE_CONNECTED_KEYS = "ordering_machine_connected_keys"
    private const val KEY_ORDERING_MACHINE_UUID_ENDPOINTS = "ordering_machine_uuid_endpoints"
    private const val KEY_CALLING_NUMBER_MIN = "calling_number_min"
    private const val KEY_CALLING_NUMBER_MAX = "calling_number_max"
    private const val KEY_ALERT_SOUND_ID = "alert_sound_id"

    fun posIp(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_POS_IP, "") ?: ""
    }

    fun posPort(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_POS_PORT, 1234)
    }

    fun savePos(context: Context, ip: String, port: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_POS_IP, ip)
            .putInt(KEY_POS_PORT, port)
            .apply()
    }

    fun wecrLogin(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_WECR_LOGIN, "") ?: ""
    }

    fun saveWecrLogin(context: Context, login: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WECR_LOGIN, login)
            .apply()
    }

    fun wecrSid(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_WECR_SID, "") ?: ""
    }

    fun saveWecrSid(context: Context, sid: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WECR_SID, sid)
            .apply()
    }

    fun wecrUseNewCertificate(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_WECR_USE_NEW_CERTIFICATE, false)
    }

    fun saveWecrUseNewCertificate(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WECR_USE_NEW_CERTIFICATE, enabled)
            .apply()
    }

    fun wecrForceTestAmount(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_WECR_FORCE_TEST_AMOUNT, false)
    }

    fun saveWecrForceTestAmount(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WECR_FORCE_TEST_AMOUNT, enabled)
            .apply()
    }

    fun callingMachineIp(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_CALLING_MACHINE_IP, "") ?: ""
    }

    fun callingMachinePort(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_CALLING_MACHINE_PORT, 9090)
    }

    fun saveCallingMachine(context: Context, ip: String, port: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CALLING_MACHINE_IP, ip)
            .putInt(KEY_CALLING_MACHINE_PORT, port)
            .apply()
    }

    fun callingMachineDisplayLanguage(context: Context): com.cofopt.cashregister.utils.Language {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(
                KEY_CALLING_MACHINE_DISPLAY_LANGUAGE,
                com.cofopt.cashregister.utils.Language.ZH.name
            )
        return runCatching {
            com.cofopt.cashregister.utils.Language.valueOf(raw ?: com.cofopt.cashregister.utils.Language.ZH.name)
        }.getOrDefault(com.cofopt.cashregister.utils.Language.ZH)
    }

    fun callingMachineVoiceLanguage(context: Context): com.cofopt.cashregister.utils.Language {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(
                KEY_CALLING_MACHINE_VOICE_LANGUAGE,
                com.cofopt.cashregister.utils.Language.ZH.name
            )
        return runCatching {
            com.cofopt.cashregister.utils.Language.valueOf(raw ?: com.cofopt.cashregister.utils.Language.ZH.name)
        }.getOrDefault(com.cofopt.cashregister.utils.Language.ZH)
    }

    fun saveCallingMachineLanguages(
        context: Context,
        displayLanguage: com.cofopt.cashregister.utils.Language,
        voiceLanguage: com.cofopt.cashregister.utils.Language
    ) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CALLING_MACHINE_DISPLAY_LANGUAGE, displayLanguage.name)
            .putString(KEY_CALLING_MACHINE_VOICE_LANGUAGE, voiceLanguage.name)
            .apply()
    }

    fun orderingMachineIp(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ORDERING_MACHINE_IP, "") ?: ""
    }

    fun orderingMachinePort(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_ORDERING_MACHINE_PORT, 19081)
    }

    fun saveOrderingMachine(context: Context, ip: String, port: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDERING_MACHINE_IP, ip)
            .putInt(KEY_ORDERING_MACHINE_PORT, port)
            .apply()
    }

    fun orderingMachineConnectedKeys(context: Context): Set<String> {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_ORDERING_MACHINE_CONNECTED_KEYS, emptySet())
            .orEmpty()
        return saved.toSet()
    }

    fun saveOrderingMachineConnectedKeys(context: Context, keys: Set<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ORDERING_MACHINE_CONNECTED_KEYS, keys.toSet())
            .apply()
    }

    fun orderingMachineKnownUuid(context: Context, host: String, port: Int): String? {
        if (host.isBlank() || port <= 0) return null
        val key = "${host.trim()}:$port"
        val entries = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_ORDERING_MACHINE_UUID_ENDPOINTS, emptySet())
            .orEmpty()
        return entries.firstNotNullOfOrNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@firstNotNullOfOrNull null
            val endpoint = line.substring(0, idx).trim()
            val uuid = sanitizeUuid(line.substring(idx + 1)).trim().lowercase()
            if (endpoint == key && uuid.isNotBlank()) uuid else null
        }
    }

    fun orderingMachineKnownEndpointsByUuid(context: Context, uuid: String): List<Pair<String, Int>> {
        val normalizedUuid = sanitizeUuid(uuid).trim().lowercase()
        if (normalizedUuid.isBlank()) return emptyList()
        val entries = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_ORDERING_MACHINE_UUID_ENDPOINTS, emptySet())
            .orEmpty()
        return entries.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val endpoint = line.substring(0, idx).trim()
            val savedUuid = sanitizeUuid(line.substring(idx + 1)).trim().lowercase()
            if (savedUuid != normalizedUuid) return@mapNotNull null
            val host = endpoint.substringBeforeLast(':').trim()
            val port = endpoint.substringAfterLast(':').trim().toIntOrNull() ?: return@mapNotNull null
            if (host.isBlank() || port <= 0) null else host to port
        }.distinct()
    }

    fun saveOrderingMachineKnownUuid(context: Context, host: String, port: Int, uuid: String) {
        val cleanHost = host.trim()
        val cleanUuid = sanitizeUuid(uuid).trim().lowercase()
        if (cleanHost.isBlank() || port <= 0 || cleanUuid.isBlank()) return
        val endpoint = "$cleanHost:$port"
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_ORDERING_MACHINE_UUID_ENDPOINTS, emptySet()).orEmpty()
        val updated = current
            .filterNot { it.substringBefore('=').trim() == endpoint }
            .toMutableSet()
            .apply { add("$endpoint=$cleanUuid") }
            .toSet()
        prefs.edit().putStringSet(KEY_ORDERING_MACHINE_UUID_ENDPOINTS, updated).apply()
    }

    fun callingNumberMin(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_CALLING_NUMBER_MIN, 1)
    }

    fun callingNumberMax(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_CALLING_NUMBER_MAX, 99)
    }

    fun saveCallingNumberRange(context: Context, min: Int, max: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CALLING_NUMBER_MIN, min)
            .putInt(KEY_CALLING_NUMBER_MAX, max)
            .apply()
    }

    fun alertSoundId(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ALERT_SOUND_ID, AlertSoundOption.BEEP.id) ?: AlertSoundOption.BEEP.id
    }

    fun saveAlertSoundId(context: Context, id: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALERT_SOUND_ID, id)
            .apply()
    }
}
