package com.cofopt.callingmachine

import android.Manifest
import android.os.Bundle
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cofopt.callingmachine.cmp.CallingMachineApp
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    private var wsServer: CallingWebSocketServer? = null
    private val wsPort = 9090
    private var nsdAdvertiser: CallingNsdAdvertiser? = null
    private var cashRegisterAutoConnectJob: Job? = null

    private var serverStartError by mutableStateOf<String?>(null)
    private var connectionCount by mutableStateOf(0)
    private var lastCloseInfo by mutableStateOf<String?>(null)

    private var preparing by mutableStateOf(emptyList<Int>())
    private var ready by mutableStateOf(emptyList<Int>())
    private var preparingLabel by mutableStateOf("备餐中")
    private var readyLabel by mutableStateOf("可取餐")

    private var hasReceivedSnapshot: Boolean = false

    private var alertOverlayNumber by mutableStateOf<Int?>(null)
    private var alertOverlayNonce by mutableStateOf(0)

    private var tts: TextToSpeech? = null
    private var ttsInitialized: Boolean = false

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
    private val lastBeepAtMillis = AtomicLong(0L)

    private fun beepOnce() {
        val now = System.currentTimeMillis()
        val last = lastBeepAtMillis.get()
        if (now - last < 900) return
        lastBeepAtMillis.set(now)
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        } catch (_: Throwable) {
        }
    }

    private val listener = object : CallingState.Listener {
        override fun onSnapshot(preparing: List<Int>, ready: List<Int>, preparingLabel: String, readyLabel: String) {
            runOnUiThread {
                val previousReady = this@MainActivity.ready
                val newReadyNumbers = ready.filter { it !in previousReady }
                this@MainActivity.preparing = preparing
                this@MainActivity.ready = ready
                this@MainActivity.preparingLabel = preparingLabel
                this@MainActivity.readyLabel = readyLabel

                if (!hasReceivedSnapshot) {
                    hasReceivedSnapshot = true
                    return@runOnUiThread
                }

                for (num in newReadyNumbers) {
                    beepOnce()
                    announceReadyNumber(num)
                }
            }
        }

        override fun onAlertNumber(number: Int) {
            runOnUiThread {
                alertOverlayNumber = number
                alertOverlayNonce++
                beepOnce()
                announceReadyNumber(number)
            }
        }

        override fun onConnectionCountChanged(count: Int) {
            runOnUiThread {
                connectionCount = count
                lastCloseInfo = CallingState.lastCloseInfo
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPostNotificationsIfNeeded()
        
        // Log if launched from boot
        val isFromBoot = intent?.getBooleanExtra("FROM_BOOT", false) == true
        if (isFromBoot) {
            Log.d("MainActivity", "Launched from boot receiver")
        }

        tts = TextToSpeech(this) { status ->
            ttsInitialized = status == TextToSpeech.SUCCESS
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val msg = throwable.message ?: throwable.javaClass.simpleName
            val top = throwable.stackTrace?.firstOrNull()?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
            CallingState.updateLastCloseInfo(
                if (top.isNullOrBlank()) {
                    "uncaught=${throwable.javaClass.simpleName}: $msg"
                } else {
                    "uncaught=${throwable.javaClass.simpleName}: $msg @ $top"
                }
            )
            previousHandler?.uncaughtException(thread, throwable)
        }

        setContent {
            val status = callingStatusText(serverStartError, connectionCount, lastCloseInfo, wsPort)
            val isConnected = serverStartError == null && connectionCount > 0
            CallingMachineApp(
                preparing = preparing,
                ready = ready,
                preparingLabel = preparingLabel,
                readyLabel = readyLabel,
                statusText = status,
                isConnected = isConnected,
                alertOverlayNumber = alertOverlayNumber,
                alertOverlayNonce = alertOverlayNonce,
                isPreparingNumber = { num -> CallingState.isNewPreparingNumber(num) }
            )
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) return

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            100
        )
    }

    override fun onDestroy() {
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
        try {
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        super.onDestroy()
    }

    private fun announceReadyNumber(number: Int) {
        val announcement = readyAnnouncement(number, CallingState.voiceLanguage())
        val locale = Locale.forLanguageTag(announcement.localeTag)
        speak(locale, announcement.text, announcement.rate)
    }

    private fun speak(locale: Locale, text: String, speechRate: Float = 1.0f) {
        val engine = tts ?: return
        if (!ttsInitialized) return
        try {
            engine.language = locale
            engine.setSpeechRate(speechRate)
        } catch (_: Throwable) {
        }
        try {
            val utteranceId = "calling_${System.currentTimeMillis()}_${text.hashCode()}"
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        } catch (_: Throwable) {
        }
    }

    override fun onStart() {
        super.onStart()

        if (wsServer == null) {
            wsServer = CallingWebSocketServer(wsPort) { host ->
                CashRegisterPeerConfig.saveLastCashRegisterIp(this, host)
                Log.d("MainActivity", "Connection Event: CASHREGISTER_IP_SAVED | host=$host")
            }
            try {
                wsServer?.start(5000, false)
                if (nsdAdvertiser == null) {
                    nsdAdvertiser = CallingNsdAdvertiser(this)
                }
                nsdAdvertiser?.register(wsPort)
                serverStartError = null
            } catch (e: Exception) {
                serverStartError = e.message ?: e.javaClass.simpleName
                wsServer = null
            }
        }

        cashRegisterAutoConnectJob?.cancel()
        cashRegisterAutoConnectJob = lifecycleScope.launch {
            attemptAutoConnectCashRegister()
        }

        CallingState.addListener(listener)
    }

    override fun onStop() {
        cashRegisterAutoConnectJob?.cancel()
        cashRegisterAutoConnectJob = null
        nsdAdvertiser?.stop()
        nsdAdvertiser = null
        CallingState.removeListener(listener)
        wsServer?.stop()
        wsServer = null
        super.onStop()
    }

    private suspend fun attemptAutoConnectCashRegister() {
        val host = CashRegisterPeerConfig.lastCashRegisterIp(this).trim()
        val port = CashRegisterPeerConfig.cashRegisterPort(this)
        if (host.isBlank() || port <= 0) return

        repeat(3) { index ->
            val ok = probeCashRegisterHealth(host, port)
            if (ok) {
                Log.d("MainActivity", "Connection Event: CASHREGISTER_AUTO_CONNECT_OK | host=$host:$port")
                return
            }
            delay((1200L * (index + 1)).coerceAtMost(3500L))
        }

        Log.w("MainActivity", "Connection Event: CASHREGISTER_AUTO_CONNECT_FAILED | host=$host:$port | error=health_check_failed")
    }

    private suspend fun probeCashRegisterHealth(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        val url = URL("http://$host:$port/health")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1500
            readTimeout = 1500
            instanceFollowRedirects = false
        }

        runCatching {
            conn.connect()
            conn.responseCode in 200..299
        }.getOrDefault(false).also {
            conn.disconnect()
        }
    }

}
