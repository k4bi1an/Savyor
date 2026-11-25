package com.savyor.app.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.KeyMomentWithImage
import com.savyor.app.domain.model.ExtractedResource
import com.savyor.app.domain.model.StructuredContent
import com.savyor.app.ui.components.StatusBadge
import com.savyor.app.ui.components.springPressClickable
import com.savyor.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

private data class LightboxFrame(
    val imagePath: String,
    val badgeText: String,
    val caption: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReelDetailScreen(
    viewModel: ReelDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val reel by viewModel.reel.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var showCategoryMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var viewingFrameIndex by remember { mutableStateOf<Int?>(null) }
    var isEditingMyNote by remember { mutableStateOf(false) }

    if (reel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalSavyorColors.current.inkPrimary)
        }
        return
    }

    val colors = LocalSavyorColors.current
    val currentReel = reel!!
    var myNoteDraft by remember(currentReel.userNote) { mutableStateOf(currentReel.userNote) }
    val gson = remember { Gson() }

    // Parse JSON fields safely
    val keyMoments: List<KeyMomentWithImage> = remember(currentReel.keyMoments) {
        try {
            val type = object : TypeToken<List<KeyMomentWithImage>>() {}.type
            gson.fromJson(currentReel.keyMoments, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    val keyTakeaways: List<String> = remember(currentReel.keyTakeaways) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(currentReel.keyTakeaways, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    val caveats: List<String> = remember(currentReel.caveats) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(currentReel.caveats, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    val structuredContent: StructuredContent? = remember(currentReel.structuredContent) {
        try {
            gson.fromJson(currentReel.structuredContent, StructuredContent::class.java)
        } catch (e: Exception) { null }
    }

    val extractedResources: List<ExtractedResource> = remember(currentReel.extractedResources) {
        try {
            val type = object : TypeToken<List<ExtractedResource>>() {}.type
            gson.fromJson(currentReel.extractedResources, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // All frames for carousel + lightbox
    val allFrames: List<LightboxFrame> = remember(currentReel.thumbnailPath, keyMoments) {
        buildList {
            if (!currentReel.thumbnailPath.isNullOrBlank() && File(currentReel.thumbnailPath).exists()) {
                add(
                    LightboxFrame(
                        imagePath = currentReel.thumbnailPath,
                        badgeText = "Cover",
                        caption = "Cover snapshot"
                    )
                )
            }
            keyMoments.forEach { moment ->
                if (File(moment.webpPath).exists()) {
                    val cleanLabel = moment.label.replace(Regex("^\\[\\d+:\\d{2}\\]\\s*"), "").trim()
                    add(
                        LightboxFrame(
                            imagePath = moment.webpPath,
                            badgeText = formatTimestamp(moment.timestampSec),
                            caption = cleanLabel
                        )
                    )
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200
        }
    }

    val tabPagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(modifier = Modifier.background(colors.glassSurface)) {
                TopAppBar(
                    title = {
                        AnimatedVisibility(
                            visible = showTopBarTitle,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180))
                        ) {
                            Text(
                                text = currentReel.title.ifBlank { "Reel @${currentReel.shortcode}" },
                                fontFamily = NewsreaderFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Star / Favorite Toggle
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleStarred()
                        }) {
                            Icon(
                                imageVector = if (currentReel.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (currentReel.isStarred) "Unstar Reel" else "Star Reel",
                                tint = if (currentReel.isStarred) Color(0xFFD4A843) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Copy Markdown
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            copyMarkdownNotes(context, currentReel, keyTakeaways, structuredContent, extractedResources)
                        }) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Markdown",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Open in Instagram
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentReel.cleanUrl))
                            context.startActivity(browserIntent)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = "Open in Instagram",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Overflow Menu
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share note", fontFamily = InterFontFamily) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        shareFormattedNote(context, currentReel, keyTakeaways, structuredContent, extractedResources)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Re-analyze reel", fontFamily = InterFontFamily) },
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.reanalyzeReel {
                                            Toast.makeText(context, "Reel queued for re-analysis", Toast.LENGTH_SHORT).show()
                                            onNavigateBack()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Change Category", fontFamily = InterFontFamily) },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showCategoryMenu = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Change Status", fontFamily = InterFontFamily) },
                                    leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showStatusMenu = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete Reel", fontFamily = InterFontFamily, color = Color(0xFFB3382C)) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFB3382C)) },
                                    onClick = {
                                        showMoreMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
                HorizontalDivider(
                    color = colors.borderSubtle,
                    thickness = 1.dp
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isEditingMyNote,
                enter = slideInVertically { it } + fadeIn(tween(180)),
                exit = slideOutVertically { it } + fadeOut(tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .savyorGlass(shape = RoundedCornerShape(18.dp), elevated = true)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Editing My Note",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    myNoteDraft = currentReel.userNote
                                    isEditingMyNote = false
                                }
                            ) {
                                Text(
                                    "Cancel",
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    color = colors.inkSecondary
                                )
                            }
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.updateUserNote(myNoteDraft.trim())
                                    isEditingMyNote = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.ctaBg,
                                    contentColor = colors.ctaFg
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Save Note", fontFamily = InterFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        },
        containerColor = colors.bgCanvas
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Calm Editorial Header
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Single quiet scannable metadata row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Tappable Category text + chevron
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showCategoryMenu = true }
                                    .padding(vertical = 2.dp, horizontal = 2.dp)
                            ) {
                                Text(
                                    text = currentReel.category,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Change Category",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, fontFamily = InterFontFamily) },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.updateCategory(cat.name)
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = " · ",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Tappable Muted Status Badge
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showStatusMenu = true }
                            ) {
                                StatusBadge(status = currentReel.status)
                            }

                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false }
                            ) {
                                listOf("UNREAD", "REVIEWED", "TRIED", "ARCHIVED").forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text(st, fontFamily = InterFontFamily) },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.updateStatus(st)
                                            showStatusMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        if (currentReel.sourceLanguage.isNotBlank() && !currentReel.sourceLanguage.equals("English", ignoreCase = true)) {
                            Text(
                                text = " · ${currentReel.sourceLanguage} → English",
                                fontFamily = InterFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title in 28sp Newsreader Serif
                    Text(
                        text = currentReel.title.ifBlank { "Reel @${currentReel.shortcode}" },
                        fontFamily = NewsreaderFontFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 34.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Creator Subtitle
                    val creatorText = if (currentReel.creator.isNotBlank()) "@${currentReel.creator}" else "@${currentReel.shortcode}"
                    Text(
                        text = creatorText,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Primary Highlighted Architectural Use-When Block
                    if (currentReel.useWhen.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val cleanUseWhen = currentReel.useWhen.replace(
                            Regex("^(Use when:|Use when|Refer to this note when:?|Refer to this note when)\\s*", RegexOption.IGNORE_CASE),
                            ""
                        ).trim()

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceCard,
                            border = BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.inkPrimary)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "USE WHEN",
                                        fontFamily = InterFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.8.sp,
                                        color = colors.inkMuted
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = cleanUseWhen,
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 20.sp,
                                        color = colors.inkPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Personal "My Note" Field
                    Spacer(modifier = Modifier.height(12.dp))
                    MyNoteCard(
                        userNote = currentReel.userNote,
                        isEditing = isEditingMyNote,
                        draftText = myNoteDraft,
                        onStartEditing = {
                            myNoteDraft = currentReel.userNote
                            isEditingMyNote = true
                        },
                        onDraftChange = { myNoteDraft = it }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Visual Moments Carousel (3:4 portrait rounded cards, frosted bottom-left timestamp pill, borderless caption underneath)
            if (allFrames.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(bottom = 18.dp)) {
                        Text(
                            text = "VISUAL MOMENTS (${allFrames.size})",
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(allFrames) { index, frame ->
                                MomentPortraitCard(
                                    frame = frame,
                                    onClick = { viewingFrameIndex = index }
                                )
                            }
                        }
                    }
                }
            }

            // Segmented Tab Rail (surfaceRecessed track + surfaceCard active pill)
            item {
                val tabTitles = listOf("Notes", "Transcript", "Links (${extractedResources.size})")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceRecessed,
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val selected = tabPagerState.currentPage == index
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        tabPagerState.animateScrollToPage(index)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) colors.surfaceCard else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, colors.borderSubtle) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) colors.inkPrimary else colors.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Swipeable HorizontalPager for Tab Bodies
            item {
                HorizontalPager(
                    state = tabPagerState,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) { page ->
                    when (page) {
                        0 -> NotesTabContent(
                            reel = currentReel,
                            keyTakeaways = keyTakeaways,
                            structuredContent = structuredContent,
                            caveats = caveats
                        )
                        1 -> TranscriptTabContent(reel = currentReel)
                        2 -> LinksTabContent(extractedResources = extractedResources)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    // Fullscreen Edge-to-Edge Dark Lightbox Viewer
    viewingFrameIndex?.let { initialIndex ->
        FullscreenLightboxDialog(
            frames = allFrames,
            initialIndex = initialIndex,
            onDismiss = { viewingFrameIndex = null }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "Delete Reel Note?",
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    "This will permanently delete this note and its extracted WebP visual snapshots.",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteReel { onNavigateBack() }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3382C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", fontFamily = InterFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun MomentPortraitCard(
    frame: LightboxFrame,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .springPressClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(LocalSavyorColors.current.surfaceRecessed)
        ) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(frame.imagePath))
                    .crossfade(true)
                    .size(480, 640)
                    .build(),
                contentDescription = frame.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Frosted dark-glass timestamp pill in bottom-left corner
            Surface(
                color = Color(0xB80B0D0C),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = frame.badgeText,
                    fontFamily = InterFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = frame.caption,
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullscreenLightboxDialog(
    frames: List<LightboxFrame>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (frames.size - 1).coerceAtLeast(0)),
        pageCount = { frames.size }
    )
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B0D0C))
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentPageZoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val frame = frames[page]
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offset by remember(page) { mutableStateOf(Offset.Zero) }

                val animatedScale by animateFloatAsState(
                    targetValue = scale,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "zoom_scale"
                )

                LaunchedEffect(pagerState.currentPage, scale) {
                    if (pagerState.currentPage == page) {
                        isCurrentPageZoomed = scale > 1.02f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.02f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val pointerCount = event.changes.count { it.pressed }
                                    if (pointerCount > 1 || scale > 1.02f) {
                                        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                        scale = newScale
                                        if (newScale > 1.02f) {
                                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                            offset = Offset(
                                                x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                                y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                            event.changes.forEach { it.consume() }
                                        } else {
                                            offset = Offset.Zero
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(frame.imagePath),
                        contentDescription = frame.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
            }

            // Top Overlay: Frame Counter & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x66FFFFFF),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${frames.size}",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x55FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lightbox",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom Overlay: Moment Timestamp Pill + Caption
            val activeFrame = frames.getOrNull(pagerState.currentPage)
            if (activeFrame != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = Color(0x40FFFFFF),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = activeFrame.badgeText,
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeFrame.caption,
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesTabContent(
    reel: ReelEntity,
    keyTakeaways: List<String>,
    structuredContent: StructuredContent?,
    caveats: List<String>
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // Summary
        Text(
            text = "SUMMARY",
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reel.summary.ifBlank { "No summary available." },
            fontFamily = InterFontFamily,
            fontSize = 15.5.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Key Takeaways
        if (keyTakeaways.isNotEmpty()) {
            Text(
                text = "KEY TAKEAWAYS",
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            keyTakeaways.forEach { takeaway ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 9.dp, end = 10.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = takeaway,
                        fontFamily = InterFontFamily,
                        fontSize = 15.5.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Structured Content
        structuredContent?.let { content ->
            StructuredContentView(content)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Caveats / Context Callout
        if (caveats.isNotEmpty()) {
            val savyorColors = LocalSavyorColors.current
            val caveatBg = savyorColors.caveatBg
            val caveatBorder = savyorColors.caveatBorder
            val caveatText = savyorColors.caveatText

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = caveatBg,
                border = BorderStroke(0.5.dp, caveatBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = caveatText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CAVEATS & CONTEXT",
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = caveatText
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    caveats.forEach { caveat ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = caveatText,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = caveat,
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = caveatText
                            )
                        }
                    }
                }
            }
        }

        // Subtle Visual Memory Cue (for visual recall context)
        if (reel.visualMemory.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "VISUAL MEMORY CUE",
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reel.visualMemory,
                        fontFamily = InterFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptTabContent(reel: ReelEntity) {
    var showOriginal by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (!reel.originalTranscript.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showOriginal = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (!showOriginal) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Text(
                                text = "English",
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (!showOriginal) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (!showOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showOriginal = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (showOriginal) MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Text(
                                text = "Original (${reel.sourceLanguage})",
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (showOriginal) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (showOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = showOriginal,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(160))
            },
            label = "transcript_language_transition"
        ) { original ->
            val activeTranscript = if (original && !reel.originalTranscript.isNullOrBlank()) {
                reel.originalTranscript
            } else {
                reel.transcript
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = activeTranscript.ifBlank { "No transcript available." },
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LinksTabContent(extractedResources: List<ExtractedResource>) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (extractedResources.isEmpty()) {
            Text(
                text = "No external resources or URLs were mentioned in this reel.",
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            extractedResources.forEach { resource ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = resource.name,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (resource.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = resource.description,
                                fontFamily = InterFontFamily,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (resource.url.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val uri = Uri.parse(resource.url)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Open Link",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val query = Uri.encode(resource.name)
                                        val uri = Uri.parse("https://www.google.com/search?q=$query")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open search", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Search Web",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StructuredContentView(content: StructuredContent) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column {
        Text(
            text = "DETAILS (${content.noteType.uppercase()})",
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Prerequisites
        if (!content.prerequisites.isNullOrEmpty()) {
            Text("Prerequisites", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            content.prerequisites.forEach { prereq ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("• ", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(prereq, fontFamily = InterFontFamily, fontSize = 15.sp, lineHeight = 23.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Recipes: Neat Ingredient Rows
        if (!content.ingredients.isNullOrEmpty()) {
            Text("Ingredients", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            content.ingredients.forEachIndexed { idx, ing ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ing.item,
                        fontFamily = InterFontFamily,
                        fontSize = 15.5.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    if (ing.quantity.isNotBlank()) {
                        Text(
                            text = ing.quantity,
                            fontFamily = InterFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (idx < content.ingredients.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        thickness = 0.5.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Recipes / Tutorials: Circular Numbered Step Badges
        if (!content.steps.isNullOrEmpty()) {
            Text("Steps", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content.steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 12.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${step.stepNumber}",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = step.instruction,
                        fontFamily = InterFontFamily,
                        fontSize = 15.5.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Workouts: Exercises
        if (!content.exercises.isNullOrEmpty()) {
            Text("Exercises", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content.exercises.forEachIndexed { idx, exercise ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 12.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = exercise,
                        fontFamily = InterFontFamily,
                        fontSize = 15.5.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Code: Commands
        if (!content.commands.isNullOrEmpty()) {
            Text("Commands", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            content.commands.forEach { cmd ->
                Surface(
                    color = Color(0xFF1A1D1C),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF2E3432)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = cmd,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF74B9A2),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Command", cmd))
                                Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Code: Snippets
        if (!content.codeSnippets.isNullOrEmpty()) {
            Text("Code Snippets", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            content.codeSnippets.forEach { snippet ->
                Surface(
                    color = Color(0xFF1A1D1C),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF2E3432)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", snippet))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                            }
                        }
                        Text(
                            text = snippet,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFFE2ECE8)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Listicles / Recommendations
        if (!content.items.isNullOrEmpty()) {
            Text("Items", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content.items.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 12.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = item,
                        fontFamily = InterFontFamily,
                        fontSize = 15.5.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

fun formatTimestamp(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}

fun buildFormattedNoteText(
    reel: ReelEntity,
    takeaways: List<String>,
    structured: StructuredContent?,
    resources: List<ExtractedResource> = emptyList(),
    includeTranscript: Boolean = true
): String {
    return buildString {
        appendLine("# ${reel.title}")
        appendLine("**Category:** ${reel.category} | **Creator:** @${reel.creator} | **Language:** ${reel.sourceLanguage}")
        appendLine("**Original Reel:** ${reel.cleanUrl}")
        if (reel.useWhen.isNotBlank()) appendLine("> **Use when:** ${reel.useWhen}")
        appendLine()
        if (reel.userNote.isNotBlank()) {
            appendLine("## My Note")
            appendLine(reel.userNote)
            appendLine()
        }
        appendLine("## Summary")
        appendLine(reel.summary)
        appendLine()
        if (takeaways.isNotEmpty()) {
            appendLine("## Key Takeaways")
            takeaways.forEach { appendLine("- $it") }
            appendLine()
        }
        if (structured != null) {
            appendLine("## Details (${structured.noteType})")
            structured.ingredients?.let { ings ->
                appendLine("### Ingredients")
                ings.forEach { appendLine("- ${it.item}: ${it.quantity}") }
            }
            structured.steps?.let { steps ->
                appendLine("### Steps")
                steps.forEach { appendLine("${it.stepNumber}. ${it.instruction}") }
            }
            structured.commands?.let { cmds ->
                appendLine("### Commands")
                cmds.forEach { appendLine("`$it`") }
            }
            structured.codeSnippets?.let { snippets ->
                appendLine("### Code")
                snippets.forEach { appendLine(it) }
            }
            structured.items?.let { items ->
                items.forEachIndexed { i, it -> appendLine("${i + 1}. $it") }
            }
            appendLine()
        }
        if (resources.isNotEmpty()) {
            appendLine("## Links & Resources")
            resources.forEach { res ->
                val urlPart = if (res.url.isNotBlank()) " — ${res.url}" else ""
                appendLine("- **${res.name}**$urlPart: ${res.description}")
            }
            appendLine()
        }
        if (includeTranscript && reel.transcript.isNotBlank()) {
            appendLine("## Transcript")
            appendLine(reel.transcript)
        }
    }.trim()
}

fun copyMarkdownNotes(
    context: Context,
    reel: ReelEntity,
    takeaways: List<String>,
    structured: StructuredContent?,
    resources: List<ExtractedResource> = emptyList()
) {
    val md = buildFormattedNoteText(reel, takeaways, structured, resources, includeTranscript = true)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Savyor Note", md))
    Toast.makeText(context, "Markdown notes copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun shareFormattedNote(
    context: Context,
    reel: ReelEntity,
    takeaways: List<String>,
    structured: StructuredContent?,
    resources: List<ExtractedResource>
) {
    val noteText = buildFormattedNoteText(reel, takeaways, structured, resources, includeTranscript = false)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, reel.title.ifBlank { "Savyor Note" })
        putExtra(Intent.EXTRA_TEXT, noteText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share note via"))
}

@Composable
fun MyNoteCard(
    userNote: String,
    isEditing: Boolean,
    draftText: String,
    onStartEditing: () -> Unit,
    onDraftChange: (String) -> Unit
) {
    val colors = LocalSavyorColors.current
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !isEditing) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onStartEditing()
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, if (isEditing) colors.borderFocus else colors.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MY NOTE",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted,
                        letterSpacing = 0.8.sp
                    )
                }

                if (!isEditing && userNote.isNotBlank()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onStartEditing()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit note",
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text(
                            text = "Add personal observations, recipe tweaks, or action items...",
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 3,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = colors.inkPrimary
                    )
                )
            } else {
                if (userNote.isNotBlank()) {
                    Text(
                        text = userNote,
                        fontFamily = InterFontFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = colors.inkPrimary
                    )
                } else {
                    Text(
                        text = "Tap to add personal observations, recipe tweaks, or reminders...",
                        fontFamily = InterFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.inkMuted
                    )
                }
            }
        }
    }
}
