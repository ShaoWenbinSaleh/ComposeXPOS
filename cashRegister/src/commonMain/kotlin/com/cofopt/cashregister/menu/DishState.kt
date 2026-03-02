package com.cofopt.cashregister.menu

data class DishState(
    val id: String,
    val category: String,
    val nameZh: String,
    val nameEn: String,
    val nameNl: String,
    val nameJa: String = "",
    val nameTr: String = "",
    val priceEur: Double,
    val discountedPrice: Double,
    val soldOut: Boolean,
    val kitchenPrint: Boolean,
    val chooseVegan: Boolean = false,
    val chooseSource: Boolean = false,
    val chooseDrink: Boolean = false,
    val containsEggs: Boolean = false,
    val containsGluten: Boolean = false,
    val containsLupin: Boolean = false,
    val containsMilk: Boolean = false,
    val containsMustard: Boolean = false,
    val containsNuts: Boolean = false,
    val containsPeanuts: Boolean = false,
    val containsCrustaceans: Boolean = false,
    val containsCelery: Boolean = false,
    val containsSesameSeeds: Boolean = false,
    val containsSoybeans: Boolean = false,
    val containsFish: Boolean = false,
    val containsMolluscs: Boolean = false,
    val containsSulphites: Boolean = false,
    val imageBase64: String? = null
) {
    fun effectivePrice(): Double = discountedPrice
}
