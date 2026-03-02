package com.cofopt.callingmachine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

private const val NSD_TYPE_CALLING = "_composexpos-calling._tcp."

class CallingNsdAdvertiser(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register(port: Int) {
        stop()
        if (port <= 0) return

        val info = NsdServiceInfo().apply {
            serviceType = NSD_TYPE_CALLING
            serviceName = "COMPOSEXPOS-CallingMachine"
            this.port = port
            setAttribute("android_name", Build.MODEL ?: "Android")
            getLocalIpv4Address()?.let { setAttribute("ipv4", it) }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "Calling NSD registered: ${serviceInfo.serviceName}:${serviceInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Calling NSD registration failed: code=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "Calling NSD unregistered: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Calling NSD unregistration failed: code=$errorCode")
            }
        }

        registrationListener = listener
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "Calling NSD register failed: ${it.message}")
        }
    }

    fun stop() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching {
            nsdManager.unregisterService(listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "Calling NSD stop failed: ${it.message}")
        }
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
