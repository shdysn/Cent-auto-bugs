package com.ct.explorer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.MiMint
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val scanResult by viewModel.cleanScan.collectAsStateWithLifecycle()
    val isScanning by viewModel.isCleanScanning.collectAsStateWithLifecycle()
    val isCleaning by viewModel.isCleaning.collectAsStateWithLifecycle()
    val cleanedBytes by viewModel.cleanedBytes.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "cleanRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar"
    )

    var showLargeFilesDialog by remember { mutableStateOf(false) }
    var showApksDialog by remember { mutableStateOf(false) }
    var showEmptyFoldersDialog by remember { mutableStateOf(false) }
    var selectedLargeFiles by remember { mutableStateOf<Set<FileItem>>(emptySet()) }
    var selectedApkFiles by remember { mutableStateOf<Set<FileItem>>(emptySet()) }

    Scaffold(
        modifier = modifier.testTag("cleaner_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Deep Clean", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("cleaner_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startCleanScan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val hasJunk = (scanResult?.totalJunkBytes ?: 0L) > 0L
                    Button(
                        onClick = { viewModel.performClean() },
                        enabled = !isScanning && !isCleaning && hasJunk,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CtOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("perform_clean_button")
                    ) {
                        if (isCleaning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.CleaningServices, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasJunk) "Clean Up ${scanResult?.formattedJunkSize}" else "Clean & Optimized",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Scanning / Status Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(MiMint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isScanning || isCleaning) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = MiMint,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .rotate(rotation)
                                )
                            } else if (cleanedBytes != null || (scanResult?.totalJunkBytes ?: 0) == 0L) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MiMint,
                                    modifier = Modifier.size(46.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    tint = CtOrange,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isScanning) {
                            Text(
                                text = "Scanning system files...",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Searching cache, temporary files, and obsolete packages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (cleanedBytes != null) {
                            Text(
                                text = "Cleaned ${FileItem.formatBytes(cleanedBytes!!)}",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MiMint)
                            )
                            Text(
                                text = "Your device storage is clean and optimized",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val junkSize = scanResult?.formattedJunkSize ?: "0 B"
                            Text(
                                text = junkSize,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (junkSize == "0 B") MiMint else CtOrange,
                                    fontSize = 36.sp
                                )
                            )
                            Text(
                                text = if (junkSize == "0 B") "No trash found" else "Trash & cache files ready to clean",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Categories Breakdown
            scanResult?.let { res ->
                item {
                    Text(
                        text = "Scan Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    CleanCategoryCard(
                        title = "App Cache & Temp Files",
                        subtitle = "${res.junkFiles.size} temporary file(s) found",
                        sizeStr = res.formattedJunkSize,
                        icon = Icons.Default.FolderDelete,
                        color = CtOrange,
                        onClick = null
                    )
                }

                item {
                    CleanCategoryCard(
                        title = "Large Files (>15MB)",
                        subtitle = "${res.largeFiles.size} large file(s) taking storage • Tap to manage",
                        sizeStr = FileItem.formatBytes(res.totalLargeBytes),
                        icon = Icons.Default.DataUsage,
                        color = Color(0xFF7C4DFF),
                        onClick = {
                            selectedLargeFiles = res.largeFiles.toSet()
                            showLargeFilesDialog = true
                        }
                    )
                }

                item {
                    CleanCategoryCard(
                        title = "APK Packages",
                        subtitle = "${res.apkFiles.size} installation package(s) • Tap to manage",
                        sizeStr = FileItem.formatBytes(res.totalApkBytes),
                        icon = Icons.Default.Android,
                        color = Color(0xFF4CAF50),
                        onClick = {
                            selectedApkFiles = res.apkFiles.toSet()
                            showApksDialog = true
                        }
                    )
                }

                item {
                    CleanCategoryCard(
                        title = "Empty Directories",
                        subtitle = "${res.emptyFolders.size} empty folder(s) detected • Tap to purge",
                        sizeStr = "${res.emptyFolders.size} items",
                        icon = Icons.Default.FolderOpen,
                        color = Color(0xFF00BCD4),
                        onClick = {
                            showEmptyFoldersDialog = true
                        }
                    )
                }
            }
        }

        // 1. Large Files Dialog
        if (showLargeFilesDialog) {
            val res = scanResult
            AlertDialog(
                onDismissRequest = { showLargeFilesDialog = false },
                title = { Text("Large Files (>15MB)") },
                text = {
                    if (res != null && res.largeFiles.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        ) {
                            items(res.largeFiles) { item ->
                                val isSelected = selectedLargeFiles.contains(item)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLargeFiles = if (isSelected) selectedLargeFiles - item else selectedLargeFiles + item
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { chk ->
                                            selectedLargeFiles = if (chk) selectedLargeFiles + item else selectedLargeFiles - item
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(item.formattedSize, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C4DFF))
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No large files found.")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteLargeFiles(selectedLargeFiles.toList())
                            showLargeFilesDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = selectedLargeFiles.isNotEmpty()
                    ) {
                        Text("Delete (${selectedLargeFiles.size})")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLargeFilesDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 2. APK Packages Dialog
        if (showApksDialog) {
            val res = scanResult
            AlertDialog(
                onDismissRequest = { showApksDialog = false },
                title = { Text("APK Packages") },
                text = {
                    if (res != null && res.apkFiles.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        ) {
                            items(res.apkFiles) { item ->
                                val isSelected = selectedApkFiles.contains(item)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedApkFiles = if (isSelected) selectedApkFiles - item else selectedApkFiles + item
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { chk ->
                                            selectedApkFiles = if (chk) selectedApkFiles + item else selectedApkFiles - item
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(item.formattedSize, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No APK packages found.")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteApkPackages(selectedApkFiles.toList())
                            showApksDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = selectedApkFiles.isNotEmpty()
                    ) {
                        Text("Delete (${selectedApkFiles.size})")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApksDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 3. Empty Folders Dialog
        if (showEmptyFoldersDialog) {
            val res = scanResult
            AlertDialog(
                onDismissRequest = { showEmptyFoldersDialog = false },
                title = { Text("Clean Empty Directories") },
                text = {
                    val count = res?.emptyFolders?.size ?: 0
                    Column {
                        Text(
                            if (count > 0)
                                "Found $count empty folders with no files inside. Would you like to clean them to organize storage?"
                            else
                                "No empty directories detected."
                        )
                        if (count > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(res!!.emptyFolders) { folder ->
                                    Text(
                                        text = "📁 ${folder.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    val count = res?.emptyFolders?.size ?: 0
                    if (count > 0) {
                        Button(
                            onClick = {
                                viewModel.deleteEmptyFolders(res!!.emptyFolders)
                                showEmptyFoldersDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
                        ) {
                            Text("Clean All ($count)")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyFoldersDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun CleanCategoryCard(
    title: String,
    subtitle: String,
    sizeStr: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                text = sizeStr,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = color)
            )

            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
