package com.cofopt.cashregister.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val COMPOSEXPOS_NSD_TYPE_CASHREGISTER = "_composexpos-cashregister._tcp."
const val COMPOSEXPOS_NSD_TYPE_ORDERING = "_composexpos-ordering._tcp."
const val COMPOSEXPOS_NSD_TYPE_CALLING = "_composexpos-calling._tcp."

data class ComposeXPOSDiscoveredService(
    val role: String,
    val serviceName: String,
    val host: String,
    val port: Int,
    val supportsCashRegisterConfigPush: Boolean = true,
    val androidDeviceName: String? = null,
    val deviceUuid: String? = null
)

class ComposeXPOSNsdAdvertiser(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun registerAsCashRegister(port: Int) {
        stop()
        if (port <= 0) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = COMPOSEXPOS_NSD_TYPE_CASHREGISTER
            serviceName = "COMPOSEXPOS-CashRegister"
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "CashRegister NSD registered: ${serviceInfo.serviceName}:${serviceInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "CashRegister NSD registration failed: code=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "CashRegister NSD unregistered: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "CashRegister NSD unregistration failed: code=$errorCode")
            }
        }

        registrationListener = listener
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "registerAsCashRegister failed: ${it.message}")
        }
    }

    fun stop() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching {
            nsdManager.unregisterService(listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "stop advertiser failed: ${it.message}")
        }
    }
}

class ComposeXPOSNsdBrowser(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveryListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val resolved = ConcurrentHashMap<String, ComposeXPOSDiscoveredService>()
    private val resolveQueue = ArrayDeque<ResolveRequest>()
    private val queuedResolveKeys = LinkedHashSet<String>()
    private var resolveInFlight = false
    private val _services = MutableStateFlow<List<ComposeXPOSDiscoveredService>>(emptyList())
    val services: StateFlow<List<ComposeXPOSDiscoveredService>> = _services

    fun start() {
        stop(clearResults = true)
        startDiscoveryForType(COMPOSEXPOS_NSD_TYPE_ORDERING, "OrderingMachine")
        startDiscoveryForType(COMPOSEXPOS_NSD_TYPE_CALLING, "CallingMachine")
    }

    fun stop(clearResults: Boolean = false) {
        discoveryListeners.values.forEach { listener ->
            runCatching {
                nsdManager.stopServiceDiscovery(listener)
            }.onFailure {
                Log.w("ComposeXPOSNsd", "stop discovery failed: ${it.message}")
            }
        }
        discoveryListeners.clear()
        resolveQueue.clear()
        queuedResolveKeys.clear()
        resolveInFlight = false
        if (clearResults) {
            resolved.clear()
            emitResolved()
        }
    }

    private fun startDiscoveryForType(type: String, role: String) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "onStartDiscoveryFailed type=$serviceType code=$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "onStopDiscoveryFailed type=$serviceType code=$errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("ComposeXPOSNsd", "Discovery started type=$serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("ComposeXPOSNsd", "Discovery stopped type=$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != type) return
                enqueueResolve(role, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val key = keyOf(type, serviceInfo.serviceName)
                resolved.remove(key)
                removeQueuedResolve(key)
                emitResolved()
            }
        }

        discoveryListeners[type] = listener
        runCatching {
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "discoverServices failed type=$type err=${it.message}")
        }
    }

    private fun enqueueResolve(role: String, serviceInfo: NsdServiceInfo, retryCount: Int = 0) {
        val key = keyOf(serviceInfo.serviceType, serviceInfo.serviceName)
        if (!queuedResolveKeys.add(key)) return
        resolveQueue.addLast(ResolveRequest(role, serviceInfo, retryCount))
        processNextResolve()
    }

    private fun processNextResolve() {
        if (resolveInFlight) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolveInFlight = true
        resolve(next.role, next.serviceInfo, next.retryCount)
    }

    private fun removeQueuedResolve(key: String) {
        if (!queuedResolveKeys.contains(key)) return
        queuedResolveKeys.remove(key)
        val keep = resolveQueue.filterNot { req ->
            keyOf(req.serviceInfo.serviceType, req.serviceInfo.serviceName) == key
        }
        resolveQueue.clear()
        resolveQueue.addAll(keep)
    }

    private fun resolve(role: String, serviceInfo: NsdServiceInfo, retryCount: Int) {
        val key = keyOf(serviceInfo.serviceType, serviceInfo.serviceName)
        runCatching {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w("ComposeXPOSNsd", "resolve failed name=${serviceInfo.serviceName} code=$errorCode")
                        queuedResolveKeys.remove(key)
                        resolveInFlight = false
                        if (retryCount < 2) {
                            enqueueResolve(role, serviceInfo, retryCount + 1)
                        } else {
                            processNextResolve()
                        }
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        val host = resolveBestHost(resolvedInfo)
                        val port = resolvedInfo.port
                        queuedResolveKeys.remove(key)
                        resolveInFlight = false
                        processNextResolve()
                        if (host.isBlank() || port <= 0) return
                        val attrs = runCatching { resolvedInfo.attributes }.getOrNull()
                        val uuid = attrs?.get("uuid")?.toString(Charsets.UTF_8)?.trim().orEmpty().ifBlank { null }
                        val androidName = attrs?.get("android_name")?.toString(Charsets.UTF_8)?.trim().orEmpty().ifBlank { null }
                        resolved[key] = ComposeXPOSDiscoveredService(
                            role = role,
                            serviceName = resolvedInfo.serviceName,
                            host = host,
                            port = port,
                            androidDeviceName = androidName,
                            deviceUuid = uuid
                        )
                        emitResolved()
                    }
                }
            )
        }.onFailure {
            Log.w("ComposeXPOSNsd", "resolveService failed name=${serviceInfo.serviceName} err=${it.message}")
            queuedResolveKeys.remove(key)
            resolveInFlight = false
            processNextResolve()
        }
    }

    private fun emitResolved() {
        _services.value = resolved.values
            .sortedWith(compareBy<ComposeXPOSDiscoveredService> { it.role }.thenBy { it.host }.thenBy { it.port })
    }

    private fun keyOf(type: String, serviceName: String): String = "$type|$serviceName"

    private fun resolveBestHost(resolvedInfo: NsdServiceInfo): String {
        val attrs = runCatching { resolvedInfo.attributes }.getOrNull()
        val attrIpv4 = attrs?.get("ipv4")?.toString(Charsets.UTF_8)?.trim().orEmpty()
        if (isUsableLanHost(attrIpv4)) return attrIpv4

        val rawAddress = resolvedInfo.host?.hostAddress?.trim().orEmpty()
        if (rawAddress.isBlank()) return ""

        val cleanRaw = rawAddress.substringBefore('%')
        val currentHost = resolvedInfo.host
        if (currentHost is Inet4Address) {
            return if (isUsableLanHost(cleanRaw)) cleanRaw else ""
        }

        val hostName = currentHost?.hostName?.trim().orEmpty()
        if (hostName.isNotBlank()) {
            val ipv4 = runCatching {
                InetAddress.getAllByName(hostName)
                    .firstOrNull { it is Inet4Address }
                    ?.hostAddress
                    ?.trim()
            }.getOrNull()
            if (!ipv4.isNullOrBlank() && isUsableLanHost(ipv4)) return ipv4
        }

        return if (isUsableLanHost(cleanRaw)) cleanRaw else ""
    }

    private fun isUsableLanHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h.isBlank()) return false
        if (h == "localhost" || h == "::1") return false
        if (h.startsWith("127.")) return false
        if (h.startsWith("169.254.")) return false
        return true
    }

    private data class ResolveRequest(
        val role: String,
        val serviceInfo: NsdServiceInfo,
        val retryCount: Int,
    )
}
