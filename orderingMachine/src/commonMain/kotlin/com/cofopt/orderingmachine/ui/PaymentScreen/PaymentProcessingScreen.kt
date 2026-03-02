package com.cofopt.orderingmachine.ui.PaymentScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.tr
import kotlinx.coroutines.delay

@Composable
fun PaymentProcessingScreen(
    language: Language,
    posTriggerFailed: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onTimeout: (() -> Unit)? = null,
    onDebugSuccess: (() -> Unit)? = null,
    onDebugFail: (() -> Unit)? = null
) {
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var hasTimedOut by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableStateOf(60) }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0 && !hasTimedOut) {
            delay(1000)
            secondsRemaining--
        }
        if (!hasTimedOut) {
            hasTimedOut = true
            showTimeoutDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💳",
            fontSize = 120.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = tr(language, "Processing Payment...", "正在支付...", "Betaling verwerken...", ja = "支払い処理中...", tr = "Ödeme işleniyor..."),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = tr(
                language,
                "Please follow the instructions on the terminal",
                "请在终端上完成操作",
                "Volg de instructies op de terminal",
                ja = "端末の案内に従って操作してください",
                tr = "Lütfen terminaldeki talimatları izleyin"
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (posTriggerFailed) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = tr(
                    language,
                    "Sorry, POS wake-up failed. Please click any number key on the POS machine and then pay.",
                    "抱歉，POS机唤醒失败。请在POS机上点击任意数字键后支付。",
                    "Sorry, POS wake-up mislukt. Klik op een willekeurige cijfertoets op de POS-machine en betaal daarna.",
                    ja = "申し訳ありません。POS端末の起動に失敗しました。POSで任意の数字キーを押してからお支払いください。",
                    tr = "Üzgünüz, POS uyandırma başarısız oldu. POS cihazında herhangi bir rakam tuşuna basıp ardından ödeme yapın."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color(0xFF22B573),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (onDebugSuccess != null || onDebugFail != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDebugFail != null) {
                    Button(
                        onClick = { onDebugFail() },
                        modifier = Modifier
                            .width(200.dp)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = tr(language, "Debug Fail", "调试失败", "Debug Fail", ja = "デバッグ失敗", tr = "Debug başarısız"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onDebugSuccess != null) {
                    Button(
                        onClick = { onDebugSuccess() },
                        modifier = Modifier
                            .width(200.dp)
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22B573),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = tr(language, "Debug Success", "调试成功", "Debug Succes", ja = "デバッグ成功", tr = "Debug başarılı"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (onCancel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCancel) {
                Text(tr(language, "Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"))
            }
        }
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = tr(language, "Payment Timeout", "支付超时", "Betaling Time-out", ja = "支払いタイムアウト", tr = "Ödeme zaman aşımı"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = tr(
                        language,
                        "Payment timeout. Please retry or select payment at counter.",
                        "支付超时。请重试或选择在收银台支付。",
                        "Betaling time-out. Probeer het opnieuw of betaal bij de kassa.",
                        ja = "支払いがタイムアウトしました。再試行するか、レジでお支払いください。",
                        tr = "Ödeme zaman aşımına uğradı. Lütfen tekrar deneyin veya kasada ödeyin."
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTimeoutDialog = false
                        onTimeout?.invoke()
                    }
                ) {
                    Text(
                        text = tr(language, "OK", "确定", "OK", ja = "OK", tr = "Tamam"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Composable
fun CounterPaymentProcessingScreen(language: Language) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = Color(0xFF22B573),
                strokeWidth = 6.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = tr(language, "Processing order...", "正在处理订单...", "Bestelling verwerken...", ja = "注文を処理中...", tr = "Sipariş işleniyor..."),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
