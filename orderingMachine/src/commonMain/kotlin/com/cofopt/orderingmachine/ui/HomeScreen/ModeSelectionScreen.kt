package com.cofopt.orderingmachine.ui.HomeScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cofopt.orderingmachine.CachedAssetImage
import com.cofopt.orderingmachine.currentTimeMillis
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.cmp.EmojiVisual
import com.cofopt.orderingmachine.tr

@Composable
fun ModeSelectionScreen(
    language: Language,
    onLanguageChange: (Language) -> Unit,
    callNumber: Int? = null,
    onCallNumberDismiss: () -> Unit = {},
    onSelect: (Boolean) -> Unit,
    onDebug: () -> Unit = {}
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var lastClickTime by remember { mutableStateOf(0L) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 700.dp
            val horizontalPadding = if (isCompact) 16.dp else 48.dp
            val verticalPadding = if (isCompact) 16.dp else 40.dp
            val logoSize = if (isCompact) 64.dp else 120.dp
            val welcomeFontSize = if (isCompact) 24.sp else 36.sp
            val modeCardHeight = if (isCompact) 170.dp else 250.dp
            val languageCardHeight = if (isCompact) 72.dp else 88.dp
            val languageColumns = if (isCompact) 3 else Language.supported.size
            val blockSpacing = if (isCompact) 12.dp else 20.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(blockSpacing)
            ) {
                CachedAssetImage(
                    assetPath = "images/logo.png",
                    contentDescription = "logo",
                    modifier = Modifier
                        .size(logoSize)
                        .padding(bottom = 4.dp)
                        .clickable {
                            val currentTime = currentTimeMillis()
                            if (currentTime - lastClickTime < 500) {
                                // 双击检测
                                showPasswordDialog = true
                                passwordInput = ""
                            }
                            lastClickTime = currentTime
                        },
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = tr(
                        language,
                        "Welcome to order",
                        "欢迎点餐",
                        "Welkom bij uw bestelling",
                        ja = "ご注文へようこそ",
                        tr = "Siparişe hoş geldiniz"
                    ),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = welcomeFontSize,
                        color = Color(0xFF1A1A1A),
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isCompact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ModeOptionCard(
                            title = tr(
                                language,
                                "Dine in",
                                "堂食",
                                "Hier eten",
                                ja = "店内飲食",
                                tr = "Restoranda"
                            ),
                            emoji = "🍽️",
                            compact = true,
                            modifier = Modifier.fillMaxWidth().height(modeCardHeight),
                            onClick = { onSelect(true) }
                        )
                        ModeOptionCard(
                            title = tr(
                                language,
                                "Takeaway",
                                "打包",
                                "Meenemen",
                                ja = "お持ち帰り",
                                tr = "Paket servis"
                            ),
                            emoji = "🥡",
                            compact = true,
                            modifier = Modifier.fillMaxWidth().height(modeCardHeight),
                            onClick = { onSelect(false) }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModeOptionCard(
                            title = tr(
                                language,
                                "Dine in",
                                "堂食",
                                "Hier eten",
                                ja = "店内飲食",
                                tr = "Restoranda"
                            ),
                            emoji = "🍽️",
                            compact = false,
                            modifier = Modifier.weight(1f).height(modeCardHeight),
                            onClick = { onSelect(true) }
                        )
                        ModeOptionCard(
                            title = tr(
                                language,
                                "Takeaway",
                                "打包",
                                "Meenemen",
                                ja = "お持ち帰り",
                                tr = "Paket servis"
                            ),
                            emoji = "🥡",
                            compact = false,
                            modifier = Modifier.weight(1f).height(modeCardHeight),
                            onClick = { onSelect(false) }
                        )
                    }
                }

                Text(
                    text = tr(
                        language,
                        "Please select language",
                        "请选择语言",
                        "Selecteer taal a.u.b.",
                        ja = "言語を選択してください",
                        tr = "Lütfen dil seçin"
                    ),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color(0xFF1A1A1A),
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isCompact) 24.sp else 32.sp
                    ),
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 16.dp)
                ) {
                    Language.supported.chunked(languageColumns).forEach { rowLanguages ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowLanguages.forEach { option ->
                                LanguageOption(
                                    emoji = languageEmoji(option),
                                    selected = language == option,
                                    compact = isCompact,
                                    modifier = Modifier.weight(1f).height(languageCardHeight),
                                    onClick = { onLanguageChange(option) }
                                )
                            }

                            repeat(languageColumns - rowLanguages.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        
        // 密码输入对话框
        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = false
                    passwordInput = ""
                },
                title = {
                    Text(
                        text = tr(
                            language,
                            "Enter Password",
                            "输入密码",
                            "Voer wachtwoord in",
                            ja = "パスワード入力",
                            tr = "Şifre girin"
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            tr(
                                language,
                                "Please enter debug password:",
                                "请输入调试密码:",
                                "Voer debugwachtwoord in:",
                                ja = "デバッグ用パスワードを入力してください:",
                                tr = "Lütfen debug şifresini girin:"
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            placeholder = {
                                Text(
                                    tr(
                                        language,
                                        "Password",
                                        "密码",
                                        "Wachtwoord",
                                        ja = "パスワード",
                                        tr = "Şifre"
                                    )
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (passwordInput == "9999") {
                                showPasswordDialog = false
                                passwordInput = ""
                                onDebug()
                            }
                        }
                    ) {
                        Text(tr(language, "Confirm", "确认", "Bevestigen", ja = "確認", tr = "Onayla"))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showPasswordDialog = false
                            passwordInput = ""
                        }
                    ) {
                        Text(tr(language, "Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"))
                    }
                }
            )
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    emoji: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }
    
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .clickable {
                val currentTime = currentTimeMillis()
                if (currentTime - lastClickTime >= 1000) { // 1 second debounce
                    lastClickTime = currentTime
                    onClick()
                }
            },
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 14.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (compact) 72.dp else 96.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(BrandGreen.copy(alpha = 0.12f))
            ) {
                EmojiVisual(
                    emoji = emoji,
                    contentDescription = title,
                    modifier = Modifier.size(if (compact) 40.dp else 52.dp),
                    fallbackFontSize = if (compact) 36.sp else 46.sp
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 44.dp else 58.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = if (compact) 20.sp else 24.sp
                    ),
                    color = Color(0xFF1A1A1A),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    emoji: String,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }
    
    val borderColor = if (selected) BrandGreen else Color(0xFFD8D8D8)
    val backgroundColor = if (selected) BrandGreen.copy(alpha = 0.08f) else Color(0xFFF7F7F7)

    Card(
        onClick = {
            val currentTime = currentTimeMillis()
            if (currentTime - lastClickTime >= 500) { // 0.5 second debounce for language selection
                lastClickTime = currentTime
                onClick()
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 4.dp else 6.dp)
        ) {
            EmojiVisual(
                emoji = emoji,
                contentDescription = emoji,
                modifier = Modifier.size(if (compact) 26.dp else 30.dp),
                fallbackFontSize = if (compact) 20.sp else 24.sp
            )
        }
    }
}

private fun languageEmoji(language: Language): String {
    return when (language) {
        Language.EN -> "🇺🇸"
        Language.ZH -> "🇨🇳"
        Language.NL -> "🇳🇱"
        Language.JA -> "🇯🇵"
        Language.TR -> "🇹🇷"
    }
}

private val BrandGreen = Color(0xFF017133)
