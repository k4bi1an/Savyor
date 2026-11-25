package com.savyor.app.ui.screens.library

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.ui.components.StatusBadge
import com.savyor.app.ui.components.springPressClickable
import com.savyor.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val categoryColorMap = remember(categories) {
        categories.associate { it.name to it.colorHex }
    }
    val activeFilterCount = remember(selectedStatus, starredOnly, sortOrder) {
        (if (selectedStatus != "All") 1 else 0) +
            (if (starredOnly) 1 else 0) +
            (if (sortOrder != SortOrder.NEWEST) 1 else 0)
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier.background(colors.glassSurface)
            ) {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = {
                        AnimatedContent(
                            targetState = isSearchExpanded,
                            transitionSpec = {
                                (fadeIn(tween(200)) + expandHorizontally(tween(220))) togetherWith
                                    (fadeOut(tween(150)) + shrinkHorizontally(tween(180)))
                            },
                            label = "search_bar_toggle"
                        ) { expanded ->
                            if (expanded) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = colors.surfaceRecessed,
                                    border = BorderStroke(1.dp, colors.borderSubtle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = colors.inkMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search notes, transcripts, or visuals...",
                                                    fontFamily = InterFontFamily,
                                                    fontSize = 13.sp,
                                                    color = colors.inkMuted
                                                )
                                            }
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { viewModel.setSearchQuery(it) },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    fontFamily = InterFontFamily,
                                                    fontSize = 13.sp,
                                                    color = colors.inkPrimary
                                                ),
                                                cursorBrush = SolidColor(colors.inkPrimary),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(searchFocusRequester)
                                            )
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.setSearchQuery("") },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = colors.inkSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Savyor",
                                    fontFamily = NewsreaderFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 26.sp,
                                    color = colors.inkPrimary,
                                    letterSpacing = (-0.4).sp
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) viewModel.setSearchQuery("")
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchExpanded) "Close Search" else "Search",
                                tint = colors.inkSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleViewMode()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            val viewIcon = when (viewMode) {
                                ViewMode.GRID -> Icons.Outlined.GridView
                                ViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
                                ViewMode.COMPACT -> Icons.Outlined.ViewHeadline
                            }
                            Icon(
                                imageVector = viewIcon,
                                contentDescription = "Cycle View (${viewMode.name})",
                                tint = colors.inkSecondary
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToSettings()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = colors.inkSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
                HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            val fabShape = RoundedCornerShape(28.dp)
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showAddDialog = true
                },
                shape = fabShape,
                color = colors.ctaBg,
                contentColor = colors.ctaFg,
                shadowElevation = 4.dp,
                modifier = Modifier.border(1.dp, colors.borderSubtle, fabShape)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save Reel",
                        fontFamily = InterFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        containerColor = colors.bgCanvas
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Single Consolidated Horizontal Chip Rail: All + Categories + Trailing Filter Chip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    CategoryChip(
                        text = "All",
                        selected = selectedCategory == "All",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectCategory("All")
                        }
                    )
                }
                items(categories) { cat ->
                    CategoryChip(
                        text = cat.name,
                        selected = selectedCategory == cat.name,
                        dotColor = categoryDotColor(cat.name, cat.colorHex, colors.isDark),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectCategory(cat.name)
                        }
                    )
                }
                item {
                    val filterShape = RoundedCornerShape(18.dp)
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFilterSheet = true
                        },
                        shape = filterShape,
                        color = if (activeFilterCount > 0) colors.surfaceElevated else colors.surfaceRecessed,
                        border = BorderStroke(
                            1.dp,
                            if (activeFilterCount > 0) colors.borderFocus else colors.borderSubtle
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Filter & Sort",
                                tint = if (activeFilterCount > 0) colors.inkPrimary else colors.inkSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Filter",
                                fontFamily = InterFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = if (activeFilterCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (activeFilterCount > 0) colors.inkPrimary else colors.inkSecondary
                            )
                            if (activeFilterCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(colors.ctaBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$activeFilterCount",
                                        fontFamily = InterFontFamily,
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

            // Active Processing Card
            AnimatedVisibility(
                visible = activeJobs.isNotEmpty(),
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
            ) {
                ActiveJobsSection(activeJobs)
            }

            // Main Content: Animated Grid <-> List <-> Compact transition
            val hasActiveFilters = searchQuery.isNotBlank() || starredOnly || selectedCategory != "All" || selectedStatus != "All"
            if (reels.isEmpty()) {
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
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 6.dp,
                                    bottom = 96.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
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
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 6.dp,
                                    bottom = 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
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
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 96.dp
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) {
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
        }
    }

    // Thumb-Zone Liquid Glass Filter & Sort Sheet
    if (showFilterSheet) {
        val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            shape = sheetShape,
            containerColor = colors.glassSurfaceElevated,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.borderFocus.copy(alpha = 0.5f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter & Sort",
                        fontFamily = NewsreaderFontFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkPrimary
                    )
                    if (activeFilterCount > 0) {
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.clearAllFilters()
                            viewModel.setSortOrder(SortOrder.NEWEST)
                        }) {
                            Text("Reset", fontFamily = InterFontFamily, color = colors.inkSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status Section
                Text(
                    text = "STATUS",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "UNREAD", "REVIEWED", "TRIED", "ARCHIVED")) { st ->
                        val label = if (st == "All") "All" else st.lowercase().replaceFirstChar { it.uppercase() }
                        CategoryChip(
                            text = label,
                            selected = selectedStatus == st,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.selectStatus(st)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Starred Only Row
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
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
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

                Spacer(modifier = Modifier.height(18.dp))

                // Sort By Section
                Text(
                    text = "SORT BY",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortOrder.entries.forEach { order ->
                        CategoryChip(
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

                // Quick Manage Categories link
                OutlinedButton(
                    onClick = {
                        showFilterSheet = false
                        onNavigateToManageCategories()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.inkPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Categories", fontFamily = InterFontFamily, fontSize = 13.sp, color = colors.inkPrimary)
                }

                Spacer(modifier = Modifier.height(12.dp))
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
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Paste a public Instagram reel link to extract self-contained knowledge notes:",
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
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
                    Text("Analyze & Save", fontFamily = InterFontFamily, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = colors.inkSecondary)
                }
            }
        )
    }
}

@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    dotColor: Color? = null,
    onClick: () -> Unit
) {
    val colors = LocalSavyorColors.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) colors.ctaBg else colors.surfaceCard,
        border = BorderStroke(1.dp, if (selected) colors.ctaBg else colors.borderSubtle)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontFamily = InterFontFamily,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) colors.ctaFg else colors.inkSecondary
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderSubtle)
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
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
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
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
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
        border = BorderStroke(1.dp, colors.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
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
                            .savyorGlass(shape = CircleShape, elevated = false),
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
                    fontFamily = NewsreaderFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp,
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
                        fontFamily = InterFontFamily,
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
                        fontFamily = InterFontFamily,
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
                        Text("Next Status", fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.triedFg)
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (reel.isStarred) "Unstar" else "Star", fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.reviewedFg)
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
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
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
                    fontFamily = NewsreaderFontFamily,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
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
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
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
                            fontFamily = InterFontFamily,
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
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
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
            fontFamily = NewsreaderFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
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
                fontFamily = NewsreaderFontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Share any Instagram reel to Savyor, or tap below to paste a reel link.",
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
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
                Text("Save Reel", fontFamily = InterFontFamily, fontWeight = FontWeight.Medium)
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
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.inkMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No matching reels",
                fontFamily = NewsreaderFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (query.isNotBlank()) "No notes, transcripts, or visual cues matched \"$query\"." else "No reels match the active filters.",
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
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
                Text("Clear search", fontFamily = InterFontFamily, fontSize = 13.sp, color = colors.inkPrimary)
            }
        }
    }
}
