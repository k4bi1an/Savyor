package com.savyor.app.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.KeyMomentWithImage
import com.savyor.app.domain.model.ExtractedResource
import com.savyor.app.domain.model.StructuredContent
import com.savyor.app.ui.screens.library.getStatusColor
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Category changer
                    Box {
                        TextButton(onClick = { showCategoryMenu = true }) {
                            Text(currentReel.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        viewModel.updateCategory(cat.name)
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Status dropdown
                    Box {
                        Surface(
                            color = getStatusColor(currentReel.status),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clickable { showStatusMenu = true }
                                .padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = currentReel.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showStatusMenu,
                            onDismissRequest = { showStatusMenu = false }
                        ) {
                            listOf("UNREAD", "REVIEWED", "TRIED", "ARCHIVED").forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st) },
                                    onClick = {
                                        viewModel.updateStatus(st)
                                        showStatusMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Copy Markdown
                    IconButton(onClick = {
                        copyMarkdownNotes(context, currentReel, keyTakeaways, structuredContent)
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Markdown")
                    }

                    // Open in Instagram
                    IconButton(onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentReel.cleanUrl))
                        context.startActivity(browserIntent)
                    }) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open in Instagram")
                    }

                    // Delete
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFB3382C))
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
            // Header: Title, Creator, Language, Use-When
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = currentReel.title.ifBlank { "Reel @${currentReel.shortcode}" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (currentReel.creator.isNotBlank()) "@${currentReel.creator}" else "@${currentReel.shortcode}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Language: ${currentReel.sourceLanguage}",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Use-When Cue
                    if (currentReel.useWhen.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Use when: ${currentReel.useWhen}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Snapshot Carousel: 360px Thumbnail + 640px Key Moments
            item {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = "Visual Moments (${keyMoments.size + (if (currentReel.thumbnailPath != null) 1 else 0)})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
                                    label = "Thumbnail"
                                )
                            }
                        }

                        // Key Moment items
                        items(keyMoments) { moment ->
                            SnapshotCard(
                                imagePath = moment.webpPath,
                                label = "[${formatTimestamp(moment.timestampSec)}] ${moment.label}"
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
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Notes", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Transcript", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Links (${extractedResources.size})", fontWeight = FontWeight.SemiBold) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tab 0: Notes
            if (selectedTab == 0) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // Summary
                        Text(
                            text = "SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentReel.summary.ifBlank { "No summary available." },
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Key Takeaways
                        if (keyTakeaways.isNotEmpty()) {
                            Text(
                                text = "KEY TAKEAWAYS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            keyTakeaways.forEach { takeaway ->
                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = takeaway,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        // Structured Content
                        structuredContent?.let { content ->
                            StructuredContentView(content)
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        // Caveats / Satire / Comment-Gated Notices
                        if (caveats.isNotEmpty()) {
                            Text(
                                text = "CAVEATS & CONTEXT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BadgeWarning,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            caveats.forEach { caveat ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = BadgeWarning,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = caveat,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }
                }
            }

            // Tab 1: Transcript
            if (selectedTab == 1) {
                item {
                    var showOriginal by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        if (!currentReel.originalTranscript.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showOriginal) "Original Spoken (${currentReel.sourceLanguage})" else "English Translation",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = { showOriginal = !showOriginal }) {
                                    Text(if (showOriginal) "Show English" else "Show Original")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        val activeTranscript = if (showOriginal && !currentReel.originalTranscript.isNullOrBlank()) {
                            currentReel.originalTranscript
                        } else {
                            currentReel.transcript
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeTranscript.ifBlank { "No transcript available." },
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(14.dp)
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
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        } else {
                            extractedResources.forEach { resource ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(resource.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        if (resource.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(resource.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                        }
                                        if (resource.url.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    try {
                                                        val uri = Uri.parse(resource.url)
                                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(resource.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
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
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Reel Note?") },
            text = { Text("This will permanently delete this note and its extracted WebP snapshots.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteReel { onNavigateBack() }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3382C))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SnapshotCard(imagePath: String, label: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .width(160.dp)
            .height(210.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFE5E5E5))
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
                fontSize = 10.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp),
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Recipes: Ingredients
        if (!content.ingredients.isNullOrEmpty()) {
            Text("Ingredients:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.ingredients.forEach { ing ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("• ${ing.item}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (ing.quantity.isNotBlank()) {
                        Text(ing.quantity, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Recipes: Steps
        if (!content.steps.isNullOrEmpty()) {
            Text("Steps:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.steps.forEach { step ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("${step.stepNumber}. ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text(step.instruction, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Code: Commands
        if (!content.commands.isNullOrEmpty()) {
            Text("Commands:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.commands.forEach { cmd ->
                Surface(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF4AF626))
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
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Code: Snippets
        if (!content.codeSnippets.isNullOrEmpty()) {
            Text("Code Snippets:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.codeSnippets.forEach { snippet ->
                Surface(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
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
                        Text(snippet, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFE0E0E0))
                    }
                }
            }
        }

        // Listicles / Recommendations
        if (!content.items.isNullOrEmpty()) {
            Text("Items:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content.items.forEachIndexed { idx, item ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("${idx + 1}. ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text(item, fontSize = 13.sp, lineHeight = 18.sp)
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
