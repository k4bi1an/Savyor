package com.savyor.app.data.local

import androidx.room.*
import com.savyor.app.data.local.entity.CategoryCorrectionEntity
import com.savyor.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY display_order ASC, name ASC")
    suspend fun getAllCategoriesList(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE name = :name AND is_default = 0")
    suspend fun deleteCustomCategory(name: String)

    @Query("UPDATE categories SET name = :newName WHERE name = :oldName AND is_default = 0")
    suspend fun updateCategoryName(oldName: String, newName: String)

    @Query("UPDATE categories SET color_hex = :colorHex WHERE name = :name")
    suspend fun updateCategoryColor(name: String, colorHex: String)

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface CategoryCorrectionDao {

    @Query("SELECT * FROM category_corrections ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentCorrections(limit: Int = 5): List<CategoryCorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: CategoryCorrectionEntity): Long

    @Query("UPDATE category_corrections SET to_category = :newName WHERE to_category = :oldName")
    suspend fun updateToCategory(oldName: String, newName: String)

    @Query("UPDATE category_corrections SET from_category = :newName WHERE from_category = :oldName")
    suspend fun updateFromCategory(oldName: String, newName: String)
}
