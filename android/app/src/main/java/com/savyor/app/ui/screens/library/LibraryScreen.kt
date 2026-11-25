package com.savyor.app.ui.screens.library

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

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val searchFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    val categoryColorMap = remember(categories) {
        categories.associate { it.name to it.colorHex }
    }
    val activeFilterCount = remember(selectedStatus, starredOnly, sortOrder) {
        (if (selectedStatus != "All") 1 else 0) +
            (if (starredOnly) 1 else 0) +
            (if (sortOrder != SortOrder.NEWEST) 1 else 0)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgCanvas)
            .nestedScroll(nestedScrollConnection)
    ) {
        // 1. Full-Bleed Scrollable Content Underneath Floating Chrome
        val hasActiveFilters = searchQuery.isNotBlank() || starredOnly || selectedCategory != "All" || selectedStatus != "All"
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
                                        dotColor = categoryDotColor(
                                            reel.category,
                                            categoryColorMap[reel.category] ?: "#52525B",
                                            colors.isDark
                                        ),
                                        onClick = { onNavigateToDetail(reel.shortcode) }
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
                                    SwipeableReelListRow(
                                        reel = reel,
                                        dotColor = categoryDotColor(
                                            reel.category,
                                            categoryColorMap[reel.category] ?: "#52525B",
                                            colors.isDark
                                        ),
                                        onClick = { onNavigateToDetail(reel.shortcode) },
                                        onCycleStatus = {
                                            val prev = reel.status
                                            val next = viewModel.nextTriageStatus(prev)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setReelStatus(reel.shortcode, next)
                                            coroutineScope.launch {
                                                val res = snackbarHostState.showSnackbar(
                                                    message = "Marked ${next.lowercase()}",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    viewModel.setReelStatus(reel.shortcode, prev)
                                                }
                                            }
                                        },
                                        onToggleStar = {
                                            val prev = reel.isStarred
                                            val next = !prev
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setReelStarred(reel.shortcode, next)
                                            coroutineScope.launch {
                                                val res = snackbarHostState.showSnackbar(
                                                    message = if (next) "Added to Starred" else "Removed from Starred",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    viewModel.setReelStarred(reel.shortcode, prev)
                                                }
                                            }
                                        }
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
                                        dotColor = categoryDotColor(
                                            reel.category,
                                            categoryColorMap[reel.category] ?: "#52525B",
                                            colors.isDark
                                        ),
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

        // 2. Subtle Top Status-Bar Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTop + 20.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(colors.bgCanvas.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
        )

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
                            dotColor = categoryDotColor(cat.name, cat.colorHex, colors.isDark),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectCategory(cat.name)
                            }
                        )
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
    dotColor: Color,
    onClick: () -> Unit
) {
    val colors = LocalSavyorColors.current
    val cardShape = RoundedCornerShape(14.dp)

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
                            .size(480, 600)
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

                if (reel.isStarred) {
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
                    status = reel.status,
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

                // Clean single-line footer: @handle + category dot
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableReelListRow(
    reel: ReelEntity,
    dotColor: Color,
    onClick: () -> Unit,
    onCycleStatus: () -> Unit,
    onToggleStar: () -> Unit
) {
    val colors = LocalSavyorColors.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onCycleStatus()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onToggleStar()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> colors.triedBg
                SwipeToDismissBoxValue.EndToStart -> colors.reviewedBg
                else -> colors.surfaceRecessed
            }
            val align = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = align
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.triedFg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Next Status", fontFamily = SavyorSansFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.triedFg)
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reel.isStarred) "Unstar" else "Star", fontFamily = SavyorSansFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.reviewedFg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Filled.Star, contentDescription = null, tint = colors.reviewedFg)
                    }
                }
            }
        },
        content = {
            ReelListRow(reel = reel, dotColor = dotColor, onClick = onClick)
        }
    )
}

@Composable
fun ReelListRow(
    reel: ReelEntity,
    dotColor: Color,
    onClick: () -> Unit
) {
    val colors = LocalSavyorColors.current
    val shape = RoundedCornerShape(14.dp)
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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
                    StatusBadge(status = reel.status)
                }
            }
        }
    }
}

@Composable
fun ReelCompactRow(
    reel: ReelEntity,
    dotColor: Color,
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

        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

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
