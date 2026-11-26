package com.savyor.app.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.ui.components.SavyorMiniEmblem
import com.savyor.app.ui.components.StatusBadge
import com.savyor.app.ui.components.springPressClickable
import com.savyor.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToManageCategories: () -> Unit = {}
) {
    val colors = LocalSavyorColors.current
    val categories by viewModel.categories.collectAsState()
    val activeJobs by viewModel.activeJobs.collectAsState()
    val reels by viewModel.reels.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val starredOnly by viewModel.starredOnly.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddTopicDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchExpanded) {
        viewModel.setSearchQuery("")
        isSearchExpanded = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val searchFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val chipListState = rememberLazyListState()

    val categoryPages = remember(categories) {
        listOf("All") + categories.map { it.name }
    }
    val pagerState = rememberPagerState(
        initialPage = categoryPages.indexOf(selectedCategory).coerceAtLeast(0),
        pageCount = { categoryPages.size }
    )

    LaunchedEffect(pagerState.currentPage, categoryPages) {
        val pageCategory = categoryPages.getOrNull(pagerState.currentPage) ?: "All"
        if (pageCategory != selectedCategory) {
            viewModel.selectCategory(pageCategory)
        }
        chipListState.animateScrollToItem(pagerState.currentPage.coerceAtMost((categoryPages.size - 1).coerceAtLeast(0)))
    }

    LaunchedEffect(selectedCategory, categoryPages) {
        val targetIndex = categoryPages.indexOf(selectedCategory)
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    val activeFilterCount = remember(selectedStatus, starredOnly, sortOrder, dateFilter) {
        (if (selectedStatus != "All") 1 else 0) +
            (if (starredOnly) 1 else 0) +
            (if (sortOrder != SortOrder.NEWEST) 1 else 0) +
            (if (dateFilter !is DateFilter.AnyTime) 1 else 0)
    }

    // Smooth scroll collapse/reveal for the floating header capsule + filter dock
    val density = LocalDensity.current
    val collapseHeightPx = with(density) { 128.dp.toPx() }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    val canCollapse = !isSearchExpanded && reels.size > 4
    LaunchedEffect(canCollapse) {
        if (!canCollapse) headerOffsetPx = 0f
    }

    val nestedScrollConnection = remember(canCollapse, collapseHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canCollapse) return Offset.Zero
                val delta = available.y
                val newOffset = (headerOffsetPx + delta).coerceIn(-collapseHeightPx, 0f)
                headerOffsetPx = newOffset
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            searchFocusRequester.requestFocus()
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listTopPadding = statusBarTop + 120.dp
    val listBottomPadding = navBarBottom + 104.dp

    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.refreshLibrary()
        }
    }
    LaunchedEffect(pullRefreshState.isRefreshing, isRefreshing) {
        if (pullRefreshState.isRefreshing && !isRefreshing) {
            // Ensure spinner settles once refresh completes
            kotlinx.coroutines.delay(50L)
            if (!viewModel.isRefreshing.value) {
                pullRefreshState.endRefresh()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgCanvas)
            .nestedScroll(nestedScrollConnection)
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        // 1. Full-Bleed Swipeable HorizontalPager Feed Underneath Floating Chrome
        val hasActiveFilters = searchQuery.isNotBlank() || starredOnly || selectedCategory != "All" ||
            selectedStatus != "All" || dateFilter !is DateFilter.AnyTime

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { _ ->
            if (reels.isEmpty() && activeJobs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = listTopPadding, bottom = listBottomPadding)) {
                    if (hasActiveFilters) {
                        EmptySearchView(
                            query = searchQuery,
                            onClearSearch = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.clearAllFilters()
                            }
                        )
                    } else {
                        EmptyLibraryView(onAddClick = { showAddDialog = true })
                    }
                }
            } else {
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "view_mode_transition"
                ) { mode ->
                    when (mode) {
                        ViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(
                                    top = listTopPadding,
                                    bottom = listBottomPadding,
                                    start = 12.dp,
                                    end = 12.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (activeJobs.isNotEmpty()) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                        ActiveJobsSection(activeJobs)
                                    }
                                }
                                items(reels, key = { it.id }) { reel ->
                                    Box(
                                        modifier = Modifier.animateItemPlacement(
                                            spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    ) {
                                        ReelGridCard(
                                            reel = reel,
                                            onClick = { onNavigateToDetail(reel.shortcode) },
                                            onRetry = { viewModel.retryReel(reel.cleanUrl) }
                                        )
                                    }
                                }
                            }
                        }

                        ViewMode.LIST -> {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    top = listTopPadding,
                                    bottom = listBottomPadding,
                                    start = 12.dp,
                                    end = 12.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (activeJobs.isNotEmpty()) {
                                    item { ActiveJobsSection(activeJobs) }
                                }
                                items(reels, key = { it.id }) { reel ->
                                    Box(
                                        modifier = Modifier.animateItemPlacement(
                                            spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    ) {
                                        ReelListRow(
                                            reel = reel,
                                            onClick = { onNavigateToDetail(reel.shortcode) },
                                            onRetry = { viewModel.retryReel(reel.cleanUrl) }
                                        )
                                    }
                                }
                            }
                        }

                        ViewMode.COMPACT -> {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    top = listTopPadding,
                                    bottom = listBottomPadding,
                                    start = 16.dp,
                                    end = 16.dp
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (activeJobs.isNotEmpty()) {
                                    item { ActiveJobsSection(activeJobs) }
                                }
                                itemsIndexed(reels, key = { _, item -> item.id }) { index, reel ->
                                    Column(
                                        modifier = Modifier.animateItemPlacement(
                                            spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                    ) {
                                        ReelCompactRow(
                                            reel = reel,
                                            onClick = { onNavigateToDetail(reel.shortcode) }
                                        )
                                        if (index < reels.lastIndex) {
                                            HorizontalDivider(
                                                color = colors.borderSubtle,
                                                thickness = 1.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Material 3 Pull-to-Refresh Indicator
        PullToRefreshContainer(
            state = pullRefreshState,
            containerColor = colors.surfaceElevated,
            contentColor = colors.inkPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = listTopPadding - 16.dp)
        )

        // 2. Top Status-Bar Fade Scrim
        TopStatusBarScrim(statusBarTop = statusBarTop)

        // 3. Floating Liquid Crystal Header Capsule + Split Filter Dock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                .statusBarsPadding()
        ) {
            // Floating Header Capsule
            val capsuleShape = RoundedCornerShape(29.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                    .height(58.dp)
                    .savyorLiquidCrystal(tier = CrystalTier.CHROME, shape = capsuleShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isSearchExpanded,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))) togetherWith
                            (fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)))
                    },
                    label = "header_search_morph"
                ) { searching ->
                    if (searching) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CrystalIconWell(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Close Search",
                                size = 36.dp,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isSearchExpanded = false
                                    viewModel.setSearchQuery("")
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search reels, notes, creators...",
                                        fontFamily = SavyorSansFamily,
                                        fontSize = 14.sp,
                                        color = colors.inkMuted
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = SavyorSansFamily,
                                        fontSize = 14.5.sp,
                                        color = colors.inkPrimary
                                    ),
                                    cursorBrush = SolidColor(colors.inkPrimary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                CrystalIconWell(
                                    icon = Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                    size = 34.dp,
                                    onClick = { viewModel.setSearchQuery("") }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Cluster: Mini Liquid Obsidian Emblem + Savyor + Count Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                SavyorMiniEmblem(size = 32.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Savyor",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.surfaceRecessed,
                                    border = BorderStroke(1.dp, colors.borderSubtle)
                                ) {
                                    Text(
                                        text = "${reels.size}",
                                        fontFamily = SavyorMonoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkMuted,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Right Cluster: 3 Tactile Crystal Icon Wells
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CrystalIconWell(
                                    icon = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isSearchExpanded = true
                                    }
                                )
                                val viewIcon = when (viewMode) {
                                    ViewMode.GRID -> Icons.Rounded.GridView
                                    ViewMode.LIST -> Icons.Rounded.ViewAgenda
                                    ViewMode.COMPACT -> Icons.Rounded.ViewHeadline
                                }
                                CrystalIconWell(
                                    icon = viewIcon,
                                    contentDescription = "Cycle View",
                                    animateIcon = true,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleViewMode()
                                    }
                                )
                                CrystalIconWell(
                                    icon = Icons.Rounded.Settings,
                                    contentDescription = "Settings",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigateToSettings()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Split Floating Filter Dock (Scrollable Categories with Right-Edge Fade + Pinned Filter Pill)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    state = chipListState,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0.86f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    contentPadding = PaddingValues(end = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        CrystalCategoryChip(
                            text = "All",
                            selected = selectedCategory == "All",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectCategory("All")
                            }
                        )
                    }
                    items(categories) { cat ->
                        CrystalCategoryChip(
                            text = cat.name,
                            selected = selectedCategory == cat.name,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectCategory(cat.name)
                            }
                        )
                    }
                    item {
                        val addTopicShape = RoundedCornerShape(18.dp)
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .savyorLiquidCrystal(tier = CrystalTier.PILL, shape = addTopicShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showAddTopicDialog = true
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Topic",
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Topic",
                                    fontFamily = SavyorSansFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Always-Visible Pinned Filter Crystal Pill
                val filterShape = RoundedCornerShape(18.dp)
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .savyorLiquidCrystal(
                            tier = CrystalTier.PILL,
                            shape = filterShape,
                            activeRim = activeFilterCount > 0
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFilterSheet = true
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Filter & Sort",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter",
                            fontFamily = SavyorSansFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkPrimary
                        )
                        if (activeFilterCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(colors.ctaBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$activeFilterCount",
                                    fontFamily = SavyorMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.ctaFg
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Tactile + Save Reel Pill FAB (Auto-Hides when Filter Sheet opens)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            AnimatedVisibility(
                visible = !showFilterSheet,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180)),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.88f, animationSpec = tween(120))
            ) {
                SavyorPrimaryPillFab(
                    text = "Save Reel",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showAddDialog = true
                    }
                )
            }
        }

        // 5. Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }

    // Thumb-Zone Liquid Crystal Filter & Sort Sheet
    if (showFilterSheet) {
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            shape = sheetShape,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.52f),
            dragHandle = null
        ) {
            ApplySheetWindowBlur()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .savyorLiquidCrystal(tier = CrystalTier.SHEET, shape = sheetShape)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                // Sleek Frosted Capsule Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.borderFocus.copy(alpha = 0.55f))
                    )
                }

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter & Sort",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.inkPrimary
                    )
                    if (activeFilterCount > 0) {
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.clearAllFilters()
                            viewModel.setSortOrder(SortOrder.NEWEST)
                        }) {
                            Text("Reset", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold, color = colors.inkSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "UNREAD", "REVIEWED", "TRIED", "ARCHIVED")) { st ->
                        val label = if (st == "All") "All" else st.lowercase().replaceFirstChar { it.uppercase() }
                        CrystalCategoryChip(
                            text = label,
                            selected = selectedStatus == st,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectStatus(st)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleStarredOnly()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (starredOnly) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = null,
                                tint = if (starredOnly) colors.starAccent else colors.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Starred Only",
                                fontFamily = SavyorSansFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkPrimary
                            )
                        }
                        Switch(
                            checked = starredOnly,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleStarredOnly()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.ctaFg,
                                checkedTrackColor = colors.ctaBg
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "DATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CrystalCategoryChip(
                            text = "Any Time",
                            selected = dateFilter is DateFilter.AnyTime,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setDateFilter(DateFilter.AnyTime)
                            }
                        )
                        CrystalCategoryChip(
                            text = "Today",
                            selected = dateFilter is DateFilter.Today,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setDateFilter(DateFilter.Today)
                            }
                        )
                        CrystalCategoryChip(
                            text = "Last 7 Days",
                            selected = dateFilter is DateFilter.Last7Days,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setDateFilter(DateFilter.Last7Days)
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CrystalCategoryChip(
                            text = "Last 30 Days",
                            selected = dateFilter is DateFilter.Last30Days,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setDateFilter(DateFilter.Last30Days)
                            }
                        )
                        CrystalCategoryChip(
                            text = if (dateFilter is DateFilter.SingleDay) dateFilter.displayLabel() else "Pick Date",
                            selected = dateFilter is DateFilter.SingleDay,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showDatePicker = true
                            }
                        )
                        CrystalCategoryChip(
                            text = if (dateFilter is DateFilter.Range) dateFilter.displayLabel() else "Date Range",
                            selected = dateFilter is DateFilter.Range,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showDateRangePicker = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SORT BY",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortOrder.entries.forEach { order ->
                        CrystalCategoryChip(
                            text = order.label,
                            selected = sortOrder == order,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setSortOrder(order)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .savyorLiquidCrystal(tier = CrystalTier.PILL, shape = RoundedCornerShape(12.dp))
                        .clickable {
                            showFilterSheet = false
                            onNavigateToManageCategories()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.inkPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Categories", fontFamily = SavyorSansFamily, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkPrimary)
                    }
                }
            }
        }
    }

    // Add Topic Dialog (opened directly from Home top category rail)
    if (showAddTopicDialog) {
        var newTopicName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTopicDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text("Add Topic", style = MaterialTheme.typography.titleLarge, color = colors.inkPrimary)
            },
            text = {
                OutlinedTextField(
                    value = newTopicName,
                    onValueChange = { newTopicName = it },
                    placeholder = { Text("e.g., Motion Design", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newTopicName.trim()
                        if (clean.isNotBlank()) {
                            viewModel.addCategory(clean)
                            viewModel.selectCategory(clean)
                            showAddTopicDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.ctaBg, contentColor = colors.ctaFg)
                ) {
                    Text("Add", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTopicDialog = false }) {
                    Text("Cancel", fontFamily = SavyorSansFamily, color = colors.inkSecondary)
                }
            }
        )
    }

    // Single Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (dateFilter as? DateFilter.SingleDay)?.epochMillis
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.setDateFilter(DateFilter.SingleDay(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Apply", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", fontFamily = SavyorSansFamily)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Date Range Picker Dialog
    if (showDateRangePicker) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = (dateFilter as? DateFilter.Range)?.startMillis,
            initialSelectedEndDateMillis = (dateFilter as? DateFilter.Range)?.endMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = rangeState.selectedStartDateMillis
                        val end = rangeState.selectedEndDateMillis ?: start
                        if (start != null && end != null) {
                            viewModel.setDateFilter(DateFilter.Range(start, end))
                        }
                        showDateRangePicker = false
                    }
                ) {
                    Text("Apply", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel", fontFamily = SavyorSansFamily)
                }
            }
        ) {
            DateRangePicker(
                state = rangeState,
                modifier = Modifier.height(420.dp)
            )
        }
    }

    // Save Reel Dialog
    if (showAddDialog) {
        var inputUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    "Save Instagram Reel",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Paste a public Instagram reel link to extract self-contained knowledge notes:",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://www.instagram.com/reel/...", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.enqueueReel(inputUrl)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ctaBg,
                        contentColor = colors.ctaFg
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Analyze & Save", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", fontFamily = SavyorSansFamily, color = colors.inkSecondary)
                }
            }
        )
    }
}

@Composable
fun CrystalIconWell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    tint: Color? = null,
    animateIcon: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalSavyorColors.current
    val resolvedTint = tint ?: colors.inkPrimary
    Box(
        modifier = Modifier
            .size(size)
            .savyorLiquidCrystal(tier = CrystalTier.PILL, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (animateIcon) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.78f, animationSpec = tween(180))) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.78f, animationSpec = tween(120)))
                },
                label = "icon_well_anim"
            ) { targetIcon ->
                Icon(
                    imageVector = targetIcon,
                    contentDescription = contentDescription,
                    tint = resolvedTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun CrystalCategoryChip(
    text: String,
    selected: Boolean,
    dotColor: Color? = null,
    onClick: () -> Unit
) {
    val colors = LocalSavyorColors.current
    val shape = RoundedCornerShape(18.dp)

    if (selected) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(shape)
                .background(colors.ctaBg, shape)
                .border(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (colors.isDark) 0.45f else 0.35f), Color.Transparent)
                    ),
                    shape = shape
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    fontFamily = SavyorSansFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ctaFg
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .height(36.dp)
                .savyorLiquidCrystal(tier = CrystalTier.PILL, shape = shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dotColor != null) {
                    Box(contentAlignment = Alignment.Center) {
                        if (colors.isDark) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            listOf(dotColor.copy(alpha = 0.28f), Color.Transparent)
                                        ),
                                        shape = CircleShape
                                    )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    fontFamily = SavyorSansFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkSecondary
                )
            }
        }
    }
}

@Composable
fun SavyorPrimaryPillFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSavyorColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "fab_scale"
    )
    val shape = RoundedCornerShape(28.dp)
    val rimTop = if (colors.isDark) Color(0xFFFFFFFF) else Color(0x66FFFFFF)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = shape,
        color = colors.ctaBg,
        contentColor = colors.ctaFg,
        shadowElevation = 8.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(rimTop, colors.borderSubtle)),
                shape = shape
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontFamily = SavyorSansFamily,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ActiveJobsSection(jobs: List<ReelEntity>) {
    val colors = LocalSavyorColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "processing_breathe")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val shape = RoundedCornerShape(14.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .savyorCardBorder(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.triedFg.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Analyzing ${jobs.size} reel(s)...",
                    fontFamily = SavyorSansFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = colors.inkPrimary
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            jobs.take(2).forEach { job ->
                val stateText = when (job.processingState) {
                    "DOWNLOADING" -> "Downloading progressive video stream"
                    "ANALYZING" -> "Gemini multimodal note extraction"
                    else -> "Queued in WorkManager"
                }
                Text(
                    text = "@${job.shortcode} · $stateText",
                    fontFamily = SavyorSansFamily,
                    fontSize = 11.5.sp,
                    color = colors.inkMuted
                )
            }
        }
    }
}

@Composable
fun ReelGridCard(
    reel: ReelEntity,
    onClick: () -> Unit,
    onRetry: () -> Unit = {}
) {
    val colors = LocalSavyorColors.current
    val cardShape = RoundedCornerShape(14.dp)
    val isProcessing = reel.processingState in setOf("QUEUED", "DOWNLOADING", "ANALYZING") ||
        reel.status.equals("PROCESSING", ignoreCase = true)
    val isFailed = reel.processingState == "FAILED" || reel.status.equals("FAILED", ignoreCase = true)

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .savyorCardBorder(cardShape)
            .springPressClickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .background(colors.surfaceRecessed)
            ) {
                if (!reel.thumbnailPath.isNullOrBlank() && File(reel.thumbnailPath).exists()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(reel.thumbnailPath))
                            .crossfade(true)
                            .size(540, 660)
                            .build(),
                        contentDescription = reel.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = colors.inkMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                if (isProcessing) {
                    val stageLabel = when (reel.processingState.uppercase()) {
                        "DOWNLOADING" -> "DOWNLOADING"
                        "ANALYZING" -> "AI NOTES"
                        else -> "ANALYZING"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.bgCanvas.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = colors.unreadFg
                            )
                            Text(
                                text = stageLabel,
                                fontFamily = SavyorMonoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = colors.inkPrimary
                            )
                        }
                    }
                } else if (isFailed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.bgCanvas.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.errorFg.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = colors.errorFg, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.errorFg)
                        }
                    }
                }

                if (reel.isStarred && !isProcessing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(26.dp)
                            .savyorLiquidCrystal(tier = CrystalTier.THUMB_BADGE, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Starred",
                            tint = colors.starAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                StatusBadge(
                    status = if (isProcessing) "PROCESSING" else reel.status,
                    isFrosted = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = reel.title.ifBlank { "Reel @${reel.shortcode}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.inkPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Clean monochrome single-line footer: @handle · Category
                val creatorText = if (reel.creator.isNotBlank()) "@${reel.creator}" else "@${reel.shortcode}"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = creatorText,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " \u00B7 ",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                    Text(
                        text = reel.category,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ReelListRow(
    reel: ReelEntity,
    onClick: () -> Unit,
    onRetry: () -> Unit = {}
) {
    val colors = LocalSavyorColors.current
    val shape = RoundedCornerShape(14.dp)
    val isProcessing = reel.processingState in setOf("QUEUED", "DOWNLOADING", "ANALYZING") ||
        reel.status.equals("PROCESSING", ignoreCase = true)
    val isFailed = reel.processingState == "FAILED" || reel.status.equals("FAILED", ignoreCase = true)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .savyorCardBorder(shape)
            .springPressClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceRecessed)
            ) {
                if (!reel.thumbnailPath.isNullOrBlank() && File(reel.thumbnailPath).exists()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(reel.thumbnailPath))
                            .crossfade(true)
                            .size(320, 420)
                            .build(),
                        contentDescription = reel.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = colors.inkMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.bgCanvas.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.unreadFg
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reel.title.ifBlank { "Reel @${reel.shortcode}" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.inkPrimary
                )

                if (reel.useWhen.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val cleanUseWhen = reel.useWhen.replace(
                        Regex("^(Use when:|Use when|Refer to this note when:?|Refer to this note when)\\s*", RegexOption.IGNORE_CASE),
                        ""
                    ).trim()
                    Text(
                        text = cleanUseWhen,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val creatorText = if (reel.creator.isNotBlank()) "@${reel.creator}" else "@${reel.shortcode}"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (reel.isStarred) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Starred",
                                tint = colors.starAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = creatorText,
                            fontFamily = SavyorSansFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = " \u00B7 ",
                            fontFamily = SavyorSansFamily,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                        Text(
                            text = reel.category,
                            fontFamily = SavyorSansFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isFailed) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            border = BorderStroke(1.dp, colors.errorFg.copy(alpha = 0.6f))
                        ) {
                            Text("Retry", fontFamily = SavyorSansFamily, fontSize = 10.5.sp, color = colors.errorFg)
                        }
                    } else {
                        StatusBadge(status = if (isProcessing) "PROCESSING" else reel.status)
                    }
                }
            }
        }
    }
}

@Composable
fun ReelCompactRow(
    reel: ReelEntity,
    onClick: () -> Unit
) {
    val colors = LocalSavyorColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springPressClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceRecessed),
            contentAlignment = Alignment.Center
        ) {
            if (!reel.thumbnailPath.isNullOrBlank() && File(reel.thumbnailPath).exists()) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(reel.thumbnailPath))
                        .crossfade(true)
                        .size(160, 160)
                        .build(),
                    contentDescription = reel.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = colors.inkMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = reel.title.ifBlank { "Reel @${reel.shortcode}" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (reel.isStarred) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Starred",
                tint = colors.starAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        StatusBadge(status = reel.status)
    }
}

@Composable
fun EmptyLibraryView(onAddClick: () -> Unit) {
    val colors = LocalSavyorColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceRecessed,
                border = BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your library is calm",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Share any Instagram reel to Savyor, or tap below to paste a reel link.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ctaBg,
                    contentColor = colors.ctaFg
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Reel", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EmptySearchView(
    query: String,
    onClearSearch: () -> Unit
) {
    val colors = LocalSavyorColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.inkMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No matching reels",
                style = MaterialTheme.typography.titleLarge,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (query.isNotBlank()) "No notes, transcripts, or visual cues matched \"$query\"." else "No reels match the active filters.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onClearSearch,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Text("Clear search", fontFamily = SavyorSansFamily, fontSize = 13.sp, color = colors.inkPrimary)
            }
        }
    }
}
