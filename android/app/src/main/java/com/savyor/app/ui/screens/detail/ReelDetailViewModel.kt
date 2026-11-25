package com.savyor.app.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.CategoryCorrectionEntity
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.data.local.entity.ReelEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ReelDetailViewModel(
    application: Application,
    private val shortcode: String
) : AndroidViewModel(application) {

    private val db = SavyorDatabase.getInstance(application)
    private val reelDao = db.reelDao()
    private val categoryDao = db.categoryDao()
    private val correctionDao = db.categoryCorrectionDao()

    val reel: StateFlow<ReelEntity?> = reelDao.getReelByShortcodeFlow(shortcode)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateStatus(newStatus: String) {
        viewModelScope.launch {
            reelDao.updateStatus(shortcode, newStatus)
        }
    }

    fun updateUserNote(note: String) {
        viewModelScope.launch {
            reelDao.updateUserNote(shortcode, note)
        }
    }

    fun updateCategory(newCategory: String) {
        viewModelScope.launch {
            val current = reel.value ?: return@launch
            if (current.category != newCategory) {
                // Record correction for few-shot prompt adaptation
                correctionDao.insertCorrection(
                    CategoryCorrectionEntity(
                        reelShortcode = shortcode,
                        title = current.title,
                        fromCategory = current.category,
                        toCategory = newCategory
                    )
                )
                reelDao.updateCategory(shortcode, newCategory)
            }
        }
    }

    fun deleteReel(onDeleted: () -> Unit) {
        viewModelScope.launch {
            // Delete snapshot files
            val snapshotDir = File(getApplication<Application>().filesDir, "snapshots/$shortcode")
            if (snapshotDir.exists()) {
                snapshotDir.deleteRecursively()
            }
            reelDao.deleteByShortcode(shortcode)
            onDeleted()
        }
    }
}
