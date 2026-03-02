package com.cofopt.cashregister.network

import android.content.Context
import com.cofopt.cashregister.CashRegisterDebugConfig
import com.cofopt.cashregister.calling.CallingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CallingMachineBridge(context: Context) {
    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsClient = CallingMachineWsClient(
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    )

    private var pushJob: Job? = null
    @Volatile
    private var lastSnapshotKey: String? = null

    private val _status = MutableStateFlow(CallingMachineBridgeStatus())
    val status: StateFlow<CallingMachineBridgeStatus> = _status
    private val connectGeneration = AtomicInteger(0)

    fun start() {
        if (pushJob != null) return

        pushJob = scope.launch {
            CallingRepository.preparing
                .combine(CallingRepository.ready) { preparing, ready -> preparing to ready }
                .distinctUntilChanged()
                .debounce(250)
                .collect { (preparing, ready) ->
                    sendSnapshot(preparing, ready, force = false)
                }
        }
    }

    fun stop() {
        pushJob?.cancel()
        pushJob = null
        connectGeneration.incrementAndGet()
        wsClient.disconnect()
        _status.value = _status.value.copy(connected = false)
    }

    fun connect(host: String, port: Int) {
        val normalizedHost = host.trim()
        val generation = connectGeneration.incrementAndGet()
        if (normalizedHost.isBlank() || port <= 0) {
            _status.value = _status.value.copy(
                connected = false,
                targetHost = normalizedHost.ifBlank { null },
                targetPort = if (port > 0) port else null,
                lastError = "invalid_host_or_port"
            )
            return
        }

        _status.value = _status.value.copy(
            targetHost = normalizedHost,
            targetPort = port,
            lastError = null
        )

        wsClient.connect(
            normalizedHost,
            port,
            object : CallingMachineWsClient.Listener {
                override fun onConnected() {
                    if (generation != connectGeneration.get()) return
                    _status.value = _status.value.copy(connected = true, lastError = null)
                    sendSnapshot(CallingRepository.preparing.value, CallingRepository.ready.value, force = true)
                }

                override fun onDisconnected() {
                    if (generation != connectGeneration.get()) return
                    _status.value = _status.value.copy(connected = false)
                }

                override fun onError(message: String) {
                    if (generation != connectGeneration.get()) return
                    _status.value = _status.value.copy(connected = false, lastError = message)
                }
            }
        )
    }

    fun disconnect() {
        connectGeneration.incrementAndGet()
        wsClient.disconnect()
        _status.value = _status.value.copy(connected = false)
    }

    fun pushSnapshot(force: Boolean = true) {
        sendSnapshot(
            preparing = CallingRepository.preparing.value,
            ready = CallingRepository.ready.value,
            force = force
        )
    }

    private fun sendSnapshot(preparing: List<Int>, ready: List<Int>, force: Boolean) {
        val snapshotKey = "${preparing.joinToString(",")}|${ready.joinToString(",")}"
        if (!force && snapshotKey == lastSnapshotKey) return
        if (!wsClient.isConnected()) return

        // Full snapshot; receiver is a dumb display.
        val displayLanguage = CashRegisterDebugConfig.callingMachineDisplayLanguage(appContext).name.lowercase()
        val voiceLanguage = CashRegisterDebugConfig.callingMachineVoiceLanguage(appContext).name.lowercase()
        val obj = JSONObject()
        obj.put("type", "calling_snapshot")
        obj.put("preparing", JSONArray(preparing))
        obj.put("ready", JSONArray(ready))
        obj.put("displayLanguage", displayLanguage)
        obj.put("voiceLanguage", voiceLanguage)
        obj.put("ts", System.currentTimeMillis())
        if (wsClient.send(obj.toString())) {
            lastSnapshotKey = snapshotKey
        }
    }

    fun sendAlert(number: Int): Boolean {
        if (number <= 0) return false
        val obj = JSONObject()
        obj.put("type", "calling_alert")
        obj.put("number", number)
        obj.put("ts", System.currentTimeMillis())
        return wsClient.send(obj.toString())
    }

    companion object {
        @Volatile
        private var instance: CallingMachineBridge? = null

        fun get(context: Context): CallingMachineBridge {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: CallingMachineBridge(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class CallingMachineBridgeStatus(
    val connected: Boolean = false,
    val targetHost: String? = null,
    val targetPort: Int? = null,
    val lastError: String? = null
)
