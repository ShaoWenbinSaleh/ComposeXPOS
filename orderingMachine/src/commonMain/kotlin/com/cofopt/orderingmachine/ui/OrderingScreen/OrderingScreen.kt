package com.cofopt.orderingmachine.ui.OrderingScreen

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.currentTimeMillis
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.MenuCategory
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.scale
import com.cofopt.orderingmachine.tr
import com.cofopt.orderingmachine.ui.common.dialog.CommonChoiceDialog
import com.cofopt.orderingmachine.ui.CustomizationScreen.CustomizationScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OrderingScreen(
    language: Language,
    menu: List<MenuItem>,
    cartItems: List<CartItem>,
    total: Double,
    dineIn: Boolean,
    onReorder: () -> Unit,
    onAdd: (MenuItem, Map<String, String>) -> Unit,
    onRemove: (MenuItem) -> Unit,
    onUpdateCartItem: (String, Int) -> Unit,
    onPay: () -> Unit
) {
    val baseTypography = MaterialTheme.typography
    val bigTypography = remember(baseTypography) {
        val s = 1f
        baseTypography.copy(
            displaySmall = baseTypography.displaySmall.scale(s),
            headlineLarge = baseTypography.headlineLarge.scale(s),
            headlineMedium = baseTypography.headlineMedium.scale(s),
            headlineSmall = baseTypography.headlineSmall.scale(s),
            titleLarge = baseTypography.titleLarge.scale(s),
            titleMedium = baseTypography.titleMedium.scale(s),
            titleSmall = baseTypography.titleSmall.scale(s),
            bodyLarge = baseTypography.bodyLarge.scale(s),
            bodyMedium = baseTypography.bodyMedium.scale(s),
            bodySmall = baseTypography.bodySmall.scale(s),
            labelLarge = baseTypography.labelLarge.scale(s),
            labelMedium = baseTypography.labelMedium.scale(s),
            labelSmall = baseTypography.labelSmall.scale(s)
        )
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = bigTypography,
        shapes = MaterialTheme.shapes
    ) {
        var showOrderSummary by remember { mutableStateOf(false) }
        val hasCart = remember(cartItems) { cartItems.sumOf { it.quantity } > 0 }

        var lastInteractionMs by remember { mutableStateOf(currentTimeMillis()) }
        var showIdleDialog by remember { mutableStateOf(false) }
        var idleCountdownSec by remember { mutableStateOf(30) }
        var allowMenuOpen by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            allowMenuOpen = false
            delay(650)
            allowMenuOpen = true
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                if (!showIdleDialog && currentTimeMillis() - lastInteractionMs >= 180_000) {
                    showIdleDialog = true
                }
            }
        }

        LaunchedEffect(showIdleDialog) {
            if (!showIdleDialog) return@LaunchedEffect
            idleCountdownSec = 30
            while (showIdleDialog && idleCountdownSec > 0) {
                delay(1_000)
                idleCountdownSec -= 1
            }
            if (showIdleDialog && idleCountdownSec <= 0) {
                showIdleDialog = false
                onReorder()
            }
        }

        var selectedMenuItem by remember { mutableStateOf<MenuItem?>(null) }
        var selectedMenuItemBounds by remember { mutableStateOf(Rect.Zero) }
        var customizationItem by remember { mutableStateOf<MenuItem?>(null) }
        var sheetQuantity by remember { mutableStateOf(1) }
        var customizationSelections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        val sheetScope = rememberCoroutineScope()
        val dialogProgress = remember { Animatable(0f) }

        val categories = remember(menu) {
            MenuCategory.entries.filter { category -> menu.any { it.category == category } }
        }
        var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: MenuCategory.NOODLES) }
        LaunchedEffect(categories) {
            val first = categories.firstOrNull() ?: return@LaunchedEffect
            if (selectedCategory !in categories) {
                selectedCategory = first
            }
        }

        val visibleMenu = remember(menu, selectedCategory) {
            menu.filter { it.category == selectedCategory }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(pass = PointerEventPass.Initial)
                            lastInteractionMs = currentTimeMillis()
                            if (showIdleDialog) {
                                showIdleDialog = false
                            }
                        }
                    }
                }
        ) {
            val rootWidthPx = constraints.maxWidth.toFloat()
            val rootHeightPx = constraints.maxHeight.toFloat()
            val isCompact = maxWidth < 900.dp
            val categoryPanelWidth = if (isCompact) 112.dp else 160.dp
            val categoryVerticalPadding = if (isCompact) 10.dp else 16.dp
            val categoryItemSpacing = if (isCompact) 8.dp else 12.dp
            val scrollbarWidth = if (isCompact) 12.dp else 16.dp

            Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Box(
                //     modifier = Modifier
                //         .fillMaxWidth()
                //         .background(Color(0xFFE53935))
                //         .padding(horizontal = 16.dp, vertical = 10.dp)
                // ) {
                //     Text(
                //         text = tr(
                //             language,
                //             "Sorry, system issue: cannot connect to cash register. Please place your order and bring the receipt to the counter.",
                //             "抱歉，系统故障，无法连接到收银台，请点单后主动凭小票到前台与店员联系。",
                //             "Sorry, systeemstoring: geen verbinding met de kassa. Plaats je bestelling en ga met het bonnetje naar de balie."
                //         ),
                //         style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                //         fontWeight = FontWeight.SemiBold
                //     )
                // }

                // Main content: category sidebar + menu grid
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Left categories
                    LazyColumn(
                        modifier = Modifier
                            .width(categoryPanelWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(vertical = categoryVerticalPadding),
                        verticalArrangement = Arrangement.spacedBy(categoryItemSpacing)
                    ) {
                        items(categories) { category ->
                            CategoryTab(
                                language = language,
                                category = category,
                                isSelected = category == selectedCategory,
                                compact = isCompact,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }

                    // Menu grid with scrollbar
                    val gridState = rememberLazyGridState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        MenuGrid(
                            language = language,
                            menu = visibleMenu,
                            cartItems = cartItems,
                            compact = isCompact,
                            modifier = Modifier.fillMaxSize(),
                            gridState = gridState,
                            onOpen = { item, bounds ->
                                if (allowMenuOpen) {
                                    val hasCustomizations =
                                        item.chooseVegan || item.chooseSource || item.chooseDrink || item.customizations.isNotEmpty()
                                    if (hasCustomizations) {
                                        customizationItem = item
                                        customizationSelections = buildDefaultSelectionsForItem(item)
                                        sheetQuantity = 1
                                    } else {
                                        selectedMenuItem = item
                                        selectedMenuItemBounds = bounds
                                        customizationSelections = buildDefaultSelectionsForItem(item)
                                        sheetQuantity = 1
                                    }
                                }
                            }
                        )
                        
                        // Custom scrollbar on the right
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(scrollbarWidth).padding(horizontal = 3.dp),
                            state = gridState
                        )
                    }
                }

                BottomOrderBar(
                    language = language,
                    dineIn = dineIn,
                    cartItems = cartItems,
                    total = total,
                    compact = isCompact,
                    onReorder = onReorder,
                    onMyOrder = { showOrderSummary = true },
                    onPay = onPay
                )
            }

            if (showIdleDialog) {
                CommonChoiceDialog(
                    onDismissRequest = { },
                    onConfirm = {
                        showIdleDialog = false
                        onReorder()
                    },
                    onDismiss = {
                        lastInteractionMs = currentTimeMillis()
                        showIdleDialog = false
                    },
                    title = tr(
                        language,
                        "No activity detected",
                        "长时间未操作",
                        "Geen activiteit"
                    ),
                    text = tr(
                        language,
                        "Clear cart and return to home? Auto in ${idleCountdownSec}s.",
                        "是否清空购物车并返回主页？${idleCountdownSec}秒后将自动清空并返回。",
                        "Winkelwagen legen en terug naar start? Automatisch over ${idleCountdownSec}s."
                    ),
                    confirmText = tr(language, "Clear & Return", "清空并返回", "Legen & Terug"),
                    dismissText = tr(language, "Continue", "继续点餐", "Doorgaan"),
                )
            }

            val item = selectedMenuItem
            val itemBounds = selectedMenuItemBounds

            if (item != null) {
                LaunchedEffect(item.id) {
                    dialogProgress.snapTo(0f)
                    dialogProgress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
                }

                val t = dialogProgress.value
                val startCenterX = itemBounds.center.x
                val startCenterY = itemBounds.center.y
                val endCenterX = rootWidthPx / 2f
                val endCenterY = rootHeightPx / 2f
                val deltaX = startCenterX - endCenterX
                val deltaY = startCenterY - endCenterY

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.55f * t))
                        .clickable {
                            sheetScope.launch {
                                dialogProgress.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
                                selectedMenuItem = null
                                selectedMenuItemBounds = Rect.Zero
                            }
                        }
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.9f)
                        .heightIn(max = maxHeight * 0.85f)
                        .graphicsLayer {
                            translationX = deltaX * (1f - t)
                            translationY = deltaY * (1f - t)
                            val s = 0.75f + 0.25f * t
                            scaleX = s
                            scaleY = s
                            alpha = t
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    MenuItemBottomSheet(
                        language = language,
                        item = item,
                        quantity = sheetQuantity,
                        selections = customizationSelections,
                        onQuantityChange = { newQty -> sheetQuantity = newQty.coerceAtLeast(1) },
                        onSelectCustomization = { optionId, choiceId ->
                            customizationSelections = customizationSelections.toMutableMap().apply { if (choiceId.isEmpty()) remove(optionId) else put(optionId, choiceId) }
                        },
                        onCancel = {
                            sheetScope.launch {
                                dialogProgress.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
                                selectedMenuItem = null
                                selectedMenuItemBounds = Rect.Zero
                            }
                        },
                        onAddToCart = {
                            repeat(sheetQuantity.coerceAtLeast(1)) {
                                onAdd(item, customizationSelections)
                            }
                            sheetScope.launch {
                                dialogProgress.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
                                selectedMenuItem = null
                                selectedMenuItemBounds = Rect.Zero
                            }
                        }
                    )
                }
            }

            if (showOrderSummary) {
                OrderSummaryDialog(
                    language = language,
                    dineIn = dineIn,
                    cartItems = cartItems,
                    total = total,
                    onDismiss = { showOrderSummary = false },
                    onUpdateQuantity = onUpdateCartItem
                )
            }

            customizationItem?.let { item ->
                CustomizationScreen(
                    language = language,
                    item = item,
                    initialQuantity = sheetQuantity,
                    onBack = { customizationItem = null },
                    onAddToCart = { qty, selections ->
                        repeat(qty) { onAdd(item, selections) }
                        customizationItem = null
                    }
                )
            }
        }
    }
}

@Composable
private fun VerticalScrollbar(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier
) {
    val scrollbarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    
    BoxWithConstraints(modifier = modifier) {
        // Calculate scrollbar thumb position and size
        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo.size
        val firstVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        
        if (totalItems > 0 && visibleItems > 0) {
            val thumbHeight = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
            val thumbOffset = (firstVisibleItemIndex.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f - thumbHeight)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(thumbHeight)
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (thumbOffset * maxHeight.value).roundToInt()) }
                    .background(
                        color = scrollbarColor,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}
