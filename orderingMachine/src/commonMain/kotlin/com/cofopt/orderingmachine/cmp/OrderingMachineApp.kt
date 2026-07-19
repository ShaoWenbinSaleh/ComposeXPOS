package com.cofopt.orderingmachine.cmp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import composexpos.orderingmachine.generated.resources.Res
import composexpos.orderingmachine.generated.resources.noto_color_emoji
import composexpos.orderingmachine.generated.resources.noto_colrv1_emojicompat
import composexpos.orderingmachine.generated.resources.noto_sans_sc_regular
import composexpos.orderingmachine.generated.resources.source_han_sans_regular
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.MenuItem
import com.cofopt.orderingmachine.OrderMode
import com.cofopt.orderingmachine.Screen
import com.cofopt.orderingmachine.network.CashRegisterClient
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.CashRegisterOrderSubmissionResult
import com.cofopt.orderingmachine.network.MenuSyncItem
import com.cofopt.orderingmachine.network.OrderingPlatformContext
import com.cofopt.orderingmachine.network.rememberOrderingPlatformContext
import com.cofopt.orderingmachine.ui.CheckoutScreen.CheckoutScreen
import com.cofopt.orderingmachine.ui.DebugScreen.DebugScreen
import com.cofopt.orderingmachine.ui.HomeScreen.ModeSelectionScreen
import com.cofopt.orderingmachine.ui.OrderingScreen.OrderingScreen
import com.cofopt.orderingmachine.ui.PaymentScreen.CounterPaymentProcessingScreen
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentError
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentEvent
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentProcessingScreen
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentResultScreen
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentSelectionScreen
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentState
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentStateMachine
import com.cofopt.orderingmachine.viewmodel.buildCashRegisterOrderPayloadImpl
import com.cofopt.orderingmachine.viewmodel.toAllergensImpl
import com.cofopt.orderingmachine.viewmodel.toCategoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font

@Composable
fun OrderingMachineApp() {
    val context = rememberOrderingPlatformContext()
    var language by remember { mutableStateOf(Language.default) }
    var currentScreen by remember { mutableStateOf(Screen.MODE_SELECTION) }
    var orderMode by remember { mutableStateOf<OrderMode?>(null) }
    var paymentSessionId by remember { mutableIntStateOf(0) }
    var activeOrder by remember { mutableStateOf<CashRegisterOrderPayload?>(null) }
    var orderSubmissionStarted by remember { mutableStateOf(false) }

    val cart = remember { mutableStateMapOf<String, CartItem>() }
    var menu by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    LaunchedEffect(context) {
        val seed = loadSeedMenu()
        if (menu.isEmpty()) menu = seed

        while (true) {
            // Pending entries are bound to the endpoint captured when they
            // were staged, so retry does not depend on today's configuration.
            try {
                CashRegisterClient.retryPendingOrders(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Preserve the queue and retry on the next pass.
            }

            try {
                if (CashRegisterClient.isConfigured(context)) {
                    CashRegisterClient.getMenu(context)?.let { remote ->
                        if (remote.isNotEmpty()) {
                            menu = remote.map { it.toUiMenuItem() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the last valid menu and retry without blanking the UI.
            }
            delay(15_000)
        }
    }

    val cartItems = cart.values.toList()
    val total = cartItems.sumOf { it.menuItem.price * it.quantity }

    fun updateCartItemQuantity(uuid: String, quantity: Int) {
        if (quantity <= 0) {
            cart.remove(uuid)
            return
        }
        val existing = cart[uuid] ?: return
        cart[uuid] = existing.copy(quantity = quantity)
    }

    fun addToCart(item: MenuItem, customizations: Map<String, String>) {
        val existing = cart.values.find { it.menuItem.id == item.id && it.customizations == customizations }
        if (existing != null) {
            updateCartItemQuantity(existing.uuid, existing.quantity + 1)
            return
        }
        val next = CartItem(
            menuItem = item,
            quantity = 1,
            customizations = customizations
        )
        cart[next.uuid] = next
    }

    fun decrementItem(item: MenuItem) {
        val existing = cart.values.findLast { it.menuItem.id == item.id } ?: return
        updateCartItemQuantity(existing.uuid, existing.quantity - 1)
    }

    fun resetToHome() {
        cart.clear()
        orderMode = null
        activeOrder = null
        orderSubmissionStarted = false
        currentScreen = Screen.MODE_SELECTION
    }

    val typography = rememberOrderingMachineTypography()

    MaterialTheme(typography = typography) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                Screen.MODE_SELECTION -> {
                    ModeSelectionScreen(
                        language = language,
                        onLanguageChange = { language = it },
                        onSelect = { dineIn ->
                            orderMode = if (dineIn) OrderMode.DINE_IN else OrderMode.TAKE_AWAY
                            currentScreen = Screen.ORDERING
                        },
                        onDebug = { currentScreen = Screen.DEBUG }
                    )
                }

                Screen.ORDERING -> {
                    OrderingScreen(
                        language = language,
                        menu = menu,
                        cartItems = cartItems,
                        total = total,
                        dineIn = orderMode == OrderMode.DINE_IN,
                        onReorder = { resetToHome() },
                        onAdd = ::addToCart,
                        onRemove = ::decrementItem,
                        onUpdateCartItem = ::updateCartItemQuantity,
                        onPay = { currentScreen = Screen.CHECKOUT }
                    )
                }

                Screen.CHECKOUT -> {
                    CheckoutScreen(
                        language = language,
                        dineIn = orderMode == OrderMode.DINE_IN,
                        cartItems = cartItems,
                        total = total,
                        onBack = {
                            // Once a request may have reached CashRegister the
                            // exact cart/orderId is frozen until it is resolved.
                            if (!orderSubmissionStarted) currentScreen = Screen.ORDERING
                        },
                        onConfirm = {
                            if (activeOrder == null) {
                                activeOrder = buildCashRegisterOrderPayloadImpl(
                                    context = context,
                                    cartItems = cartItems,
                                    total = total,
                                    dineIn = orderMode == OrderMode.DINE_IN,
                                    paymentMethod = "CASH",
                                    paymentStatus = "UNPAID",
                                )
                                paymentSessionId += 1
                            }
                            currentScreen = Screen.PAYMENT_SELECTION
                        }
                    )
                }

                Screen.PAYMENT_SELECTION,
                Screen.PAYMENT_PROCESSING,
                Screen.PAYMENT_RESULT -> {
                    WebPaymentFlowScreen(
                        sessionId = paymentSessionId,
                        language = language,
                        context = context,
                        dineIn = orderMode == OrderMode.DINE_IN,
                        cartItems = cartItems,
                        total = total,
                        counterOrder = checkNotNull(activeOrder),
                        onSubmissionStarted = { orderSubmissionStarted = true },
                        onPermanentRejection = {
                            orderSubmissionStarted = false
                        },
                        onBackToCheckout = {
                            if (!orderSubmissionStarted) activeOrder = null
                            currentScreen = Screen.CHECKOUT
                        },
                        onFinish = {
                            resetToHome()
                        }
                    )
                }

                Screen.DEBUG -> {
                    DebugScreen(onBack = { currentScreen = Screen.MODE_SELECTION })
                }

                else -> {
                    currentScreen = Screen.MODE_SELECTION
                }
            }
        }
    }
}

@Composable
private fun WebPaymentFlowScreen(
    sessionId: Int,
    language: Language,
    context: OrderingPlatformContext,
    dineIn: Boolean,
    cartItems: List<CartItem>,
    total: Double,
    counterOrder: CashRegisterOrderPayload,
    onSubmissionStarted: () -> Unit,
    onPermanentRejection: () -> Unit,
    onBackToCheckout: () -> Unit,
    onFinish: () -> Unit
) {
    val stateMachine = remember(sessionId) { PaymentStateMachine(language) }
    var currentState by remember(sessionId) { mutableStateOf(stateMachine.getCurrentState()) }
    var cashRegisterCreateFailed by remember(sessionId) { mutableStateOf(false) }

    DisposableEffect(stateMachine) {
        stateMachine.setStateChangeListener { currentState = it }
        onDispose {
            stateMachine.setStateChangeListener { }
        }
    }

    LaunchedEffect(language, stateMachine) {
        stateMachine.updateLanguage(language)
    }

    LaunchedEffect(currentState, counterOrder.orderId) {
        when (currentState) {
            is PaymentState.ProcessingCounterPayment -> {
                onSubmissionStarted()
                val result = try {
                    if (CashRegisterClient.isConfigured(context)) {
                        CashRegisterClient.submitOrder(context, counterOrder)
                    } else {
                        CashRegisterOrderSubmissionResult.Rejected(0, "cashregister_not_configured")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val durable = try {
                        CashRegisterClient.isOrderDurablyRecorded(context, counterOrder)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (durable) {
                        CashRegisterOrderSubmissionResult.Pending(
                            orderId = counterOrder.orderId,
                            message = error.message,
                        )
                    } else {
                        CashRegisterOrderSubmissionResult.Indeterminate(
                            orderId = counterOrder.orderId,
                            message = error.message ?: "order_submission_durability_unknown",
                        )
                    }
                }
                when (result) {
                    is CashRegisterOrderSubmissionResult.Accepted -> {
                        stateMachine.handleEvent(PaymentEvent.PaymentCompleted(result.callNumber))
                    }

                    is CashRegisterOrderSubmissionResult.Pending -> {
                        // The exact endpoint-bound order is durable and will be
                        // retried automatically. End this customer session so a
                        // second orderId cannot be created for the same checkout.
                        cashRegisterCreateFailed = true
                        stateMachine.handleEvent(PaymentEvent.PaymentCompleted(null))
                    }

                    is CashRegisterOrderSubmissionResult.Rejected -> {
                        onPermanentRejection()
                        stateMachine.handleEvent(
                            PaymentEvent.PaymentFailed(PaymentError.UnknownError(result.message))
                        )
                    }

                    is CashRegisterOrderSubmissionResult.Indeterminate -> {
                        // Keep orderSubmissionStarted and the exact payload
                        // frozen. The operator may retry this same identity,
                        // but navigating back cannot create a new orderId.
                        stateMachine.handleEvent(
                            PaymentEvent.PaymentFailed(PaymentError.UnknownError(result.message))
                        )
                    }
                }
            }

            is PaymentState.ProcessingCardRequest -> {
                stateMachine.handleEvent(PaymentEvent.CardRequestFailed(PaymentError.RequestFailed))
            }

            is PaymentState.ProcessingPayment -> {
                stateMachine.handleEvent(PaymentEvent.PaymentFailed(PaymentError.RequestFailed))
            }

            else -> Unit
        }
    }

    when (val state = currentState) {
        is PaymentState.SelectingPayment -> {
            PaymentSelectionScreen(
                language = state.language,
                dineIn = dineIn,
                cartItems = cartItems,
                total = total,
                paymentError = state.paymentError?.let { stateMachine.getErrorMessage(it, state.language) },
                printError = false,
                onSelect = { paymentMethod, _ ->
                    stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                },
                onBack = onBackToCheckout
            )
        }

        is PaymentState.ProcessingCardRequest -> {
            PaymentProcessingScreen(
                language = state.language,
                posTriggerFailed = false,
                onCancel = { stateMachine.handleEvent(PaymentEvent.PaymentCancelled) },
                onTimeout = { stateMachine.handleEvent(PaymentEvent.PaymentTimeout) },
                onDebugSuccess = null,
                onDebugFail = null
            )
        }

        is PaymentState.ProcessingCounterPayment -> {
            CounterPaymentProcessingScreen(language = state.language)
        }

        is PaymentState.ProcessingPayment -> {
            PaymentProcessingScreen(
                language = state.language,
                posTriggerFailed = false,
                onCancel = { stateMachine.handleEvent(PaymentEvent.PaymentCancelled) },
                onTimeout = { stateMachine.handleEvent(PaymentEvent.PaymentTimeout) },
                onDebugSuccess = null,
                onDebugFail = null
            )
        }

        is PaymentState.PaymentSuccess -> {
            PaymentResultScreen(
                language = state.language,
                paymentMethod = state.paymentMethod,
                callNumber = state.callNumber,
                cashRegisterCreateFailed = cashRegisterCreateFailed,
                cashRegisterCreateFailedWasPaid = false,
                onNextCustomer = onFinish
            )
        }

        is PaymentState.PaymentFailed -> {
            PaymentSelectionScreen(
                language = state.language,
                dineIn = dineIn,
                cartItems = cartItems,
                total = total,
                paymentError = stateMachine.getErrorMessage(state.error, state.language),
                printError = false,
                onSelect = { paymentMethod, _ ->
                    stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                },
                onBack = onBackToCheckout
            )
        }

        is PaymentState.PaymentTimeout -> {
            PaymentSelectionScreen(
                language = state.language,
                dineIn = dineIn,
                cartItems = cartItems,
                total = total,
                paymentError = stateMachine.getErrorMessage(PaymentError.PaymentTimeout, state.language),
                printError = false,
                onSelect = { paymentMethod, _ ->
                    stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                },
                onBack = onBackToCheckout
            )
        }

        is PaymentState.Idle -> {
            PaymentSelectionScreen(
                language = language,
                dineIn = dineIn,
                cartItems = cartItems,
                total = total,
                paymentError = null,
                printError = false,
                onSelect = { paymentMethod, _ ->
                    stateMachine.handleEvent(PaymentEvent.SelectPaymentMethod(paymentMethod))
                },
                onBack = onBackToCheckout
            )
        }
    }
}

@Composable
private fun rememberOrderingMachineTypography(): Typography {
    val preferSystem = preferSystemFontsOnWeb()
    val appFontFamily = FontFamily(
        Font(Res.font.source_han_sans_regular),
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

private fun MenuSyncItem.toUiMenuItem(): MenuItem {
    return MenuItem(
        id = id,
        nameEn = nameEn,
        nameZh = nameZh,
        nameNl = nameNl,
        nameJa = nameJa,
        nameTr = nameTr,
        descriptionEn = "",
        descriptionZh = "",
        descriptionNl = "",
        priceEur = priceEur,
        discountedPrice = discountedPrice,
        soldOut = soldOut,
        price = discountedPrice,
        imagePath = "images/menu/$id.jpg",
        category = toCategoryImpl(category),
        allergens = toAllergensImpl(this),
        customizations = emptyList(),
        chooseVegan = chooseVegan,
        chooseSource = chooseSource,
        chooseDrink = chooseDrink,
    )
}
