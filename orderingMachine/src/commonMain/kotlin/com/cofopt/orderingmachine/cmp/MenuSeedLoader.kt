package com.cofopt.orderingmachine.cmp

import com.cofopt.orderingmachine.EuAllergen
import com.cofopt.orderingmachine.MenuCategory
import com.cofopt.orderingmachine.MenuItem

internal suspend fun loadSeedMenu(): List<MenuItem> {
    val csv = EMBEDDED_MENU_CSV
    if (csv.isBlank()) return emptyList()
    return parseMenuCsv(csv)
}

private fun parseMenuCsv(csv: String): List<MenuItem> {
    val lines = csv
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .filter { it.isNotBlank() }

    if (lines.isEmpty()) return emptyList()

    val header = splitCsvLine(lines.first())
    val index = header.withIndex().associate { it.value.trim() to it.index }
    fun idx(name: String): Int = index[name] ?: -1
    fun at(parts: List<String>, i: Int): String = if (i < 0) "" else parts.getOrElse(i) { "" }.trim()
    fun parseBool(raw: String): Boolean = raw.equals("true", ignoreCase = true)

    val iCategory = idx("category")
    val iId = idx("id")
    val iZh = idx("zh")
    val iEn = idx("en")
    val iNl = idx("nl")
    val iJa = idx("ja")
    val iTr = idx("tr")
    val iPrice = idx("price_eur")
    val iChooseVegan = idx("chooseVegan")
    val iChooseSource = idx("chooseSource")
    val iChooseDrink = idx("chooseDrink")

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

    return lines
        .drop(1)
        .mapNotNull { line ->
            val parts = splitCsvLine(line)
            val id = at(parts, iId)
            if (id.isBlank()) return@mapNotNull null
            val price = at(parts, iPrice).toDoubleOrNull() ?: return@mapNotNull null

            MenuItem(
                id = id,
                category = toCategory(at(parts, iCategory)),
                nameZh = at(parts, iZh),
                nameEn = at(parts, iEn),
                nameNl = at(parts, iNl),
                nameJa = at(parts, iJa).ifBlank { at(parts, iEn) },
                nameTr = at(parts, iTr).ifBlank { at(parts, iEn) },
                descriptionEn = "",
                descriptionZh = "",
                descriptionNl = "",
                priceEur = price,
                discountedPrice = price,
                soldOut = false,
                chooseVegan = parseBool(at(parts, iChooseVegan)),
                chooseSource = parseBool(at(parts, iChooseSource)),
                chooseDrink = parseBool(at(parts, iChooseDrink)),
                price = price,
                imagePath = "images/menu/${id}.jpg",
                allergens = toAllergens(
                    eggs = parseBool(at(parts, iEggs)),
                    gluten = parseBool(at(parts, iGluten)),
                    lupin = parseBool(at(parts, iLupin)),
                    milk = parseBool(at(parts, iMilk)),
                    mustard = parseBool(at(parts, iMustard)),
                    nuts = parseBool(at(parts, iNuts)),
                    peanuts = parseBool(at(parts, iPeanuts)),
                    crustaceans = parseBool(at(parts, iCrustaceans)),
                    celery = parseBool(at(parts, iCelery)),
                    sesameSeeds = parseBool(at(parts, iSesameSeeds)),
                    soybeans = parseBool(at(parts, iSoybeans)),
                    fish = parseBool(at(parts, iFish)),
                    molluscs = parseBool(at(parts, iMolluscs)),
                    sulphites = parseBool(at(parts, iSulphites))
                ),
                customizations = emptyList()
            )
        }
}

private fun toCategory(raw: String): MenuCategory {
    return when (raw.trim().lowercase()) {
        "noodles" -> MenuCategory.NOODLES
        "dumplings" -> MenuCategory.DUMPLINGS
        "main course" -> MenuCategory.MAIN_COURSE
        "side dish" -> MenuCategory.SIDE_DISH
        "drinks" -> MenuCategory.DRINKS
        "burgers" -> MenuCategory.BURGERS
        "ayam goreng & nuggets" -> MenuCategory.AYAM_GORENG_NUGGETS
        "bubur & nasi lemak" -> MenuCategory.BUBUR_NASI_LEMAK
        else -> MenuCategory.MAIN_COURSE
    }
}

private fun toAllergens(
    eggs: Boolean,
    gluten: Boolean,
    lupin: Boolean,
    milk: Boolean,
    mustard: Boolean,
    nuts: Boolean,
    peanuts: Boolean,
    crustaceans: Boolean,
    celery: Boolean,
    sesameSeeds: Boolean,
    soybeans: Boolean,
    fish: Boolean,
    molluscs: Boolean,
    sulphites: Boolean
): Set<EuAllergen> {
    return buildSet {
        if (eggs) add(EuAllergen.EGGS)
        if (gluten) add(EuAllergen.GLUTEN)
        if (lupin) add(EuAllergen.LUPIN)
        if (milk) add(EuAllergen.MILK)
        if (mustard) add(EuAllergen.MUSTARD)
        if (nuts) add(EuAllergen.NUTS)
        if (peanuts) add(EuAllergen.PEANUTS)
        if (crustaceans) add(EuAllergen.CRUSTACEANS)
        if (celery) add(EuAllergen.CELERY)
        if (sesameSeeds) add(EuAllergen.SESAME_SEEDS)
        if (soybeans) add(EuAllergen.SOYBEANS)
        if (fish) add(EuAllergen.FISH)
        if (molluscs) add(EuAllergen.MOLLUSCS)
        if (sulphites) add(EuAllergen.SULPHITES)
    }
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
