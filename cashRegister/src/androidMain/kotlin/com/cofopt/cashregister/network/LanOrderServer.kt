package com.cofopt.cashregister.network

import android.util.Log
import com.cofopt.cashregister.calling.CallingRepository
import com.cofopt.cashregister.menu.DishesRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
                    val body = HashMap<String, String>()
                    session.parseBody(body)
                    val raw = body["postData"] ?: ""
                    val decoded = json.decodeFromString(OrderPayload.serializer(), raw)
                    val source = decoded.source.trim().uppercase()
                    val paymentMethod = decoded.paymentMethod.trim().uppercase()

                    Log.d("LanOrderServer", "Order Event: ORDER_RECEIVED | orderId=${decoded.orderId} | callNumber=${decoded.callNumber}")
                    Log.d("LanOrderServer", "Network Event: ORDER_POST | source=${session.remoteHostName}")

                    val incomingStatus = decoded.paymentStatus?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
                        ?: decoded.status.trim().uppercase()

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

                    val overtaken = assignResult?.overtakenNumber
                    if (overtaken != null) {
                        OrdersRepository.updateOrderStatusByCallNumber(overtaken, "COMPLETED")
                    }

                    val assignedCallNumber = assignResult?.number ?: decoded.callNumber

                    val normalized = if (
                        incomingStatus == "PAID" &&
                        source == "KIOSK" &&
                        paymentMethod == "CASH"
                    ) {
                        decoded.copy(
                            source = source,
                            paymentMethod = paymentMethod,
                            status = "UNPAID",
                            callNumber = assignedCallNumber
                        )
                    } else {
                        decoded.copy(
                            source = source,
                            paymentMethod = paymentMethod,
                            status = incomingStatus,
                            callNumber = assignedCallNumber
                        )
                    }
                    OrdersRepository.add(normalized)

                    val callNumberJson = assignedCallNumber?.toString() ?: "null"
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        "{\"status\":\"ok\",\"callNumber\":$callNumberJson}"
                    ).let(::withCors)
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

    private fun withCors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-ComposeXPOS-Key")
        response.addHeader("Access-Control-Max-Age", "86400")
        response.addHeader("Access-Control-Allow-Private-Network", "true")
        return response
    }
}
