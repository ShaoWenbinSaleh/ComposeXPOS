package com.cofopt.orderingmachine.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.R
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.MenuSyncItem
import com.cofopt.orderingmachine.network.SyncedMenuImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun MainViewModel.loadMenuImpl(context: Context) {
    refreshMenuFromCashRegisterImpl(context)
}

internal fun MainViewModel.refreshMenuFromCashRegisterImpl(context: Context) {
    if (!CashRegisterClient.isConfigured(context)) {
        viewModelScope.launch(Dispatchers.Main) {
            menu = emptyList()
        }
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val remote = CashRegisterClient.getMenu(context) ?: return@launch
            val merged = buildMenuFromRemoteImpl(context, remote)
            if (merged.isEmpty()) return@launch

            launch(Dispatchers.Main) {
                menu = merged
            }
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

private fun MainViewModel.buildMenuFromRemoteImpl(
    context: Context,
    remote: List<MenuSyncItem>
): List<MenuItem> {
    return remote.map { r ->
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
