package com.ct.explorer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.ApkFileItem
import com.ct.explorer.data.model.ApkTab
import com.ct.explorer.data.model.AppBackupGroup
import com.ct.explorer.data.model.AppInfoItem
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.components.ChecksumDialog
import com.ct.explorer.ui.theme.MiGreen
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.FileOpener
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.apkScreenTab.collectAsStateWithLifecycle()
    val storageApks by viewModel.storageApks.collectAsStateWithLifecycle()
    val isStorageApksLoading by viewModel.isStorageApksLoading.collectAsStateWithLifecycle()

    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isAppsLoading by viewModel.isAppsLoading.collectAsStateWithLifecycle()
    val includeSystem by viewModel.includeSystemApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.appsSearchQuery.collectAsStateWithLifecycle()

    val backupGroups by viewModel.appBackupGroups.collectAsStateWithLifecycle()
    val isBackupsLoading by viewModel.isBackupsLoading.collectAsStateWithLifecycle()
    val selectedAppsForBatch by viewModel.selectedAppsForBatch.collectAsStateWithLifecycle()
    val isBatchBackingUp by viewModel.isBatchBackingUp.collectAsStateWithLifecycle()
    val batchBackupProgress by viewModel.batchBackupProgress.collectAsStateWithLifecycle()

    var isBatchModeActive by remember { mutableStateOf(false) }
    var appFilter by remember { mutableStateOf("ALL") }
    var apkFilter by remember { mutableStateOf("ALL") }
    var selectedApp by remember { mutableStateOf<AppInfoItem?>(null) }
    var checksumTarget by remember { mutableStateOf<FileItem?>(null) }
    var apkToDelete by remember { mutableStateOf<ApkFileItem?>(null) }
    var downgradeTarget by remember { mutableStateOf<ApkFileItem?>(null) }

    BackHandler {
        if (isBatchModeActive) {
            isBatchModeActive = false
            viewModel.clearAppBatchSelection()
        } else {
            viewModel.handleBackPress()
        }
    }

    val filteredApps = remember(installedApps, searchQuery, appFilter) {
        val base = if (searchQuery.isBlank()) installedApps else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        when (appFilter) {
            "USER" -> base.filter { !it.isSystemApp }
            "SYSTEM" -> base.filter { it.isSystemApp }
            "BACKED_UP" -> base.filter { it.isBackedUp }
            else -> base
        }
    }

    val filteredBackups = remember(backupGroups, searchQuery) {
        if (searchQuery.isBlank()) backupGroups else backupGroups.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredApks = remember(storageApks, searchQuery, apkFilter) {
        val base = if (searchQuery.isBlank()) storageApks else storageApks.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        when (apkFilter) {
            "NOT_INSTALLED" -> base.filter { !it.isInstalled }
            "INSTALLED" -> base.filter { it.isInstalled }
            else -> base
        }
    }

    // In-App APK / XAPK / APKS file picker launcher
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = getFileNameFromUri(context, uri)
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            try {
                val cacheFile = if (uri.scheme == "file" && uri.path != null && File(uri.path!!).canRead()) {
                    File(uri.path!!)
                } else {
                    File(context.cacheDir, "installer_${System.currentTimeMillis()}_$safeName").apply {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(this).use { output -> input.copyTo(output) }
                        }
                    }
                }
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    viewModel.showMessage("Selected file is empty or inaccessible")
                } else if (displayName.lowercase().endsWith(".xapk") || displayName.lowercase().endsWith(".apks")) {
                    viewModel.openXapkFile(cacheFile)
                } else {
                    viewModel.openApkInstallDialog(cacheFile)
                }
            } catch (e: Exception) {
                viewModel.showMessage("Failed to load APK: ${e.localizedMessage}")
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("app_manager_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (currentTab) {
                                ApkTab.INSTALLED_APPS -> "App Extractor & Cloner"
                                ApkTab.DOWNGRADE_HUB -> "Backup & Downgrade Hub"
                                ApkTab.APK_FILES -> "Storage APK Packages"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (currentTab) {
                                ApkTab.INSTALLED_APPS -> "${filteredApps.size} apps available to backup/clone"
                                ApkTab.DOWNGRADE_HUB -> "${backupGroups.sumOf { it.backupCount }} backups across ${backupGroups.size} apps"
                                ApkTab.APK_FILES -> "${storageApks.size} APK file(s) on storage"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isBatchModeActive) {
                                isBatchModeActive = false
                                viewModel.clearAppBatchSelection()
                            } else {
                                viewModel.handleBackPress()
                            }
                        },
                        modifier = Modifier.testTag("apps_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    when (currentTab) {
                        ApkTab.INSTALLED_APPS -> {
                            IconButton(onClick = { isBatchModeActive = !isBatchModeActive }) {
                                Icon(
                                    imageVector = if (isBatchModeActive) Icons.Default.ChecklistRtl else Icons.Default.Checklist,
                                    contentDescription = "Batch Mode",
                                    tint = if (isBatchModeActive) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.loadApps() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps")
                            }
                        }
                        ApkTab.DOWNGRADE_HUB -> {
                            IconButton(onClick = { viewModel.loadAppBackups() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Backups")
                            }
                        }
                        ApkTab.APK_FILES -> {
                            IconButton(onClick = { viewModel.loadStorageApks() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh APKs")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Batch Action Bar
            AnimatedVisibility(visible = isBatchModeActive && currentTab == ApkTab.INSTALLED_APPS) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${selectedAppsForBatch.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Select All",
                                    color = CtOrange,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable {
                                        viewModel.selectAllAppsForBatch(filteredApps)
                                    }
                                )
                                Text(
                                    text = "Clear",
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable {
                                        viewModel.clearAppBatchSelection()
                                    }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.batchBackupSelectedApps() },
                                colors = ButtonDefaults.buttonColors(containerColor = MiGreen),
                                enabled = selectedAppsForBatch.isNotEmpty() && !isBatchBackingUp,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup (${selectedAppsForBatch.size})", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 3-Tab Header: Cloner/Installed, Downgrade Hub, Storage APKs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabPill(
                    text = "App Cloner (${installedApps.size})",
                    isSelected = currentTab == ApkTab.INSTALLED_APPS,
                    onClick = { viewModel.setApkTab(ApkTab.INSTALLED_APPS) },
                    modifier = Modifier.weight(1f)
                )
                TabPill(
                    text = "Downgrade Hub",
                    isSelected = currentTab == ApkTab.DOWNGRADE_HUB,
                    badgeCount = backupGroups.count { it.hasDowngradeOption },
                    onClick = { viewModel.setApkTab(ApkTab.DOWNGRADE_HUB) },
                    modifier = Modifier.weight(1f)
                )
                TabPill(
                    text = "Storage APKs",
                    isSelected = currentTab == ApkTab.APK_FILES,
                    onClick = { viewModel.setApkTab(ApkTab.APK_FILES) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setAppsSearchQuery(it) },
                placeholder = {
                    Text(
                        when (currentTab) {
                            ApkTab.INSTALLED_APPS -> "Search installed user & system apps..."
                            ApkTab.DOWNGRADE_HUB -> "Search backed up versions & archives..."
                            ApkTab.APK_FILES -> "Search APK installation files..."
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CtOrange) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setAppsSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // In-App Package Installer 1-Tap Trigger
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MiGreen.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        apkPickerLauncher.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MiGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Install APK / Bundle from Device",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                        Text(
                            text = "Pick .apk, .xapk, or .apks to inspect and install in-app",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Batch Progress Indicator
            if (isBatchBackingUp) {
                Surface(
                    color = CtOrange.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CtOrange)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = batchBackupProgress,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Filter Chips per Tab
            when (currentTab) {
                ApkTab.INSTALLED_APPS -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = appFilter == "ALL",
                            onClick = { appFilter = "ALL" },
                            label = { Text("All (${installedApps.size})") }
                        )
                        FilterChip(
                            selected = appFilter == "USER",
                            onClick = { appFilter = "USER" },
                            label = { Text("User Apps (${installedApps.count { !it.isSystemApp }})") }
                        )
                        FilterChip(
                            selected = appFilter == "SYSTEM",
                            onClick = {
                                if (!includeSystem) viewModel.toggleSystemApps()
                                appFilter = "SYSTEM"
                            },
                            label = { Text("System Apps") }
                        )
                        FilterChip(
                            selected = appFilter == "BACKED_UP",
                            onClick = { appFilter = "BACKED_UP" },
                            label = { Text("Backed Up (${installedApps.count { it.isBackedUp }})") }
                        )
                    }
                }
                ApkTab.APK_FILES -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = apkFilter == "ALL",
                            onClick = { apkFilter = "ALL" },
                            label = { Text("All (${storageApks.size})") }
                        )
                        FilterChip(
                            selected = apkFilter == "NOT_INSTALLED",
                            onClick = { apkFilter = "NOT_INSTALLED" },
                            label = { Text("Not Installed (${storageApks.count { !it.isInstalled }})") }
                        )
                        FilterChip(
                            selected = apkFilter == "INSTALLED",
                            onClick = { apkFilter = "INSTALLED" },
                            label = { Text("Installed (${storageApks.count { it.isInstalled }})") }
                        )
                    }
                }
                ApkTab.DOWNGRADE_HUB -> {
                    // Downgrade Summary Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Version Archive & Rollback",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Roll back apps to previous stable versions anytime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val downgradeCount = backupGroups.count { it.hasDowngradeOption }
                            if (downgradeCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CtOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "$downgradeCount Rollback(s)",
                                        color = CtOrange,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Content Area for current tab
            when (currentTab) {
                ApkTab.INSTALLED_APPS -> {
                    // TAB 1: INSTALLED APPS (CLONER / EXTRACTOR)
                    if (isAppsLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CtOrange)
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No applications found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                InstalledAppClonerRow(
                                    app = app,
                                    isBatchMode = isBatchModeActive,
                                    isSelectedForBatch = selectedAppsForBatch.contains(app.packageName),
                                    onBatchToggle = { viewModel.toggleAppBatchSelection(app.packageName) },
                                    onClick = { selectedApp = app },
                                    onExtractBackup = { viewModel.backupInstalledApp(app) },
                                    onShareDirect = { viewModel.extractAndShareApp(context, app) },
                                    onFastShare = { viewModel.sendAppViaFastShare(app) }
                                )
                            }
                        }
                    }
                }

                ApkTab.DOWNGRADE_HUB -> {
                    // TAB 2: BACKUP & DOWNGRADE ARCHIVE HUB
                    if (isBackupsLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CtOrange)
                        }
                    } else if (filteredBackups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(CtOrange.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = CtOrange,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No Backed-up Versions Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Backup any installed app with 1-tap in 'App Cloner'. Your saved versions will appear here for easy rollback or offline sharing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.setApkTab(ApkTab.INSTALLED_APPS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Go to App Cloner")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredBackups, key = { it.packageName }) { group ->
                                DowngradeAppGroupCard(
                                    group = group,
                                    onInstallBackup = { backup ->
                                        if (backup.isDowngradeCandidate) {
                                            downgradeTarget = backup
                                        } else {
                                            viewModel.openApkInstallDialog(backup.file)
                                        }
                                    },
                                    onShareBackup = { backup ->
                                        FileOpener.shareFile(context, FileItem(backup.file))
                                    },
                                    onFastShareBackup = { backup ->
                                        viewModel.sendBackupViaFastShare(backup)
                                    },
                                    onChecksum = { backup ->
                                        checksumTarget = FileItem(backup.file)
                                    },
                                    onDeleteBackup = { backup ->
                                        viewModel.deleteBackup(backup)
                                    }
                                )
                            }
                        }
                    }
                }

                ApkTab.APK_FILES -> {
                    // TAB 3: STORAGE APK FILES
                    if (isStorageApksLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CtOrange)
                        }
                    } else if (filteredApks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MiGreen.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        tint = MiGreen,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No APK files found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "APK installation files downloaded from the browser, WhatsApp, or Telegram will appear here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.loadStorageApks() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MiGreen)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Storage")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApks, key = { it.path }) { apk ->
                                StorageApkRow(
                                    apk = apk,
                                    onInstall = {
                                        viewModel.openApkInstallDialog(apk.file)
                                    },
                                    onMenuAction = { action ->
                                        when (action) {
                                            "install" -> viewModel.openApkInstallDialog(apk.file)
                                            "share" -> FileOpener.shareFile(context, FileItem(apk.file))
                                            "fast_share" -> viewModel.sendBackupViaFastShare(apk)
                                            "checksum" -> checksumTarget = FileItem(apk.file)
                                            "delete" -> apkToDelete = apk
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Downgrade Guidance Dialog
    downgradeTarget?.let { backup ->
        val installedPkg = remember(backup.packageName) {
            try { context.packageManager.getPackageInfo(backup.packageName, 0) } catch (e: Exception) { null }
        }
        val installedVer = installedPkg?.versionName ?: "Current"

        AlertDialog(
            onDismissRequest = { downgradeTarget = null },
            icon = { Icon(Icons.Default.History, contentDescription = null, tint = CtOrange, modifier = Modifier.size(32.dp)) },
            title = { Text("Downgrade ${backup.appName}?") },
            text = {
                Column {
                    Text(
                        text = "You are rolling back from v$installedVer to archived v${backup.versionName}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Android Security Rule: Android does not allow installing an older APK directly over a newer version. If direct install fails, tap 'Uninstall Current' below first, then install this backup.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = downgradeTarget
                        downgradeTarget = null
                        if (target != null) {
                            viewModel.openApkInstallDialog(target.file)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Proceed to Install")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            FileOpener.uninstallApp(context, backup.packageName)
                        }
                    ) {
                        Text("Uninstall Current First", color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = { downgradeTarget = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Checksum Dialog
    checksumTarget?.let { item ->
        ChecksumDialog(
            item = item,
            onDismiss = { checksumTarget = null }
        )
    }

    // Delete APK confirmation
    apkToDelete?.let { apk ->
        AlertDialog(
            onDismissRequest = { apkToDelete = null },
            title = { Text("Delete APK File") },
            text = { Text("Delete \"${apk.name}\"? This APK file will be removed from storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStorageApk(apk)
                        apkToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { apkToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // App Details Bottom Sheet
    selectedApp?.let { app ->
        AppDetailSheet(
            app = app,
            onDismiss = { selectedApp = null },
            onBackup = {
                selectedApp = null
                viewModel.backupInstalledApp(app)
            },
            onShare = {
                selectedApp = null
                viewModel.extractAndShareApp(context, app)
            },
            onFastShare = {
                selectedApp = null
                viewModel.sendAppViaFastShare(app)
            },
            onLaunch = {
                selectedApp = null
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) context.startActivity(intent)
            },
            onUninstall = {
                selectedApp = null
                FileOpener.uninstallApp(context, app.packageName)
            },
            onSettings = {
                selectedApp = null
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun InstalledAppClonerRow(
    app: AppInfoItem,
    isBatchMode: Boolean,
    isSelectedForBatch: Boolean,
    onBatchToggle: () -> Unit,
    onClick: () -> Unit,
    onExtractBackup: () -> Unit,
    onShareDirect: () -> Unit,
    onFastShare: () -> Unit
) {
    var showActionMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = if (isBatchMode) onBatchToggle else onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = isSelectedForBatch,
                    onCheckedChange = { onBatchToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = CtOrange)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (app.icon != null) {
                val bitmap = remember(app.icon) {
                    try { app.icon.toBitmap(width = 96, height = 96).asImageBitmap() } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                } else {
                    DefaultApkIcon()
                }
            } else {
                DefaultApkIcon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (app.isSystemApp) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "System",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (app.isBackedUp) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MiGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "✓ Saved",
                                color = Color(0xFF059669),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${app.formattedSize} • v${app.versionName} (${app.versionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isBatchMode) {
                // 1-Tap Extract & Backup Button
                FilledTonalButton(
                    onClick = onExtractBackup,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MiGreen.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Extract", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669))
                }

                Box {
                    IconButton(onClick = { showActionMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { showActionMenu = false },
                        containerColor = Color.White
                    ) {
                        DropdownMenuItem(
                            text = { Text("Extract APK") },
                            leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null, tint = MiGreen) },
                            onClick = {
                                showActionMenu = false
                                onExtractBackup()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share APK (Bluetooth/Nearby)") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = CtOrange) },
                            onClick = {
                                showActionMenu = false
                                onShareDirect()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send via Cent Fast Share") },
                            leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF10B981)) },
                            onClick = {
                                showActionMenu = false
                                onFastShare()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DowngradeAppGroupCard(
    group: AppBackupGroup,
    onInstallBackup: (ApkFileItem) -> Unit,
    onShareBackup: (ApkFileItem) -> Unit,
    onFastShareBackup: (ApkFileItem) -> Unit,
    onChecksum: (ApkFileItem) -> Unit,
    onDeleteBackup: (ApkFileItem) -> Unit
) {
    var isExpanded by remember { mutableStateOf(group.hasDowngradeOption) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group.icon != null) {
                    val bitmap = remember(group.icon) {
                        try { group.icon.toBitmap(width = 96, height = 96).asImageBitmap() } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = group.appName,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        DefaultApkIcon()
                    }
                } else {
                    DefaultApkIcon()
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (group.hasDowngradeOption) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CtOrange.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Downgrade Ready",
                                    color = CtOrange,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (group.isInstalled) "Current installed: v${group.installedVersionName}" else "Not currently installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${group.backupCount} archived version(s) • Total ${group.formattedTotalSize}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand"
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.backups.forEach { backup ->
                        BackupVersionRow(
                            backup = backup,
                            onInstall = { onInstallBackup(backup) },
                            onShare = { onShareBackup(backup) },
                            onFastShare = { onFastShareBackup(backup) },
                            onChecksum = { onChecksum(backup) },
                            onDelete = { onDeleteBackup(backup) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupVersionRow(
    backup: ApkFileItem,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onFastShare: () -> Unit,
    onChecksum: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${backup.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    when {
                        backup.isDowngradeCandidate -> {
                            Surface(shape = RoundedCornerShape(4.dp), color = CtOrange.copy(alpha = 0.15f)) {
                                Text(
                                    text = "Older Version (Rollback)",
                                    color = CtOrange,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        backup.isCurrentVersion -> {
                            Surface(shape = RoundedCornerShape(4.dp), color = MiGreen.copy(alpha = 0.15f)) {
                                Text(
                                    text = "Current Installed",
                                    color = Color(0xFF059669),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        backup.isUpgradeCandidate -> {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF3B82F6).copy(alpha = 0.15f)) {
                                Text(
                                    text = "Newer Backup",
                                    color = Color(0xFF3B82F6),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "${backup.formattedSize} • Saved ${backup.formattedDate}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Install / Rollback Button
            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (backup.isDowngradeCandidate) CtOrange else MiGreen
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = if (backup.isDowngradeCandidate) "Downgrade" else "Install",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = Color.White
                ) {
                    DropdownMenuItem(
                        text = { Text("Share APK (Offline)") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send via Cent Fast Share") },
                        leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF10B981)) },
                        onClick = {
                            showMenu = false
                            onFastShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Calculate Hash") },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = CtOrange) },
                        onClick = {
                            showMenu = false
                            onChecksum()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete Backup", color = Color(0xFFEF4444)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageApkRow(
    apk: ApkFileItem,
    onInstall: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onInstall),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (apk.icon != null) {
                val bitmap = remember(apk.icon) {
                    try { apk.icon.toBitmap(width = 96, height = 96).asImageBitmap() } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = apk.appName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    DefaultApkIcon()
                }
            } else {
                DefaultApkIcon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = apk.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (apk.isInstalled) MiGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (apk.isInstalled) "Installed" else "Not installed",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (apk.isInstalled) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "${apk.name} • ${apk.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${apk.versionName} • ${apk.formattedDate}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MiGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(if (apk.isInstalled) "Update" else "Install", style = MaterialTheme.typography.labelMedium)
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = Color.White
                ) {
                    DropdownMenuItem(
                        text = { Text("Install / Open") },
                        leadingIcon = { Icon(Icons.Default.Android, contentDescription = null, tint = MiGreen) },
                        onClick = {
                            showMenu = false
                            onMenuAction("install")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share APK") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMenuAction("share")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send via Cent Fast Share") },
                        leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF10B981)) },
                        onClick = {
                            showMenu = false
                            onMenuAction("fast_share")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Calculate Hash") },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = CtOrange) },
                        onClick = {
                            showMenu = false
                            onMenuAction("checksum")
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete APK", color = Color(0xFFEF4444)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                        onClick = {
                            showMenu = false
                            onMenuAction("delete")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultApkIcon() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiGreen.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = null,
            tint = MiGreen,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(
    app: AppInfoItem,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onShare: () -> Unit,
    onFastShare: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
    onSettings: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.icon != null) {
                    val bitmap = remember(app.icon) {
                        try { app.icon.toBitmap(width = 120, height = 120).asImageBitmap() } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Version: ${app.versionName} (${app.versionCode}) • Size: ${app.formattedSize}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // 1-Tap Extract APK
            Button(
                onClick = onBackup,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MiGreen)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extract & Backup APK (Save to Hub)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1-Tap Offline Share
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Offline")
                }

                FilledTonalButton(
                    onClick = onFastShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fast Share")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onLaunch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch")
                }

                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("App Info")
                }
            }

            if (!app.isSystemApp) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uninstall App")
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    text: String,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "tabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = textColor
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(CtOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$badgeCount",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "app.apk"
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        name = cursor.getString(idx)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    } else if (uri.path != null) {
        name = java.io.File(uri.path!!).name
    }
    return name
}

