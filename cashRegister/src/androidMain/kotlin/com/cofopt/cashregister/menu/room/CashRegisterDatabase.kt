package com.cofopt.cashregister.menu.room

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DishEntity::class],
    version = 9,
    exportSchema = false
)
abstract class CashRegisterDatabase : RoomDatabase() {
    abstract fun dishDao(): DishDao

    companion object {
        @Volatile private var INSTANCE: CashRegisterDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dishes_new (
                        id TEXT NOT NULL,
                        category TEXT NOT NULL,
                        name_zh TEXT NOT NULL,
                        name_en TEXT NOT NULL,
                        name_nl TEXT NOT NULL,
                        price_eur REAL NOT NULL,
                        discounted_price_eur REAL NOT NULL,
                        sold_out INTEGER NOT NULL,
                        kitchen_print INTEGER NOT NULL,
                        contains_eggs INTEGER NOT NULL,
                        contains_gluten INTEGER NOT NULL,
                        contains_lupin INTEGER NOT NULL,
                        contains_milk INTEGER NOT NULL,
                        contains_mustard INTEGER NOT NULL,
                        contains_nuts INTEGER NOT NULL,
                        contains_peanuts INTEGER NOT NULL,
                        contains_crustaceans INTEGER NOT NULL,
                        contains_celery INTEGER NOT NULL,
                        contains_sesame_seeds INTEGER NOT NULL,
                        contains_soybeans INTEGER NOT NULL,
                        contains_fish INTEGER NOT NULL,
                        contains_molluscs INTEGER NOT NULL,
                        contains_sulphites INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR REPLACE INTO dishes_new (
                        id, category, name_zh, name_en, name_nl,
                        price_eur, discounted_price_eur, sold_out,
                        kitchen_print,
                        contains_eggs, contains_gluten, contains_lupin, contains_milk,
                        contains_mustard, contains_nuts, contains_peanuts,
                        contains_crustaceans, contains_celery, contains_sesame_seeds,
                        contains_soybeans, contains_fish, contains_molluscs, contains_sulphites
                    )
                    SELECT
                        CAST(id AS TEXT), category, name_zh, name_en, name_nl,
                        price_eur, discounted_price_eur, sold_out,
                        kitchen_print,
                        contains_eggs, contains_gluten, contains_lupin, contains_milk,
                        contains_mustard, contains_nuts, contains_peanuts,
                        contains_crustaceans, contains_celery, contains_sesame_seeds,
                        contains_soybeans, contains_fish, contains_molluscs, contains_sulphites
                    FROM dishes
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE dishes")
                db.execSQL("ALTER TABLE dishes_new RENAME TO dishes")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dishes ADD COLUMN choose_vegan INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dishes ADD COLUMN choose_noodle_type INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dishes ADD COLUMN choose_spicy INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dishes ADD COLUMN choose_extra INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dishes ADD COLUMN choose_soup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dishes ADD COLUMN choose_meat INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE dishes ADD COLUMN choose_noodle_rice INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dishes ADD COLUMN image_base64 TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dishes ADD COLUMN name_ja TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dishes ADD COLUMN name_tr TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate table to normalize column metadata (especially default values)
                // so schema exactly matches Room's expected TableInfo for version 7.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dishes_new (
                        id TEXT NOT NULL,
                        category TEXT NOT NULL,
                        name_zh TEXT NOT NULL,
                        name_en TEXT NOT NULL,
                        name_nl TEXT NOT NULL,
                        name_ja TEXT NOT NULL,
                        name_tr TEXT NOT NULL,
                        price_eur REAL NOT NULL,
                        discounted_price_eur REAL NOT NULL,
                        sold_out INTEGER NOT NULL,
                        kitchen_print INTEGER NOT NULL,
                        choose_vegan INTEGER NOT NULL,
                        choose_noodle_type INTEGER NOT NULL,
                        choose_spicy INTEGER NOT NULL,
                        choose_extra INTEGER NOT NULL,
                        choose_soup INTEGER NOT NULL,
                        choose_meat INTEGER NOT NULL,
                        choose_noodle_rice INTEGER NOT NULL,
                        contains_eggs INTEGER NOT NULL,
                        contains_gluten INTEGER NOT NULL,
                        contains_lupin INTEGER NOT NULL,
                        contains_milk INTEGER NOT NULL,
                        contains_mustard INTEGER NOT NULL,
                        contains_nuts INTEGER NOT NULL,
                        contains_peanuts INTEGER NOT NULL,
                        contains_crustaceans INTEGER NOT NULL,
                        contains_celery INTEGER NOT NULL,
                        contains_sesame_seeds INTEGER NOT NULL,
                        contains_soybeans INTEGER NOT NULL,
                        contains_fish INTEGER NOT NULL,
                        contains_molluscs INTEGER NOT NULL,
                        contains_sulphites INTEGER NOT NULL,
                        image_base64 TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO dishes_new (
                        id, category, name_zh, name_en, name_nl, name_ja, name_tr,
                        price_eur, discounted_price_eur, sold_out, kitchen_print,
                        choose_vegan, choose_noodle_type, choose_spicy, choose_extra,
                        choose_soup, choose_meat, choose_noodle_rice,
                        contains_eggs, contains_gluten, contains_lupin, contains_milk,
                        contains_mustard, contains_nuts, contains_peanuts,
                        contains_crustaceans, contains_celery, contains_sesame_seeds,
                        contains_soybeans, contains_fish, contains_molluscs, contains_sulphites,
                        image_base64
                    )
                    SELECT
                        ${exprFor(db, "dishes", "id", "''")},
                        ${exprFor(db, "dishes", "category", "''")},
                        ${exprFor(db, "dishes", "name_zh", "''")},
                        ${exprFor(db, "dishes", "name_en", "''")},
                        ${exprFor(db, "dishes", "name_nl", "''")},
                        ${exprFor(db, "dishes", "name_ja", "''")},
                        ${exprFor(db, "dishes", "name_tr", "''")},
                        ${exprFor(db, "dishes", "price_eur", "0")},
                        ${exprFor(db, "dishes", "discounted_price_eur", "0")},
                        ${exprFor(db, "dishes", "sold_out", "0")},
                        ${exprFor(db, "dishes", "kitchen_print", "0")},
                        ${exprFor(db, "dishes", "choose_vegan", "0")},
                        ${exprFor(db, "dishes", "choose_noodle_type", "0")},
                        ${exprFor(db, "dishes", "choose_spicy", "0")},
                        ${exprFor(db, "dishes", "choose_extra", "0")},
                        ${exprFor(db, "dishes", "choose_soup", "0")},
                        ${exprFor(db, "dishes", "choose_meat", "0")},
                        ${exprFor(db, "dishes", "choose_noodle_rice", "0")},
                        ${exprFor(db, "dishes", "contains_eggs", "0")},
                        ${exprFor(db, "dishes", "contains_gluten", "0")},
                        ${exprFor(db, "dishes", "contains_lupin", "0")},
                        ${exprFor(db, "dishes", "contains_milk", "0")},
                        ${exprFor(db, "dishes", "contains_mustard", "0")},
                        ${exprFor(db, "dishes", "contains_nuts", "0")},
                        ${exprFor(db, "dishes", "contains_peanuts", "0")},
                        ${exprFor(db, "dishes", "contains_crustaceans", "0")},
                        ${exprFor(db, "dishes", "contains_celery", "0")},
                        ${exprFor(db, "dishes", "contains_sesame_seeds", "0")},
                        ${exprFor(db, "dishes", "contains_soybeans", "0")},
                        ${exprFor(db, "dishes", "contains_fish", "0")},
                        ${exprFor(db, "dishes", "contains_molluscs", "0")},
                        ${exprFor(db, "dishes", "contains_sulphites", "0")},
                        ${exprFor(db, "dishes", "image_base64", "NULL")}
                    FROM dishes
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE dishes")
                db.execSQL("ALTER TABLE dishes_new RENAME TO dishes")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addBooleanColumnIfMissing(db, "dishes", "choose_source")
                addBooleanColumnIfMissing(db, "dishes", "choose_drink")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dishes_new (
                        id TEXT NOT NULL,
                        category TEXT NOT NULL,
                        name_zh TEXT NOT NULL,
                        name_en TEXT NOT NULL,
                        name_nl TEXT NOT NULL,
                        name_ja TEXT NOT NULL,
                        name_tr TEXT NOT NULL,
                        price_eur REAL NOT NULL,
                        discounted_price_eur REAL NOT NULL,
                        sold_out INTEGER NOT NULL,
                        kitchen_print INTEGER NOT NULL,
                        choose_vegan INTEGER NOT NULL,
                        choose_source INTEGER NOT NULL,
                        choose_drink INTEGER NOT NULL,
                        contains_eggs INTEGER NOT NULL,
                        contains_gluten INTEGER NOT NULL,
                        contains_lupin INTEGER NOT NULL,
                        contains_milk INTEGER NOT NULL,
                        contains_mustard INTEGER NOT NULL,
                        contains_nuts INTEGER NOT NULL,
                        contains_peanuts INTEGER NOT NULL,
                        contains_crustaceans INTEGER NOT NULL,
                        contains_celery INTEGER NOT NULL,
                        contains_sesame_seeds INTEGER NOT NULL,
                        contains_soybeans INTEGER NOT NULL,
                        contains_fish INTEGER NOT NULL,
                        contains_molluscs INTEGER NOT NULL,
                        contains_sulphites INTEGER NOT NULL,
                        image_base64 TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO dishes_new (
                        id, category, name_zh, name_en, name_nl, name_ja, name_tr,
                        price_eur, discounted_price_eur, sold_out, kitchen_print,
                        choose_vegan, choose_source, choose_drink,
                        contains_eggs, contains_gluten, contains_lupin, contains_milk,
                        contains_mustard, contains_nuts, contains_peanuts,
                        contains_crustaceans, contains_celery, contains_sesame_seeds,
                        contains_soybeans, contains_fish, contains_molluscs, contains_sulphites,
                        image_base64
                    )
                    SELECT
                        ${exprFor(db, "dishes", "id", "''")},
                        ${exprFor(db, "dishes", "category", "''")},
                        ${exprFor(db, "dishes", "name_zh", "''")},
                        ${exprFor(db, "dishes", "name_en", "''")},
                        ${exprFor(db, "dishes", "name_nl", "''")},
                        ${exprFor(db, "dishes", "name_ja", "''")},
                        ${exprFor(db, "dishes", "name_tr", "''")},
                        ${exprFor(db, "dishes", "price_eur", "0")},
                        ${exprFor(db, "dishes", "discounted_price_eur", "0")},
                        ${exprFor(db, "dishes", "sold_out", "0")},
                        ${exprFor(db, "dishes", "kitchen_print", "0")},
                        ${exprFor(db, "dishes", "choose_vegan", "0")},
                        ${exprFor(db, "dishes", "choose_source", "0")},
                        ${exprFor(db, "dishes", "choose_drink", "0")},
                        ${exprFor(db, "dishes", "contains_eggs", "0")},
                        ${exprFor(db, "dishes", "contains_gluten", "0")},
                        ${exprFor(db, "dishes", "contains_lupin", "0")},
                        ${exprFor(db, "dishes", "contains_milk", "0")},
                        ${exprFor(db, "dishes", "contains_mustard", "0")},
                        ${exprFor(db, "dishes", "contains_nuts", "0")},
                        ${exprFor(db, "dishes", "contains_peanuts", "0")},
                        ${exprFor(db, "dishes", "contains_crustaceans", "0")},
                        ${exprFor(db, "dishes", "contains_celery", "0")},
                        ${exprFor(db, "dishes", "contains_sesame_seeds", "0")},
                        ${exprFor(db, "dishes", "contains_soybeans", "0")},
                        ${exprFor(db, "dishes", "contains_fish", "0")},
                        ${exprFor(db, "dishes", "contains_molluscs", "0")},
                        ${exprFor(db, "dishes", "contains_sulphites", "0")},
                        ${exprFor(db, "dishes", "image_base64", "NULL")}
                    FROM dishes
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE dishes")
                db.execSQL("ALTER TABLE dishes_new RENAME TO dishes")
            }
        }

        private fun exprFor(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            fallbackSql: String
        ): String {
            val cursor = db.query("PRAGMA table_info($table)")
            val found = try {
                var hasColumn = false
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                        hasColumn = true
                        break
                    }
                }
                hasColumn
            } finally {
                cursor.close()
            }
            return if (found) column else fallbackSql
        }

        private fun addBooleanColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String
        ) {
            val cursor = db.query("PRAGMA table_info($table)")
            val exists = try {
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                        found = true
                        break
                    }
                }
                found
            } finally {
                cursor.close()
            }

            if (!exists) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): CashRegisterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CashRegisterDatabase::class.java,
                    "cashregister.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
