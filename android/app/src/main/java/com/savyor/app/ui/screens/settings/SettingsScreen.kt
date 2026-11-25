package com.savyor.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val maskedApiKey by viewModel.maskedApiKey.collectAsState()
    val isWifiOnly by viewModel.isWifiOnly.collectAsState()
    val ytDlpVersion by viewModel.ytDlpVersion.collectAsState()
    val isUpdatingEngine by viewModel.isUpdatingEngine.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionStatus by viewModel.connectionTestStatus.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontFamily = NewsreaderFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 1: Gemini AI Key
            item {
                Text(
                    text = "GEMINI AI CONFIGURATION",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
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
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = maskedApiKey,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { showApiKeyDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Set Key", fontFamily = InterFontFamily, fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testGeminiConnection() },
                                enabled = !isTestingConnection,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Test Connection", fontFamily = InterFontFamily, fontSize = 12.sp)
                            }

                            if (!connectionStatus.isNullOrBlank()) {
                                val isSuccess = connectionStatus!!.startsWith("Success")
                                Text(
                                    text = if (isSuccess) "Connected" else "Failed",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSuccess) MaterialTheme.colorScheme.primary else Color(0xFFB3382C)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 2: Engine & Network
            item {
                Text(
                    text = "ENGINE & NETWORK",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Wi-Fi Only toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Wi-Fi Only Downloads", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Only process reels when connected to unmetered Wi-Fi", fontFamily = InterFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isWifiOnly,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setWifiOnly(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp
                        )

                        // yt-dlp version & update
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("yt-dlp Engine Version", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(ytDlpVersion, fontFamily = InterFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            OutlinedButton(
                                onClick = { viewModel.updateYtDlpEngine() },
                                enabled = !isUpdatingEngine,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                if (isUpdatingEngine) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Update", fontFamily = InterFontFamily, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section 3: Samsung One UI Battery Optimization Guide
            item {
                Text(
                    text = "DEVICE OPTIMIZATION",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Samsung One UI Background Processing", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To allow Savyor to finish reel analysis when your screen is turned off, set App Battery usage to 'Unrestricted' in Samsung One UI settings.",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text("Open App Settings", fontFamily = InterFontFamily, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Section 4: Storage Breakdown (Non-overlapping layout)
            item {
                Text(
                    text = "STORAGE BREAKDOWN",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StorageRow(label = "Notes & FTS Database", bytes = storageStats.dbSizeBytes)
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp
                        )
                        StorageRow(label = "Visual WebP Snapshots", bytes = storageStats.snapshotsSizeBytes)
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline,
                            thickness = 0.5.dp
                        )
                        StorageRow(
                            label = "Permanent Video Storage",
                            subtitle = "Auto-cleaned after analysis",
                            bytes = storageStats.videoSizeBytes
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { viewModel.clearSnapshots() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3382C)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFB3382C).copy(alpha = 0.5f))
                        ) {
                            Text("Clear Snapshots Cache", fontFamily = InterFontFamily, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Set API Key Dialog
    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text(
                    "Gemini API Key",
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Enter your Google AI Studio Gemini API key. Stored securely in Android Keystore encrypted storage and never leaves your device.",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save", fontFamily = InterFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun StorageRow(
    label: String,
    bytes: Long,
    subtitle: String = ""
) {
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
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "${"%.2f".format(mb)} MB",
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (bytes == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
