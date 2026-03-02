package com.cofopt.cashregister.menu.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dishes")
data class DishEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    @ColumnInfo(name = "name_zh") val nameZh: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "name_nl") val nameNl: String,
    @ColumnInfo(name = "name_ja") val nameJa: String = "",
    @ColumnInfo(name = "name_tr") val nameTr: String = "",
    @ColumnInfo(name = "price_eur") val priceEur: Double,
    @ColumnInfo(name = "discounted_price_eur") val discountedPriceEur: Double,
    @ColumnInfo(name = "sold_out") val soldOut: Boolean,
    @ColumnInfo(name = "kitchen_print") val kitchenPrint: Boolean,
    @ColumnInfo(name = "choose_vegan") val chooseVegan: Boolean,
    @ColumnInfo(name = "choose_source") val chooseSource: Boolean,
    @ColumnInfo(name = "choose_drink") val chooseDrink: Boolean,
    @ColumnInfo(name = "contains_eggs") val containsEggs: Boolean,
    @ColumnInfo(name = "contains_gluten") val containsGluten: Boolean,
    @ColumnInfo(name = "contains_lupin") val containsLupin: Boolean,
    @ColumnInfo(name = "contains_milk") val containsMilk: Boolean,
    @ColumnInfo(name = "contains_mustard") val containsMustard: Boolean,
    @ColumnInfo(name = "contains_nuts") val containsNuts: Boolean,
    @ColumnInfo(name = "contains_peanuts") val containsPeanuts: Boolean,
    @ColumnInfo(name = "contains_crustaceans") val containsCrustaceans: Boolean,
    @ColumnInfo(name = "contains_celery") val containsCelery: Boolean,
    @ColumnInfo(name = "contains_sesame_seeds") val containsSesameSeeds: Boolean,
    @ColumnInfo(name = "contains_soybeans") val containsSoybeans: Boolean,
    @ColumnInfo(name = "contains_fish") val containsFish: Boolean,
    @ColumnInfo(name = "contains_molluscs") val containsMolluscs: Boolean,
    @ColumnInfo(name = "contains_sulphites") val containsSulphites: Boolean,
    @ColumnInfo(name = "image_base64") val imageBase64: String? = null
)
