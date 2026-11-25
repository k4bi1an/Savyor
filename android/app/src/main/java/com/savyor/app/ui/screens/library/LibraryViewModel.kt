package com.savyor.app.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.Coil
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.FtsQuerySanitizer
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.work.EnqueueResult
import com.savyor.app.work.WorkManagerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

sealed class DateFilter {
    data object AnyTime : DateFilter()
    data object Today : DateFilter()
    data object Last7Days : DateFilter()
    data object Last30Days : DateFilter()
    data class SingleDay(val epochMillis: Long) : DateFilter()
    data class Range(val startMillis: Long, val endMillis: Long) : DateFilter()

    fun displayLabel(): String {
        val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val shortFmt = DateTimeFormatter.ofPattern("MMM d")
        val zone = ZoneId.systemDefault()
        return when (this) {
            AnyTime -> "Any Time"
            Today -> "Today"
            Last7Days -> "Last 7 Days"
            Last30Days -> "Last 30 Days"
            is SingleDay -> Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(fmt)
            is Range -> {
                val s = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate().format(shortFmt)
                val e = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate().format(shortFmt)
                "$s \u2013 $e"
            }
        }
    }

    fun matches(createdAtMillis: Long): Boolean {
        if (this is AnyTime) return true
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return when (this) {
            AnyTime -> true
            Today -> {
                val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                createdAtMillis in start..end
            }
            Last7Days -> {
                val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
                createdAtMillis >= start
            }
            Last30Days -> {
                val start = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
                createdAtMillis >= start
            }
            is SingleDay -> {
                val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                createdAtMillis in start..end
            }
            is Range -> {
                val sDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                val eDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                val start = minOf(sDate, eDate).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = maxOf(sDate, eDate).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                createdAtMillis in start..end
            }
        }
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

    private val _dateFilter = MutableStateFlow<DateFilter>(DateFilter.AnyTime)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0L)

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private data class FilterState(
        val category: String,
        val status: String,
        val query: String,
        val starredOnly: Boolean,
        val sortOrder: SortOrder,
        val dateFilter: DateFilter,
        val refreshTick: Long
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val reels: StateFlow<List<ReelEntity>> = combine(
        _selectedCategory,
        _selectedStatus,
        _searchQuery,
        _starredOnly,
        _sortOrder,
        _dateFilter,
        _refreshTrigger
    ) { values ->
        FilterState(
            category = values[0] as String,
            status = values[1] as String,
            query = values[2] as String,
            starredOnly = values[3] as Boolean,
            sortOrder = values[4] as SortOrder,
            dateFilter = values[5] as DateFilter,
            refreshTick = values[6] as Long
        )
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
                val matchesDate = state.dateFilter.matches(reel.createdAt)
                matchesStar && matchesCat && matchesStatus && matchesDate
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

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun clearAllFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = "All"
        _selectedStatus.value = "All"
        _starredOnly.value = false
        _dateFilter.value = DateFilter.AnyTime
    }

    fun refreshLibrary() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 1. Reconcile any stalled PROCESSING jobs with WorkManager
                    val app = getApplication<Application>()
                    val workManager = WorkManager.getInstance(app)
                    val activeReels = reelDao.getActiveProcessingReelsOnce()
                    activeReels.forEach { reel ->
                        val infos = runCatching {
                            workManager.getWorkInfosForUniqueWork("process_reel_${reel.shortcode}").get()
                        }.getOrDefault(emptyList())
                        val hasLiveWork = infos.any { info ->
                            info.state == WorkInfo.State.ENQUEUED ||
                                info.state == WorkInfo.State.RUNNING ||
                                info.state == WorkInfo.State.BLOCKED
                        }
                        if (!hasLiveWork) {
                            reelDao.updateProcessingAndStatus(
                                shortcode = reel.shortcode,
                                state = "FAILED",
                                status = "FAILED",
                                errorMessage = "Processing job was interrupted. Tap Retry to re-run."
                            )
                        }
                    }

                    // 2. Clear stale Coil memory cache entries for newly generated thumbnails
                    runCatching {
                        Coil.imageLoader(app).memoryCache?.clear()
                    }
                }
                _refreshTrigger.value = System.currentTimeMillis()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retryReel(url: String) {
        viewModelScope.launch {
            when (val res = scheduler.enqueueReel(url, forceReanalyze = true)) {
                is EnqueueResult.Success -> _snackbarMessage.emit("Retrying reel analysis...")
                is EnqueueResult.InvalidUrl -> _snackbarMessage.emit(res.message)
                is EnqueueResult.Error -> _snackbarMessage.emit("Retry failed: ${res.message}")
            }
        }
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
