package com.cofopt.cashregister.cmp.debug

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.cofopt.cashregister.CallingMachineTab
import com.cofopt.cashregister.CashRegisterDebugConfig
import com.cofopt.cashregister.OrderingMachinesTab
import com.cofopt.cashregister.menu.DishesRepository
import com.cofopt.cashregister.menu.MenuCsvParser
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.network.wecr.CardPaymentOutcome
import com.cofopt.cashregister.network.wecr.WecrCardPayment
import com.cofopt.cashregister.printer.PrintUtils
import com.cofopt.cashregister.utils.AlertSoundOption
import com.cofopt.cashregister.utils.AlertSoundPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object DebugPlatformActions {
    @Composable
    actual fun rememberPaymentActions(): PaymentDebugActions {
        val context = LocalContext.current.applicationContext
        return remember(context) { AndroidPaymentDebugActions(context) }
    }

    @Composable
    actual fun rememberPrinterActions(): PrinterDebugActions {
        val context = LocalContext.current.applicationContext
        return remember(context) { AndroidPrinterDebugActions(context) }
    }

    @Composable
    actual fun CallingMachineTabContent() {
        CallingMachineTab()
    }

    @Composable
    actual fun OrderingMachinesTabContent() {
        OrderingMachinesTab()
    }
}

private class AndroidPaymentDebugActions(
    private val context: Context
) : PaymentDebugActions {
    override val alertSoundOptions: List<String> = AlertSoundOption.entries.map { it.id }

    override fun loadAlertSoundId(): String = CashRegisterDebugConfig.alertSoundId(context)

    override fun saveAlertSoundId(id: String) {
        CashRegisterDebugConfig.saveAlertSoundId(context, id)
    }

    override fun loadForceTestAmount(): Boolean = CashRegisterDebugConfig.wecrForceTestAmount(context)

    override fun saveForceTestAmount(enabled: Boolean) {
        CashRegisterDebugConfig.saveWecrForceTestAmount(context, enabled)
    }

    override suspend fun resetMenuPricesToDefault(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val seed = MenuCsvParser.parseDishes(context)
            if (seed.isEmpty()) error("CSV file is empty")

            seed.forEach { dish ->
                DishesRepository.updatePriceEur(dish.id, dish.priceEur)
                DishesRepository.updateDiscountedPrice(dish.id, dish.discountedPriceEur)
            }
            seed.size
        }
    }

    override suspend fun runMockPayment(amount: Double): String {
        val result = WecrCardPayment.pay(context = context, amount = amount)
        return when (result) {
            is CardPaymentOutcome.Success -> "OK: MOCK accepted ref=${result.transactionRef} status=${result.status.status}"
            is CardPaymentOutcome.Failed -> "ERROR: ${result.message}"
            is CardPaymentOutcome.RequestFailed -> "ERROR: ${result.message}"
            CardPaymentOutcome.PosTriggerFailed -> "ERROR: Pos trigger failed"
            CardPaymentOutcome.Timeout -> "ERROR: Timeout"
            CardPaymentOutcome.Cancelled -> "ERROR: Cancelled"
        }
    }

    override fun playAlertSound() {
        AlertSoundPlayer.play(context)
    }
}

private class AndroidPrinterDebugActions(
    private val context: Context
) : PrinterDebugActions {
    override suspend fun print(kind: DebugPrinterKind, order: OrderPayload, callNumber: String?): Boolean {
        return withContext(Dispatchers.IO) {
            when (kind) {
                DebugPrinterKind.ORDER -> PrintUtils.printOrder(context, order, callNumber)
                DebugPrinterKind.RECEIPT -> PrintUtils.printReceipt(context, order, callNumber)
                DebugPrinterKind.KITCHEN -> PrintUtils.printKitchen(context, order, callNumber)
            }
        }
    }
}
