package com.cofopt.cashregister.network

import android.util.Log
import com.cofopt.cashregister.calling.CallingRepository
import com.cofopt.cashregister.menu.DishesRepository
import com.cofopt.shared.network.submissionValidationError
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

class LanOrderServer(
    port: Int
) : NanoHTTPD(port) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class DishesSyncItem(
        val id: String,
        @SerialName("price_eur") val priceEur: Double,
        @SerialName("discounted_price") val discountedPrice: Double,
        @SerialName("sold_out") val soldOut: Boolean
    )

    @Serializable
    private data class MenuSyncItem(
        val id: String,
        val category: String,
        @SerialName("name_zh") val nameZh: String,
        @SerialName("name_en") val nameEn: String,
        @SerialName("name_nl") val nameNl: String,
        @SerialName("name_ja") val nameJa: String,
        @SerialName("name_tr") val nameTr: String,
        @SerialName("price_eur") val priceEur: Double,
        @SerialName("discounted_price") val discountedPrice: Double,
        @SerialName("sold_out") val soldOut: Boolean,
        @SerialName("kitchen_print") val kitchenPrint: Boolean,
        @SerialName("choose_vegan") val chooseVegan: Boolean,
        @SerialName("choose_source") val chooseSource: Boolean,
        @SerialName("choose_drink") val chooseDrink: Boolean,
        @SerialName("contains_eggs") val containsEggs: Boolean,
        @SerialName("contains_gluten") val containsGluten: Boolean,
        @SerialName("contains_lupin") val containsLupin: Boolean,
        @SerialName("contains_milk") val containsMilk: Boolean,
        @SerialName("contains_mustard") val containsMustard: Boolean,
        @SerialName("contains_nuts") val containsNuts: Boolean,
        @SerialName("contains_peanuts") val containsPeanuts: Boolean,
        @SerialName("contains_crustaceans") val containsCrustaceans: Boolean,
        @SerialName("contains_celery") val containsCelery: Boolean,
        @SerialName("contains_sesame_seeds") val containsSesameSeeds: Boolean,
        @SerialName("contains_soybeans") val containsSoybeans: Boolean,
        @SerialName("contains_fish") val containsFish: Boolean,
        @SerialName("contains_molluscs") val containsMolluscs: Boolean,
        @SerialName("contains_sulphites") val containsSulphites: Boolean,
        @SerialName("image_base64") val imageBase64: String? = null
    )

    @Serializable
    private data class OrderSubmissionResponse(
        val status: String,
        val callNumber: Int? = null,
        val duplicate: Boolean = false,
        val message: String? = null,
    )

    private sealed interface OrderAcceptance {
        data class Accepted(
            val orderId: String,
            val callNumber: Int?,
            val duplicate: Boolean,
        ) : OrderAcceptance

        data class Conflict(val message: String) : OrderAcceptance
        data class Invalid(val message: String) : OrderAcceptance
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.OPTIONS -> {
                    withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
                }

                session.method == Method.GET && session.uri == "/health" -> {
                    withCors(newFixedLengthResponse(Response.Status.OK, "text/plain", "ok"))
                }

                session.method == Method.GET && session.uri == "/dishes" -> {
                    val payload = DishesRepository.dishes.value.map { d ->
                        DishesSyncItem(
                            id = d.id,
                            priceEur = d.priceEur,
                            discountedPrice = d.discountedPrice,
                            soldOut = d.soldOut
                        )
                    }
                    val raw = json.encodeToString(payload)
                    withCors(newFixedLengthResponse(Response.Status.OK, "application/json", raw))
                }

                session.method == Method.GET && session.uri == "/menu" -> {
                    val payload = DishesRepository.dishes.value.map { d ->
                        MenuSyncItem(
                            id = d.id,
                            category = d.category,
                            nameZh = d.nameZh,
                            nameEn = d.nameEn,
                            nameNl = d.nameNl,
                            nameJa = d.nameJa,
                            nameTr = d.nameTr,
                            priceEur = d.priceEur,
                            discountedPrice = d.discountedPrice,
                            soldOut = d.soldOut,
                            kitchenPrint = d.kitchenPrint,
                            chooseVegan = d.chooseVegan,
                            chooseSource = d.chooseSource,
                            chooseDrink = d.chooseDrink,
                            containsEggs = d.containsEggs,
                            containsGluten = d.containsGluten,
                            containsLupin = d.containsLupin,
                            containsMilk = d.containsMilk,
                            containsMustard = d.containsMustard,
                            containsNuts = d.containsNuts,
                            containsPeanuts = d.containsPeanuts,
                            containsCrustaceans = d.containsCrustaceans,
                            containsCelery = d.containsCelery,
                            containsSesameSeeds = d.containsSesameSeeds,
                            containsSoybeans = d.containsSoybeans,
                            containsFish = d.containsFish,
                            containsMolluscs = d.containsMolluscs,
                            containsSulphites = d.containsSulphites,
                            imageBase64 = d.imageBase64
                        )
                    }
                    val raw = json.encodeToString(payload)
                    withCors(newFixedLengthResponse(Response.Status.OK, "application/json", raw))
                }

                session.method == Method.POST && session.uri == "/orders" -> {
                    val raw = try {
                        val body = HashMap<String, String>()
                        session.parseBody(body)
                        body["postData"].orEmpty()
                    } catch (e: Exception) {
                        Log.w("LanOrderServer", "Unable to read order request body", e)
                        return orderResponse(
                            Response.Status.BAD_REQUEST,
                            OrderSubmissionResponse(status = "error", message = "request_body_invalid"),
                        )
                    }

                    if (raw.isBlank()) {
                        return orderResponse(
                            Response.Status.BAD_REQUEST,
                            OrderSubmissionResponse(status = "error", message = "request_body_missing"),
                        )
                    }
                    if (raw.length > MAX_ORDER_BODY_CHARS) {
                        return orderResponse(
                            Response.Status.PAYLOAD_TOO_LARGE,
                            OrderSubmissionResponse(status = "error", message = "request_body_too_large"),
                        )
                    }

                    val decoded = try {
                        json.decodeFromString(OrderPayload.serializer(), raw)
                    } catch (e: Exception) {
                        Log.w("LanOrderServer", "Invalid order JSON", e)
                        return orderResponse(
                            Response.Status.BAD_REQUEST,
                            OrderSubmissionResponse(status = "error", message = "order_json_invalid"),
                        )
                    }

                    decoded.submissionValidationError()?.let { validationError ->
                        return orderResponse(
                            Response.Status.BAD_REQUEST,
                            OrderSubmissionResponse(status = "error", message = validationError),
                        )
                    }

                    Log.d("LanOrderServer", "Order Event: ORDER_RECEIVED | orderId=${decoded.orderId} | callNumber=${decoded.callNumber}")
                    Log.d("LanOrderServer", "Network Event: ORDER_POST | source=${session.remoteHostName}")

                    when (val acceptance = acceptOrder(decoded)) {
                        is OrderAcceptance.Accepted -> {
                            if (acceptance.duplicate) {
                                Log.i(
                                    "LanOrderServer",
                                    "Duplicate order retry accepted idempotently orderId=${acceptance.orderId}",
                                )
                            }
                            val durable = runBlocking {
                                val ordersPersisted = OrdersRepository.persistNow()
                                val callingPersisted = CallingRepository.persistNow()
                                ordersPersisted && callingPersisted
                            }
                            if (!durable) {
                                Log.e(
                                    "LanOrderServer",
                                    "Order accepted in memory but durable commit failed orderId=${acceptance.orderId}",
                                )
                                orderResponse(
                                    Response.Status.SERVICE_UNAVAILABLE,
                                    OrderSubmissionResponse(
                                        status = "error",
                                        callNumber = acceptance.callNumber,
                                        duplicate = acceptance.duplicate,
                                        message = "state_persistence_failed",
                                    ),
                                )
                            } else {
                                orderResponse(
                                    Response.Status.OK,
                                    OrderSubmissionResponse(
                                        status = "ok",
                                        callNumber = acceptance.callNumber,
                                        duplicate = acceptance.duplicate,
                                    ),
                                )
                            }
                        }

                        is OrderAcceptance.Conflict -> orderResponse(
                            Response.Status.CONFLICT,
                            OrderSubmissionResponse(status = "error", message = acceptance.message),
                        )

                        is OrderAcceptance.Invalid -> orderResponse(
                            Response.Status.BAD_REQUEST,
                            OrderSubmissionResponse(status = "error", message = acceptance.message),
                        )
                    }
                }

                else -> {
                    withCors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found"))
                }
            }
        } catch (e: Exception) {
            Log.e("LanOrderServer", "serve error", e)
            withCors(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "error"))
        }
    }

    /** Serializes allocation + insert across every server instance in this process. */
    private fun acceptOrder(decoded: OrderPayload): OrderAcceptance = synchronized(ORDER_ACCEPTANCE_LOCK) {
        acceptOrderLocked(decoded)
    }

    private fun acceptOrderLocked(decoded: OrderPayload): OrderAcceptance {
        val source = decoded.source.trim().uppercase()
        val paymentMethod = decoded.paymentMethod.trim().uppercase()
        val incomingStatus = decoded.paymentStatus
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase()
            ?: decoded.status.trim().uppercase()
        val fingerprint = submissionFingerprint(
            decoded = decoded,
            source = source,
            paymentMethod = paymentMethod,
            incomingStatus = incomingStatus,
        )

        when (val ledgerMatch = OrdersRepository.matchAcceptedLanOrder(decoded.orderId, fingerprint)) {
            is OrderIdempotencyMatch.Duplicate -> {
                return OrderAcceptance.Accepted(
                    orderId = decoded.orderId,
                    callNumber = ledgerMatch.entry.originalCallNumber,
                    duplicate = true,
                )
            }
            OrderIdempotencyMatch.Conflict -> {
                return OrderAcceptance.Conflict("order_id_conflict")
            }
            OrderIdempotencyMatch.Missing -> Unit
        }

        val existing = OrdersRepository.findByOrderId(decoded.orderId)
        if (existing != null) {
            val retry = normalizeOrder(
                decoded = decoded,
                source = source,
                paymentMethod = paymentMethod,
                incomingStatus = incomingStatus,
                callNumber = decoded.callNumber ?: existing.callNumber,
                acceptedAtMillis = existing.acceptedAtMillis,
                submissionFingerprint = fingerprint,
            )
            val sameImmutableOrder = isSameOrderSubmission(
                existing = existing,
                retry = retry,
                retryHadExplicitCallNumber = decoded.callNumber != null,
            )

            if (!sameImmutableOrder) {
                return OrderAcceptance.Conflict("order_id_conflict")
            }

            return when (
                val result = OrdersRepository.rememberAcceptedLanOrderIdentity(
                    order = existing,
                    payloadFingerprint = fingerprint,
                )
            ) {
                is OrderIdempotencyRecordResult.Recorded -> OrderAcceptance.Accepted(
                    orderId = existing.orderId,
                    callNumber = result.entry.originalCallNumber,
                    duplicate = true,
                )
                is OrderIdempotencyRecordResult.Duplicate -> OrderAcceptance.Accepted(
                    orderId = existing.orderId,
                    callNumber = result.entry.originalCallNumber,
                    duplicate = true,
                )
                OrderIdempotencyRecordResult.Conflict -> {
                    OrderAcceptance.Conflict("order_id_conflict")
                }
            }
        }

        newOrderProtocolValidationError(
            source = source,
            paymentMethod = paymentMethod,
            incomingStatus = incomingStatus,
            callNumber = decoded.callNumber,
        )?.let { return OrderAcceptance.Invalid(it) }

        val assignResult = if (
            decoded.callNumber == null &&
            source == "KIOSK" &&
            (
                (paymentMethod == "CARD" && incomingStatus == "PAID") ||
                    paymentMethod == "CASH"
                )
        ) {
            if (paymentMethod == "CASH") {
                CallingRepository.reserveNext()
            } else {
                CallingRepository.assignNext()
            }
        } else {
            null
        }

        assignResult?.overtakenNumber?.let { overtaken ->
            OrdersRepository.updateOrderStatusByCallNumber(overtaken, "COMPLETED")
        }

        val normalized = normalizeOrder(
            decoded = decoded,
            source = source,
            paymentMethod = paymentMethod,
            incomingStatus = incomingStatus,
            callNumber = assignResult?.number ?: decoded.callNumber,
            acceptedAtMillis = System.currentTimeMillis(),
            submissionFingerprint = fingerprint,
        )
        return when (
            val result = OrdersRepository.addAcceptedLanOrder(
                order = normalized,
                payloadFingerprint = fingerprint,
            )
        ) {
            is OrderIdempotencyRecordResult.Recorded -> OrderAcceptance.Accepted(
                orderId = normalized.orderId,
                callNumber = result.entry.originalCallNumber,
                duplicate = false,
            )
            is OrderIdempotencyRecordResult.Duplicate -> OrderAcceptance.Accepted(
                orderId = normalized.orderId,
                callNumber = result.entry.originalCallNumber,
                duplicate = true,
            )
            OrderIdempotencyRecordResult.Conflict -> OrderAcceptance.Conflict("order_id_conflict")
        }
    }

    private fun normalizeOrder(
        decoded: OrderPayload,
        source: String,
        paymentMethod: String,
        incomingStatus: String,
        callNumber: Int?,
        acceptedAtMillis: Long?,
        submissionFingerprint: String,
    ): OrderPayload {
        val normalizedStatus = if (
            incomingStatus == "PAID" &&
            source == "KIOSK" &&
            paymentMethod == "CASH"
        ) {
            "UNPAID"
        } else {
            incomingStatus
        }
        return decoded.copy(
            source = source,
            paymentMethod = paymentMethod,
            status = normalizedStatus,
            callNumber = callNumber,
            acceptedAtMillis = acceptedAtMillis,
            submissionFingerprint = submissionFingerprint,
        )
    }

    private fun submissionFingerprint(
        decoded: OrderPayload,
        source: String,
        paymentMethod: String,
        incomingStatus: String,
    ): String {
        val canonical = decoded.copy(
            source = source,
            paymentMethod = paymentMethod,
            paymentStatus = incomingStatus,
            status = incomingStatus,
            acceptedAtMillis = null,
            submissionFingerprint = null,
        )
        val raw = json.encodeToString(OrderPayload.serializer(), canonical)
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.encodeToByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun orderResponse(
        status: Response.Status,
        payload: OrderSubmissionResponse,
    ): Response {
        return withCors(
            newFixedLengthResponse(
                status,
                "application/json",
                json.encodeToString(OrderSubmissionResponse.serializer(), payload),
            )
        )
    }

    private fun withCors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-ComposeXPOS-Key")
        response.addHeader("Access-Control-Max-Age", "86400")
        response.addHeader("Access-Control-Allow-Private-Network", "true")
        return response
    }

    private companion object {
        const val MAX_ORDER_BODY_CHARS = 2_000_000
        val ORDER_ACCEPTANCE_LOCK = Any()
    }
}
