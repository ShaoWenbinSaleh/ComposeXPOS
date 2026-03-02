package com.cofopt.cashregister.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.cofopt.cashregister.menu.DishState

enum class Language {
    EN,
    ZH,
    NL,
    JA,
    TR;

    val selectorFlagEmoji: String
        get() = when (this) {
            EN -> "🇺🇸"
            ZH -> "🇨🇳"
            NL -> "🇳🇱"
            JA -> "🇯🇵"
            TR -> "🇹🇷"
        }

    companion object {
        val default: Language = EN
        val supported: List<Language> = values().toList()

        fun fromLocaleTag(tag: String?): Language {
            val normalized = tag.orEmpty().trim().lowercase()
            if (normalized.isEmpty()) return default
            return when {
                normalized == "zh" || normalized.startsWith("zh-") -> ZH
                normalized == "nl" || normalized.startsWith("nl-") -> NL
                normalized == "ja" || normalized.startsWith("ja-") -> JA
                normalized == "tr" || normalized.startsWith("tr-") -> TR
                else -> default
            }
        }
    }
}

val LocalLanguage = staticCompositionLocalOf { Language.default }

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

@Composable
fun tr(
    en: String,
    zh: String,
    nl: String,
    ja: String = en,
    tr: String = en
): String {
    return tr(LocalLanguage.current, en, zh, nl, ja, tr)
}

fun DishState.localizedName(language: Language): String {
    return tr(language, nameEn, nameZh, nameNl, nameJa, nameTr).ifBlank { nameEn }
}
