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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savyor.app.R
import com.savyor.app.data.local.entity.CategoryEntity
import com.savyor.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(modifier = Modifier.background(colors.glassSurface)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Manage Categories",
                            fontFamily = NewsreaderFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
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
                        text = "Add Topic",
                        fontFamily = InterFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        containerColor = colors.bgCanvas
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = "LIBRARY TOPICS",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap any color dot to recolor, or organize your custom topics",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                }
            }

            items(categoriesWithCounts, key = { it.category.name }) { item ->
                val category = item.category
                val isOther = category.name.equals("Other", ignoreCase = true)
                val catColor = categoryDotColor(category.name, category.colorHex, colors.isDark)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clean tappable color swatch circle
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(catColor)
                                .border(1.dp, colors.borderSubtle, CircleShape)
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
                                    fontFamily = InterFontFamily,
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
                                            fontFamily = InterFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pluralStringResource(R.plurals.saved_reels_count, item.reelCount, item.reelCount),
                                fontFamily = InterFontFamily,
                                fontSize = 11.5.sp,
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
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Category Name", fontFamily = InterFontFamily, fontSize = 12.sp) },
                        placeholder = { Text("e.g. Photography", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Color Swatch",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
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
                    Text("Create", fontFamily = InterFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = colors.inkSecondary)
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
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Renaming updates all saved reels in this category and keeps AI suggestions in sync.",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
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
                    Text("Rename", fontFamily = InterFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = colors.inkSecondary)
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
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select a color swatch for this category's dots and badges.",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
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
                    Text("Apply", fontFamily = InterFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { recoloringCategory = null }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = colors.inkSecondary)
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
                    fontFamily = NewsreaderFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.errorFg
                )
            },
            text = {
                val countText = pluralStringResource(R.plurals.saved_reels_count, item.reelCount, item.reelCount)
                Text(
                    text = "This category currently has $countText.\n\nDeleting it will safely move those reels to \"Other\". Your reels and notes will not be deleted.",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
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
                    Text("Delete & Reassign", fontFamily = InterFontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Cancel", fontFamily = InterFontFamily, color = colors.inkSecondary)
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
