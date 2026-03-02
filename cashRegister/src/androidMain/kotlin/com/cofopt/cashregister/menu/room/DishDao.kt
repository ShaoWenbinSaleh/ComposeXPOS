package com.cofopt.cashregister.menu.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DishDao {
    // Preserve CSV-defined order (insert order) to avoid lexicographic id sorting (e.g., 1,10,11,2)
    @Query("SELECT * FROM dishes ORDER BY rowid")
    fun observeAll(): Flow<List<DishEntity>>

    @Query("SELECT id FROM dishes")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM dishes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DishEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<DishEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DishEntity)

    @Query("SELECT * FROM dishes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DishEntity?

    @Query(
        "SELECT price_eur AS priceEur, discounted_price_eur AS discountedPriceEur, sold_out AS soldOut FROM dishes WHERE id = :id LIMIT 1"
    )
    suspend fun getEditableFields(id: String): DishEditableFields?

    @Query(
        "UPDATE dishes SET price_eur = :priceEur, discounted_price_eur = :discountedPriceEur, sold_out = :soldOut WHERE id = :id"
    )
    suspend fun updateEditableFields(
        id: String,
        priceEur: Double,
        discountedPriceEur: Double,
        soldOut: Boolean
    )

    @Query("UPDATE dishes SET id = :newId WHERE id = :oldId")
    suspend fun renameId(oldId: String, newId: String)

    @Query("DELETE FROM dishes")
    suspend fun clearAll()

    @Query("DELETE FROM dishes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE dishes SET price_eur = :priceEur WHERE id = :id")
    suspend fun updatePriceEur(id: String, priceEur: Double): Int

    @Query("UPDATE dishes SET discounted_price_eur = :discountedPriceEur WHERE id = :id")
    suspend fun updateDiscountedPriceEur(id: String, discountedPriceEur: Double): Int

    @Query("UPDATE dishes SET sold_out = :soldOut WHERE id = :id")
    suspend fun updateSoldOut(id: String, soldOut: Boolean): Int
}

data class DishEditableFields(
    val priceEur: Double,
    val discountedPriceEur: Double,
    val soldOut: Boolean
)
