package com.savyor.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savyor.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToManageCategories: () -> Unit = {}
) {
    val colors = LocalSavyorColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val maskedApiKey by viewModel.maskedApiKey.collectAsState()
    val isWifiOnly by viewModel.isWifiOnly.collectAsState()
    val ytDlpVersion by viewModel.ytDlpVersion.collectAsState()
    val isUpdatingEngine by viewModel.isUpdatingEngine.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionStatus by viewModel.connectionTestStatus.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val lastBackupMs by viewModel.lastBackupMs.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.backupRestoreMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.exportBackup(uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.importBackup(uri)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(modifier = Modifier.background(colors.glassSurface)) {
                TopAppBar(
                    title = {
                        Text(
                            "Settings",
                            fontFamily = NewsreaderFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 24.sp,
                            color = colors.inkPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.inkPrimary
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
        containerColor = colors.bgCanvas
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 0: Appearance (3-way Theme Selector)
            item {
                SectionLabel("APPEARANCE")
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Theme",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceRecessed,
                            border = BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SavyorThemeMode.entries.forEach { mode ->
                                    val selected = themeMode == mode
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.setThemeMode(mode)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) colors.surfaceCard else Color.Transparent,
                                        border = if (selected) BorderStroke(1.dp, colors.borderSubtle) else null,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = mode.label,
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (selected) colors.inkPrimary else colors.inkMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 1: Gemini AI Key
            item {
                SectionLabel("GEMINI AI CONFIGURATION")
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "API Key",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = maskedApiKey,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { showApiKeyDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle)
                            ) {
                                Text("Set Key", fontFamily = InterFontFamily, fontSize = 12.sp, color = colors.inkPrimary)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = colors.borderSubtle,
                            thickness = 1.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testGeminiConnection() },
                                enabled = !isTestingConnection,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.inkPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Test Connection", fontFamily = InterFontFamily, fontSize = 12.sp, color = colors.inkPrimary)
                            }

                            if (!connectionStatus.isNullOrBlank()) {
                                val isSuccess = connectionStatus!!.startsWith("Success")
                                Text(
                                    text = if (isSuccess) "Connected" else "Failed",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSuccess) colors.triedFg else colors.errorFg
                                )
                            }
                        }

                        if (!connectionStatus.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = connectionStatus!!,
                                fontFamily = InterFontFamily,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }
                }
            }

            // Section 2: Engine & Network
            item {
                SectionLabel("ENGINE & NETWORK")
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "Wi-Fi Only Downloads",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Only process reels when connected to unmetered Wi-Fi",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                            Switch(
                                checked = isWifiOnly,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setWifiOnly(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.ctaFg,
                                    checkedTrackColor = colors.ctaBg
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = colors.borderSubtle,
                            thickness = 1.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "yt-dlp Engine Version",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    ytDlpVersion,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.updateYtDlpEngine() },
                                enabled = !isUpdatingEngine,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle)
                            ) {
                                if (isUpdatingEngine) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.inkPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Update", fontFamily = InterFontFamily, fontSize = 12.sp, color = colors.inkPrimary)
                            }
                        }
                    }
                }
            }

            // Section 3: Organization & Categories
            item {
                SectionLabel("ORGANIZATION")
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToManageCategories()
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Manage Categories",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Add, rename, and recolor topic tags",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = colors.inkMuted
                        )
                    }
                }
            }

            // Section 4: Backup & Restore (.zip) via SAF
            item {
                SectionLabel("BACKUP & RESTORE")
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "Backup to Google Drive / Files (.zip)",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Save all notes, categories, and snapshots directly to Google Drive or phone storage",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                                if (lastBackupMs > 0L) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val formattedDate = remember(lastBackupMs) {
                                        java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault())
                                            .format(java.util.Date(lastBackupMs))
                                    }
                                    Text(
                                        text = "Last backup: $formattedDate",
                                        fontFamily = InterFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.triedFg
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    val defaultName = "savyor-backup-${System.currentTimeMillis()}.zip"
                                    exportBackupLauncher.launch(defaultName)
                                },
                                enabled = !isBackingUp && !isRestoring,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle)
                            ) {
                                if (isBackingUp) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.inkPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Export", fontFamily = InterFontFamily, fontSize = 12.sp, color = colors.inkPrimary)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = colors.borderSubtle,
                            thickness = 1.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "Restore from Backup (.zip)",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Restore notes, categories, and snapshots from a backup archive",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    importBackupLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                                },
                                enabled = !isBackingUp && !isRestoring,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle)
                            ) {
                                if (isRestoring) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.inkPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Restore", fontFamily = InterFontFamily, fontSize = 12.sp, color = colors.inkPrimary)
                            }
                        }
                    }
                }
            }

            // Section 5: OEM-Aware Device Optimization
            item {
                SectionLabel("DEVICE OPTIMIZATION")
                Spacer(modifier = Modifier.height(6.dp))

                val (oemTitle, oemGuidance) = remember { oemBatteryGuidance() }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = colors.inkPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                oemTitle,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = oemGuidance,
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = colors.inkSecondary,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Text("Open App Settings", fontFamily = InterFontFamily, fontSize = 12.sp, color = colors.inkPrimary)
                        }
                    }
                }
            }

            // Section 6: Storage Breakdown
            item {
                SectionLabel("STORAGE BREAKDOWN")
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StorageRow(label = "Notes & FTS Database", bytes = storageStats.dbSizeBytes)
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = colors.borderSubtle,
                            thickness = 1.dp
                        )
                        StorageRow(label = "Visual WebP Snapshots", bytes = storageStats.snapshotsSizeBytes)
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = colors.borderSubtle,
                            thickness = 1.dp
                        )
                        StorageRow(
                            label = "Permanent Video Storage",
                            subtitle = "Auto-cleaned after analysis",
                            bytes = storageStats.videoSizeBytes
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { viewModel.clearSnapshots() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.errorFg),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.errorFg.copy(alpha = 0.45f))
                        ) {
                            Text("Clear Snapshots Cache", fontFamily = InterFontFamily, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Set API Key Dialog
    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    "Gemini API Key",
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Enter your Google AI Studio Gemini API key. Stored securely in Android Keystore encrypted storage and never leaves your device.",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("AIzaSy...", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            viewModel.saveApiKey(keyInput)
                            showApiKeyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ctaBg,
                        contentColor = colors.ctaFg
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save", fontFamily = InterFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = colors.inkSecondary)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalSavyorColors.current
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.inkMuted,
        letterSpacing = 0.8.sp
    )
}

private fun oemBatteryGuidance(): Pair<String, String> {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("samsung") -> {
            "Samsung One UI Background Processing" to
                "To let Savyor finish reel analysis when your screen is off, set App Battery usage to 'Unrestricted' in Samsung One UI app settings."
        }
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
            "Xiaomi / HyperOS Background Processing" to
                "To prevent HyperOS from pausing reel analysis in the background, set Battery saver to 'No restrictions' and enable Autostart in App settings."
        }
        manufacturer.contains("oneplus") || manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
            "OxygenOS / ColorOS Background Processing" to
                "To allow reel analysis while the screen is off, allow 'Background activity' under Battery usage in App settings."
        }
        else -> {
            "Android Background Processing" to
                "To allow Savyor to finish reel analysis when your screen is off, set Battery optimization to 'Unrestricted' in App settings."
        }
    }
}

@Composable
fun StorageRow(
    label: String,
    bytes: Long,
    subtitle: String = ""
) {
    val colors = LocalSavyorColors.current
    val mb = bytes.toDouble() / (1024 * 1024)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.inkPrimary
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    color = colors.inkSecondary
                )
            }
        }
        Text(
            text = "${"%.2f".format(mb)} MB",
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (bytes == 0L) colors.triedFg else colors.inkPrimary
        )
    }
}
