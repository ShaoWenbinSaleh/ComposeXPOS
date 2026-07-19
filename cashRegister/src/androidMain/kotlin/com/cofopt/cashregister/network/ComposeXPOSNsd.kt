package com.cofopt.cashregister.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var desiredPort: Int? = null
    private val retryRegistration = Runnable { registerDesiredService() }

    fun registerAsCashRegister(port: Int) {
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

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = COMPOSEXPOS_NSD_TYPE_CASHREGISTER
            serviceName = "COMPOSEXPOS-CashRegister"
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                mainHandler.removeCallbacks(retryRegistration)
                Log.d("ComposeXPOSNsd", "CashRegister NSD registered: ${serviceInfo.serviceName}:${serviceInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "CashRegister NSD registration failed: code=$errorCode")
                if (registrationListener === this) {
                    registrationListener = null
                    scheduleRegistrationRetry()
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "CashRegister NSD unregistered: ${serviceInfo.serviceName}")
                if (desiredPort != null && registrationListener === this) {
                    registrationListener = null
                    scheduleRegistrationRetry()
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "CashRegister NSD unregistration failed: code=$errorCode")
            }
        }

        registrationListener = listener
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            if (registrationListener === listener) registrationListener = null
            Log.w("ComposeXPOSNsd", "registerAsCashRegister failed: ${it.message}")
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
            Log.w("ComposeXPOSNsd", "stop advertiser failed: ${it.message}")
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

class ComposeXPOSNsdBrowser(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val multicastLock = (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
        ?.createMulticastLock("ComposeXPOS.NsdBrowser")
        ?.apply { setReferenceCounted(false) }
    private var multicastLockHeld = false
    private val discoveryListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val discoveryRetryCallbacks = mutableMapOf<String, Runnable>()
    private val discoveryLifecycle = NsdDiscoveryLifecycle()
    private val resolved = ConcurrentHashMap<String, ComposeXPOSDiscoveredService>()
    private val resolveQueue = ArrayDeque<ResolveRequest>()
    private val queuedResolveKeys = LinkedHashSet<String>()
    private val foundServiceKeys = LinkedHashSet<String>()
    private var resolveInFlight = false
    private var inFlightResolveKey: String? = null
    private val _services = MutableStateFlow<List<ComposeXPOSDiscoveredService>>(emptyList())
    val services: StateFlow<List<ComposeXPOSDiscoveredService>> = _services

    fun start() {
        acquireMulticastLock()
        val wasRunning = discoveryLifecycle.isRunning
        val generation = discoveryLifecycle.start()
        if (!wasRunning) {
            resolveQueue.clear()
            queuedResolveKeys.clear()
            foundServiceKeys.clear()
            resolveInFlight = false
            inFlightResolveKey = null
            resolved.clear()
            emitResolved()
        }

        startDiscoveryForType(
            type = COMPOSEXPOS_NSD_TYPE_ORDERING,
            role = "OrderingMachine",
            generation = generation,
            retryImmediately = true,
        )
        startDiscoveryForType(
            type = COMPOSEXPOS_NSD_TYPE_CALLING,
            role = "CallingMachine",
            generation = generation,
            retryImmediately = true,
        )
    }

    fun stop(clearResults: Boolean = false) {
        discoveryLifecycle.stop()
        cancelDiscoveryRetries()
        val listeners = discoveryListeners.values.toList()
        discoveryListeners.clear()
        listeners.forEach { listener ->
            runCatching {
                nsdManager.stopServiceDiscovery(listener)
            }.onFailure {
                Log.w("ComposeXPOSNsd", "stop discovery failed: ${it.message}")
            }
        }
        resolveQueue.clear()
        queuedResolveKeys.clear()
        foundServiceKeys.clear()
        resolveInFlight = false
        inFlightResolveKey = null
        releaseMulticastLock()
        if (clearResults) {
            resolved.clear()
            emitResolved()
        }
    }

    private fun startDiscoveryForType(
        type: String,
        role: String,
        generation: Long,
        retryImmediately: Boolean = false,
    ) {
        if (!discoveryLifecycle.isCurrent(generation)) return
        if (discoveryListeners.containsKey(type)) return
        if (retryImmediately) cancelDiscoveryRetry(type)
        if (discoveryRetryCallbacks.containsKey(type)) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!isCurrentDiscovery(type, this, generation)) return
                discoveryListeners.remove(type)
                Log.w("ComposeXPOSNsd", "onStartDiscoveryFailed type=$serviceType code=$errorCode")
                runCatching { nsdManager.stopServiceDiscovery(this) }
                    .onFailure {
                        Log.w("ComposeXPOSNsd", "cleanup failed discovery type=$type err=${it.message}")
                    }
                scheduleDiscoveryRetry(type, role, generation)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!isCurrentDiscovery(type, this, generation)) return
                Log.w("ComposeXPOSNsd", "onStopDiscoveryFailed type=$serviceType code=$errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                if (!isCurrentDiscovery(type, this, generation)) return
                discoveryLifecycle.markDiscoveryStarted(type)
                Log.d("ComposeXPOSNsd", "Discovery started type=$serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                if (!isCurrentDiscovery(type, this, generation)) return
                discoveryListeners.remove(type)
                Log.w("ComposeXPOSNsd", "Discovery stopped unexpectedly type=$serviceType")
                scheduleDiscoveryRetry(type, role, generation)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!isCurrentDiscovery(type, this, generation)) return
                if (serviceInfo.serviceType != type) return
                val key = keyOf(type, serviceInfo.serviceName)
                foundServiceKeys.add(key)
                enqueueResolve(role, serviceInfo, generation = generation)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (!isCurrentDiscovery(type, this, generation)) return
                val key = keyOf(type, serviceInfo.serviceName)
                foundServiceKeys.remove(key)
                resolved.remove(key)
                removeQueuedResolve(key)
                emitResolved()
            }
        }

        discoveryListeners[type] = listener
        runCatching {
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            if (isCurrentDiscovery(type, listener, generation)) {
                discoveryListeners.remove(type)
                scheduleDiscoveryRetry(type, role, generation)
            }
            Log.w("ComposeXPOSNsd", "discoverServices failed type=$type err=${it.message}")
        }
    }

    private fun isCurrentDiscovery(
        type: String,
        listener: NsdManager.DiscoveryListener,
        generation: Long,
    ): Boolean {
        return discoveryLifecycle.isCurrent(generation) && discoveryListeners[type] === listener
    }

    private fun scheduleDiscoveryRetry(type: String, role: String, generation: Long) {
        if (!discoveryLifecycle.isCurrent(generation)) return
        if (discoveryListeners.containsKey(type) || discoveryRetryCallbacks.containsKey(type)) return

        val delayMillis = discoveryLifecycle.nextRetryDelayMillis(type)
        lateinit var retry: Runnable
        retry = Runnable {
            if (discoveryRetryCallbacks[type] !== retry) return@Runnable
            discoveryRetryCallbacks.remove(type)
            if (!discoveryLifecycle.isCurrent(generation)) return@Runnable
            startDiscoveryForType(type, role, generation)
        }
        discoveryRetryCallbacks[type] = retry
        if (!mainHandler.postDelayed(retry, delayMillis)) {
            discoveryRetryCallbacks.remove(type)
            Log.w("ComposeXPOSNsd", "Unable to schedule discovery retry type=$type")
        }
    }

    private fun cancelDiscoveryRetry(type: String) {
        val callback = discoveryRetryCallbacks.remove(type) ?: return
        mainHandler.removeCallbacks(callback)
    }

    private fun cancelDiscoveryRetries() {
        val callbacks = discoveryRetryCallbacks.values.toList()
        discoveryRetryCallbacks.clear()
        callbacks.forEach(mainHandler::removeCallbacks)
    }

    private fun enqueueResolve(
        role: String,
        serviceInfo: NsdServiceInfo,
        retryCount: Int = 0,
        generation: Long = discoveryLifecycle.generation,
    ) {
        if (!discoveryLifecycle.isCurrent(generation)) return
        val key = keyOf(serviceInfo.serviceType, serviceInfo.serviceName)
        if (key !in foundServiceKeys) return
        if (!queuedResolveKeys.add(key)) return
        resolveQueue.addLast(ResolveRequest(role, serviceInfo, retryCount, generation))
        processNextResolve()
    }

    private fun processNextResolve() {
        if (resolveInFlight) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        if (!discoveryLifecycle.isCurrent(next.generation)) {
            queuedResolveKeys.remove(keyOf(next.serviceInfo.serviceType, next.serviceInfo.serviceName))
            processNextResolve()
            return
        }
        resolveInFlight = true
        inFlightResolveKey = keyOf(next.serviceInfo.serviceType, next.serviceInfo.serviceName)
        resolve(next.role, next.serviceInfo, next.retryCount, next.generation)
    }

    private fun removeQueuedResolve(key: String) {
        if (!queuedResolveKeys.contains(key)) return
        if (inFlightResolveKey != key) {
            queuedResolveKeys.remove(key)
        }
        val keep = resolveQueue.filterNot { req ->
            keyOf(req.serviceInfo.serviceType, req.serviceInfo.serviceName) == key
        }
        resolveQueue.clear()
        resolveQueue.addAll(keep)
    }

    private fun resolve(role: String, serviceInfo: NsdServiceInfo, retryCount: Int, generation: Long) {
        val key = keyOf(serviceInfo.serviceType, serviceInfo.serviceName)
        runCatching {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (!discoveryLifecycle.isCurrent(generation)) return
                        Log.w("ComposeXPOSNsd", "resolve failed name=${serviceInfo.serviceName} code=$errorCode")
                        queuedResolveKeys.remove(key)
                        resolveInFlight = false
                        inFlightResolveKey = null
                        if (retryCount < 2 && key in foundServiceKeys) {
                            enqueueResolve(role, serviceInfo, retryCount + 1, generation)
                        } else {
                            processNextResolve()
                        }
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        if (!discoveryLifecycle.isCurrent(generation)) return
                        val host = resolveBestHost(resolvedInfo)
                        val port = resolvedInfo.port
                        queuedResolveKeys.remove(key)
                        resolveInFlight = false
                        inFlightResolveKey = null
                        processNextResolve()
                        if (key !in foundServiceKeys || host.isBlank() || port <= 0) return
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
            if (!discoveryLifecycle.isCurrent(generation)) return@onFailure
            Log.w("ComposeXPOSNsd", "resolveService failed name=${serviceInfo.serviceName} err=${it.message}")
            queuedResolveKeys.remove(key)
            resolveInFlight = false
            inFlightResolveKey = null
            processNextResolve()
        }
    }

    private fun emitResolved() {
        _services.value = resolved.values
            .sortedWith(compareBy<ComposeXPOSDiscoveredService> { it.role }.thenBy { it.host }.thenBy { it.port })
    }

    private fun acquireMulticastLock() {
        if (multicastLockHeld) return
        runCatching {
            multicastLock?.acquire()
            multicastLockHeld = multicastLock?.isHeld == true
        }.onFailure {
            multicastLockHeld = false
            Log.w("ComposeXPOSNsd", "Unable to acquire multicast lock: ${it.message}")
        }
    }

    private fun releaseMulticastLock() {
        if (!multicastLockHeld) return
        runCatching { multicastLock?.release() }
            .onFailure { Log.w("ComposeXPOSNsd", "Unable to release multicast lock: ${it.message}") }
        multicastLockHeld = false
    }

    private fun keyOf(type: String, serviceName: String): String = "$type|$serviceName"

    private fun resolveBestHost(resolvedInfo: NsdServiceInfo): String {
        val rawAddress = resolvedInfo.host?.hostAddress?.trim().orEmpty()
        val currentHost = resolvedInfo.host
        val cleanRaw = if (currentHost is Inet4Address) rawAddress.substringBefore('%') else rawAddress
        if (currentHost is Inet4Address) {
            return if (isUsableLanHost(cleanRaw)) cleanRaw else ""
        }

        // Prefer the address resolved by NSD on the current network. The TXT
        // fallback can point at another active interface on multi-homed devices.
        val attrs = runCatching { resolvedInfo.attributes }.getOrNull()
        val attrIpv4 = attrs?.get("ipv4")?.toString(Charsets.UTF_8)?.trim().orEmpty()
        if (isUsableIpv4Literal(attrIpv4)) return attrIpv4

        if (rawAddress.isBlank()) return ""
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

    private fun isUsableIpv4Literal(host: String): Boolean {
        val octets = host.split('.')
        if (octets.size != 4) return false
        if (octets.any { it.isEmpty() || it.toIntOrNull() !in 0..255 }) return false
        return isUsableLanHost(host)
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
        val generation: Long,
    )
}

internal class NsdDiscoveryLifecycle {
    private val retryAttempts = mutableMapOf<String, Int>()

    var generation: Long = 0L
        private set

    var isRunning: Boolean = false
        private set

    fun start(): Long {
        if (!isRunning) {
            generation += 1
            isRunning = true
            retryAttempts.clear()
        }
        return generation
    }

    fun stop() {
        generation += 1
        isRunning = false
        retryAttempts.clear()
    }

    fun isCurrent(callbackGeneration: Long): Boolean {
        return isRunning && callbackGeneration == generation
    }

    fun nextRetryDelayMillis(type: String): Long {
        val attempt = retryAttempts[type] ?: 0
        retryAttempts[type] = (attempt + 1).coerceAtMost(MAX_TRACKED_RETRY_ATTEMPT)
        return nsdDiscoveryRetryDelayMillis(attempt)
    }

    fun markDiscoveryStarted(type: String) {
        retryAttempts.remove(type)
    }

    private companion object {
        const val MAX_TRACKED_RETRY_ATTEMPT = 4
    }
}

internal fun nsdDiscoveryRetryDelayMillis(failureCount: Int): Long {
    return when (failureCount.coerceAtLeast(0)) {
        0 -> 1_000L
        1 -> 2_000L
        2 -> 4_000L
        else -> 8_000L
    }
}
