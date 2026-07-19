package com.cofopt.callingmachine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

private const val NSD_TYPE_CALLING = "_composexpos-calling._tcp."

class CallingNsdAdvertiser(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var desiredPort: Int? = null
    private val retryRegistration = Runnable { registerDesiredService() }

    fun register(port: Int) {
        if (port !in 1..65535) {
            stop()
            return
        }
        if (desiredPort == port && registrationListener != null) return

        stop()
        desiredPort = port
        registerDesiredService()
    }

    private fun registerDesiredService() {
        val port = desiredPort ?: return
        if (registrationListener != null) return

        val info = runCatching {
            NsdServiceInfo().apply {
                serviceType = NSD_TYPE_CALLING
                serviceName = "COMPOSEXPOS-CallingMachine"
                this.port = port
                setAttribute("android_name", (Build.MODEL ?: "Android").take(200))
                getLocalIpv4Address()?.let { setAttribute("ipv4", it) }
            }
        }.getOrElse { error ->
            Log.w("ComposeXPOSNsd", "Unable to build Calling NSD service info", error)
            scheduleRegistrationRetry()
            return
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                mainHandler.removeCallbacks(retryRegistration)
                Log.d("ComposeXPOSNsd", "Calling NSD registered: ${serviceInfo.serviceName}:${serviceInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Calling NSD registration failed: code=$errorCode")
                if (registrationListener === this) {
                    registrationListener = null
                    scheduleRegistrationRetry()
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "Calling NSD unregistered: ${serviceInfo.serviceName}")
                if (desiredPort != null && registrationListener === this) {
                    registrationListener = null
                    scheduleRegistrationRetry()
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Calling NSD unregistration failed: code=$errorCode")
            }
        }

        registrationListener = listener
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            if (registrationListener === listener) registrationListener = null
            Log.w("ComposeXPOSNsd", "Calling NSD register failed: ${it.message}")
            scheduleRegistrationRetry()
        }
    }

    fun stop() {
        desiredPort = null
        mainHandler.removeCallbacks(retryRegistration)
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching {
            nsdManager.unregisterService(listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "Calling NSD stop failed: ${it.message}")
        }
    }

    private fun scheduleRegistrationRetry() {
        if (desiredPort == null) return
        mainHandler.removeCallbacks(retryRegistration)
        mainHandler.postDelayed(retryRegistration, NSD_RETRY_DELAY_MILLIS)
    }

    private companion object {
        const val NSD_RETRY_DELAY_MILLIS = 2_000L
    }
}

private fun getLocalIpv4Address(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        interfaces.toList().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress?.trim().orEmpty() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("169.254") }
    } catch (_: Exception) {
        null
    }
}
