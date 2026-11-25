package com.savyor.app.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savyor.app.data.local.EncryptedSettingsManager
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
    GRID, LIST, COMPACT;

    fun next(): ViewMode = when (this) {
        GRID -> LIST
        LIST -> COMPACT
        COMPACT -> GRID
    }

    companion object {
        fun fromString(raw: String): ViewMode = entries.firstOrNull { it.name == raw } ?: GRID
    }
}

enum class SortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    TITLE_AZ("Title A–Z");

    companion object {
        fun fromString(raw: String): SortOrder = entries.firstOrNull { it.name == raw } ?: NEWEST
    }
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SavyorDatabase.getInstance(application)
    private val reelDao = db.reelDao()
    private val categoryDao = db.categoryDao()
    private val scheduler = WorkManagerScheduler(application)
    private val settings = EncryptedSettingsManager(application)

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeJobs: StateFlow<List<ReelEntity>> = reelDao.getActiveProcessingReels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedStatus = MutableStateFlow("All")
    val selectedStatus: StateFlow<String> = _selectedStatus.asStateFlow()

    private val _starredOnly = MutableStateFlow(false)
    val starredOnly: StateFlow<Boolean> = _starredOnly.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.fromString(settings.libraryViewMode))
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.fromString(settings.librarySortOrder))
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private data class FilterState(
        val category: String,
        val status: String,
        val query: String,
        val starredOnly: Boolean,
        val sortOrder: SortOrder
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val reels: StateFlow<List<ReelEntity>> = combine(
        _selectedCategory,
        _selectedStatus,
        _searchQuery,
        _starredOnly,
        _sortOrder
    ) { category, status, query, starredOnly, sortOrder ->
        FilterState(category, status, query, starredOnly, sortOrder)
    }.flatMapLatest { state ->
        val baseFlow = when {
            state.query.isNotBlank() -> {
                val ftsQuery = FtsQuerySanitizer.sanitizeForFts5(state.query)
                if (ftsQuery != null) {
                    reelDao.searchReelsFts(ftsQuery).catch {
                        emitAll(reelDao.searchReelsFallback(state.query.trim()))
                    }
                } else {
                    reelDao.searchReelsFallback(state.query.trim())
                }
            }
            state.category == "All" && state.status == "All" -> reelDao.getAllReels()
            state.category == "All" -> reelDao.getReelsByStatus(state.status)
            state.status == "All" -> reelDao.getReelsByCategory(state.category)
            else -> reelDao.getReelsByCategoryAndStatus(state.category, state.status)
        }

        baseFlow.map { list ->
            val filtered = list.filter { reel ->
                val matchesStar = !state.starredOnly || reel.isStarred
                val matchesCat = state.query.isBlank() || state.category == "All" || reel.category == state.category
                val matchesStatus = state.query.isBlank() || state.status == "All" || reel.status == state.status
                matchesStar && matchesCat && matchesStatus
            }
            when (state.sortOrder) {
                SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
                SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
                SortOrder.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectStatus(status: String) {
        _selectedStatus.value = status
    }

    fun toggleStarredOnly() {
        _starredOnly.value = !_starredOnly.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearAllFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = "All"
        _selectedStatus.value = "All"
        _starredOnly.value = false
    }

    fun toggleViewMode() {
        val next = _viewMode.value.next()
        _viewMode.value = next
        settings.libraryViewMode = next.name
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        settings.librarySortOrder = order.name
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

    fun nextTriageStatus(currentStatus: String): String = when (currentStatus.uppercase()) {
        "UNREAD" -> "REVIEWED"
        "REVIEWED" -> "TRIED"
        else -> "UNREAD"
    }

    fun setReelStatus(shortcode: String, status: String) {
        viewModelScope.launch {
            reelDao.updateStatus(shortcode, status)
        }
    }

    fun setReelStarred(shortcode: String, isStarred: Boolean) {
        viewModelScope.launch {
            reelDao.updateStarred(shortcode, isStarred)
        }
    }
}
