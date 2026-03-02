package com.cofopt.cashregister.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cofopt.cashregister.cmp.components.EmojiVisual
import com.cofopt.cashregister.cmp.platform.preferSystemFontsOnWeb
import composexpos.cashregister.generated.resources.noto_color_emoji
import composexpos.cashregister.generated.resources.noto_colrv1_emojicompat
import composexpos.cashregister.generated.resources.Res
import composexpos.cashregister.generated.resources.noto_sans_sc_regular
import org.jetbrains.compose.resources.Font

enum class CashRegisterDestination {
    CHECKOUT,
    MENU,
    KIOSK,
    HISTORY,
    CALLING,
    SALES,
    SETTINGS
}

enum class CashRegisterTransport {
    WIFI,
    CELL,
    OFFLINE
}

data class CashRegisterLanguageOption(
    val code: String,
    val selector: String,
    val selected: Boolean
)

data class CashRegisterTopBarState(
    val languageTitle: String,
    val transportLabel: String,
    val ipText: String,
    val clockText: String,
    val isConnected: Boolean,
    val transport: CashRegisterTransport
)

data class CashRegisterNavLabels(
    val checkout: String,
    val menu: String,
    val kiosk: String,
    val history: String,
    val calling: String,
    val sales: String,
    val settings: String
)

data class CashRegisterBadgeState(
    val kioskOrders: Int,
    val callingOrders: Int
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CashRegisterShell(
    languageOptions: List<CashRegisterLanguageOption>,
    onLanguageSelect: (String) -> Unit,
    topBarState: CashRegisterTopBarState,
    navLabels: CashRegisterNavLabels,
    selectedDestination: CashRegisterDestination,
    onDestinationSelected: (CashRegisterDestination) -> Unit,
    badges: CashRegisterBadgeState,
    content: @Composable (CashRegisterDestination) -> Unit
) {
    val sidebarBg = Color(0xFF0F1114)
    val accentRed = Color(0xFFE53935)
    val accentGreen = Color(0xFF22B573)
    val accentBlue = Color(0xFF1E88E5)
    val accentYellow = Color(0xFFFBC02D)
    val canvasBlue = Color(0xFFEFF3FF)

    val colorScheme = lightColorScheme(
        primary = accentBlue,
        onPrimary = Color.White,
        secondary = accentGreen,
        onSecondary = Color.White,
        tertiary = accentYellow,
        onTertiary = sidebarBg,
        background = canvasBlue,
        onBackground = Color(0xFF0F1114),
        surface = Color.White,
        onSurface = Color(0xFF0F1114),
        error = accentRed,
        onError = Color.White
    )

    val typography = rememberCashRegisterTypography()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color(0xFF0F1114)
                    ),
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(topBarState.languageTitle, fontSize = 14.sp, color = Color(0xFF4B4F55))
                                languageOptions.forEach { option ->
                                    androidx.compose.material3.IconButton(onClick = { onLanguageSelect(option.code) }) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(
                                                    if (option.selected) Color(0xFF1E88E5).copy(alpha = 0.12f) else Color.Transparent,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            EmojiVisual(
                                                emoji = option.selector,
                                                contentDescription = option.code,
                                                modifier = Modifier.size(20.dp),
                                                fallbackFontSize = 20.sp,
                                                fallbackColor = if (option.selected) Color(0xFF1E88E5) else Color(0xFF121417)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val networkColor = if (topBarState.isConnected) Color(0xFF22B573) else Color(0xFFE53935)
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(networkColor, CircleShape)
                                )
                                Text(
                                    "${topBarState.transportLabel}  IP: ${topBarState.ipText}",
                                    fontSize = 14.sp,
                                    color = Color(0xFF121417)
                                )
                            }

                            Text(topBarState.clockText, fontSize = 14.sp, color = Color(0xFF121417))
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.width(120.dp),
                        containerColor = sidebarBg,
                        contentColor = Color(0xFFE0E5EF)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        CashRegisterNavItem(
                            label = navLabels.checkout,
                            icon = Icons.Rounded.PointOfSale,
                            selected = selectedDestination == CashRegisterDestination.CHECKOUT,
                            onClick = { onDestinationSelected(CashRegisterDestination.CHECKOUT) },
                            accentYellow = accentYellow,
                            accentRed = accentRed
                        )
                        CashRegisterNavItem(
                            label = navLabels.menu,
                            icon = Icons.Rounded.RestaurantMenu,
                            selected = selectedDestination == CashRegisterDestination.MENU,
                            onClick = { onDestinationSelected(CashRegisterDestination.MENU) },
                            accentYellow = accentYellow,
                            accentRed = accentRed
                        )
                        CashRegisterNavItem(
                            label = navLabels.kiosk,
                            icon = Icons.Rounded.Storefront,
                            selected = selectedDestination == CashRegisterDestination.KIOSK,
                            onClick = { onDestinationSelected(CashRegisterDestination.KIOSK) },
                            badgeCount = badges.kioskOrders,
                            accentYellow = accentYellow,
                            accentRed = accentRed
                        )
                        CashRegisterNavItem(
                            label = navLabels.history,
                            icon = Icons.Rounded.History,
                            selected = selectedDestination == CashRegisterDestination.HISTORY,
                            onClick = { onDestinationSelected(CashRegisterDestination.HISTORY) },
                            accentYellow = accentYellow,
                            accentRed = accentRed
                        )
                        CashRegisterNavItem(
                            label = navLabels.calling,
                            icon = Icons.Rounded.Campaign,
                            selected = selectedDestination == CashRegisterDestination.CALLING,
                            onClick = { onDestinationSelected(CashRegisterDestination.CALLING) },
                            badgeCount = badges.callingOrders,
                            accentYellow = accentYellow,
                            accentRed = accentRed
                        )
                        CashRegisterNavItem(
                            label = navLabels.sales,
                            icon = Icons.Rounded.BarChart,
                            selected = selectedDestination == CashRegisterDestination.SALES,
                            onClick = { onDestinationSelected(CashRegisterDestination.SALES) },
                            accentYellow = accentYellow,
                            accentRed = accentRed
                        )
                        CashRegisterNavItem(
                            label = navLabels.settings,
                            icon = Icons.Rounded.Settings,
                            selected = selectedDestination == CashRegisterDestination.SETTINGS,
                            onClick = { onDestinationSelected(CashRegisterDestination.SETTINGS) },
                            accentYellow = accentYellow,
                            accentRed = accentRed
                        )
                    }

                    content(selectedDestination)
                }
            }
        }
    }
}

@Composable
private fun rememberCashRegisterTypography(): Typography {
    val preferSystem = preferSystemFontsOnWeb()
    val appFontFamily = FontFamily(
        Font(Res.font.noto_sans_sc_regular),
        Font(Res.font.noto_color_emoji),
        Font(Res.font.noto_colrv1_emojicompat)
    )
    return remember(preferSystem, appFontFamily) {
        if (preferSystem) Typography() else Typography().withFontFamily(appFontFamily)
    }
}

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography {
    return copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily)
    )
}

@Composable
private fun CashRegisterNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    accentYellow: Color,
    accentRed: Color,
    badgeCount: Int = 0
) {
    NavigationRailItem(
        icon = {
            BadgedIcon(
                imageVector = icon,
                selected = selected,
                badgeCount = badgeCount,
                activeColor = accentYellow,
                inactiveColor = Color(0xFF8F9BB3)
            )
        },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = accentYellow,
            selectedTextColor = Color.White,
            indicatorColor = accentRed,
            unselectedIconColor = Color(0xFF8F9BB3),
            unselectedTextColor = Color(0xFF8F9BB3)
        )
    )
}

@Composable
private fun BadgedIcon(
    imageVector: ImageVector,
    selected: Boolean,
    badgeCount: Int,
    activeColor: Color,
    inactiveColor: Color
) {
    Box(modifier = Modifier.size(24.dp)) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = if (selected) activeColor else inactiveColor,
            modifier = Modifier.align(Alignment.Center)
        )

        if (badgeCount > 0) {
            val badgeText = if (badgeCount > 9) "9+" else badgeCount.toString()
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(0xFFE53935), CircleShape)
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
