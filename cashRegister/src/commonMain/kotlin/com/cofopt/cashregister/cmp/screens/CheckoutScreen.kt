package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cofopt.cashregister.cmp.components.OrderDetailDialog
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.cmp.utils.formatMoney
import com.cofopt.cashregister.cmp.utils.nowMillis
import com.cofopt.cashregister.menu.DishState
import com.cofopt.cashregister.network.CustomizationPrintLinePayload
import com.cofopt.cashregister.network.OrderItemPayload
import com.cofopt.cashregister.network.OrderPayload
import com.cofopt.cashregister.utils.Language
import com.cofopt.cashregister.utils.LocalLanguage
import com.cofopt.cashregister.utils.tr
import kotlin.random.Random

private data class CustomizationStep(
    val key: String,
    val titleEn: String,
    val titleZh: String,
    val titleNl: String
)

private data class CartEntry(
    val id: String,
    val dishId: String,
    val customizations: Map<String, String>
)

private fun customizationLinesForPrint(customizations: Map<String, String>): List<CustomizationPrintLinePayload> {
    if (customizations.isEmpty()) return emptyList()

    val lines = mutableListOf<CustomizationPrintLinePayload>()
    customizations.forEach { (optionId, rawValue) ->
        when (optionId) {
            "special_vegan" -> {
                val v = when (rawValue) {
                    "meat" -> CustomizationPrintLinePayload("Protein", "蛋白选择", "Meat", "肉类")
                    "vegan" -> CustomizationPrintLinePayload("Protein", "蛋白选择", "Vegan", "纯素")
                    else -> null
                }
                if (v != null) lines += v
            }

            "special_sauce" -> {
                val v = when (rawValue) {
                    "ketchup" -> CustomizationPrintLinePayload("Sauce", "酱料", "Ketchup", "番茄酱")
                    "hot_sauce" -> CustomizationPrintLinePayload("Sauce", "酱料", "Hot sauce", "辣酱")
                    "mayonnaise" -> CustomizationPrintLinePayload("Sauce", "酱料", "Mayonnaise", "蛋黄酱")
                    else -> null
                }
                if (v != null) lines += v
            }

            "special_drink" -> {
                val v = when (rawValue) {
                    "drink1" -> CustomizationPrintLinePayload("Free drink", "附赠饮料", "Drink 1", "饮料1")
                    "drink2" -> CustomizationPrintLinePayload("Free drink", "附赠饮料", "Drink 2", "饮料2")
                    "drink3" -> CustomizationPrintLinePayload("Free drink", "附赠饮料", "Drink 3", "饮料3")
                    else -> null
                }
                if (v != null) lines += v
            }

            else -> {
                lines += CustomizationPrintLinePayload(
                    titleEn = optionId,
                    titleZh = optionId,
                    valueEn = rawValue,
                    valueZh = rawValue
                )
            }
        }
    }

    return lines
}

@Composable
fun CheckoutScreen() {
    val dishes by CashRegisterPlatform.dishes.collectAsState()
    val cart = remember { mutableStateListOf<CartEntry>() }

    var dineIn by remember { mutableStateOf(true) }
    var paymentMethod by remember { mutableStateOf("CASH") }

    var editingDishId by remember { mutableStateOf<String?>(null) }
    var customizingDishId by remember { mutableStateOf<String?>(null) }
    var showOrderDetailDialog by remember { mutableStateOf(false) }
    var lastCreatedOrder by remember { mutableStateOf<OrderPayload?>(null) }

    val editingDish = remember(dishes, editingDishId) {
        editingDishId?.let { id -> dishes.firstOrNull { it.id == id } }
    }

    val customizingDish = remember(dishes, customizingDishId) {
        customizingDishId?.let { id -> dishes.firstOrNull { it.id == id } }
    }
    val currentLanguage = LocalLanguage.current

    fun addCartEntry(dishId: String, customizations: Map<String, String>) {
        val now = nowMillis()
        val id = "CI_${now}_${Random.nextInt(1000, 9999)}"
        cart.add(CartEntry(id = id, dishId = dishId, customizations = customizations))
    }

    fun cartCustomizationText(customizations: Map<String, String>, currentLanguage: Language): String? {
        if (customizations.isEmpty()) return null
        val lines = customizationLinesForPrint(customizations)
        if (lines.isEmpty()) return null
        return lines.joinToString(" · ") {
            val title = tr(currentLanguage, it.titleEn, it.titleZh, it.titleEn)
            val value = tr(currentLanguage, it.valueEn, it.valueZh, it.valueEn)
            "$title: $value"
        }
    }

    fun translateCategory(category: String): String {
        return when (category) {
            "Noodles" -> tr(currentLanguage, "Noodles", "面条", "Noedels", ja = "麺類", tr = "Erişte")
            "Dumplings" -> tr(currentLanguage, "Dumplings", "饺子", "Gnocchi", ja = "餃子", tr = "Mantı")
            "Main Course" -> tr(currentLanguage, "Main Course", "主菜", "Hoofdgerecht", ja = "メイン", tr = "Ana yemek")
            "Side Dish" -> tr(currentLanguage, "Side Dish", "小菜", "Bijgerecht", ja = "サイド", tr = "Yan yemek")
            "Drinks" -> tr(currentLanguage, "Drinks", "饮料", "Drankjes", ja = "ドリンク", tr = "İçecekler")
            "Burgers" -> tr(currentLanguage, "Burgers", "汉堡", "Burgers", ja = "バーガー", tr = "Burgerler")
            "Ayam Goreng & Nuggets", "Fried Chicken & Nuggets" -> tr(
                currentLanguage,
                "Fried Chicken & Nuggets",
                "炸鸡和鸡块",
                "Gebakken kip en nuggets",
                ja = "フライドチキン＆ナゲット",
                tr = "Kızarmış tavuk ve nugget"
            )

            "Bubur & Nasi Lemak", "Congee & Rice" -> tr(
                currentLanguage,
                "Congee & Coconut Rice",
                "粥和椰浆饭",
                "Congee en kokosrijst",
                ja = "お粥とココナッツライス",
                tr = "Lapa ve Hindistan cevizli pilav"
            )

            else -> category
        }
    }

    if (editingDish != null) {
        var priceText by remember(editingDish.id, editingDish.priceEur) {
            mutableStateOf(editingDish.priceEur.toString())
        }
        var discountedText by remember(editingDish.id, editingDish.discountedPrice) {
            mutableStateOf(editingDish.discountedPrice.toString())
        }
        var soldOut by remember(editingDish.id, editingDish.soldOut) {
            mutableStateOf(editingDish.soldOut)
        }

        val priceValue = priceText.toDoubleOrNull()
        val discountedValue = discountedText.toDoubleOrNull()
        val canSave = priceValue != null && discountedValue != null

        AlertDialog(
            onDismissRequest = { editingDishId = null },
            title = { Text(tr("Edit", "编辑", "Bewerken", ja = "編集", tr = "Düzenle")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr(editingDish.nameEn, editingDish.nameZh, editingDish.nameNl), fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(tr("Price", "价格", "Prijs", ja = "価格", tr = "Fiyat")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = discountedText,
                        onValueChange = { discountedText = it },
                        label = { Text(tr("Discount", "折扣价", "Korting", ja = "割引価格", tr = "İndirimli fiyat")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr("Sold out", "售罄", "Uitverkocht", ja = "売り切れ", tr = "Tükendi"))
                        Switch(checked = soldOut, onCheckedChange = { soldOut = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val p = priceText.toDoubleOrNull()
                        val d = discountedText.toDoubleOrNull()
                        if (p != null && d != null) {
                            CashRegisterPlatform.updateDishPrice(editingDish.id, p)
                            CashRegisterPlatform.updateDishDiscountedPrice(editingDish.id, d)
                            CashRegisterPlatform.updateDishSoldOut(editingDish.id, soldOut)
                            editingDishId = null
                        }
                    },
                    enabled = canSave
                ) {
                    Text(tr("Save", "保存", "Opslaan", ja = "保存", tr = "Kaydet"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDishId = null }) {
                    Text(tr("Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"))
                }
            }
        )
    }

    val cartItems: List<Pair<DishState, CartEntry>> = cart.mapNotNull { entry ->
        dishes.firstOrNull { it.id == entry.dishId }?.let { it to entry }
    }

    val total = cartItems.sumOf { (d, _) -> d.effectivePrice() }

    val categoryPalette = listOf(
        Color(0xFF4CAF50),
        Color(0xFF7E57C2),
        Color(0xFFE53935),
        Color(0xFFFBC02D),
        Color(0xFF1E88E5),
        Color(0xFF00ACC1),
        Color(0xFF8E24AA)
    )
    val categories = remember(dishes) { dishes.map { it.category }.distinct() }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val filteredDishes = remember(dishes, selectedCategory) {
        if (selectedCategory == null) dishes else dishes.filter { it.category == selectedCategory }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .width(170.dp)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1114)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = tr("Categories", "分类", "Categorieën", ja = "カテゴリ", tr = "Kategoriler"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val color = categoryPalette[categories.indexOf(cat) % categoryPalette.size]
                            val selected = selectedCategory == cat
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable { selectedCategory = if (selected) null else cat },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) color else color.copy(alpha = 0.85f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = translateCategory(cat),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = dishes.count { it.category == cat }.toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        items(filteredDishes, key = { it.id }) { dish ->
                            val color = categoryPalette[categories.indexOf(dish.category) % categoryPalette.size]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clickable { customizingDishId = dish.id },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = tr(dish.nameEn, dish.nameZh, dish.nameNl),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Text(
                                            text = dish.id,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (dish.soldOut) {
                                            Text(
                                                text = tr("SOLD OUT", "售罄", "UITVERKOCHT", ja = "売り切れ", tr = "TÜKENDİ"),
                                                color = Color.Red,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(start = 6.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "€ ${formatMoney(dish.effectivePrice())}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { editingDishId = dish.id },
                                            modifier = Modifier.height(40.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                        ) {
                                            Text(tr("Edit", "编辑", "Bewerken", ja = "編集", tr = "Düzenle"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cart",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { cart.clear() },
                            enabled = cartItems.isNotEmpty()
                        ) {
                            Text(tr("Clear", "清空", "Wissen", ja = "クリア", tr = "Temizle"))
                        }
                    }

                    if (cartItems.isEmpty()) {
                        Text(
                            text = "Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF6A7078)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cartItems, key = { it.second.id }) { (dish, entry) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tr(dish.nameEn, dish.nameZh, dish.nameNl),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val customizationText = cartCustomizationText(entry.customizations, currentLanguage)
                                        if (customizationText != null) {
                                            Text(
                                                text = customizationText,
                                                fontSize = 11.sp,
                                                color = Color(0xFF6A7078),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = "€ ${formatMoney(dish.effectivePrice())}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RadioButton(selected = dineIn, onClick = { dineIn = true })
                            Text(tr("Dine in", "堂食", "Hier eten", ja = "店内飲食", tr = "Restoranda"))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RadioButton(selected = !dineIn, onClick = { dineIn = false })
                            Text(tr("Takeaway", "打包", "Meenemen", ja = "お持ち帰り", tr = "Paket servis"))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { paymentMethod = "CASH" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (paymentMethod == "CASH") Color(0xFF2E7D32) else Color.Unspecified
                            )
                        ) {
                            Text(tr("Cash", "现金", "Contant"))
                        }
                        OutlinedButton(
                            onClick = { paymentMethod = "CARD" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (paymentMethod == "CARD") Color(0xFF1565C0) else Color.Unspecified
                            )
                        ) {
                            Text(tr("Card", "刷卡", "Pinpas"))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Tax (VAT 9%)", "税 (VAT 9%)", "BTW (9%)", ja = "税金 (VAT 9%)", tr = "Vergi (KDV %9)"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6A7078)
                        )
                        Text(
                            text = "€ ${formatMoney(total * 0.09)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6A7078)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Total", "总计", "Totaal", ja = "合計", tr = "Toplam"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "€ ${formatMoney(total)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (cartItems.isEmpty()) return@Button
                            val now = nowMillis()
                            val orderId = "CR_${now}_${Random.nextInt(1000, 9999)}"
                            val payload = OrderPayload(
                                orderId = orderId,
                                createdAtMillis = now,
                                source = "CHECKOUT",
                                dineIn = dineIn,
                                paymentMethod = paymentMethod,
                                total = total,
                                items = cartItems.map { (dish, entry) ->
                                    val customizations = entry.customizations
                                    OrderItemPayload(
                                        menuItemId = dish.id,
                                        nameEn = dish.nameEn,
                                        nameZh = dish.nameZh,
                                        nameNl = dish.nameNl,
                                        quantity = 1,
                                        unitPrice = dish.effectivePrice(),
                                        customizations = customizations,
                                        customizationLines = customizationLinesForPrint(customizations)
                                    )
                                }
                            )
                            CashRegisterPlatform.addOrder(payload)
                            lastCreatedOrder = payload
                            showOrderDetailDialog = true
                        },
                        enabled = cartItems.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(tr("Pay", "收款", "Betalen", ja = "会計", tr = "Ödeme"))
                    }
                }
            }
        }
    }

    customizingDish?.let { dish ->
        var selections by remember(dish.id) {
            mutableStateOf<Map<String, String>>(
                buildMap {
                    if (dish.chooseVegan) put("special_vegan", "meat")
                    if (dish.chooseSource) put("special_sauce", "ketchup")
                    if (dish.chooseDrink) put("special_drink", "drink1")
                }
            )
        }

        val steps = remember(dish.id) {
            buildList {
                if (dish.chooseVegan) add(CustomizationStep("special_vegan", "Protein Choice", "蛋白选择", "Eiwitkeuze"))
                if (dish.chooseSource) add(CustomizationStep("special_sauce", "Free Sauce", "附赠酱料", "Gratis saus"))
                if (dish.chooseDrink) add(CustomizationStep("special_drink", "Free Drink", "附赠饮料", "Gratis drankje"))
            }
        }

        var currentStepIndex by remember(dish.id) { mutableStateOf(0) }

        fun selectionLabelFor(step: CustomizationStep): String? {
            val value = selections[step.key] ?: return null
            return when (step.key) {
                "special_vegan" -> when (value) {
                    "meat" -> "[MEAT] " + tr(currentLanguage, "Meat", "肉类", "Vlees", ja = "肉", tr = "Et")
                    "vegan" -> "[VEGAN] " + tr(currentLanguage, "Vegan", "纯素", "Vegan", ja = "ヴィーガン", tr = "Vegan")
                    else -> null
                }

                "special_sauce" -> when (value) {
                    "ketchup" -> "[KETCHUP] " + tr(currentLanguage, "Ketchup", "番茄酱", "Ketchup", ja = "ケチャップ", tr = "Ketçap")
                    "hot_sauce" -> "[HOT] " + tr(currentLanguage, "Hot Sauce", "辣酱", "Pittige saus", ja = "ホットソース", tr = "Acı sos")
                    "mayonnaise" -> "[MAYO] " + tr(currentLanguage, "Mayonnaise", "蛋黄酱", "Mayonaise", ja = "マヨネーズ", tr = "Mayonez")
                    else -> null
                }

                "special_drink" -> when (value) {
                    "drink1" -> "[D1] " + tr(currentLanguage, "Drink 1", "饮料1", "Drank 1", ja = "ドリンク1", tr = "İçecek 1")
                    "drink2" -> "[D2] " + tr(currentLanguage, "Drink 2", "饮料2", "Drank 2", ja = "ドリンク2", tr = "İçecek 2")
                    "drink3" -> "[D3] " + tr(currentLanguage, "Drink 3", "饮料3", "Drank 3", ja = "ドリンク3", tr = "İçecek 3")
                    else -> null
                }

                else -> value
            }
        }

        fun goToStep(index: Int) {
            if (steps.isNotEmpty()) {
                currentStepIndex = index.coerceIn(0, steps.lastIndex)
            }
        }

        fun goNext() {
            if (currentStepIndex < steps.lastIndex) {
                currentStepIndex += 1
            }
        }

        fun goPrevious() {
            if (steps.isEmpty()) return
            goToStep((currentStepIndex - 1).coerceAtLeast(0))
        }

        fun updateSelection(key: String, value: String) {
            val nextSelections = selections.toMutableMap().apply { put(key, value) }.toMap()
            selections = nextSelections

            if (steps.isNotEmpty() && currentStepIndex == steps.lastIndex) {
                addCartEntry(dish.id, nextSelections)
                customizingDishId = null
                return
            }
            goNext()
        }

        AlertDialog(
            onDismissRequest = { customizingDishId = null },
            modifier = Modifier.size(width = 1000.dp, height = 700.dp),
            title = {
                Text(
                    text = tr(dish.nameEn, dish.nameZh, dish.nameNl, dish.nameJa, dish.nameTr),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "€ ${formatMoney(dish.effectivePrice())}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE60000)
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.width(190.dp)) {
                            val pendingLabel = tr("Pending", "待选择", "In afwachting", ja = "未選択", tr = "Bekliyor")
                            steps.forEachIndexed { index, step ->
                                val selected = index == currentStepIndex
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) Color(0xFF1E88E5) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = tr(step.titleEn, step.titleZh, step.titleNl),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color.White else Color.Unspecified,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val label = selectionLabelFor(step) ?: pendingLabel
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = if (selected) Color.White else Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            val step = steps.getOrNull(currentStepIndex)
                            if (step != null) {
                                Text(
                                    text = tr(step.titleEn, step.titleZh, step.titleNl),
                                    fontWeight = FontWeight.Bold
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        when (step.key) {
                                            "special_vegan" -> {
                                                listOf(
                                                    "[MEAT] ${tr("Meat", "肉类", "Vlees", ja = "肉", tr = "Et")}" to "meat",
                                                    "[VEGAN] ${tr("Vegan", "纯素", "Vegan", ja = "ヴィーガン", tr = "Vegan")}" to "vegan"
                                                ).forEach { (label, value) ->
                                                    Button(
                                                        onClick = { updateSelection(step.key, value) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (selections[step.key] == value) Color(0xFF1E88E5) else Color.Gray
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(56.dp)
                                                    ) {
                                                        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            "special_sauce" -> {
                                                listOf(
                                                    "[KETCHUP] ${tr("Ketchup", "番茄酱", "Ketchup", ja = "ケチャップ", tr = "Ketçap")}" to "ketchup",
                                                    "[HOT] ${tr("Hot Sauce", "辣酱", "Pittige saus", ja = "ホットソース", tr = "Acı sos")}" to "hot_sauce",
                                                    "[MAYO] ${tr("Mayonnaise", "蛋黄酱", "Mayonaise", ja = "マヨネーズ", tr = "Mayonez")}" to "mayonnaise"
                                                ).forEach { (label, value) ->
                                                    Button(
                                                        onClick = { updateSelection(step.key, value) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (selections[step.key] == value) Color(0xFF1E88E5) else Color.Gray
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(56.dp)
                                                    ) {
                                                        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            "special_drink" -> {
                                                listOf(
                                                    "[D1] ${tr("Drink 1", "饮料1", "Drank 1", ja = "ドリンク1", tr = "İçecek 1")}" to "drink1",
                                                    "[D2] ${tr("Drink 2", "饮料2", "Drank 2", ja = "ドリンク2", tr = "İçecek 2")}" to "drink2",
                                                    "[D3] ${tr("Drink 3", "饮料3", "Drank 3", ja = "ドリンク3", tr = "İçecek 3")}" to "drink3"
                                                ).forEach { (label, value) ->
                                                    Button(
                                                        onClick = { updateSelection(step.key, value) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (selections[step.key] == value) Color(0xFF1E88E5) else Color.Gray
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(56.dp)
                                                    ) {
                                                        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val isLastStep = steps.isEmpty() || currentStepIndex == steps.lastIndex
                if (isLastStep) {
                    Button(
                        onClick = {
                            addCartEntry(dish.id, selections)
                            customizingDishId = null
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(tr("Add to Cart", "加入购物车", "Toevoegen aan winkelwagen", ja = "カートに追加", tr = "Sepete ekle"), fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = { goNext() },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(tr("Next", "下一步", "Volgende", ja = "次へ", tr = "İleri"), fontSize = 14.sp)
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { goPrevious() },
                        enabled = steps.isNotEmpty() && currentStepIndex > 0,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(tr("Previous", "上一步", "Vorige", ja = "戻る", tr = "Geri"), fontSize = 14.sp)
                    }
                    TextButton(
                        onClick = { customizingDishId = null },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(tr("Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"), fontSize = 14.sp)
                    }
                }
            }
        )
    }

    if (showOrderDetailDialog && lastCreatedOrder != null) {
        OrderDetailDialog(
            order = lastCreatedOrder!!,
            onDismiss = {
                showOrderDetailDialog = false
                lastCreatedOrder = null
            },
            onCancel = {
                CashRegisterPlatform.removeOrder(lastCreatedOrder!!.orderId)
                showOrderDetailDialog = false
                lastCreatedOrder = null
            },
            onCashCheckout = {
                val orderId = lastCreatedOrder?.orderId ?: return@OrderDetailDialog
                CashRegisterPlatform.updateOrderPaymentMethod(orderId, "CASH")
                CashRegisterPlatform.updateOrderPaymentStatus(orderId, "PAID")
            },
            onCardCheckout = {
                val orderId = lastCreatedOrder?.orderId ?: return@OrderDetailDialog
                CashRegisterPlatform.updateOrderPaymentMethod(orderId, "CARD")
                CashRegisterPlatform.updateOrderPaymentStatus(orderId, "PAID")
            },
            onPaymentSuccess = {
                cart.clear()
                showOrderDetailDialog = false
                lastCreatedOrder = null
            }
        )
    }
}
