package com.savyor.app.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.KeyMomentWithImage
import com.savyor.app.domain.model.ExtractedResource
import com.savyor.app.domain.model.StructuredContent
import com.savyor.app.ui.components.StatusBadge
import com.savyor.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelDetailScreen(
    viewModel: ReelDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val reel by viewModel.reel.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Notes, 1 = Transcript, 2 = Links
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Fullscreen image viewer state
    var viewingImageSnapshot by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(imagePath, label)

    if (reel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val currentReel = reel!!
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Copy Markdown
                    IconButton(onClick = {
                        copyMarkdownNotes(context, currentReel, keyTakeaways, structuredContent)
                    }) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Markdown",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Open in Instagram
                    IconButton(onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentReel.cleanUrl))
                        context.startActivity(browserIntent)
                    }) {
                        Icon(
                            Icons.Outlined.OpenInNew,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header: Category Pill, Status Badge, Non-English Badge, Title, Creator, Use-When
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Category & Status & Language pills row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Tappable Category Chip
                        Box {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable { showCategoryMenu = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = currentReel.category,
                                        fontFamily = InterFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, fontFamily = InterFontFamily) },
                                        onClick = {
                                            viewModel.updateCategory(cat.name)
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Tappable Status Badge
                        Box {
                            Box(modifier = Modifier.clickable { showStatusMenu = true }) {
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
                                            viewModel.updateStatus(st)
                                            showStatusMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Non-English indicator badge
                        if (currentReel.sourceLanguage.isNotBlank() && !currentReel.sourceLanguage.equals("English", ignoreCase = true)) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = "${currentReel.sourceLanguage} → English",
                                    fontFamily = InterFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title in Large Newsreader Serif
                    Text(
                        text = currentReel.title.ifBlank { "Reel @${currentReel.shortcode}" },
                        fontFamily = NewsreaderFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 32.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Creator subtitle
                    val creatorText = if (currentReel.creator.isNotBlank()) "@${currentReel.creator}" else "@${currentReel.shortcode}"
                    Text(
                        text = creatorText,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Clean Left-Accented Use-When Callout
                    if (currentReel.useWhen.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val cleanUseWhen = currentReel.useWhen.replace(
                            Regex("^(Use when:|Use when|Refer to this note when:?|Refer to this note when)\\s*", RegexOption.IGNORE_CASE),
                            ""
                        ).trim()

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "USE WHEN",
                                        fontFamily = InterFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = cleanUseWhen,
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Visual Moments Carousel
            item {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = "VISUAL MOMENTS (${keyMoments.size + (if (currentReel.thumbnailPath != null) 1 else 0)})",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Thumbnail item
                        if (!currentReel.thumbnailPath.isNullOrBlank() && File(currentReel.thumbnailPath).exists()) {
                            item {
                                SnapshotCard(
                                    imagePath = currentReel.thumbnailPath,
                                    label = "Thumbnail",
                                    onClick = { viewingImageSnapshot = Pair(currentReel.thumbnailPath, "Thumbnail") }
                                )
                            }
                        }

                        // Key Moment items
                        items(keyMoments) { moment ->
                            SnapshotCard(
                                imagePath = moment.webpPath,
                                label = "[${formatTimestamp(moment.timestampSec)}] ${moment.label}",
                                onClick = { viewingImageSnapshot = Pair(moment.webpPath, "[${formatTimestamp(moment.timestampSec)}] ${moment.label}") }
                            )
                        }
                    }
                }
            }

            // Navigation Tabs (Notes, Transcript, Links)
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Notes",
                                fontFamily = InterFontFamily,
                                fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Transcript",
                                fontFamily = InterFontFamily,
                                fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "Links (${extractedResources.size})",
                                fontFamily = InterFontFamily,
                                fontWeight = if (selectedTab == 2) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Tab 0: Notes
            if (selectedTab == 0) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // Summary
                        Text(
                            text = "SUMMARY",
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentReel.summary.ifBlank { "No summary available." },
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(20.dp))

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
                            Spacer(modifier = Modifier.height(8.dp))
                            keyTakeaways.forEach { takeaway ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = takeaway,
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Structured Content
                        structuredContent?.let { content ->
                            StructuredContentView(content)
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Caveats / Context Callout
                        if (caveats.isNotEmpty()) {
                            val dark = isSystemInDarkTheme()
                            val caveatBg = if (dark) StatusBadgeColors.CaveatBgDark else StatusBadgeColors.CaveatBgLight
                            val caveatBorder = if (dark) StatusBadgeColors.CaveatBorderDark else StatusBadgeColors.CaveatBorderLight
                            val caveatText = if (dark) StatusBadgeColors.CaveatTextDark else StatusBadgeColors.CaveatTextLight

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = caveatBg,
                                border = BorderStroke(0.5.dp, caveatBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
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
                                                fontSize = 13.sp,
                                                color = caveatText,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Text(
                                                text = caveat,
                                                fontFamily = InterFontFamily,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                color = caveatText
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }

            // Tab 1: Transcript
            if (selectedTab == 1) {
                item {
                    var showOriginal by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // Segmented Toggle for Non-English Reels
                        if (!currentReel.originalTranscript.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Row(modifier = Modifier.padding(3.dp)) {
                                        Surface(
                                            onClick = { showOriginal = false },
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
                                            onClick = { showOriginal = true },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (showOriginal) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ) {
                                            Text(
                                                text = "Original (${currentReel.sourceLanguage})",
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

                        val activeTranscript = if (showOriginal && !currentReel.originalTranscript.isNullOrBlank()) {
                            currentReel.originalTranscript
                        } else {
                            currentReel.transcript
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeTranscript.ifBlank { "No transcript available." },
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Tab 2: Links & Extracted Resources
            if (selectedTab == 2) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        if (extractedResources.isEmpty()) {
                            Text(
                                text = "No external resources or URLs were mentioned in this reel.",
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            extractedResources.forEach { resource ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = resource.name,
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (resource.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = resource.description,
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        if (resource.url.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val uri = Uri.parse(resource.url)
                                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.OpenInNew,
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
                                            // Comment-gated or no direct URL: show Search Web button
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val query = Uri.encode(resource.name)
                                                        val uri = Uri.parse("https://www.google.com/search?q=$query")
                                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                                        context.startActivity(intent)
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
            }

            item {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }

    // Fullscreen Image Viewer Dialog
    viewingImageSnapshot?.let { (imagePath, label) ->
        Dialog(onDismissRequest = { viewingImageSnapshot = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewingImageSnapshot = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
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
fun SnapshotCard(
    imagePath: String,
    label: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .width(160.dp)
            .height(210.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StructuredContentView(content: StructuredContent) {
    val context = LocalContext.current

    Column {
        Text(
            text = "DETAILS (${content.noteType.uppercase()})",
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Recipes: Ingredients
        if (!content.ingredients.isNullOrEmpty()) {
            Text("Ingredients:", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.ingredients.forEach { ing ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("• ${ing.item}", fontFamily = InterFontFamily, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (ing.quantity.isNotBlank()) {
                        Text(ing.quantity, fontFamily = InterFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Recipes: Steps
        if (!content.steps.isNullOrEmpty()) {
            Text("Steps:", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.steps.forEach { step ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("${step.stepNumber}. ", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text(step.instruction, fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Code: Commands
        if (!content.commands.isNullOrEmpty()) {
            Text("Commands:", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.commands.forEach { cmd ->
                Surface(
                    color = Color(0xFF1E2120),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF333836)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF74B9A2))
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Command", cmd))
                                Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Code: Snippets
        if (!content.codeSnippets.isNullOrEmpty()) {
            Text("Code Snippets:", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.codeSnippets.forEach { snippet ->
                Surface(
                    color = Color(0xFF1E2120),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF333836)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", snippet))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(snippet, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFE2ECE8))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Listicles / Recommendations
        if (!content.items.isNullOrEmpty()) {
            Text("Items:", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.items.forEachIndexed { idx, item ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("${idx + 1}. ", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text(item, fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 19.sp)
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

fun copyMarkdownNotes(
    context: Context,
    reel: ReelEntity,
    takeaways: List<String>,
    structured: StructuredContent?
) {
    val md = buildString {
        appendLine("# ${reel.title}")
        appendLine("**Category:** ${reel.category} | **Creator:** @${reel.creator} | **Language:** ${reel.sourceLanguage}")
        appendLine("**Original Reel:** ${reel.cleanUrl}")
        if (reel.useWhen.isNotBlank()) appendLine("> **Use when:** ${reel.useWhen}")
        appendLine()
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
        if (reel.transcript.isNotBlank()) {
            appendLine("## Transcript")
            appendLine(reel.transcript)
        }
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Savyor Note", md))
    Toast.makeText(context, "Markdown notes copied to clipboard", Toast.LENGTH_SHORT).show()
}
