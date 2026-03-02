package com.cofopt.cashregister.menu

import android.content.Context
import com.cofopt.cashregister.menu.room.DishEntity

internal object MenuCsvParser {

    fun parseDishes(context: Context): List<DishEntity> {
        val fileName = "menu.csv"
        val lines = context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readLines() }
        if (lines.isEmpty()) return emptyList()

        val header = splitCsvLine(lines.first())
        val index = header.withIndex().associate { it.value.trim() to it.index }

        fun idx(name: String): Int = index[name]
            ?: error("menu.csv missing required column '$name'")

        val iCategory = idx("category")
        val iId = idx("id")
        val iZh = idx("zh")
        val iEn = idx("en")
        val iNl = idx("nl")
        val iJa = index["ja"]
        val iTr = index["tr"]
        val iPrice = idx("price_eur")
        val iKitchenPrint = idx("kitchen_print")
        val iChooseVegan = index["chooseVegan"]
        val iChooseSource = index["chooseSource"]
        val iChooseDrink = index["chooseDrink"]

        val iEggs = idx("containsEggs")
        val iGluten = idx("containsGluten")
        val iLupin = idx("containsLupin")
        val iMilk = idx("containsMilk")
        val iMustard = idx("containsMustard")
        val iNuts = idx("containsNuts")
        val iPeanuts = idx("containsPeanuts")
        val iCrustaceans = idx("containsCrustaceans")
        val iCelery = idx("containsCelery")
        val iSesameSeeds = idx("containsSesameSeeds")
        val iSoybeans = idx("containsSoybeans")
        val iFish = idx("containsFish")
        val iMolluscs = idx("containsMolluscs")
        val iSulphites = idx("containsSulphites")

        fun at(parts: List<String>, i: Int): String = parts.getOrElse(i) { "" }.trim()

        return lines
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = splitCsvLine(line)

                val idRaw = at(parts, iId)
                val id = idRaw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null

                val price = at(parts, iPrice).toDoubleOrNull() ?: return@mapNotNull null

                DishEntity(
                    id = id,
                    category = at(parts, iCategory),
                    nameZh = at(parts, iZh),
                    nameEn = at(parts, iEn),
                    nameNl = at(parts, iNl),
                    nameJa = iJa?.let { at(parts, it) }?.ifBlank { at(parts, iEn) } ?: at(parts, iEn),
                    nameTr = iTr?.let { at(parts, it) }?.ifBlank { at(parts, iEn) } ?: at(parts, iEn),
                    priceEur = price,
                    discountedPriceEur = price,
                    soldOut = false,
                    kitchenPrint = parseBool(at(parts, iKitchenPrint)),
                    chooseVegan = iChooseVegan?.let { parseBool(at(parts, it)) } ?: false,
                    chooseSource = iChooseSource?.let { parseBool(at(parts, it)) } ?: false,
                    chooseDrink = iChooseDrink?.let { parseBool(at(parts, it)) } ?: false,
                    containsEggs = parseBool(at(parts, iEggs)),
                    containsGluten = parseBool(at(parts, iGluten)),
                    containsLupin = parseBool(at(parts, iLupin)),
                    containsMilk = parseBool(at(parts, iMilk)),
                    containsMustard = parseBool(at(parts, iMustard)),
                    containsNuts = parseBool(at(parts, iNuts)),
                    containsPeanuts = parseBool(at(parts, iPeanuts)),
                    containsCrustaceans = parseBool(at(parts, iCrustaceans)),
                    containsCelery = parseBool(at(parts, iCelery)),
                    containsSesameSeeds = parseBool(at(parts, iSesameSeeds)),
                    containsSoybeans = parseBool(at(parts, iSoybeans)),
                    containsFish = parseBool(at(parts, iFish)),
                    containsMolluscs = parseBool(at(parts, iMolluscs)),
                    containsSulphites = parseBool(at(parts, iSulphites)),
                    imageBase64 = null
                )
            }
    }

    private fun parseBool(raw: String): Boolean {
        return raw.equals("true", ignoreCase = true)
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }

                (c == ',' || c == ';') && !inQuotes -> {
                    out.add(sb.toString())
                    sb.setLength(0)
                }

                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
