package com.cofopt.orderingmachine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlin.random.Random

enum class Language {
    EN,
    ZH,
    NL,
    JA,
    TR;

    val localeCode: String
        get() = when (this) {
            EN -> "en"
            ZH -> "zh"
            NL -> "nl"
            JA -> "ja"
            TR -> "tr"
        }

    val selectorShortLabel: String
        get() = when (this) {
            EN -> "EN"
            ZH -> "中"
            NL -> "NL"
            JA -> "日"
            TR -> "TR"
        }

    val selectorLabel: String
        get() = when (this) {
            EN -> "English"
            ZH -> "简体中文"
            NL -> "Nederlands"
            JA -> "日本語"
            TR -> "Türkçe"
        }

    val selectorAssetName: String
        get() = when (this) {
            EN -> "united-kingdom.png"
            ZH -> "china.png"
            NL -> "netherlands.png"
            JA -> "united-kingdom.png"
            TR -> "netherlands.png"
        }

    val menuBannerPath: String
        get() = when (this) {
            EN, ZH, NL -> "images/menu/banner/banner2_${localeCode}.jpg"
            JA, TR -> "images/menu/banner/banner2_en.jpg"
        }

    companion object {
        val default: Language = EN
        val supported: List<Language> = values().toList()
    }
}

/** 导航用的 Screen 标记，只在 Main 中使用 */
enum class Screen {
    MODE_SELECTION,
    ORDERING,
    CHECKOUT,
    PAYMENT_SELECTION,
    PAYMENT_PROCESSING,
    PAYMENT_RESULT,
    ORDER_SUCCESS,
    ORDER_FAILURE,
    DEBUG
}

enum class OrderMode {
    DINE_IN,
    TAKE_AWAY
}

enum class PaymentMethod {
    CARD,
    CASH,
    COUNTER
}

enum class MenuCategory {
    NOODLES,
    DUMPLINGS,
    MAIN_COURSE,
    SIDE_DISH,
    DRINKS,
    BURGERS,
    AYAM_GORENG_NUGGETS,
    BUBUR_NASI_LEMAK
}

enum class EuAllergen {
    EGGS,
    GLUTEN,
    LUPIN,
    MILK,
    MUSTARD,
    NUTS,
    PEANUTS,
    CRUSTACEANS,
    CELERY,
    SESAME_SEEDS,
    SOYBEANS,
    FISH,
    MOLLUSCS,
    SULPHITES
}

data class CustomizationChoice(
    val id: String,
    val nameEn: String,
    val nameZh: String,
    val nameNl: String
)

data class CustomizationOption(
    val id: String,
    val nameEn: String,
    val nameZh: String,
    val nameNl: String,
    val required: Boolean,
    val choices: List<CustomizationChoice>
)

data class MenuItem(
    val id: String,
    val nameEn: String,
    val nameZh: String,
    val nameNl: String,
    val nameJa: String = "",
    val nameTr: String = "",
    val descriptionEn: String,
    val descriptionZh: String,
    val descriptionNl: String,
    val priceEur: Double = 0.0,
    val discountedPrice: Double = 0.0,
    val soldOut: Boolean = false,
    val price: Double,
    val imageRes: Int = 0,
    val imagePath: String? = null, // Asset path, e.g. "images/food_1.png"
    val category: MenuCategory,
    val allergens: Set<EuAllergen> = emptySet(),
    val customizations: List<CustomizationOption> = emptyList(),
    val chooseVegan: Boolean = false,
    val chooseSource: Boolean = false,
    val chooseDrink: Boolean = false
)

data class CartItem(
    val uuid: String = "${Random.nextLong()}-${Random.nextLong()}",
    val menuItem: MenuItem,
    val quantity: Int,
    val customizations: Map<String, String> // OptionID -> ChoiceID
)

fun formatEuroAmount(amount: Double): String {
    val scaled = (amount * 100.0).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absScaled = scaled.absoluteValue
    val whole = absScaled / 100
    val cents = (absScaled % 100).toString().padStart(2, '0')
    return "$sign$whole.$cents"
}

fun tr(
    lang: Language,
    en: String,
    zh: String,
    nl: String,
    ja: String = en,
    tr: String = en
): String = when (lang) {
    Language.EN -> en
    Language.ZH -> zh
    Language.NL -> nl
    Language.JA -> ja
    Language.TR -> tr
}

fun MenuItem.localizedName(language: Language): String =
    tr(language, nameEn, nameZh, nameNl, nameJa, nameTr).ifBlank { nameEn }

fun MenuItem.localizedDescription(language: Language): String =
    tr(language, descriptionEn, descriptionZh, descriptionNl).ifBlank { descriptionEn }

fun CustomizationChoice.localizedName(language: Language): String =
    tr(language, nameEn, nameZh, nameNl).ifBlank { nameEn }

fun CustomizationOption.localizedName(language: Language): String =
    tr(language, nameEn, nameZh, nameNl).ifBlank { nameEn }

fun EuAllergen.localizedName(language: Language): String = when (this) {
    EuAllergen.EGGS -> tr(language, "Eggs", "鸡蛋", "Eieren")
    EuAllergen.GLUTEN -> tr(language, "Gluten", "麸质", "Gluten")
    EuAllergen.LUPIN -> tr(language, "Lupin", "羽扇豆", "Lupine")
    EuAllergen.MILK -> tr(language, "Milk", "牛奶", "Melk")
    EuAllergen.MUSTARD -> tr(language, "Mustard", "芥末", "Mosterd")
    EuAllergen.NUTS -> tr(language, "Nuts", "坚果", "Noten")
    EuAllergen.PEANUTS -> tr(language, "Peanuts", "花生", "Pinda's")
    EuAllergen.CRUSTACEANS -> tr(language, "Crustaceans", "甲壳类", "Schaaldieren")
    EuAllergen.CELERY -> tr(language, "Celery", "芹菜", "Selderij")
    EuAllergen.SESAME_SEEDS -> tr(language, "Sesame", "芝麻", "Sesam")
    EuAllergen.SOYBEANS -> tr(language, "Soybeans", "大豆", "Sojabonen")
    EuAllergen.FISH -> tr(language, "Fish", "鱼", "Vis")
    EuAllergen.MOLLUSCS -> tr(language, "Molluscs", "软体动物", "Weekdieren")
    EuAllergen.SULPHITES -> tr(language, "Sulphites", "亚硫酸盐", "Sulfieten")
}

@Composable
fun LanguageSelector(language: Language, onLanguageChange: (Language) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Language.supported.forEach { option ->
            TextButton(onClick = { onLanguageChange(option) }) {
                Text(
                    text = option.selectorShortLabel,
                    fontWeight = if (language == option) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
