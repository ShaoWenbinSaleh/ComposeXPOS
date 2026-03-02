package com.cofopt.cashregister

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cofopt.cashregister.calling.CallingRepository
import com.cofopt.cashregister.cmp.CashRegisterBadgeState
import com.cofopt.cashregister.cmp.CashRegisterDestination
import com.cofopt.cashregister.cmp.CashRegisterLanguageOption
import com.cofopt.cashregister.cmp.CashRegisterNavLabels
import com.cofopt.cashregister.cmp.CashRegisterShell
import com.cofopt.cashregister.cmp.CashRegisterTopBarState
import com.cofopt.cashregister.cmp.CashRegisterTransport
import com.cofopt.cashregister.cmp.screens.CallingScreen as SharedCallingScreen
import com.cofopt.cashregister.cmp.screens.CheckoutScreen as SharedCheckoutScreen
import com.cofopt.cashregister.cmp.screens.DebugToolsScreen as SharedDebugToolsScreen
import com.cofopt.cashregister.cmp.screens.HistoryScreen as SharedHistoryScreen
import com.cofopt.cashregister.cmp.screens.KioskScreen as SharedKioskScreen
import com.cofopt.cashregister.cmp.screens.MenuManagementScreen as SharedMenuManagementScreen
import com.cofopt.cashregister.cmp.screens.SalesScreen as SharedSalesScreen
import com.cofopt.cashregister.cmp.screens.SettingsScreen as SharedSettingsScreen
import com.cofopt.cashregister.menu.DishesRepository
import com.cofopt.cashregister.network.OrdersRepository
import com.cofopt.cashregister.network.getLocalIpv4Address
import com.cofopt.cashregister.utils.Language
import com.cofopt.cashregister.utils.LocalLanguage
import com.cofopt.cashregister.utils.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class HistoryRange {
    TODAY,
    LAST_7_DAYS,
    ALL
}

private enum class ActiveTransport {
    WIFI,
    CELL,
    OFFLINE
}

private data class NetworkStatus(
    val transport: ActiveTransport,
    val isConnected: Boolean
)

@Composable
private fun rememberNetworkStatus(context: Context): NetworkStatus {
    fun current(): NetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val transport = when {
            !connected -> ActiveTransport.OFFLINE
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> ActiveTransport.WIFI
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> ActiveTransport.CELL
            else -> ActiveTransport.WIFI
        }
        return NetworkStatus(transport = transport, isConnected = connected)
    }

    var status by remember { mutableStateOf(current()) }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun update() {
            status = current()
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = update()
        }

        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
        }

        onDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    return status
}

@Composable
private fun rememberClockText(): String {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        while (true) {
            text = fmt.format(Date())
            delay(1000)
        }
    }
    return text
}

@Composable
internal fun CashRegisterApp() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        DishesRepository.ensureLoaded(context)
        OrdersRepository.ensureLoaded(context)
        CallingRepository.ensureLoaded(context)
    }

    val initialLanguage = remember {
        Language.fromLocaleTag(Locale.getDefault().toLanguageTag())
    }
    var language by rememberSaveable { mutableStateOf(initialLanguage) }
    var selectedDestination by remember { mutableStateOf(CashRegisterDestination.CHECKOUT) }
    var showDebugScreen by rememberSaveable { mutableStateOf(false) }

    val orders by OrdersRepository.todayOrders.collectAsState()
    val preparing by CallingRepository.preparing.collectAsState()
    val ready by CallingRepository.ready.collectAsState()

    val kioskOrders = remember(orders) {
        orders.count { order ->
            order.source == "KIOSK" && order.status == "UNPAID"
        }
    }

    val callingOrders = remember(preparing, ready) {
        preparing.size + ready.size
    }

    val networkStatus = rememberNetworkStatus(context)
    val clockText = rememberClockText()

    val transport = when (networkStatus.transport) {
        ActiveTransport.WIFI -> CashRegisterTransport.WIFI
        ActiveTransport.CELL -> CashRegisterTransport.CELL
        ActiveTransport.OFFLINE -> CashRegisterTransport.OFFLINE
    }

    val transportLabel = when (networkStatus.transport) {
        ActiveTransport.WIFI -> tr(language, "Wi-Fi", "Wi-Fi", "Wi-Fi", ja = "Wi-Fi", tr = "Wi-Fi")
        ActiveTransport.CELL -> tr(language, "Cell", "蜂窝", "Mobiel", ja = "モバイル", tr = "Hücresel")
        ActiveTransport.OFFLINE -> tr(language, "Offline", "离线", "Offline", ja = "オフライン", tr = "Çevrimdışı")
    }

    val navLabels = CashRegisterNavLabels(
        checkout = tr(language, "Checkout", "收银", "Afrekenen", ja = "会計", tr = "Kasa"),
        menu = tr(language, "Menu", "菜单", "Menu", ja = "メニュー", tr = "Menü"),
        kiosk = tr(language, "Kiosk", "自助", "Kiosk", ja = "セルフ注文", tr = "Kiosk"),
        history = tr(language, "History", "历史", "Geschiedenis", ja = "履歴", tr = "Geçmiş"),
        calling = tr(language, "Calling", "叫号", "Oproepen", ja = "呼び出し", tr = "Çağrı"),
        sales = tr(language, "Sales", "销售", "Verkoop", ja = "売上", tr = "Satış"),
        settings = tr(language, "Settings", "设置", "Instellingen", ja = "設定", tr = "Ayarlar")
    )

    CompositionLocalProvider(LocalLanguage provides language) {
        CashRegisterShell(
            languageOptions = Language.supported.map { option ->
                CashRegisterLanguageOption(
                    code = option.name,
                    selector = option.selectorFlagEmoji,
                    selected = option == language
                )
            },
            onLanguageSelect = { code ->
                language = Language.supported.firstOrNull { it.name == code } ?: language
            },
            topBarState = CashRegisterTopBarState(
                languageTitle = tr(language, "Language", "系统语言", "Taal", ja = "言語", tr = "Dil"),
                transportLabel = transportLabel,
                ipText = getLocalIpv4Address() ?: "-",
                clockText = clockText,
                isConnected = networkStatus.isConnected,
                transport = transport
            ),
            navLabels = navLabels,
            selectedDestination = selectedDestination,
            onDestinationSelected = { selectedDestination = it },
            badges = CashRegisterBadgeState(
                kioskOrders = kioskOrders,
                callingOrders = callingOrders
            )
        ) { destination ->
            when (destination) {
                CashRegisterDestination.CHECKOUT -> SharedCheckoutScreen()
                CashRegisterDestination.MENU -> SharedMenuManagementScreen()
                CashRegisterDestination.KIOSK -> SharedKioskScreen()
                CashRegisterDestination.HISTORY -> SharedHistoryScreen()
                CashRegisterDestination.CALLING -> SharedCallingScreen()
                CashRegisterDestination.SALES -> SharedSalesScreen()
                CashRegisterDestination.SETTINGS -> {
                    if (showDebugScreen) {
                        SharedDebugToolsScreen(onBack = { showDebugScreen = false })
                    } else {
                        SharedSettingsScreen(
                            title = tr(language, "Settings", "设置", "Instellingen", ja = "設定", tr = "Ayarlar"),
                            debugLabel = tr(language, "Debug", "调试", "Debug", ja = "デバッグ", tr = "Hata ayiklama"),
                            onOpenDebug = { showDebugScreen = true }
                        )
                    }
                }
            }
        }
    }
}
