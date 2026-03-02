package com.cofopt.cashregister

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cofopt.cashregister.network.LanOrderServer
import com.cofopt.cashregister.network.CallingMachineBridge
import com.cofopt.cashregister.network.ComposeXPOSNsdAdvertiser
import com.cofopt.cashregister.cmp.platform.CallingPlatform
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var server: LanOrderServer? = null
    private var callingBridge: CallingMachineBridge? = null
    private var nsdAdvertiser: ComposeXPOSNsdAdvertiser? = null
    private val serverPort = 8080

    private var serverStartJob: Job? = null

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
        startLanServerWithRetry()

        if (callingBridge == null) {
            val bridge = CallingMachineBridge.get(this)
            callingBridge = bridge
            CallingPlatform.bindBridge(bridge)
            bridge.start()

            val callingIp = CashRegisterDebugConfig.callingMachineIp(this).trim()
            val callingPort = CashRegisterDebugConfig.callingMachinePort(this)
            if (callingIp.isNotBlank() && callingPort > 0) {
                bridge.connect(callingIp, callingPort)
            }
        }
    }

    override fun onStop() {
        val bridgeSnapshot = callingBridge?.status?.value
        val lastHost = bridgeSnapshot?.targetHost?.trim().orEmpty()
        val lastPort = bridgeSnapshot?.targetPort ?: 0
        if (lastHost.isNotBlank() && lastPort > 0) {
            CashRegisterDebugConfig.saveCallingMachine(this, lastHost, lastPort)
        }
        callingBridge?.stop()
        CallingPlatform.bindBridge(null)
        nsdAdvertiser?.stop()
        nsdAdvertiser = null
        serverStartJob?.cancel()
        serverStartJob = null
        server?.stop()
        server = null
        super.onStop()
    }

    private fun startLanServerWithRetry() {
        if (server != null) return
        if (serverStartJob != null) return

        serverStartJob = lifecycleScope.launch {
            var attempt = 0
            while (server == null && attempt < 10) {
                attempt += 1
                try {
                    val s = LanOrderServer(serverPort)
                    s.start(5000, false)
                    server = s
                    if (nsdAdvertiser == null) {
                        nsdAdvertiser = ComposeXPOSNsdAdvertiser(this@MainActivity)
                    }
                    nsdAdvertiser?.registerAsCashRegister(serverPort)
                    Log.d("MainActivity", "LanOrderServer started on port=$serverPort")
                    break
                } catch (e: Exception) {
                    Log.e("MainActivity", "LanOrderServer start failed attempt=$attempt port=$serverPort", e)
                    delay((500L * attempt).coerceAtMost(5000L))
                }
            }

            if (server == null) {
                Log.e("MainActivity", "LanOrderServer failed to start after retries port=$serverPort")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
