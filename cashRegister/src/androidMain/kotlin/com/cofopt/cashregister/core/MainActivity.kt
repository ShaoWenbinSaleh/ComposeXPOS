package com.cofopt.cashregister

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cofopt.cashregister.network.LanOrderServer
import com.cofopt.cashregister.network.CallingMachineBridge
import com.cofopt.cashregister.network.ComposeXPOSNsdAdvertiser
import com.cofopt.cashregister.network.OrdersRepository
import com.cofopt.cashregister.calling.CallingRepository
import com.cofopt.cashregister.menu.DishesRepository
import com.cofopt.cashregister.cmp.platform.CallingPlatform
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var server: LanOrderServer? = null
    private var callingBridge: CallingMachineBridge? = null
    private var nsdAdvertiser: ComposeXPOSNsdAdvertiser? = null
    private val serverPort = 8080

    private var serverStartJob: Job? = null
    private var serverLifecycleGeneration: Int = 0
    private var activityStarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Log if launched from boot
        val isFromBoot = intent?.getBooleanExtra("FROM_BOOT", false) == true
        if (isFromBoot) {
            Log.d("MainActivity", "Launched from boot receiver")
        }

        setContent {
            CashRegisterApp()
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        startLanServerWithRetry()

        // The bridge is stopped in onStop. Always rebind and restart the
        // process singleton here; keeping a non-null Activity field previously
        // made every second onStart skip reconnection permanently.
        val bridge = CallingMachineBridge.get(this)
        callingBridge = bridge
        CallingPlatform.bindBridge(bridge)
        bridge.start()

        val callingIp = CashRegisterDebugConfig.callingMachineIp(this).trim()
        val callingPort = CashRegisterDebugConfig.callingMachinePort(this)
        if (callingIp.isNotBlank() && callingPort in 1..65535) {
            bridge.connect(callingIp, callingPort)
        }
    }

    override fun onStop() {
        activityStarted = false
        serverLifecycleGeneration++
        val bridgeSnapshot = callingBridge?.status?.value
        val lastHost = bridgeSnapshot?.targetHost?.trim().orEmpty()
        val lastPort = bridgeSnapshot?.targetPort ?: 0
        if (lastHost.isNotBlank() && lastPort > 0) {
            CashRegisterDebugConfig.saveCallingMachine(this, lastHost, lastPort)
        }
        callingBridge?.stop()
        callingBridge = null
        CallingPlatform.bindBridge(null)
        nsdAdvertiser?.stop()
        nsdAdvertiser = null
        serverStartJob?.cancel()
        server?.stop()
        server = null
        super.onStop()
    }

    private fun startLanServerWithRetry() {
        if (server != null) return
        if (serverStartJob != null) return

        val generation = serverLifecycleGeneration
        serverStartJob = lifecycleScope.launch {
            var attempt = 0
            try {
                while (server == null) {
                    attempt = (attempt + 1).coerceAtMost(1000)
                    var candidate: LanOrderServer? = null
                    var adopted = false
                    try {
                    // Do not expose /menu or /orders until persisted state is available. Starting
                    // NanoHTTPD first allowed an early request to be answered from empty state and
                    // then overwritten when DataStore/Room finished loading.
                    DishesRepository.ensureLoaded(this@MainActivity)
                    OrdersRepository.ensureLoaded(this@MainActivity)
                    CallingRepository.ensureLoaded(this@MainActivity)
                    // Archived orders may still be operationally active (for example, a checkout
                    // opened before midnight and paid after midnight), so reconciliation must see
                    // the complete in-memory order set.
                    CallingRepository.reconcileWithOrders(OrdersRepository.orders.value)
                    check(OrdersRepository.persistNow()) {
                        "Unable to persist the restored order snapshot"
                    }
                    check(CallingRepository.persistNow()) {
                        "Unable to persist the restored calling snapshot"
                    }

                    val s = LanOrderServer(serverPort)
                    candidate = s
                    withContext(Dispatchers.IO) {
                        s.start(5000, false)
                    }
                    currentCoroutineContext().ensureActive()
                    server = s
                    adopted = true
                    if (nsdAdvertiser == null) {
                        nsdAdvertiser = ComposeXPOSNsdAdvertiser(this@MainActivity)
                    }
                    nsdAdvertiser?.registerAsCashRegister(serverPort)
                    Log.d("MainActivity", "LanOrderServer started on port=$serverPort")
                    break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        Log.e("MainActivity", "LanOrderServer start failed attempt=$attempt port=$serverPort", e)
                        delay((500L * attempt).coerceAtMost(5000L))
                    } finally {
                        if (!adopted) {
                            candidate?.let { failedCandidate ->
                                withContext(NonCancellable + Dispatchers.IO) {
                                    runCatching { failedCandidate.stop() }
                                }
                            }
                        }
                    }
                }
            } finally {
                serverStartJob = null
                if (
                    activityStarted &&
                    generation != serverLifecycleGeneration &&
                    server == null
                ) {
                    startLanServerWithRetry()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
