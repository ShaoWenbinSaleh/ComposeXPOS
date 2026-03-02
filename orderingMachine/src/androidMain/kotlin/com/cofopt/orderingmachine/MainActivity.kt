package com.cofopt.orderingmachine

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cofopt.orderingmachine.ui.theme.OrderingMachineTheme
import com.cofopt.orderingmachine.network.OrderingPresenceServer
import com.cofopt.orderingmachine.network.ComposeXPOSOrderingNsdAdvertiser
import com.cofopt.orderingmachine.viewmodel.MainViewModel
import com.cofopt.orderingmachine.ui.DebugScreen.DebugScreen
import com.cofopt.orderingmachine.ui.CheckoutScreen.CheckoutScreen
import com.cofopt.orderingmachine.ui.HomeScreen.ModeSelectionScreen
import com.cofopt.orderingmachine.ui.OrderingScreen.OrderingScreen
import com.cofopt.orderingmachine.ui.PaymentScreen.PaymentFlowScreen

class MainActivity : ComponentActivity() {
    companion object {
        private const val ORDERING_PRESENCE_PORT = 19081
        private const val MENU_SYNC_INTERVAL_MS = 15_000L
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }
    private var nsdAdvertiser: ComposeXPOSOrderingNsdAdvertiser? = null
    private var presenceServer: OrderingPresenceServer? = null

    private var keepSplashOnScreen: Boolean = true
    private val handler = Handler(Looper.getMainLooper())
    
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }
    
    private val hideSystemBarsRunnable = object : Runnable {
        override fun run() {
            hideSystemUI()
            // Repeat every 100ms to ensure navigation bar never appears
            handler.postDelayed(this, 100)
        }
    }

    private val menuSyncRunnable = object : Runnable {
        override fun run() {
            viewModel.refreshMenuFromCashRegister(this@MainActivity)
            handler.postDelayed(this, MENU_SYNC_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if app started from boot
        val isBootStart = intent.getBooleanExtra("boot_start", false)
        if (isBootStart) {
            Log.d("MainActivity", "App started from boot receiver")
        }
        
        // Hide system UI permanently
        hideSystemUI()
        
        // Start aggressive hiding timer
        handler.post(hideSystemBarsRunnable)

        // Handle config intent
        intent?.let {
            when (it.getStringExtra("action")) {
                "set_config" -> {
                    val host = it.getStringExtra("host") ?: "192.168.1.100"
                    val port = it.getIntExtra("port", 8080)
                    MainViewModel.setCashRegisterConfig(this, host, port)
                }
            }
        }

        ImageCache.startPreload(this, "images/menu")
        ImageCache.startPreload(this, "images/menu/icons")
        ImageCache.startPreload(this, "images/menu/allergens")
        ImageCache.startPreload(this, "images/menu/banner")
        ImageCache.startPreload(this, "images/food")
        
        setContent {
            OrderingMachineTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val markReadyOnce = remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (!markReadyOnce.value) {
                            keepSplashOnScreen = false
                            markReadyOnce.value = true
                        }
                    }

                    OrderApp(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshMenuFromCashRegister(this)
        handler.postDelayed(menuSyncRunnable, MENU_SYNC_INTERVAL_MS)
        if (presenceServer == null) {
            presenceServer = OrderingPresenceServer(this, ORDERING_PRESENCE_PORT).also { it.start() }
        }
        if (nsdAdvertiser == null) {
            nsdAdvertiser = ComposeXPOSOrderingNsdAdvertiser(this)
        }
        nsdAdvertiser?.register(ORDERING_PRESENCE_PORT)
    }

    override fun onStop() {
        handler.removeCallbacks(menuSyncRunnable)
        nsdAdvertiser?.stop()
        nsdAdvertiser = null
        presenceServer?.stop()
        presenceServer = null
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Immediately hide system bars whenever window gains focus
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Restart timer when app resumes
        handler.post(hideSystemBarsRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // Stop timer when app pauses to save battery
        handler.removeCallbacks(hideSystemBarsRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up timer
        handler.removeCallbacks(hideSystemBarsRunnable)
    }
    
    override fun onUserInteraction() {
        super.onUserInteraction()
        // Hide system bars on any user interaction
        hideSystemUI()
    }
}

@Composable
private fun OrderApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val language = viewModel.language
    val currentScreen = viewModel.currentScreen
    val menu = viewModel.menu
    val cartItems = viewModel.cartItems
    val total = viewModel.totalAmount
    val paymentError = viewModel.paymentError

    LaunchedEffect(Unit) {
        viewModel.loadMenu(context)
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f))
                .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f))
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            Screen.MODE_SELECTION -> {
                ModeSelectionScreen(
                    language = language,
                    onLanguageChange = viewModel::updateLanguage,
                    onSelect = { isDineIn -> 
                        viewModel.selectOrderMode(if (isDineIn) OrderMode.DINE_IN else OrderMode.TAKE_AWAY)
                    },
                    onDebug = { viewModel.navigateTo(Screen.DEBUG) }
                )
            }

            Screen.ORDERING -> OrderingScreen(
                language = language,
                menu = menu,
                cartItems = cartItems,
                total = total,
                dineIn = viewModel.orderMode == OrderMode.DINE_IN,
                onReorder = viewModel::reorder,
                onAdd = viewModel::addToCart,
                onRemove = viewModel::decrementQuantity,
                onUpdateCartItem = viewModel::updateCartItemQuantity,
                onPay = viewModel::startPayment
            )

            Screen.CHECKOUT -> CheckoutScreen(
                language = language,
                dineIn = viewModel.orderMode == OrderMode.DINE_IN,
                cartItems = cartItems,
                total = total,
                onBack = { viewModel.navigateTo(Screen.ORDERING) },
                onConfirm = viewModel::startPayment
            )

            Screen.PAYMENT_SELECTION,
            Screen.PAYMENT_PROCESSING,
            Screen.PAYMENT_RESULT -> {
                PaymentFlowScreen(
                    viewModel = viewModel,
                    onBackToOrdering = { viewModel.navigateTo(Screen.ORDERING) },
                    onNextCustomer = { viewModel.navigateTo(Screen.MODE_SELECTION) }
                )
            }

            Screen.DEBUG -> {
                DebugScreen(
                    onBack = { viewModel.navigateTo(Screen.MODE_SELECTION) }
                )
            }

            else -> {
                // Placeholder for other screens
            }
        }
    }
}
