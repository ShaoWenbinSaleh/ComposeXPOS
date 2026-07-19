package com.cofopt.orderingmachine.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.R
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.CashRegisterConfig
import com.cofopt.orderingmachine.network.MenuSyncItem
import com.cofopt.orderingmachine.network.SyncedMenuImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun MainViewModel.loadMenuImpl(context: Context) {
    refreshMenuFromCashRegisterImpl(context)
}

private fun cashRegisterTargetKey(context: Context): String? =
    CashRegisterConfig.endpoint(context)?.let { endpoint ->
        "${endpoint.host.trim().lowercase()}|${endpoint.port}"
    }

internal fun MainViewModel.refreshMenuFromCashRegisterImpl(context: Context) {
    val targetKey = cashRegisterTargetKey(context)
    if (targetKey == null) {
        menuRefreshJob?.cancel()
        menuRefreshJob = null
        menuRefreshGeneration++
        menuRefreshTarget = null
        viewModelScope.launch(Dispatchers.Main) {
            menu = emptyList()
        }
        return
    }
    if (menuRefreshJob?.isActive == true && menuRefreshTarget == targetKey) return

    menuRefreshJob?.cancel()
    val generation = ++menuRefreshGeneration
    menuRefreshTarget = targetKey

    val appContext = context.applicationContext
    menuRefreshJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            val remote = CashRegisterClient.getMenu(appContext) ?: return@launch
            val currentTargetBeforeMerge = cashRegisterTargetKey(appContext)
            if (generation != menuRefreshGeneration || currentTargetBeforeMerge != targetKey) {
                return@launch
            }
            val merged = menuImageSyncMutex.withLock {
                currentCoroutineContext().ensureActive()
                val targetBeforeImages = cashRegisterTargetKey(appContext)
                if (generation != menuRefreshGeneration || targetBeforeImages != targetKey) {
                    throw CancellationException("cash_register_target_changed")
                }
                buildMenuFromRemoteImpl(appContext, remote, generation, targetKey)
            }

            withContext(Dispatchers.Main) {
                val currentTarget = cashRegisterTargetKey(appContext)
                if (generation == menuRefreshGeneration && currentTarget == targetKey) {
                    menu = merged
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
}

internal fun MainViewModel.syncPricesOnHomeScreenImpl() {
    val context = getApplication<android.app.Application>().applicationContext
    refreshMenuFromCashRegisterImpl(context)
}

internal fun MainViewModel.syncDishesFromCashRegisterImpl(context: Context) {
    refreshMenuFromCashRegisterImpl(context)
}

private suspend fun MainViewModel.buildMenuFromRemoteImpl(
    context: Context,
    remote: List<MenuSyncItem>,
    generation: Int,
    targetKey: String,
): List<MenuItem> {
    return remote.map { r ->
        currentCoroutineContext().ensureActive()
        val currentTarget = cashRegisterTargetKey(context)
        if (generation != menuRefreshGeneration || currentTarget != targetKey) {
            throw CancellationException("cash_register_target_changed")
        }
        val syncedImagePath = SyncedMenuImageStore.saveBase64(context, r.id, r.imageBase64)
        val imagePath = syncedImagePath?.let { "$it?v=${r.imageBase64?.hashCode() ?: 0}" }
            ?: "images/menu/${r.id}.jpg"

        MenuItem(
            id = r.id,
            nameEn = r.nameEn,
            nameZh = r.nameZh,
            nameNl = r.nameNl,
            nameJa = r.nameJa,
            nameTr = r.nameTr,
            descriptionEn = "",
            descriptionZh = "",
            descriptionNl = "",
            priceEur = r.priceEur,
            discountedPrice = r.discountedPrice,
            soldOut = r.soldOut,
            price = r.discountedPrice,
            imageRes = R.drawable.logo,
            imagePath = imagePath,
            category = toCategoryImpl(r.category),
            allergens = toAllergensImpl(r),
            customizations = emptyList(),
            chooseVegan = r.chooseVegan,
            chooseSource = r.chooseSource,
            chooseDrink = r.chooseDrink
        )
    }
}
