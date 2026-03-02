package com.cofopt.cashregister.menu

import android.content.Context
import android.util.Log
import com.cofopt.cashregister.menu.room.CashRegisterDatabase
import com.cofopt.cashregister.menu.room.DishDao
import com.cofopt.cashregister.menu.room.DishEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object DishesRepository {
    private const val MENU_PREFS_NAME = "menu_seed_state"
    private const val KEY_MENU_SEED_VERSION = "menu_seed_version"
    private const val MENU_SEED_VERSION = 4

    private val _dishes = MutableStateFlow<List<DishState>>(emptyList())
    val dishes: StateFlow<List<DishState>> = _dishes

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var initialized = false
    private lateinit var dishDao: DishDao
    private lateinit var appContext: Context

    @Volatile private var loaded: Boolean = false

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return

            if (!initialized) {
                appContext = context.applicationContext
                val db = CashRegisterDatabase.getInstance(appContext)
                dishDao = db.dishDao()
                initialized = true
            }
        }

        val seed = MenuCsvParser.parseDishes(context)
        if (seed.isEmpty()) return

        try {
            normalizeLegacyIds(context)
        } catch (_: Exception) {
            // ignore
        }

        // When seed version changes, hard-reset menu rows to the new default seed.
        // This is needed when menu ids are reused but names/categories/images are replaced.
        scope.launch {
            val prefs = this@DishesRepository.appContext.getSharedPreferences(MENU_PREFS_NAME, Context.MODE_PRIVATE)
            val storedVersion = prefs.getInt(KEY_MENU_SEED_VERSION, 0)
            val needsSeedReset = storedVersion < MENU_SEED_VERSION

            if (needsSeedReset) {
                dishDao.clearAll()
                dishDao.insertAll(seed)
                prefs.edit().putInt(KEY_MENU_SEED_VERSION, MENU_SEED_VERSION).apply()
                Log.i("DishesRepository", "Applied menu seed reset to version=$MENU_SEED_VERSION")
            } else {
                dishDao.insertAllIgnore(seed)
            }
        }

        scope.launch {
            dishDao.observeAll().collectLatest { entities ->
                _dishes.value = entities.map { e ->
                    DishState(
                        id = e.id,
                        category = e.category,
                        nameZh = e.nameZh,
                        nameEn = e.nameEn,
                        nameNl = e.nameNl,
                        nameJa = e.nameJa,
                        nameTr = e.nameTr,
                        priceEur = e.priceEur,
                        discountedPrice = e.discountedPriceEur,
                        soldOut = e.soldOut,
                        kitchenPrint = e.kitchenPrint,
                        chooseVegan = e.chooseVegan,
                        chooseSource = e.chooseSource,
                        chooseDrink = e.chooseDrink,
                        containsEggs = e.containsEggs,
                        containsGluten = e.containsGluten,
                        containsLupin = e.containsLupin,
                        containsMilk = e.containsMilk,
                        containsMustard = e.containsMustard,
                        containsNuts = e.containsNuts,
                        containsPeanuts = e.containsPeanuts,
                        containsCrustaceans = e.containsCrustaceans,
                        containsCelery = e.containsCelery,
                        containsSesameSeeds = e.containsSesameSeeds,
                        containsSoybeans = e.containsSoybeans,
                        containsFish = e.containsFish,
                        containsMolluscs = e.containsMolluscs,
                        containsSulphites = e.containsSulphites,
                        imageBase64 = e.imageBase64
                    )
                }
            }
        }

        loaded = true
    }

    private suspend fun normalizeLegacyIds(context: Context) {
        val seed = MenuCsvParser.parseDishes(context)
        if (seed.isEmpty()) return

        val seedById = seed.associateBy { it.id }
        val currentIds = try {
            dishDao.getAllIds().toHashSet()
        } catch (_: Exception) {
            return
        }

        // Legacy builds encoded string ids like "d01" into a negative Int key
        // -(prefix*100000 + number). When we migrate INTEGER -> TEXT, these become strings
        // like "-400001". Normalize them back to the real CSV ids to avoid duplicates.
        val legacyToReal = buildMap {
            for ((realId, entity) in seedById) {
                val legacyId = legacyEncodedIdFor(realId) ?: continue
                put(legacyId, entity.id)
            }
        }

        for ((legacyId, realId) in legacyToReal) {
            if (!currentIds.contains(legacyId)) continue
            if (legacyId == realId) continue

            val legacyEditable = dishDao.getEditableFields(legacyId) ?: continue
            val realEditable = dishDao.getEditableFields(realId)
            val defaults = seedById[realId]?.let {
                com.cofopt.cashregister.menu.room.DishEditableFields(
                    priceEur = it.priceEur,
                    discountedPriceEur = it.discountedPriceEur,
                    soldOut = it.soldOut
                )
            }

            if (realEditable == null) {
                // No real row yet -> rename legacy id to the real id.
                dishDao.renameId(oldId = legacyId, newId = realId)
                continue
            }

            // Both exist: prefer keeping the real id row. If it looks unchanged from seed defaults
            // but legacy has edits, copy legacy edits over, then delete legacy.
            val realLooksDefault = defaults != null && realEditable == defaults
            val legacyLooksDefault = defaults != null && legacyEditable == defaults

            if (realLooksDefault && !legacyLooksDefault) {
                dishDao.updateEditableFields(
                    id = realId,
                    priceEur = legacyEditable.priceEur,
                    discountedPriceEur = legacyEditable.discountedPriceEur,
                    soldOut = legacyEditable.soldOut
                )
            }

            dishDao.deleteById(legacyId)
        }
    }

    private fun legacyEncodedIdFor(rawId: String): String? {
        val trimmed = rawId.trim()
        val match = Regex("^([A-Za-z]+)(\\d+)$").matchEntire(trimmed) ?: return null
        val letters = match.groupValues[1].lowercase()
        val number = match.groupValues[2].toIntOrNull() ?: return null
        val prefix = letters.fold(0) { acc, c -> acc * 26 + (c - 'a' + 1) }
        return (-(prefix * 100_000 + number)).toString()
    }

    fun reloadDishes(context: Context) {
        if (!initialized) return
        scope.launch {
            try {
                val seed = MenuCsvParser.parseDishes(context)
                if (seed.isNotEmpty()) {
                    // Reset all prices to CSV defaults
                    seed.forEach { dishEntity ->
                        updatePriceEur(dishEntity.id, dishEntity.priceEur)
                        updateDiscountedPrice(dishEntity.id, dishEntity.discountedPriceEur)
                    }
                    android.util.Log.d("DishesRepository", "Reset ${seed.size} dish prices from CSV")
                }
            } catch (e: Exception) {
                android.util.Log.e("DishesRepository", "Failed to reload dishes", e)
            }
        }
    }

    fun updatePriceEur(id: String, priceEur: Double) {
        if (!initialized) return
        scope.launch {
            try {
                val rows = dishDao.updatePriceEur(id = id, priceEur = priceEur)
                if (rows > 0) {
                    _dishes.update { list -> list.map { if (it.id == id) it.copy(priceEur = priceEur) else it } }
                } else {
                    Log.e("DishesRepository", "updatePriceEur affected 0 rows for id=$id")
                }
            } catch (e: Exception) {
                Log.e("DishesRepository", "updatePriceEur failed for id=$id", e)
            }
        }
    }

    fun updateDiscountedPrice(id: String, discountedPrice: Double) {
        if (!initialized) return
        scope.launch {
            try {
                val rows = dishDao.updateDiscountedPriceEur(id = id, discountedPriceEur = discountedPrice)
                if (rows > 0) {
                    _dishes.update { list -> list.map { if (it.id == id) it.copy(discountedPrice = discountedPrice) else it } }
                } else {
                    Log.e("DishesRepository", "updateDiscountedPrice affected 0 rows for id=$id")
                }
            } catch (e: Exception) {
                Log.e("DishesRepository", "updateDiscountedPrice failed for id=$id", e)
            }
        }
    }

    fun updateSoldOut(id: String, soldOut: Boolean) {
        if (!initialized) return
        scope.launch {
            try {
                val rows = dishDao.updateSoldOut(id = id, soldOut = soldOut)
                if (rows > 0) {
                    _dishes.update { list -> list.map { if (it.id == id) it.copy(soldOut = soldOut) else it } }
                } else {
                    Log.e("DishesRepository", "updateSoldOut affected 0 rows for id=$id")
                }
            } catch (e: Exception) {
                Log.e("DishesRepository", "updateSoldOut failed for id=$id", e)
            }
        }
    }

    fun upsertDish(context: Context, dish: DishState) {
        scope.launch {
            ensureLoaded(context)
            upsertDish(dish)
        }
    }

    fun upsertDish(dish: DishState) {
        if (!initialized) return
        scope.launch {
            runCatching {
                dishDao.upsert(dish.toEntity())
            }.onFailure { e ->
                Log.e("DishesRepository", "upsertDish failed id=${dish.id}", e)
            }
        }
    }

    fun deleteDish(context: Context, id: String) {
        scope.launch {
            ensureLoaded(context)
            deleteDish(id)
        }
    }

    fun deleteDish(id: String) {
        if (!initialized) return
        scope.launch {
            runCatching {
                dishDao.deleteById(id)
            }.onFailure { e ->
                Log.e("DishesRepository", "deleteDish failed id=$id", e)
            }
        }
    }

    fun setDishImageBase64(context: Context, id: String, imageBase64: String?) {
        scope.launch {
            ensureLoaded(context)
            setDishImageBase64(id, imageBase64)
        }
    }

    fun setDishImageBase64(id: String, imageBase64: String?) {
        if (!initialized) return
        scope.launch {
            val existing = runCatching { dishDao.findById(id) }.getOrNull()
            if (existing == null) {
                Log.w("DishesRepository", "setDishImageBase64: dish not found id=$id")
                return@launch
            }
            val updated = existing.copy(imageBase64 = imageBase64)
            runCatching {
                dishDao.upsert(updated)
            }.onFailure { e ->
                Log.e("DishesRepository", "setDishImageBase64 failed id=$id", e)
            }
        }
    }

    private fun DishState.toEntity(): DishEntity {
        return DishEntity(
            id = id,
            category = category,
            nameZh = nameZh,
            nameEn = nameEn,
            nameNl = nameNl,
            nameJa = nameJa,
            nameTr = nameTr,
            priceEur = priceEur,
            discountedPriceEur = discountedPrice,
            soldOut = soldOut,
            kitchenPrint = kitchenPrint,
            chooseVegan = chooseVegan,
            chooseSource = chooseSource,
            chooseDrink = chooseDrink,
            containsEggs = containsEggs,
            containsGluten = containsGluten,
            containsLupin = containsLupin,
            containsMilk = containsMilk,
            containsMustard = containsMustard,
            containsNuts = containsNuts,
            containsPeanuts = containsPeanuts,
            containsCrustaceans = containsCrustaceans,
            containsCelery = containsCelery,
            containsSesameSeeds = containsSesameSeeds,
            containsSoybeans = containsSoybeans,
            containsFish = containsFish,
            containsMolluscs = containsMolluscs,
            containsSulphites = containsSulphites,
            imageBase64 = imageBase64
        )
    }
}
