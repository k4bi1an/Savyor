package com.savyor.app.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savyor.app.data.local.FtsQuerySanitizer
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.work.EnqueueResult
import com.savyor.app.work.WorkManagerScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ViewMode {
    GRID, LIST
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SavyorDatabase.getInstance(application)
    private val reelDao = db.reelDao()
    private val categoryDao = db.categoryDao()
    private val scheduler = WorkManagerScheduler(application)

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeJobs: StateFlow<List<ReelEntity>> = reelDao.getActiveProcessingReels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedStatus = MutableStateFlow("All")
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val reels: StateFlow<List<ReelEntity>> = combine(
        _selectedCategory,
        _selectedStatus,
        _searchQuery
    ) { category, status, query ->
        Triple(category, status, query)
    }.flatMapLatest { (category, status, query) ->
        when {
            query.isNotBlank() -> {
                val ftsQuery = FtsQuerySanitizer.sanitizeForFts5(query)
                if (ftsQuery != null) {
                    reelDao.searchReelsFts(ftsQuery).catch {
                        emitAll(reelDao.searchReelsFallback(query.trim()))
                    }
                } else {
                    reelDao.searchReelsFallback(query.trim())
                }
            }
            category == "All" && status == "All" -> reelDao.getAllReels()
            category == "All" -> reelDao.getReelsByStatus(status)
            status == "All" -> reelDao.getReelsByCategory(category)
            else -> reelDao.getReelsByCategoryAndStatus(category, status)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectStatus(status: String) {
        _selectedStatus.value = status
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    }

    fun enqueueReel(url: String) {
        viewModelScope.launch {
            when (val res = scheduler.enqueueReel(url)) {
                is EnqueueResult.Success -> {
                    val msg = if (res.isNew) "Reel queued for analysis" else "Reel already exists in library"
                    _snackbarMessage.emit(msg)
                }
                is EnqueueResult.InvalidUrl -> _snackbarMessage.emit(res.message)
                is EnqueueResult.Error -> _snackbarMessage.emit("Error: ${res.message}")
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isNotBlank()) {
                categoryDao.insert(CategoryEntity(name = clean, isDefault = false, displayOrder = 99))
                _snackbarMessage.emit("Category \"$clean\" created")
            }
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            categoryDao.deleteCustomCategory(name)
            if (_selectedCategory.value == name) {
                _selectedCategory.value = "All"
            }
            _snackbarMessage.emit("Category \"$name\" deleted")
        }
    }
}
