package com.savyor.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savyor.app.ui.screens.library.CrystalIconWell
import com.savyor.app.ui.theme.*

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
    val ytDlpLatestVersion by viewModel.ytDlpLatestVersion.collectAsState()
    val ytDlpUpdateStatus by viewModel.ytDlpUpdateStatus.collectAsState()
    val ytDlpLastCheckedMs by viewModel.ytDlpLastCheckedMs.collectAsState()
    val isUpdatingEngine by viewModel.isUpdatingEngine.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionStatus by viewModel.connectionTestStatus.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val isExportingTelemetry by viewModel.isExportingTelemetry.collectAsState()
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

    val exportTelemetryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.exportTelemetry(uri)
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgCanvas)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + 76.dp,
                bottom = navBarBottom + 36.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 0: Appearance (3-way Crystal Segmented Control)
            item {
                SectionLabel("APPEARANCE")
                Spacer(modifier = Modifier.height(6.dp))

                val cardShape = RoundedCornerShape(16.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Theme",
                            fontFamily = SavyorSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
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
                                    val pillShape = RoundedCornerShape(10.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(
                                                if (selected) {
                                                    Modifier.savyorLiquidCrystal(
                                                        tier = CrystalTier.PILL,
                                                        shape = pillShape,
                                                        activeRim = true
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setThemeMode(mode)
                                            }
                                            .padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.label,
                                            fontFamily = SavyorSansFamily,
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

            // Section 1: Gemini AI Key
            item {
                SectionLabel("GEMINI AI CONFIGURATION")
                Spacer(modifier = Modifier.height(6.dp))

                val cardShape = RoundedCornerShape(16.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
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
                                    fontFamily = SavyorSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = maskedApiKey,
                                    fontFamily = SavyorMonoFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = { showApiKeyDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle)
                            ) {
                                Text("Set Key", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.inkPrimary)
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
                                Text("Test Connection", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.inkPrimary)
                            }

                            if (!connectionStatus.isNullOrBlank()) {
                                val isSuccess = connectionStatus!!.startsWith("Success")
                                Text(
                                    text = if (isSuccess) "Connected" else "Failed",
                                    fontFamily = SavyorSansFamily,
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
                                style = MaterialTheme.typography.bodySmall,
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

                val cardShape = RoundedCornerShape(16.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
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
                                    fontFamily = SavyorSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Only process reels when connected to unmetered Wi-Fi",
                                    style = MaterialTheme.typography.bodySmall,
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
                                    fontFamily = SavyorSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        ytDlpVersion,
                                        fontFamily = SavyorMonoFamily,
                                        fontSize = 12.sp,
                                        color = colors.inkSecondary
                                    )
                                    val (badgeText, badgeFg, badgeBg) = when (ytDlpUpdateStatus) {
                                        "UP_TO_DATE" -> Triple("Up to date", colors.triedFg, colors.triedBg)
                                        "UPDATE_AVAILABLE" -> Triple(
                                            if (ytDlpLatestVersion.isNotBlank()) "Update available ($ytDlpLatestVersion)" else "Update available",
                                            colors.reviewedFg,
                                            colors.reviewedBg
                                        )
                                        "CHECKING" -> Triple("Checking...", colors.unreadFg, colors.unreadBg)
                                        else -> Triple("Unknown", colors.inkMuted, colors.surfaceRecessed)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontFamily = SavyorMonoFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (ytDlpLastCheckedMs > 0L) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    val rel = DateUtils.getRelativeTimeSpanString(
                                        ytDlpLastCheckedMs,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    )
                                    Text(
                                        text = "Last checked: $rel",
                                        fontFamily = SavyorSansFamily,
                                        fontSize = 11.sp,
                                        color = colors.inkMuted
                                    )
                                }
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
                                Text("Update", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.inkPrimary)
                            }
                        }
                    }
                }
            }

            // Section 3: Organization & Categories
            item {
                SectionLabel("ORGANIZATION")
                Spacer(modifier = Modifier.height(6.dp))

                val cardShape = RoundedCornerShape(16.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToManageCategories()
                        },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
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
                                    fontFamily = SavyorSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Add, rename, and recolor topic tags",
                                    style = MaterialTheme.typography.bodySmall,
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

                val cardShape = RoundedCornerShape(16.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
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
                                    fontFamily = SavyorSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Save all notes, categories, and snapshots directly to Google Drive or phone storage",
                                    style = MaterialTheme.typography.bodySmall,
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
                                        fontFamily = SavyorMonoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
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
                                Text("Export", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.inkPrimary)
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
                                    fontFamily = SavyorSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Restore notes, categories, and snapshots from a backup archive",
                                    style = MaterialTheme.typography.bodySmall,
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
                                Text("Restore", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.inkPrimary)
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
                                    "Export Processing Telemetry (.json)",
                                    fontFamily = SavyorSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Export device specs, average stage latencies, and per-reel metrics",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    exportTelemetryLauncher.launch("savyor-telemetry-${System.currentTimeMillis()}.json")
                                },
                                enabled = !isExportingTelemetry,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle)
                            ) {
                                if (isExportingTelemetry) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.inkPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Export", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.inkPrimary)
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
                val cardShape = RoundedCornerShape(16.dp)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = colors.inkPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                oemTitle,
                                fontFamily = SavyorSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = oemGuidance,
                            style = MaterialTheme.typography.bodySmall,
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
                            Text("Open App Settings", fontFamily = SavyorSansFamily, fontSize = 12.sp, color = colors.inkPrimary)
                        }
                    }
                }
            }

            // Section 6: Storage Breakdown
            item {
                SectionLabel("STORAGE BREAKDOWN")
                Spacer(modifier = Modifier.height(6.dp))

                val cardShape = RoundedCornerShape(16.dp)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
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
                            Text("Clear Snapshots Cache", fontFamily = SavyorSansFamily, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Top Status-Bar Fade Scrim
        TopStatusBarScrim(statusBarTop = statusBarTop)

        // Floating Liquid Crystal Header Capsule
        val capsuleShape = RoundedCornerShape(29.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                .height(56.dp)
                .savyorLiquidCrystal(tier = CrystalTier.CHROME, shape = capsuleShape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CrystalIconWell(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                size = 36.dp,
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.inkPrimary
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
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
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "Enter your Google AI Studio Gemini API key. Stored securely in Android Keystore encrypted storage and never leaves your device.",
                        style = MaterialTheme.typography.bodySmall,
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
                    Text("Save", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", fontFamily = SavyorSansFamily, color = colors.inkSecondary)
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
        style = MaterialTheme.typography.labelSmall,
        color = colors.inkMuted
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
                fontFamily = SavyorSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.inkPrimary
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = colors.inkSecondary
                )
            }
        }
        Text(
            text = "${"%.2f".format(mb)} MB",
            fontFamily = SavyorMonoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (bytes == 0L) colors.triedFg else colors.inkPrimary
        )
    }
}
