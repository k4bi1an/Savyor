package com.savyor.app.ui.screens.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategoryWithCount(
    val category: CategoryEntity,
    val reelCount: Int
)

class ManageCategoriesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SavyorDatabase.getInstance(application)
    private val categoryDao = db.categoryDao()
    private val reelDao = db.reelDao()
    private val correctionDao = db.categoryCorrectionDao()

    private val _userFeedback = MutableSharedFlow<String>()
    val userFeedback: SharedFlow<String> = _userFeedback.asSharedFlow()

    val categoriesWithCounts: StateFlow<List<CategoryWithCount>> = combine(
        categoryDao.getAllCategories(),
        reelDao.getAllReels()
    ) { categories, reels ->
        val countsMap = reels.groupingBy { it.category }.eachCount()
        categories.map { cat ->
            CategoryWithCount(
                category = cat,
                reelCount = countsMap[cat.name] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, colorHex: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            viewModelScope.launch { _userFeedback.emit("Category name cannot be empty") }
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = categoryDao.getCategoryByName(cleanName)
                if (existing != null) {
                    _userFeedback.emit("Category \"$cleanName\" already exists")
                    return@withContext
                }

                categoryDao.insert(
                    CategoryEntity(
                        name = cleanName,
                        isDefault = false,
                        displayOrder = 99,
                        colorHex = colorHex
                    )
                )
                _userFeedback.emit("Category \"$cleanName\" created")
            }
        }
    }

    fun renameCategory(oldName: String, newName: String) {
        val cleanNew = newName.trim()
        if (cleanNew.isBlank()) {
            viewModelScope.launch { _userFeedback.emit("New category name cannot be empty") }
            return
        }
        if (oldName.equals("Other", ignoreCase = true)) {
            viewModelScope.launch { _userFeedback.emit("System category \"Other\" cannot be renamed") }
            return
        }
        if (oldName.equals(cleanNew, ignoreCase = true)) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = categoryDao.getCategoryByName(cleanNew)
                if (existing != null) {
                    _userFeedback.emit("A category named \"$cleanNew\" already exists")
                    return@withContext
                }

                // 1. Rename category
                categoryDao.updateCategoryName(oldName, cleanNew)
                // 2. Reassign reels
                reelDao.reassignCategory(fromCategory = oldName, toCategory = cleanNew)
                // 3. Keep corrections in sync
                correctionDao.updateToCategory(oldName, cleanNew)
                correctionDao.updateFromCategory(oldName, cleanNew)

                _userFeedback.emit("Renamed \"$oldName\" to \"$cleanNew\"")
            }
        }
    }

    fun updateColor(categoryName: String, colorHex: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                categoryDao.updateCategoryColor(categoryName, colorHex)
                _userFeedback.emit("Color updated for \"$categoryName\"")
            }
        }
    }

    fun deleteCategory(categoryName: String) {
        if (categoryName.equals("Other", ignoreCase = true)) {
            viewModelScope.launch { _userFeedback.emit("System category \"Other\" cannot be deleted") }
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Safely reassign all reels to "Other"
                reelDao.reassignCategory(fromCategory = categoryName, toCategory = "Other")
                // Delete custom category
                categoryDao.deleteCustomCategory(categoryName)
                // Update correction history
                correctionDao.updateToCategory(categoryName, "Other")

                _userFeedback.emit("Deleted \"$categoryName\" (reels moved to Other)")
            }
        }
    }
}
