package com.savyor.app.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savyor.app.R
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.ui.screens.library.CrystalIconWell
import com.savyor.app.ui.screens.library.SavyorPrimaryPillFab
import com.savyor.app.ui.theme.*

@Composable
fun ManageCategoriesScreen(
    viewModel: ManageCategoriesViewModel,
    onNavigateBack: () -> Unit
) {
    val colors = LocalSavyorColors.current
    val haptic = LocalHapticFeedback.current
    val categoriesWithCounts by viewModel.categoriesWithCounts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var recoloringCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryWithCount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.userFeedback.collect { message ->
            snackbarHostState.showSnackbar(message)
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
                bottom = navBarBottom + 104.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = "LIBRARY TOPICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Tap any color dot to recolor, or organize your custom topics",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary
                    )
                }
            }

            items(categoriesWithCounts, key = { it.category.name }) { item ->
                val category = item.category
                val isOther = category.name.equals("Other", ignoreCase = true)
                val catColor = categoryDotColor(category.name, category.colorHex, colors.isDark)
                val cardShape = RoundedCornerShape(14.dp)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .savyorCardBorder(cardShape),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 26dp category dot with subtle specular inner ring
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(catColor)
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.15f))
                                    ),
                                    shape = CircleShape
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    recoloringCategory = category
                                }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = category.name,
                                    fontFamily = SavyorSansFamily,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkPrimary
                                )
                                if (category.isDefault) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceRecessed
                                    ) {
                                        Text(
                                            text = if (isOther) "Fallback" else "Default",
                                            fontFamily = SavyorMonoFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pluralStringResource(R.plurals.saved_reels_count, item.reelCount, item.reelCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkSecondary
                            )
                        }

                        if (!isOther) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    editingCategory = category
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Rename category",
                                    tint = colors.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (!category.isDefault) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        deletingCategory = item
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete category",
                                        tint = colors.errorFg,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CrystalIconWell(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    size = 36.dp,
                    onClick = onNavigateBack
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Manage Categories",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.inkPrimary
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceRecessed,
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Text(
                    text = "${categoriesWithCounts.size}",
                    fontFamily = SavyorMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Pinned Bottom + Add Topic Pill FAB
        SavyorPrimaryPillFab(
            text = "Add Topic",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showAddDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }

    // Dialog: Add Category
    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedColorHex by remember { mutableStateOf(CategorySwatches.first().hex) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "New Category",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Category Name", fontFamily = SavyorSansFamily, fontSize = 12.sp) },
                        placeholder = { Text("e.g. Photography", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "COLOR SWATCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorSwatchPicker(
                        selectedHex = selectedColorHex,
                        onSelectHex = { selectedColorHex = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addCategory(newName, selectedColorHex)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ctaBg,
                        contentColor = colors.ctaFg
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Create", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", fontFamily = SavyorSansFamily, color = colors.inkSecondary)
                }
            }
        )
    }

    // Dialog: Rename Category
    editingCategory?.let { cat ->
        var renameInput by remember { mutableStateOf(cat.name) }
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Rename Category",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Renaming updates all saved reels in this category and keeps AI suggestions in sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameCategory(cat.name, renameInput)
                            editingCategory = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ctaBg,
                        contentColor = colors.ctaFg
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Rename", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancel", fontFamily = SavyorSansFamily, color = colors.inkSecondary)
                }
            }
        )
    }

    // Dialog: Recolor Category
    recoloringCategory?.let { cat ->
        var chosenHex by remember { mutableStateOf(cat.colorHex) }
        AlertDialog(
            onDismissRequest = { recoloringCategory = null },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Recolor \"${cat.name}\"",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select a color swatch for this category's dots and badges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ColorSwatchPicker(
                        selectedHex = chosenHex,
                        onSelectHex = { chosenHex = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateColor(cat.name, chosenHex)
                        recoloringCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ctaBg,
                        contentColor = colors.ctaFg
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recoloringCategory = null }) {
                    Text("Cancel", fontFamily = SavyorSansFamily, color = colors.inkSecondary)
                }
            }
        )
    }

    // Dialog: Delete Category Confirmation
    deletingCategory?.let { item ->
        val cat = item.category
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = "Delete \"${cat.name}\"?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.errorFg
                )
            },
            text = {
                val countText = pluralStringResource(R.plurals.saved_reels_count, item.reelCount, item.reelCount)
                Text(
                    text = "This category currently has $countText.\n\nDeleting it will safely move those reels to \"Other\". Your reels and notes will not be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.5.sp,
                    color = colors.inkSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat.name)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.errorFg,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete & Reassign", fontFamily = SavyorSansFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Cancel", fontFamily = SavyorSansFamily, color = colors.inkSecondary)
                }
            }
        )
    }
}

@Composable
fun ColorSwatchPicker(
    selectedHex: String,
    onSelectHex: (String) -> Unit
) {
    val colors = LocalSavyorColors.current
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = CategorySwatches.chunked(5)
        rows.forEach { rowSwatches ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowSwatches.forEach { swatch ->
                    val isSelected = selectedHex.equals(swatch.hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatch.color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) colors.inkPrimary else colors.borderSubtle,
                                shape = CircleShape
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectHex(swatch.hex)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
