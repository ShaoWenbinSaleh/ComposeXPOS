package com.cofopt.orderingmachine.ui.PaymentScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cofopt.orderingmachine.CartItem
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.cmp.EmojiVisual
import com.cofopt.orderingmachine.currentTimeMillis
import com.cofopt.orderingmachine.formatEuroAmount
import com.cofopt.orderingmachine.localizedName
import com.cofopt.orderingmachine.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SharedPaymentSelectionScreen(
    language: Language,
    dineIn: Boolean,
    cartItems: List<CartItem>,
    total: Double,
    paymentError: String? = null,
    printError: Boolean = false,
    cardPaymentEnabled: Boolean,
    isCardSystemConnected: Boolean?,
    isCheckingCardSystem: Boolean,
    debugCardSmEnabled: Boolean,
    debugPosRequestSuccess: Boolean,
    debugPosTriggerSuccess: Boolean,
    onDebugPosRequestSuccessChanged: (Boolean) -> Unit,
    onDebugPosTriggerSuccessChanged: (Boolean) -> Unit,
    onSelect: (PaymentMethod, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val brandGreen = Color(0xFF22B573)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showIdleDialog by remember { mutableStateOf(false) }
    var idleCountdownSec by remember { mutableStateOf(30) }
    var lastInteractionMs by remember { mutableStateOf(currentTimeMillis()) }

    LaunchedEffect(printError) {
        if (printError) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = tr(
                        language,
                        "Order printing failed. Please contact staff.",
                        "订单打印失败。请与工作人员联系。",
                        "Bestelling afdrukken mislukt. Neem contact op met het personeel.",
                        ja = "注文伝票の印刷に失敗しました。スタッフにお声がけください。",
                        tr = "Sipariş yazdırılamadı. Lütfen personele haber verin."
                    )
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = currentTimeMillis()
            val elapsedSec = ((now - lastInteractionMs) / 1000).toInt()

            if (elapsedSec >= 30 && !showIdleDialog) {
                showIdleDialog = true
                idleCountdownSec = 30
            }

            if (showIdleDialog) {
                idleCountdownSec--
                if (idleCountdownSec <= 0) {
                    onBack()
                    return@LaunchedEffect
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (paymentError != null) {
                val errorMessage = when {
                    paymentError.contains("timeout") || paymentError.contains("超时") -> tr(
                        language,
                        "Payment timeout. Please retry or select payment at counter.",
                        "支付超时。请重试或选择在收银台支付。",
                        "Betaling time-out. Probeer het opnieuw of betaal bij de kassa.",
                        ja = "支払いがタイムアウトしました。再試行するか、レジでお支払いください。",
                        tr = "Ödeme zaman aşımına uğradı. Lütfen tekrar deneyin veya kasada ödeyin."
                    )

                    paymentError.contains("Failed to create transaction") || paymentError.contains("Card payment disabled") -> tr(
                        language,
                        "Request failed. Please retry or select payment at counter.",
                        "发送请求失败。请重试或选择在收银台支付。",
                        "Verzoek mislukt. Probeer het opnieuw of betaal bij de kassa.",
                        ja = "リクエストに失敗しました。再試行するか、レジでお支払いください。",
                        tr = "İstek başarısız oldu. Lütfen tekrar deneyin veya kasada ödeyin."
                    )

                    else -> tr(
                        language,
                        "Payment failed. Please retry or select payment at counter.",
                        "支付失败。请重试或选择在收银台支付。",
                        "Betaling mislukt. Probeer het opnieuw of betaal bij de kassa.",
                        ja = "支払いに失敗しました。再試行するか、レジでお支払いください。",
                        tr = "Ödeme başarısız oldu. Lütfen tekrar deneyin veya kasada ödeyin."
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    border = BorderStroke(1.dp, Color(0xFFFFC107))
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF856404)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFE6E7E9))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (dineIn) {
                                    tr(language, "Dine In", "堂食", "Hier eten", ja = "店内飲食", tr = "Restoranda")
                                } else {
                                    tr(language, "Take Away", "打包", "Meenemen", ja = "お持ち帰り", tr = "Paket servis")
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "€ ${formatEuroAmount(total)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = brandGreen
                            )
                        }

                        Text(
                            text = tr(
                                language,
                                "${cartItems.sumOf { it.quantity }} items",
                                "${cartItems.sumOf { it.quantity }} 件",
                                "${cartItems.sumOf { it.quantity }} items",
                                ja = "${cartItems.sumOf { it.quantity }} 点",
                                tr = "${cartItems.sumOf { it.quantity }} ürün"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF4B4F55)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 110.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            cartItems.take(4).forEach { ci ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ci.quantity}x ${ci.menuItem.localizedName(language)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "€ ${formatEuroAmount(ci.menuItem.price * ci.quantity)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            if (cartItems.size > 4) {
                                Text(
                                    text = tr(language, "…and more", "…更多", "…en meer", ja = "…他", tr = "…ve daha fazlası"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF6A7078)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (cardPaymentEnabled) {
                    if (isCardSystemConnected == true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PaymentMethodCard(
                                title = tr(language, "Card", "刷卡", "Kaart", ja = "カード", tr = "Kart"),
                                emoji = "💳",
                                subEmojis = emptyList(),
                                onClick = {
                                    lastInteractionMs = currentTimeMillis()
                                    onSelect(PaymentMethod.CARD, false)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            PaymentMethodCard(
                                title = tr(language, "Cash", "现金", "Contant", ja = "現金", tr = "Nakit"),
                                emoji = "💵",
                                subEmojis = emptyList(),
                                onClick = {
                                    lastInteractionMs = currentTimeMillis()
                                    onSelect(PaymentMethod.COUNTER, false)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PaymentMethodCard(
                                title = tr(language, "Card", "刷卡", "Kaart", ja = "カード", tr = "Kart"),
                                emoji = "💳",
                                subEmojis = listOf("💠", "🏦"),
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            )

                            PaymentMethodCard(
                                title = tr(language, "Cash", "现金", "Contant", ja = "現金", tr = "Nakit"),
                                emoji = "💵",
                                subEmojis = emptyList(),
                                onClick = {
                                    lastInteractionMs = currentTimeMillis()
                                    onSelect(PaymentMethod.COUNTER, false)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                            border = BorderStroke(1.dp, Color(0xFFFFC107))
                        ) {
                            Text(
                                text = if (isCheckingCardSystem) {
                                    tr(
                                        language,
                                        "Checking payment system...",
                                        "正在检查支付系统...",
                                        "Betalingssysteem controleren...",
                                        ja = "決済システムを確認中...",
                                        tr = "Ödeme sistemi kontrol ediliyor..."
                                    )
                                } else {
                                    tr(
                                        language,
                                        "Sorry, payment system error. Please pay at the counter.",
                                        "抱歉，支付系统故障。请在收银台支付。",
                                        "Sorry, betalingssysteem fout. Betaal alstublieft bij de kassa.",
                                        ja = "申し訳ありません。決済システムに障害があります。レジでお支払いください。",
                                        tr = "Üzgünüz, ödeme sistemi hatası. Lütfen kasada ödeme yapın."
                                    )
                                },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF856404)
                            )
                        }
                    }

                    if (debugCardSmEnabled) {
                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "POS机请求成功",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = debugPosRequestSuccess,
                                onCheckedChange = onDebugPosRequestSuccessChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF22B573)
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "POS机trigger",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = debugPosTriggerSuccess,
                                onCheckedChange = onDebugPosTriggerSuccessChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF22B573)
                                )
                            )
                        }
                    }
                } else {
                    PaymentMethodCard(
                        title = tr(language, "Cash", "现金", "Contant", ja = "現金", tr = "Nakit"),
                        emoji = "💵",
                        subEmojis = emptyList(),
                        onClick = {
                            lastInteractionMs = currentTimeMillis()
                            onSelect(PaymentMethod.COUNTER, false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        lastInteractionMs = currentTimeMillis()
                        onBack()
                    },
                    modifier = Modifier
                        .width(300.dp)
                        .height(120.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = tr(language, "Back", "返回", "Terug", ja = "戻る", tr = "Geri"),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF22B573),
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PaymentMethodCard(
    title: String,
    emoji: String,
    subEmojis: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier
            .height(280.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmojiVisual(
                emoji = emoji,
                contentDescription = title,
                modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
                fallbackFontSize = 86.sp,
                fallbackColor = if (enabled) Color.Unspecified else Color(0xFF9E9E9E)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.Unspecified else Color(0xFF9E9E9E)
            )

            if (subEmojis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    subEmojis.forEach { subEmoji ->
                        EmojiVisual(
                            emoji = subEmoji,
                            contentDescription = null,
                            modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
                            fallbackFontSize = 22.sp,
                            fallbackColor = if (enabled) Color.Unspecified else Color(0xFF9E9E9E)
                        )
                    }
                }
            }
        }
    }
}
