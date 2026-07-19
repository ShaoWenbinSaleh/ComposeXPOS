package com.cofopt.orderingmachine.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cofopt.shared.network.OrderingCashRegisterConfigRequest
import com.cofopt.shared.network.OrderingCashRegisterConfigResponse
import com.cofopt.shared.network.COMPOSEXPOS_LINK_SHARED_KEY
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

private const val NSD_TYPE_ORDERING = "_composexpos-ordering._tcp."

@Serializable
private data class OrderingDiscoveryPayload(
    val app: String = "OrderingMachine",
    val platform: String = "android",
    val protocol: String = "ComposeXPOS-ordering-discovery-v1",
    val supportsCashRegisterConfigPush: Boolean = true
)

class ComposeXPOSOrderingNsdAdvertiser(context: Context) {
    private val appContext = context.applicationContext
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

        val uuid = DeviceConfig.deviceUuid(appContext)
        val androidName = DeviceConfig.androidDeviceName()
        val info = runCatching {
            NsdServiceInfo().apply {
                serviceType = NSD_TYPE_ORDERING
                serviceName = "ComposeXPOS-OrderingMachine-${uuid.takeLast(6)}"
                this.port = port
                setAttribute("uuid", uuid)
                setAttribute("android_name", androidName)
                resolveLocalIpv4Address()?.let { setAttribute("ipv4", it) }
            }
        }.getOrElse {
            Log.w("ComposeXPOSNsd", "Unable to build Ordering NSD service info: ${it.message}")
            scheduleRegistrationRetry()
            return
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                mainHandler.removeCallbacks(retryRegistration)
                Log.d("ComposeXPOSNsd", "Ordering NSD registered: ${serviceInfo.serviceName}:${serviceInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Ordering NSD registration failed: code=$errorCode")
                if (registrationListener === this) {
                    registrationListener = null
                    scheduleRegistrationRetry()
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "Ordering NSD unregistered: ${serviceInfo.serviceName}")
                if (desiredPort != null && registrationListener === this) {
                    registrationListener = null
                    scheduleRegistrationRetry()
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Ordering NSD unregistration failed: code=$errorCode")
            }
        }

        registrationListener = listener
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            if (registrationListener === listener) {
                registrationListener = null
            }
            Log.w("ComposeXPOSNsd", "Ordering NSD register failed: ${it.message}")
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
            Log.w("ComposeXPOSNsd", "Ordering NSD stop failed: ${it.message}")
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

class OrderingPresenceServer(
    private val context: Context,
    private val port: Int
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var server: NanoHTTPD? = null
    private val configLock = Any()

    @Synchronized
    fun start(): Boolean {
        if (server != null) return true
        val httpServer = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response {
                return handleSession(session)
            }
        }
        return runCatching {
            httpServer.start(5000, false)
            server = httpServer
            Log.d("ComposeXPOSNsd", "Ordering presence HTTP server started on port=$port")
            true
        }.onFailure {
            runCatching { httpServer.stop() }
            Log.w("ComposeXPOSNsd", "Ordering presence HTTP server start failed: ${it.message}")
        }.getOrDefault(false)
    }

    @Synchronized
    fun stop() {
        val running = server ?: return
        server = null
        runCatching { running.stop() }
            .onFailure { Log.w("ComposeXPOSNsd", "Ordering presence HTTP server stop failed: ${it.message}") }
    }

    private fun handleSession(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        return try {
            when {
                session.method == NanoHTTPD.Method.OPTIONS -> {
                    corsResponse(
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NO_CONTENT,
                            NanoHTTPD.MIME_PLAINTEXT,
                            ""
                        )
                    )
                }

                session.method == NanoHTTPD.Method.GET && session.uri == "/health" -> {
                    corsResponse(
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK,
                            NanoHTTPD.MIME_PLAINTEXT,
                            "ok"
                        )
                    )
                }

                session.method == NanoHTTPD.Method.GET && session.uri == "/composexpos-ordering.json" -> {
                    corsResponse(
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK,
                            "application/json",
                            json.encodeToString(OrderingDiscoveryPayload())
                        )
                    )
                }

                session.method == NanoHTTPD.Method.GET && session.uri == "/cashregister" -> {
                    val endpoint = synchronized(configLock) { CashRegisterConfig.endpoint(appContext) }
                    val host = endpoint?.host.orEmpty()
                    val savedPort = endpoint?.port ?: 8080
                    val configured = cashRegisterUrl(host, savedPort, "/health") != null
                    corsResponse(
                        jsonResponse(
                            status = NanoHTTPD.Response.Status.OK,
                            payload = OrderingCashRegisterConfigResponse(
                                status = "ok",
                                host = host.takeIf { configured },
                                port = savedPort.takeIf { configured },
                                configured = configured
                            )
                        )
                    )
                }

                session.method == NanoHTTPD.Method.POST && session.uri == "/cashregister" -> {
                    val request = parseSetCashRegisterRequest(session)
                        ?: return corsResponse(
                            jsonResponse(
                                status = NanoHTTPD.Response.Status.BAD_REQUEST,
                                payload = OrderingCashRegisterConfigResponse(
                                    status = "error",
                                    message = "invalid_json"
                                )
                            )
                        )
                    if (!isAuthorized(session, request)) {
                        return corsResponse(
                            jsonResponse(
                                status = NanoHTTPD.Response.Status.UNAUTHORIZED,
                                payload = OrderingCashRegisterConfigResponse(
                                    status = "error",
                                    message = "unauthorized"
                                )
                            )
                        )
                    }

                    val host = normalizeCashRegisterHost(request.host, request.port)
                    val savedPort = request.port
                    if (host == null) {
                        return corsResponse(
                            jsonResponse(
                                status = NanoHTTPD.Response.Status.BAD_REQUEST,
                                payload = OrderingCashRegisterConfigResponse(
                                    status = "error",
                                    message = "invalid_cashregister_endpoint"
                                )
                            )
                        )
                    }

                    val saved = synchronized(configLock) {
                        CashRegisterConfig.save(appContext, host, savedPort)
                    }
                    if (!saved) {
                        return corsResponse(
                            jsonResponse(
                                status = NanoHTTPD.Response.Status.INTERNAL_ERROR,
                                payload = OrderingCashRegisterConfigResponse(
                                    status = "error",
                                    message = "cashregister_config_persistence_failed",
                                ),
                            ),
                        )
                    }
                    Log.d("ComposeXPOSNsd", "Ordering received CashRegister config via HTTP: $host:$savedPort")
                    corsResponse(
                        jsonResponse(
                            status = NanoHTTPD.Response.Status.OK,
                            payload = OrderingCashRegisterConfigResponse(
                                status = "ok",
                                host = host,
                                port = savedPort,
                                configured = true
                            )
                        )
                    )
                }

                else -> {
                    corsResponse(
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NOT_FOUND,
                            NanoHTTPD.MIME_PLAINTEXT,
                            "not found"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ComposeXPOSNsd", "Ordering presence HTTP server error", e)
            corsResponse(
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    e.message ?: "error"
                )
            )
        }
    }

    private fun parseSetCashRegisterRequest(session: NanoHTTPD.IHTTPSession): OrderingCashRegisterConfigRequest? {
        return runCatching {
            val body = HashMap<String, String>()
            session.parseBody(body)
            val raw = body["postData"].orEmpty()
            if (raw.isBlank() || raw.length > MAX_CONFIG_BODY_CHARS) return@runCatching null
            json.decodeFromString<OrderingCashRegisterConfigRequest>(raw)
        }.getOrNull()
    }

    private fun isAuthorized(
        session: NanoHTTPD.IHTTPSession,
        request: OrderingCashRegisterConfigRequest
    ): Boolean {
        val headerKey = session.headers.entries
            .firstOrNull { (name, _) -> name.equals("x-composexpos-key", ignoreCase = true) }
            ?.value
            ?.trim()
            .orEmpty()
        val bodyKey = request.sharedKey?.trim().orEmpty()
        val provided = if (headerKey.isNotBlank()) headerKey else bodyKey
        return provided == COMPOSEXPOS_LINK_SHARED_KEY
    }

    private fun jsonResponse(
        status: NanoHTTPD.Response.Status,
        payload: OrderingCashRegisterConfigResponse
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            json.encodeToString(payload)
        )
    }

    private fun corsResponse(response: NanoHTTPD.Response): NanoHTTPD.Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-ComposeXPOS-Key")
        response.addHeader("Access-Control-Max-Age", "86400")
        response.addHeader("Access-Control-Allow-Private-Network", "true")
        return response
    }

    private companion object {
        const val MAX_CONFIG_BODY_CHARS = 16_384
    }
}

private fun resolveLocalIpv4Address(): String? {
    return runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        val addresses = interfaces.toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .toList()

        val preferred = addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull()
        preferred?.hostAddress?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
