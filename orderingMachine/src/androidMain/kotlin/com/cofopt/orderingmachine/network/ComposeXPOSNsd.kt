package com.cofopt.orderingmachine.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.cofopt.shared.network.OrderingCashRegisterConfigRequest
import com.cofopt.shared.network.OrderingCashRegisterConfigResponse
import com.cofopt.shared.network.POSROID_LINK_SHARED_KEY
import fi.iki.elonen.NanoHTTPD
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
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register(port: Int) {
        stop()
        if (port <= 0) return

        val uuid = DeviceConfig.deviceUuid(appContext)
        val androidName = DeviceConfig.androidDeviceName()
        val info = NsdServiceInfo().apply {
            serviceType = NSD_TYPE_ORDERING
            serviceName = "ComposeXPOS-OrderingMachine-${uuid.takeLast(6)}"
            this.port = port
            setAttribute("uuid", uuid)
            setAttribute("android_name", androidName)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "Ordering NSD registered: ${serviceInfo.serviceName}:${serviceInfo.port}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Ordering NSD registration failed: code=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("ComposeXPOSNsd", "Ordering NSD unregistered: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("ComposeXPOSNsd", "Ordering NSD unregistration failed: code=$errorCode")
            }
        }

        registrationListener = listener
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "Ordering NSD register failed: ${it.message}")
        }
    }

    fun stop() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching {
            nsdManager.unregisterService(listener)
        }.onFailure {
            Log.w("ComposeXPOSNsd", "Ordering NSD stop failed: ${it.message}")
        }
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

    fun start() {
        if (server != null) return
        val httpServer = object : NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response {
                return handleSession(session)
            }
        }
        runCatching {
            httpServer.start(5000, false)
            server = httpServer
            Log.d("ComposeXPOSNsd", "Ordering presence HTTP server started on port=$port")
        }.onFailure {
            Log.w("ComposeXPOSNsd", "Ordering presence HTTP server start failed: ${it.message}")
        }
    }

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

                session.method == NanoHTTPD.Method.GET && session.uri == "/posroid-ordering.json" -> {
                    corsResponse(
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK,
                            "application/json",
                            json.encodeToString(OrderingDiscoveryPayload())
                        )
                    )
                }

                session.method == NanoHTTPD.Method.GET && session.uri == "/cashregister" -> {
                    val host = CashRegisterConfig.host(appContext).trim()
                    val savedPort = CashRegisterConfig.port(appContext)
                    val configured = host.isNotBlank() && savedPort > 0
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

                    val host = request.host.trim()
                    val savedPort = request.port
                    if (!isValidEndpoint(host, savedPort)) {
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

                    CashRegisterConfig.save(appContext, host, savedPort)
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
        val body = HashMap<String, String>()
        session.parseBody(body)
        val raw = body["postData"].orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            json.decodeFromString<OrderingCashRegisterConfigRequest>(raw)
        }.getOrNull()
    }

    private fun isAuthorized(
        session: NanoHTTPD.IHTTPSession,
        request: OrderingCashRegisterConfigRequest
    ): Boolean {
        val headerKey = session.headers["x-posroid-key"]?.trim().orEmpty()
        val bodyKey = request.sharedKey?.trim().orEmpty()
        val provided = if (headerKey.isNotBlank()) headerKey else bodyKey
        return provided == POSROID_LINK_SHARED_KEY
    }

    private fun isValidEndpoint(host: String, port: Int): Boolean {
        if (host.isBlank()) return false
        if (host.contains(' ')) return false
        if (port !in 1..65535) return false
        return true
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
}
