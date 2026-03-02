package com.cofopt.callingmachine

data class CallingAnnouncement(
    val localeTag: String,
    val text: String,
    val rate: Float
)

fun readyAnnouncement(number: Int, language: CallingLanguage): CallingAnnouncement {
    return when (language) {
        CallingLanguage.EN -> CallingAnnouncement(
            localeTag = "en",
            text = "Number $number, please pick up your order.",
            rate = 0.95f
        )
        CallingLanguage.ZH -> CallingAnnouncement(
            localeTag = "zh-CN",
            text = "${number}号 请取餐",
            rate = 0.8f
        )
        CallingLanguage.NL -> CallingAnnouncement(
            localeTag = "nl-NL",
            text = "Nummer $number, uw bestelling staat klaar.",
            rate = 0.95f
        )
        CallingLanguage.JA -> CallingAnnouncement(
            localeTag = "ja-JP",
            text = "${number}番のお客様、お受け取りをお願いします。",
            rate = 0.95f
        )
        CallingLanguage.TR -> CallingAnnouncement(
            localeTag = "tr-TR",
            text = "$number numara, siparisiniz hazir.",
            rate = 0.95f
        )
    }
}
