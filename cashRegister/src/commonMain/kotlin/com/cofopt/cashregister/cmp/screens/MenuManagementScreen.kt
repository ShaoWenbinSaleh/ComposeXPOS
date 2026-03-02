package com.cofopt.cashregister.cmp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cofopt.cashregister.cmp.platform.CashRegisterPlatform
import com.cofopt.cashregister.cmp.utils.formatMoney
import com.cofopt.cashregister.menu.DishState
import com.cofopt.cashregister.utils.LocalLanguage
import com.cofopt.cashregister.utils.localizedName
import com.cofopt.cashregister.utils.tr

@Composable
fun MenuManagementScreen() {
    val dishes by CashRegisterPlatform.dishes.collectAsState()
    val language = LocalLanguage.current

    var editingDish by remember { mutableStateOf<DishState?>(null) }
    var deleteTarget by remember { mutableStateOf<DishState?>(null) }

    fun localizedCategory(category: String): String {
        return when (category) {
            "Noodles" -> tr(language, "Noodles", "面条", "Noedels", ja = "麺類", tr = "Erişte")
            "Dumplings" -> tr(language, "Dumplings", "饺子", "Dumplings", ja = "餃子", tr = "Mantı")
            "Main Course" -> tr(language, "Main Course", "主菜", "Hoofdgerecht", ja = "メイン", tr = "Ana yemek")
            "Side Dish" -> tr(language, "Side Dish", "小菜", "Bijgerecht", ja = "サイド", tr = "Yan yemek")
            "Drinks" -> tr(language, "Drinks", "饮料", "Drankjes", ja = "ドリンク", tr = "İçecekler")
            "Burgers" -> tr(language, "Burgers", "汉堡", "Burgers", ja = "バーガー", tr = "Burgerler")
            "Ayam Goreng & Nuggets", "Fried Chicken & Nuggets" -> tr(
                language,
                "Fried Chicken & Nuggets",
                "炸鸡和鸡块",
                "Gebakken kip en nuggets",
                ja = "フライドチキン＆ナゲット",
                tr = "Kızarmış tavuk ve nugget"
            )
            "Bubur & Nasi Lemak", "Congee & Rice" -> tr(
                language,
                "Congee & Coconut Rice",
                "粥和椰浆饭",
                "Congee en kokosrijst",
                ja = "お粥とココナッツライス",
                tr = "Lapa ve Hindistan cevizli pilav"
            )
            else -> category
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Menu", "菜单", "Menu", ja = "メニュー", tr = "Menü"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        editingDish = DishState(
                            id = "",
                            category = "Noodles",
                            nameZh = "",
                            nameEn = "",
                            nameNl = "",
                            nameJa = "",
                            nameTr = "",
                            priceEur = 0.0,
                            discountedPrice = 0.0,
                            soldOut = false,
                            kitchenPrint = true
                        )
                    }
                ) {
                    Text(tr("Add Dish", "新增菜品", "Nieuw gerecht", ja = "メニュー追加", tr = "Ürün ekle"))
                }
            }

            Text(
                text = tr(
                    "This menu is the single source for OrderingMachine.",
                    "该菜单是 OrderingMachine 的唯一来源。",
                    "Dit menu is de enige bron voor OrderingMachine."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5E6470)
            )

            val grouped = remember(dishes) {
                dishes
                    .groupBy { it.category }
                    .toList()
                    .sortedBy { it.first }
                    .map { (category, list) ->
                        category to list.sortedBy { it.id }
                    }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = when {
                    maxWidth >= 1400.dp -> 4
                    maxWidth >= 980.dp -> 3
                    maxWidth >= 680.dp -> 2
                    else -> 1
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (category, list) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = localizedCategory(category),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${list.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF5E6470),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = Color(0xFFE6E8EC))
                        }

                        items(list, key = { it.id }) { dish ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 132.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dish.id,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF5E6470)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (dish.soldOut) {
                                                Text("SOLD OUT", color = Color(0xFFD94348), fontWeight = FontWeight.Bold)
                                            }
                                            if (!dish.imageBase64.isNullOrBlank()) {
                                                Text("IMG", color = Color(0xFF1BAA62), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Text(
                                        text = dish.localizedName(language),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("€ ${formatMoney(dish.discountedPrice)}", color = Color(0xFF1E88E5))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { editingDish = dish }) {
                                            Text(tr("Edit", "编辑", "Bewerken", ja = "編集", tr = "Düzenle"))
                                        }
                                        TextButton(
                                            onClick = { deleteTarget = dish }
                                        ) {
                                            Text(tr("Delete", "删除", "Verwijderen", ja = "削除", tr = "Sil"), color = Color(0xFFD94348))
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

    if (editingDish != null) {
        val original = editingDish!!
        var id by remember(original.id) { mutableStateOf(original.id) }
        var category by remember(original.id) { mutableStateOf(original.category) }
        var nameZh by remember(original.id) { mutableStateOf(original.nameZh) }
        var nameEn by remember(original.id) { mutableStateOf(original.nameEn) }
        var nameNl by remember(original.id) { mutableStateOf(original.nameNl) }
        var nameJa by remember(original.id) { mutableStateOf(original.nameJa) }
        var nameTr by remember(original.id) { mutableStateOf(original.nameTr) }
        var priceText by remember(original.id) { mutableStateOf(original.priceEur.toString()) }
        var discountedText by remember(original.id) { mutableStateOf(original.discountedPrice.toString()) }
        var soldOut by remember(original.id) { mutableStateOf(original.soldOut) }
        var kitchenPrint by remember(original.id) { mutableStateOf(original.kitchenPrint) }
        var imageBase64 by remember(original.id) { mutableStateOf(original.imageBase64.orEmpty()) }
        var containsEggs by remember(original.id) { mutableStateOf(original.containsEggs) }
        var containsGluten by remember(original.id) { mutableStateOf(original.containsGluten) }
        var containsLupin by remember(original.id) { mutableStateOf(original.containsLupin) }
        var containsMilk by remember(original.id) { mutableStateOf(original.containsMilk) }
        var containsMustard by remember(original.id) { mutableStateOf(original.containsMustard) }
        var containsNuts by remember(original.id) { mutableStateOf(original.containsNuts) }
        var containsPeanuts by remember(original.id) { mutableStateOf(original.containsPeanuts) }
        var containsCrustaceans by remember(original.id) { mutableStateOf(original.containsCrustaceans) }
        var containsCelery by remember(original.id) { mutableStateOf(original.containsCelery) }
        var containsSesameSeeds by remember(original.id) { mutableStateOf(original.containsSesameSeeds) }
        var containsSoybeans by remember(original.id) { mutableStateOf(original.containsSoybeans) }
        var containsFish by remember(original.id) { mutableStateOf(original.containsFish) }
        var containsMolluscs by remember(original.id) { mutableStateOf(original.containsMolluscs) }
        var containsSulphites by remember(original.id) { mutableStateOf(original.containsSulphites) }

        AlertDialog(
            onDismissRequest = { editingDish = null },
            title = { Text(tr("Edit Menu", "编辑菜单", "Menu bewerken", ja = "メニュー編集", tr = "Menüyü düzenle")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = id,
                        onValueChange = { if (original.id.isBlank()) id = it },
                        enabled = original.id.isBlank(),
                        label = { Text("ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it },
                        label = { Text("Name EN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameZh,
                        onValueChange = { nameZh = it },
                        label = { Text("Name ZH") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameNl,
                        onValueChange = { nameNl = it },
                        label = { Text("Name NL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameJa,
                        onValueChange = { nameJa = it },
                        label = { Text("Name JA") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameTr,
                        onValueChange = { nameTr = it },
                        label = { Text("Name TR") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price EUR") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = discountedText,
                        onValueChange = { discountedText = it },
                        label = { Text("Discounted EUR") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = imageBase64,
                        onValueChange = { imageBase64 = it },
                        label = { Text("Image Base64 (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = soldOut, onCheckedChange = { soldOut = it })
                        Text(tr("Sold Out", "售罄", "Uitverkocht", ja = "売り切れ", tr = "Tükendi"))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = kitchenPrint, onCheckedChange = { kitchenPrint = it })
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(tr("Kitchen Print", "后厨打印", "Keuken print", ja = "キッチン印刷", tr = "Mutfak yazdırma"))
                    }

                    Text(
                        text = tr("Allergens", "过敏原", "Allergenen", ja = "アレルゲン", tr = "Alerjenler"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    AllergenToggleRow("Eggs", containsEggs) { containsEggs = it }
                    AllergenToggleRow("Gluten", containsGluten) { containsGluten = it }
                    AllergenToggleRow("Lupin", containsLupin) { containsLupin = it }
                    AllergenToggleRow("Milk", containsMilk) { containsMilk = it }
                    AllergenToggleRow("Mustard", containsMustard) { containsMustard = it }
                    AllergenToggleRow("Nuts", containsNuts) { containsNuts = it }
                    AllergenToggleRow("Peanuts", containsPeanuts) { containsPeanuts = it }
                    AllergenToggleRow("Crustaceans", containsCrustaceans) { containsCrustaceans = it }
                    AllergenToggleRow("Celery", containsCelery) { containsCelery = it }
                    AllergenToggleRow("Sesame Seeds", containsSesameSeeds) { containsSesameSeeds = it }
                    AllergenToggleRow("Soybeans", containsSoybeans) { containsSoybeans = it }
                    AllergenToggleRow("Fish", containsFish) { containsFish = it }
                    AllergenToggleRow("Molluscs", containsMolluscs) { containsMolluscs = it }
                    AllergenToggleRow("Sulphites", containsSulphites) { containsSulphites = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val normalizedId = id.trim()
                        val p = priceText.toDoubleOrNull() ?: return@Button
                        val d = discountedText.toDoubleOrNull() ?: p
                        if (normalizedId.isBlank()) return@Button

                        val updated = original.copy(
                            id = normalizedId,
                            category = category.trim().ifBlank { "Noodles" },
                            nameZh = nameZh,
                            nameEn = nameEn,
                            nameNl = nameNl,
                            nameJa = nameJa,
                            nameTr = nameTr,
                            priceEur = p,
                            discountedPrice = d,
                            soldOut = soldOut,
                            kitchenPrint = kitchenPrint,
                            containsEggs = containsEggs,
                            containsGluten = containsGluten,
                            containsLupin = containsLupin,
                            containsMilk = containsMilk,
                            containsMustard = containsMustard,
                            containsNuts = containsNuts,
                            containsPeanuts = containsPeanuts,
                            containsCrustaceans = containsCrustaceans,
                            containsCelery = containsCelery,
                            containsSesameSeeds = containsSesameSeeds,
                            containsSoybeans = containsSoybeans,
                            containsFish = containsFish,
                            containsMolluscs = containsMolluscs,
                            containsSulphites = containsSulphites,
                            imageBase64 = imageBase64.ifBlank { null }
                        )

                        if (original.id.isNotBlank() && original.id != normalizedId) {
                            CashRegisterPlatform.deleteDish(original.id)
                        }
                        CashRegisterPlatform.upsertDish(updated)
                        editingDish = null
                    }
                ) {
                    Text(tr("Save", "保存", "Opslaan", ja = "保存", tr = "Kaydet"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDish = null }) {
                    Text(tr("Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"))
                }
            }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(tr("Delete Dish", "删除菜品", "Gerecht verwijderen", ja = "メニュー削除", tr = "Ürünü sil")) },
            text = { Text("${target.id} - ${target.localizedName(language)}") },
            confirmButton = {
                Button(
                    onClick = {
                        CashRegisterPlatform.deleteDish(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD94348))
                ) {
                    Text(tr("Delete", "删除", "Verwijderen", ja = "削除", tr = "Sil"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(tr("Cancel", "取消", "Annuleren", ja = "キャンセル", tr = "İptal"))
                }
            }
        )
    }
}

@Composable
private fun AllergenToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
