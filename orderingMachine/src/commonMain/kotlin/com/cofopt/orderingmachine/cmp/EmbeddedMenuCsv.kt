package com.cofopt.orderingmachine.cmp

internal val EMBEDDED_MENU_CSV: String = """
category,id,zh,en,nl,ja,tr,price_eur,kitchen_print,chooseVegan,chooseSource,chooseDrink,containsEggs,containsGluten,containsLupin,containsMilk,containsMustard,containsNuts,containsPeanuts,containsCrustaceans,containsCelery,containsSesameSeeds,containsSoybeans,containsFish,containsMolluscs,containsSulphites
Burgers,1,牛肉汉堡,Beef Burger,Rundvleesburger,ビーフバーガー,Sığır Burger,13.21,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Burgers,2,双层牛肉汉堡,Double Beef Burger,Dubbele Rundvleesburger,ダブルビーフバーガー,Duble Sığır Burger,17.21,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Burgers,3,鸡肉汉堡,Chicken Burger,Kipburger,チキンバーガー,Tavuk Burger,12.74,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Burgers,4,双层鸡肉汉堡,Double Chicken Burger,Dubbele Kipburger,ダブルチキンバーガー,Duble Tavuk Burger,16.51,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Burgers,8,鸡肉汉堡,Chicken Burger,Kippenburger,チキンバーガー,Tavuk Burger,8.71,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Burgers,9,双层鸡肉汉堡,Double Chicken Burger,Dubbele Kippenburger,ダブルチキンバーガー,Çift Tavuk Burger,12.5,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Burgers,10,鸡肉汉堡加鸡蛋,Chicken Burger with Egg,Kippenburger met ei,チキンバーガー 卵入り,Yumurtalı Tavuk Burger,10.2,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Burgers,12,双层鸡肉汉堡加鸡蛋,Double Chicken Burger with Egg,Dubbele Kippenburger met ei,ダブルチキンバーガー 卵入り,Yumurtalı Çift Tavuk Burger,13.82,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,13,Ayam Goreng 炸鸡 (5 粒),Ayam Goreng Fried Chicken (5pcs),Ayam Goreng Gebakken Kip (5 stuks),アヤム ゴレン フライドチキン (5 個),Ayam Goreng Kızarmış Tavuk (5 adet),31.5,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,14,Ayam Goreng 炸鸡 (9 粒),Ayam Goreng Fried Chicken (9pcs),Ayam Goreng Gebakken Kip (9 stuks),アヤム ゴレン フライドチキン (9 個),Ayam Goreng Kızarmış Tavuk (9 adet),51.24,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,15,Ayam Goreng 常规 (2 件),Ayam Goreng Regular (2pcs),Ayam Goreng Normaal (2 stuks),アヤムゴレン レギュラー (2 個),Ayam Goreng Normal (2 adet),12.5,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,16,Ayam Goreng 辣味 (2 件),Ayam Goreng Spicy (2pcs),Ayam Goreng Pittig (2 stuks),アヤムゴレン スパイシー (2個),Ayam Goreng Baharatlı (2 adet),12.5,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,17,Ayam Goreng 辣味 (3 件),Ayam Goreng Spicy (3pcs),Ayam Goreng Pittig (3st),アヤム ゴレン スパイシー (3 個),Ayam Goreng Baharatlı (3 adet),16.5,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,19,Ayam Goreng 常规 (3 件),Ayam Goreng Regular (3pcs),Ayam Goreng Normaal (3 stuks),アヤムゴレン レギュラー (3 個),Ayam Goreng Normal (3 adet),16.5,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,20,6 块鸡块,6pcs Chicken Nuggets,6 stuks Kipnuggets,チキンナゲット 6個,6 adet Tavuk Nugget,9.8,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,22,9 块鸡块,9pcs Chicken Nuggets,9 stuks Kipnuggets,チキンナゲット 9個,9 adet Tavuk Nugget,13.5,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Ayam Goreng & Nuggets,24,20 块鸡块,20pcs Chicken Nuggets,20 stuks Kipnuggets,チキンナゲット 20個,20 adet Tavuk Nugget,25.85,TRUE,TRUE,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE
Bubur & Nasi Lemak,25,椰浆饭套餐,Nasi Lemak Set,Nasi Lemak Set,ナシレマック セット,Nasi Lemak Set,5.8,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE
Bubur & Nasi Lemak,26,椰浆饭套餐 A,Nasi Lemak Set A,Nasi Lemak Set A,ナシレマック セットA,Nasi Lemak Set A,15.23,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE
Bubur & Nasi Lemak,27,椰浆饭套餐 B,Nasi Lemak Set B,Nasi Lemak Set B,ナシレマック セットB,Nasi Lemak Set B,17.81,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE
Bubur & Nasi Lemak,28,椰浆饭套餐 + 香辣鸡排,Nasi Lemak Set + Spicy Chicken Cutlet,Nasi Lemak Set + Pittige Kipkotelet,ナシレマック セット + スパイシーチキン カツレツ,Nasi Lemak Set + Baharatlı Tavuk Köfte,16.52,TRUE,TRUE,TRUE,TRUE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE
Bubur & Nasi Lemak,29,椰浆饭套餐 + 鸡肉炸排,Nasi Lemak Set + Chicken Cutlet,Nasi Lemak Set + Kipkotelet,ナシレマック セット + チキン カツレツ,Nasi Lemak Set + Tavuk Köfte,16.47,TRUE,TRUE,TRUE,TRUE,FALSE,TRUE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,FALSE,FALSE,TRUE,TRUE,FALSE,FALSE
Bubur & Nasi Lemak,31,布布尔鸡粥,Bubur Ayam,Bubur Ayam,ブブル アヤム,Bubur Ayam,6.83,TRUE,TRUE,TRUE,TRUE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,TRUE,FALSE,TRUE,FALSE,FALSE,FALSE
Drinks,32,汽泡饮料,Sparkling Drink,Bruisdrank,炭酸ドリンク,Gazlı İçecek,4.56,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,33,可乐,Cola,Cola,コーラ,Kola,4.56,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,34,柠檬青柠汽水,Lemon-Lime Soda,Citroen-limoen frisdrank,レモンライムソーダ,Limon-Lime Gazoz,4.56,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,35,冰柠檬茶,Iced Lemon Tea,Ijsthee met citroen,アイスレモンティー,Buzlu Limon Çayı,6.55,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,36,橙汁,Orange Juice,Sinaasappelsap,オレンジジュース,Portakal Suyu,6.82,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,37,饮用水,Drinking water,Drinkwater,飲料水,İçme suyu,4.39,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,38,巧克力麦芽饮料,Chocolate Malt Drink,Chocolade moutdrank,チョコレート麦芽ドリンク,Çikolatalı Malt İçeceği,7.53,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,39,热巧克力麦芽饮料,Hot Chocolate Malt Drink,Hete chocolade moutdrank,ホットチョコレート麦芽ドリンク,Sıcak Çikolatalı Malt İçeceği,4.85,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,40,热拉茶,Hot Teh Tarik,Hete Teh Tarik,ホット・テー・タリク,Ateşli Tarık,4.98,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
Drinks,41,热茶,Hot Tea,Hete thee,ホットティー,Sıcak Çay,4.98,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE,FALSE
""".trim()
