package com.cofopt.callingmachine.cmp

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun CallingMachineScreen(
    preparing: List<Int>,
    ready: List<Int>,
    preparingLabel: String,
    readyLabel: String,
    statusText: String,
    isConnected: Boolean,
    alertOverlayNumber: Int?,
    alertOverlayNonce: Int,
    isPreparingNumber: (Int) -> Boolean = { false },
) {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isConnected) {
                    Text(
                        text = "Calling Machine",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = preparingLabel,
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        NumberGrid(
                            numbers = preparing,
                            textColor = Color.White,
                            isPreparingGrid = true,
                            isPreparingNumber = isPreparingNumber
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = readyLabel,
                            color = Color(0xFFFFD700),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        ReadyGridWithAlertOverlay(
                            numbers = ready,
                            textColor = Color(0xFFFFD700),
                            alertOverlayNumber = alertOverlayNumber,
                            alertOverlayNonce = alertOverlayNonce
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyGridWithAlertOverlay(
    numbers: List<Int>,
    textColor: Color,
    alertOverlayNumber: Int?,
    alertOverlayNonce: Int
) {
    var showOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(alertOverlayNonce) {
        if (alertOverlayNumber == null) return@LaunchedEffect
        showOverlay = true
        delay(5000)
        showOverlay = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showOverlay && alertOverlayNumber != null) {
            AlertBlinkOverlay(
                number = alertOverlayNumber,
                modifier = Modifier.fillMaxSize()
            )
        }

        NumberGrid(
            numbers = numbers,
            textColor = textColor,
            isPreparingGrid = false,
            isPreparingNumber = { false }
        )
    }
}

@Composable
private fun AlertBlinkOverlay(number: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "alert_blink")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_alpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .border(
                    width = 4.dp,
                    color = Color(0xFFFFD700).copy(alpha = alpha),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = Color(0xFFFFD700).copy(alpha = alpha),
                fontSize = 180.sp,
                fontWeight = FontWeight.Thin,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NumberGrid(
    numbers: List<Int>,
    textColor: Color,
    isPreparingGrid: Boolean = false,
    isPreparingNumber: (Int) -> Boolean = { false }
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(numbers) { num ->
            val isNewPreparing = isPreparingGrid && isPreparingNumber(num)
            val transition = rememberInfiniteTransition(label = "prep_scale_$num")
            val animatedScale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "prep_scale_anim_$num"
            )
            val scaleValue = if (isNewPreparing) animatedScale else 1f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .height(90.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = num.toString(),
                    color = textColor,
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Thin,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.scale(scaleValue)
                )
            }
        }
    }
}
