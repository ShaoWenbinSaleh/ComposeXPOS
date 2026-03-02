package com.cofopt.orderingmachine.ui.OrderingScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.Language
import com.cofopt.orderingmachine.MenuCategory
import com.cofopt.orderingmachine.tr

@Composable
internal fun CategoryTab(
    language: Language,
    category: MenuCategory,
    isSelected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val label = when (category) {
        MenuCategory.NOODLES -> tr(language, "Noodles", "面", "Noedels", ja = "麺類", tr = "Erişte")
        MenuCategory.DUMPLINGS -> tr(language, "Dumplings", "饺子", "Dumplings", ja = "餃子", tr = "Mantı")
        MenuCategory.MAIN_COURSE -> tr(language, "Main course", "主食", "Hoofdgerecht", ja = "メイン", tr = "Ana yemek")
        MenuCategory.SIDE_DISH -> tr(language, "Side dish", "配菜", "Bijgerecht", ja = "サイド", tr = "Yan yemek")
        MenuCategory.DRINKS -> tr(language, "Drinks", "饮品", "Drankjes", ja = "ドリンク", tr = "İçecekler")
        MenuCategory.BURGERS -> tr(language, "Burgers", "汉堡", "Burgers", ja = "バーガー", tr = "Burgerler")
        MenuCategory.AYAM_GORENG_NUGGETS -> tr(
            language,
            "Fried Chicken & Nuggets",
            "炸鸡和鸡块",
            "Gebakken kip en nuggets",
            ja = "フライドチキン＆ナゲット",
            tr = "Kızarmış tavuk ve nugget"
        )
        MenuCategory.BUBUR_NASI_LEMAK -> tr(
            language,
            "Congee & Coconut Rice",
            "粥和椰浆饭",
            "Congee en kokosrijst",
            ja = "お粥とココナッツライス",
            tr = "Lapa ve Hindistan cevizli pilav"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 56.dp else 70.dp)
            .padding(horizontal = if (compact) 6.dp else 10.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White, RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else Color(0xFFE0E3E7),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
