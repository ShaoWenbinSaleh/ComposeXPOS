package com.cofopt.cashregister.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cofopt.cashregister.cmp.screens.CallingScreen
import com.cofopt.cashregister.cmp.screens.CheckoutScreen
import com.cofopt.cashregister.cmp.screens.DebugToolsScreen
import com.cofopt.cashregister.cmp.screens.HistoryScreen
import com.cofopt.cashregister.cmp.screens.KioskScreen
import com.cofopt.cashregister.cmp.screens.MenuManagementScreen
import com.cofopt.cashregister.cmp.screens.SalesScreen
import com.cofopt.cashregister.cmp.screens.SettingsScreen
import com.cofopt.cashregister.cmp.utils.formatOrderTime
import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.cmp.platform.CallingPlatform
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.utils.Language
import com.cofopt.cashregister.utils.LocalLanguage
import com.cofopt.cashregister.utils.tr
import kotlinx.coroutines.delay

@Composable
fun CashRegisterApp() {
    var language by remember { mutableStateOf(Language.default) }
    var selectedDestination by remember { mutableStateOf(CashRegisterDestination.CHECKOUT) }
    var showDebug by remember { mutableStateOf(false) }
    var clockText by remember { mutableStateOf("") }

    val todayOrders by CashRegisterPlatform.todayOrders.collectAsState()
    val preparing by CallingPlatform.preparing.collectAsState()
    val ready by CallingPlatform.ready.collectAsState()

    val kioskOrders = remember(todayOrders) {
        todayOrders.count { order -> order.source == "KIOSK" && order.status == "UNPAID" }
    }
    val callingOrders = remember(preparing, ready) { preparing.size + ready.size }

    LaunchedEffect(Unit) {
        while (true) {
            clockText = formatOrderTime(nowMillis())
            delay(1000)
        }
    }

    CompositionLocalProvider(LocalLanguage provides language) {
        CashRegisterShell(
            languageOptions = Language.supported.map {
                CashRegisterLanguageOption(
                    code = it.name,
                    selector = it.selectorFlagEmoji,
                    selected = it == language
                )
            },
            onLanguageSelect = { code ->
                language = Language.supported.firstOrNull { it.name == code } ?: language
            },
            topBarState = CashRegisterTopBarState(
                languageTitle = tr(language, "Language", "系统语言", "Taal", ja = "言語", tr = "Dil"),
                transportLabel = tr(language, "Offline", "离线", "Offline", ja = "オフライン", tr = "Çevrimdışı"),
                ipText = "-",
                clockText = clockText,
                isConnected = false,
                transport = CashRegisterTransport.OFFLINE
            ),
            navLabels = CashRegisterNavLabels(
                checkout = tr(language, "Checkout", "收银", "Afrekenen", ja = "会計", tr = "Kasa"),
                menu = tr(language, "Menu", "菜单", "Menu", ja = "メニュー", tr = "Menü"),
                kiosk = tr(language, "Kiosk", "自助", "Kiosk", ja = "セルフ注文", tr = "Kiosk"),
                history = tr(language, "History", "历史", "Geschiedenis", ja = "履歴", tr = "Geçmiş"),
                calling = tr(language, "Calling", "叫号", "Oproepen", ja = "呼び出し", tr = "Çağrı"),
                sales = tr(language, "Sales", "销售", "Verkoop", ja = "売上", tr = "Satış"),
                settings = tr(language, "Settings", "设置", "Instellingen", ja = "設定", tr = "Ayarlar")
            ),
            selectedDestination = selectedDestination,
            onDestinationSelected = {
                selectedDestination = it
                if (it != CashRegisterDestination.SETTINGS) {
                    showDebug = false
                }
            },
            badges = CashRegisterBadgeState(kioskOrders = kioskOrders, callingOrders = callingOrders)
        ) { destination ->
            when (destination) {
                CashRegisterDestination.CHECKOUT -> CheckoutScreen()
                CashRegisterDestination.MENU -> MenuManagementScreen()
                CashRegisterDestination.KIOSK -> KioskScreen()
                CashRegisterDestination.HISTORY -> HistoryScreen()
                CashRegisterDestination.CALLING -> CallingScreen()
                CashRegisterDestination.SALES -> SalesScreen()
                CashRegisterDestination.SETTINGS -> {
                    if (showDebug) {
                        DebugToolsScreen(onBack = { showDebug = false })
                    } else {
                        SettingsScreen(
                            title = tr(language, "Settings", "设置", "Instellingen", ja = "設定", tr = "Ayarlar"),
                            debugLabel = tr(language, "Debug", "调试", "Debug", ja = "デバッグ", tr = "Hata ayiklama"),
                            onOpenDebug = { showDebug = true }
                        )
                    }
                }
            }
        }
    }
}
