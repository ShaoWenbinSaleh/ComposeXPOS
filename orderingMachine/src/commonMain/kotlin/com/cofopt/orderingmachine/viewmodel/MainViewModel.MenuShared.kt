package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.EuAllergen
import com.cofopt.orderingmachine.MenuCategory
import com.cofopt.orderingmachine.network.MenuSyncItem

internal fun toAllergensImpl(r: MenuSyncItem): Set<EuAllergen> {
    return buildSet {
        if (r.containsEggs) add(EuAllergen.EGGS)
        if (r.containsGluten) add(EuAllergen.GLUTEN)
        if (r.containsLupin) add(EuAllergen.LUPIN)
        if (r.containsMilk) add(EuAllergen.MILK)
        if (r.containsMustard) add(EuAllergen.MUSTARD)
        if (r.containsNuts) add(EuAllergen.NUTS)
        if (r.containsPeanuts) add(EuAllergen.PEANUTS)
        if (r.containsCrustaceans) add(EuAllergen.CRUSTACEANS)
        if (r.containsCelery) add(EuAllergen.CELERY)
        if (r.containsSesameSeeds) add(EuAllergen.SESAME_SEEDS)
        if (r.containsSoybeans) add(EuAllergen.SOYBEANS)
        if (r.containsFish) add(EuAllergen.FISH)
        if (r.containsMolluscs) add(EuAllergen.MOLLUSCS)
        if (r.containsSulphites) add(EuAllergen.SULPHITES)
    }
}

internal fun toCategoryImpl(raw: String): MenuCategory {
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
