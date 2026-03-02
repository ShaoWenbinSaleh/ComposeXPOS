package com.cofopt.orderingmachine.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DishesSyncItem(
    val id: String,
    @SerialName("price_eur") val priceEur: Double,
    @SerialName("discounted_price") val discountedPrice: Double,
    @SerialName("sold_out") val soldOut: Boolean
)

@Serializable
data class MenuSyncItem(
    val id: String,
    val category: String,
    @SerialName("name_zh") val nameZh: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_nl") val nameNl: String,
    @SerialName("name_ja") val nameJa: String = "",
    @SerialName("name_tr") val nameTr: String = "",
    @SerialName("price_eur") val priceEur: Double,
    @SerialName("discounted_price") val discountedPrice: Double,
    @SerialName("sold_out") val soldOut: Boolean,
    @SerialName("kitchen_print") val kitchenPrint: Boolean = false,
    @SerialName("choose_vegan") val chooseVegan: Boolean = false,
    @SerialName("choose_source") val chooseSource: Boolean = false,
    @SerialName("choose_drink") val chooseDrink: Boolean = false,
    @SerialName("contains_eggs") val containsEggs: Boolean = false,
    @SerialName("contains_gluten") val containsGluten: Boolean = false,
    @SerialName("contains_lupin") val containsLupin: Boolean = false,
    @SerialName("contains_milk") val containsMilk: Boolean = false,
    @SerialName("contains_mustard") val containsMustard: Boolean = false,
    @SerialName("contains_nuts") val containsNuts: Boolean = false,
    @SerialName("contains_peanuts") val containsPeanuts: Boolean = false,
    @SerialName("contains_crustaceans") val containsCrustaceans: Boolean = false,
    @SerialName("contains_celery") val containsCelery: Boolean = false,
    @SerialName("contains_sesame_seeds") val containsSesameSeeds: Boolean = false,
    @SerialName("contains_soybeans") val containsSoybeans: Boolean = false,
    @SerialName("contains_fish") val containsFish: Boolean = false,
    @SerialName("contains_molluscs") val containsMolluscs: Boolean = false,
    @SerialName("contains_sulphites") val containsSulphites: Boolean = false,
    @SerialName("image_base64") val imageBase64: String? = null
)
