package com.cofopt.callingmachine

enum class CallingLanguage(
    val wireValue: String,
    val preparingLabel: String,
    val readyLabel: String
) {
    EN("en", "Preparing", "Ready"),
    ZH("zh", "备餐中", "可取餐"),
    NL("nl", "Voorbereiden", "Klaar"),
    JA("ja", "調理中", "お受け取り"),
    TR("tr", "Hazirlaniyor", "Hazir");

    companion object {
        private val byWireValue: Map<String, CallingLanguage> = values().associateBy { it.wireValue }

        fun fromWireValue(raw: String?): CallingLanguage? {
            val normalized = raw.orEmpty().trim().lowercase()
            if (normalized.isBlank()) return null
            return byWireValue[normalized]
        }
    }
}
