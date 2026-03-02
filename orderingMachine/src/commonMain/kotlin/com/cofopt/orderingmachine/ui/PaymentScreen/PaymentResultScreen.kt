package com.cofopt.orderingmachine.ui.PaymentScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.PaymentMethod
import com.cofopt.orderingmachine.tr
import kotlinx.coroutines.delay

@Composable
fun PaymentResultScreen(
    language: Language,
    paymentMethod: PaymentMethod?,
    callNumber: Int?,
    cardPaymentFailed: Boolean = false,
    cashRegisterCreateFailed: Boolean = false,
    cashRegisterCreateFailedWasPaid: Boolean = false,
    onNextCustomer: () -> Unit,
) {
    val brandGreen = Color(0xFF22B573)

    var secondsRemaining by remember { mutableStateOf(10) }
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(hasNavigated) {
        if (hasNavigated) return@LaunchedEffect

        while (secondsRemaining > 0 && !hasNavigated) {
            delay(1000)
            secondsRemaining -= 1
        }

        if (!hasNavigated) {
            hasNavigated = true
            onNextCustomer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandGreen)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tr(language, "Call Number", "取餐号", "Afhaalnummer", ja = "受取番号", tr = "Çağrı numarası"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (cashRegisterCreateFailed && callNumber != null) {
                    callNumber.toString().padStart(4, '0')
                } else {
                    callNumber?.toString() ?: "—"
                },
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (cardPaymentFailed && paymentMethod == PaymentMethod.COUNTER) {
                Text(
                    text = tr(
                        language,
                        "Sorry, this machine is faulty and cannot support card payment. Please pay by card or cash at the counter.",
                        "抱歉，该机器故障无法支持刷卡。请到前台刷卡或现金支付。",
                        "Sorry, deze machine is defect en ondersteunt geen kaartbetaling. Betaal met kaart of contant bij de kassa.",
                        ja = "申し訳ありません。この端末は故障しておりカード決済に対応できません。レジでカードまたは現金でお支払いください。",
                        tr = "Üzgünüz, bu cihaz arızalı ve kart ödemesini desteklemiyor. Lütfen kasada kart veya nakit ile ödeme yapın."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (cashRegisterCreateFailed) {
                val message = if (cashRegisterCreateFailedWasPaid) {
                    tr(
                        language,
                        "Cannot connect to cashier. Please bring the receipt to the counter and contact staff.",
                        "无法连接到收银台。请凭小票到收银台与工作人员联系。",
                        "Kan geen verbinding maken met de kassa. Neem uw bon mee naar de balie en neem contact op met het personeel.",
                        ja = "レジに接続できません。レシートを持ってレジへ行き、スタッフにお声がけください。",
                        tr = "Kasaya bağlanılamıyor. Lütfen fişle kasaya gidin ve personelle iletişime geçin."
                    )
                } else {
                    tr(
                        language,
                        "Cannot connect to cashier. Please bring the receipt to the counter to pay and contact staff.",
                        "无法连接到收银台。请凭小票到收银台支付并与工作人员联系。",
                        "Kan geen verbinding maken met de kassa. Neem uw bon mee naar de balie om te betalen en neem contact op met het personeel.",
                        ja = "レジに接続できません。レシートを持ってレジでお支払いのうえ、スタッフにお声がけください。",
                        tr = "Kasaya bağlanılamıyor. Lütfen fişle kasaya gidip ödeme yapın ve personelle iletişime geçin."
                    )
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            val mainMessage = when (paymentMethod) {
                PaymentMethod.CARD -> tr(language, "Payment Successful!", "支付成功!", "Betaling geslaagd!", ja = "お支払いが完了しました！", tr = "Ödeme başarılı!")
                PaymentMethod.COUNTER -> tr(
                    language,
                    "Please take this receipt to the counter for payment",
                    "请带小票到柜台付款",
                    "Neem deze bon mee naar de balie om te betalen",
                    ja = "このレシートをレジへお持ちいただき、お支払いください",
                    tr = "Lütfen bu fişle kasaya gidip ödeme yapın"
                )

                else -> tr(language, "Done", "完成", "Klaar", ja = "完了", tr = "Tamamlandı")
            }

            Text(
                text = mainMessage,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = tr(
                    language,
                    "Returning to home in ${secondsRemaining}s",
                    "${secondsRemaining} 秒后返回首页",
                    "Terug naar start in ${secondsRemaining}s",
                    ja = "${secondsRemaining}秒後にホームへ戻ります",
                    tr = "${secondsRemaining} sn sonra ana sayfaya dönülüyor"
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (!hasNavigated) {
                        hasNavigated = true
                        onNextCustomer()
                    }
                },
                modifier = Modifier
                    .width(360.dp)
                    .height(120.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = tr(language, "Next Customer", "下一位顾客", "Volgende klant", ja = "次のお客様", tr = "Sıradaki müşteri"),
                    fontWeight = FontWeight.Bold,
                    color = brandGreen,
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }
    }
}
