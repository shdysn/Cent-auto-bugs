package com.ct.explorer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.*
import com.ct.explorer.ui.components.*
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.ui.viewmodel.Screen
import com.ct.explorer.ui.viewmodel.StorageTabState
import com.ct.explorer.utils.FileOpener
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val storageSpace by viewModel.storageSpace.collectAsStateWithLifecycle()
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val isRecentLoading by viewModel.isRecentLoading.collectAsStateWithLifecycle()
    val clipboardState by viewModel.clipboard.collectAsStateWithLifecycle()
    val isAmoled by viewModel.isAmoledMode.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    val isDualPaneActive by viewModel.isDualPaneActive.collectAsStateWithLifecycle()
    val paneBState by viewModel.paneBState.collectAsStateWithLifecycle()
    val activePaneIndex by viewModel.activePaneIndex.collectAsStateWithLifecycle()
    val storageVolumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val selectedVolume by viewModel.selectedVolume.collectAsStateWithLifecycle()
    val fileTagsMap by viewModel.fileTagsMap.collectAsStateWithLifecycle()
    val selectedTagFilter by viewModel.selectedTagFilter.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var recentFilter by remember { mutableStateOf("All") }
    var showToolsSheet by remember { mutableStateOf(false) }
    var showFavoritesSheet by remember { mutableStateOf(false) }

    // Dialog states
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFileName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var deleteTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var checksumTarget by remember { mutableStateOf<FileItem?>(null) }
    var zipTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var zipArchiveName by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var openWithTarget by remember { mutableStateOf<FileItem?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var tagTarget by remember { mutableStateOf<FileItem?>(null) }
    var exifCleanerTarget by remember { mutableStateOf<FileItem?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    BackHandler(enabled = storageState.isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        modifier = modifier.testTag("main_screen"),
        containerColor = Color.White,
        topBar = {
            if (storageState.isSelectionMode) {
                val isRecentTab = selectedTab == CtTab.RECENT
                val currentTargetList = if (isRecentTab) recentFiles else storageState.displayItems
                val totalItemsCount = currentTargetList.size
                val isAllSelected = currentTargetList.isNotEmpty() && storageState.selectedItems.containsAll(currentTargetList)
                val subtitleText = if (isRecentTab) "Today | $totalItemsCount items" else "${storageState.currentDir.name} | $totalItemsCount items"

                MiSelectionTopBar(
                    selectedCount = storageState.selectedItems.size,
                    subtitle = subtitleText,
                    isAllSelected = isAllSelected,
                    onClose = { viewModel.clearSelection() },
                    onToggleSelectAll = {
                        if (isAllSelected) {
                            viewModel.clearSelection()
                        } else {
                            viewModel.selectAll(currentTargetList)
                        }
                    }
                )
            } else if (isSearchActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = storageState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search files & folders...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CtOrange) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mi_search_field")
                        )
                    }
                }
            } else {
                CtTopHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    onSearchClick = { isSearchActive = true },
                    onCleanerClick = { viewModel.openCleaner() },
                    onFtpClick = { viewModel.openFtpServer() },
                    onVaultClick = { viewModel.openVault() },
                    onDuplicatesClick = { viewModel.openDuplicateFinder() },
                    onAnalyzerClick = { viewModel.openStorageAnalyzer() },
                    onTrashClick = { viewModel.openTrash() },
                    onNetworkDrivesClick = { viewModel.openNetworkDrives() },
                    onFastShareClick = { viewModel.openFastShare() },
                    onDualPaneToggle = { viewModel.toggleDualPane() },
                    isDualPaneActive = isDualPaneActive,
                    onAmoledToggle = { viewModel.toggleAmoledMode() },
                    isAmoled = isAmoled,
                    onToolsClick = { showToolsSheet = true },
                    onFavoritesClick = { showFavoritesSheet = true }
                )
            }
        },
        bottomBar = {
            if (storageState.isSelectionMode) {
                MiSelectionBottomBar(
                    selectedItems = storageState.selectedItems.toList(),
                    onSend = {
                        val items = storageState.selectedItems.toList()
                        if (items.size == 1) {
                            FileOpener.shareFile(context, items.first())
                        } else {
                            FileOpener.shareMultipleFiles(context, items)
                        }
                    },
                    onMove = {
                        viewModel.cutSelected()
                    },
                    onDelete = {
                        deleteTargets = storageState.selectedItems.toList()
                    },
                    onCopyToClipboard = {
                        val items = storageState.selectedItems.toList()
                        val text = items.joinToString("\n") { it.path }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("File Path", text)
                        clipboard.setPrimaryClip(clip)
                        viewModel.showMessage("Copied path to clipboard")
                        viewModel.clearSelection()
                    },
                    onCopy = {
                        viewModel.copySelected()
                    },
                    onMakePrivate = {
                        viewModel.addFilesToVault(storageState.selectedItems.toList())
                    },
                    onToggleFavorite = {
                        viewModel.toggleFavorites(storageState.selectedItems.toList())
                    },
                    onRename = {
                        val items = storageState.selectedItems.toList()
                        if (items.size == 1) {
                            renameTarget = items.first()
                            renameNewName = items.first().name
                        } else {
                            showBatchRenameDialog = true
                        }
                    },
                    onOpenInAnotherApp = {
                        val items = storageState.selectedItems.toList()
                        if (items.size == 1) {
                            openWithTarget = items.first()
                        }
                    },
                    onDetails = {
                        val items = storageState.selectedItems.toList()
                        if (items.size == 1) {
                            detailsTarget = items.first()
                        }
                    },
                    onPrint = {
                        val item = storageState.selectedItems.firstOrNull()
                        if (item != null && !item.isDirectory) {
                            com.ct.explorer.utils.PrintHelper.printFile(context, item.file)
                        }
                    }
                )
            } else {
                MiClipboardBar(
                    clipboardState = clipboardState,
                    onPaste = { viewModel.pasteToCurrentDirectory() },
                    onClear = { viewModel.clearClipboard() }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            var hasAllFilesAccess by remember {
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else true
                )
            }

            val requestAllFilesLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    hasAllFilesAccess = Environment.isExternalStorageManager()
                    if (hasAllFilesAccess) {
                        viewModel.onStoragePermissionGranted()
                    }
                }
            }

            AnimatedVisibility(visible = !hasAllFilesAccess) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All Files Access",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Allow storage access to view and manage your files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        requestAllFilesLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        requestAllFilesLauncher.launch(fallback)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            when (selectedTab) {
                CtTab.RECENT -> {
                    // Recent Tab Content
                    RecentTabContent(
                        recentFiles = recentFiles,
                        isLoading = isRecentLoading,
                        activeFilter = recentFilter,
                        onFilterSelected = { recentFilter = it },
                        onOpenFile = { item ->
                            if (!viewModel.openFileSmart(item, recentFiles)) {
                                openWithTarget = item
                            }
                        },
                        onMenuAction = { action, item ->
                            when (action) {
                                "open" -> {
                                    if (!viewModel.openFileSmart(item, recentFiles)) {
                                        openWithTarget = item
                                    }
                                }
                                "open_with" -> openWithTarget = item
                                "toggle_favorite" -> viewModel.toggleFavorite(item.file)
                                "pin_home" -> {
                                    val pinned = com.ct.explorer.utils.ShortcutHelper.pinFileOrFolderToHomeScreen(context, item)
                                    viewModel.showMessage(if (pinned) "Shortcut request sent to Home Screen" else "Pinned shortcut not supported on this launcher")
                                }
                                "checksum" -> checksumTarget = item
                                "vault" -> viewModel.addFileToVault(item)
                                "tags" -> tagTarget = item
                                "clean_exif" -> exifCleanerTarget = item
                                "fast_share" -> viewModel.openFastShare(listOf(item))
                                "shred" -> viewModel.openFileShredder(listOf(item.file))
                                "copy" -> viewModel.copySingle(item)
                                "cut" -> viewModel.cutSingle(item)
                                "rename" -> {
                                    renameTarget = item
                                    renameNewName = item.name
                                }
                                "delete" -> deleteTargets = listOf(item)
                                "details" -> detailsTarget = item
                                "zip" -> {
                                    zipArchiveName = "${item.name}.zip"
                                    zipTargets = listOf(item)
                                }
                                "unzip" -> viewModel.openZipViewer(item.file)
                            }
                        },
                        onRefresh = { viewModel.loadRecentFiles() },
                        selectedItems = storageState.selectedItems,
                        onToggleSelect = { viewModel.toggleSelectItem(it) }
                    )
                }
                CtTab.STORAGE -> {
                    // Storage Tab Content (Cent Categories & Folder Navigation)
                    StorageTabContent(
                        storageSpace = storageSpace,
                        storageState = storageState,
                        rootStorageDir = viewModel.fileRepository.rootStorageDirectory,
                        favorites = favorites,
                        isDualPaneActive = isDualPaneActive,
                        paneBState = paneBState,
                        activePaneIndex = activePaneIndex,
                        onSelectPane = { viewModel.setActivePane(it) },
                        onNavigatePaneA = { viewModel.loadDirectory(it, addToHistory = true) },
                        onNavigatePaneB = { viewModel.navigatePaneB(it) },
                        onBackPaneA = { viewModel.goBackInDirectory() },
                        onBackPaneB = { viewModel.backPaneB() },
                        onCopyAtoB = { viewModel.copyPaneAtoB() },
                        onCopyBtoA = { viewModel.copyPaneBtoA() },
                        onMoveAtoB = { viewModel.movePaneAtoB() },
                        onMoveBtoA = { viewModel.movePaneBtoA() },
                        onToggleSelectB = { viewModel.toggleSelectPaneB(it) },
                        storageVolumes = storageVolumes,
                        selectedVolume = selectedVolume,
                        onSwitchVolume = { viewModel.switchStorageVolume(it) },
                        fileTagsMap = fileTagsMap,
                        selectedTagFilter = selectedTagFilter,
                        onFilterTag = { viewModel.filterByTag(it) },
                        onFastShareSelected = { viewModel.openFastShare(storageState.selectedItems.toList()) },
                        onCleanClick = { viewModel.openCleaner() },
                        onTrashClick = { viewModel.openTrash() },
                        onNetworkDrivesClick = { viewModel.openNetworkDrives() },
                        onFastShareClick = { viewModel.openFastShare() },
                        onFtpClick = { viewModel.openFtpServer() },
                        onDualPaneToggle = { viewModel.toggleDualPane() },
                        onToolsClick = { showToolsSheet = true },
                        onFavoritesClick = { showFavoritesSheet = true },
                        onCategoryClick = { cat, title ->
                            if (cat == FileCategory.APK) {
                                viewModel.openAppInstaller()
                            } else {
                                viewModel.openCategory(cat, title)
                            }
                        },
                        onSocialClick = { viewModel.openSocialHub() },
                        onAppManagerClick = { viewModel.openAppManager() },
                        onAppInstallerClick = { viewModel.openAppInstaller() },
                        onVaultClick = { viewModel.openVault() },
                        onDuplicatesClick = { viewModel.openDuplicateFinder() },
                        onAnalyzerClick = { viewModel.openStorageAnalyzer() },
                        onWebShareClick = { viewModel.openWebShare() },
                        onFileShredderClick = { viewModel.openFileShredder() },
                        onSmartCollectionsClick = { viewModel.openSmartCollections() },
                        onTimeMachineClick = { viewModel.openTimeMachine() },
                        onPinWidgetClick = {
                            val ok = com.ct.explorer.utils.ShortcutHelper.requestPinStorageWidget(context)
                            viewModel.showMessage(if (ok) "Home Screen Storage Widget prompt opened!" else "Long-press Home Screen -> Widgets -> Cent File Manager")
                        },
                        onBatchRename = {
                            if (storageState.selectedItems.size >= 2) {
                                showBatchRenameDialog = true
                            } else {
                                viewModel.showMessage("Select 2 or more files to batch rename")
                            }
                        },
                        onNavigateTo = { viewModel.loadDirectory(it, addToHistory = true) },
                        onNavigateUp = {
                            val parent = storageState.currentDir.parentFile
                            if (parent != null && parent.canRead()) {
                                viewModel.loadDirectory(parent, addToHistory = true)
                            }
                        },
                        onOpenFile = { item ->
                            if (!viewModel.openFileSmart(item, storageState.items)) {
                                openWithTarget = item
                            }
                        },
                        onToggleSelect = { viewModel.toggleSelectItem(it) },
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        onCopySelected = { viewModel.copySelected() },
                        onCutSelected = { viewModel.cutSelected() },
                        onDeleteSelected = { deleteTargets = storageState.selectedItems.toList() },
                        onZipSelected = {
                            zipArchiveName = "${storageState.currentDir.name}.zip"
                            zipTargets = storageState.selectedItems.toList()
                        },
                        onNewFolder = {
                            newFolderName = ""
                            showCreateFolderDialog = true
                        },
                        onNewFile = {
                            newFileName = ""
                            showCreateFileDialog = true
                        },
                        onToggleViewMode = { viewModel.toggleViewMode() },
                        onShowSortMenu = { showSortMenu = true },
                        onToggleBigFilesFilter = { viewModel.toggleBigFilesFilter() },
                        onMenuAction = { action, item ->
                            when (action) {
                                "open" -> {
                                    if (!viewModel.openFileSmart(item, storageState.items)) {
                                        openWithTarget = item
                                    }
                                }
                                "open_with" -> openWithTarget = item
                                "toggle_favorite" -> viewModel.toggleFavorite(item.file)
                                "pin_home" -> {
                                    val pinned = com.ct.explorer.utils.ShortcutHelper.pinFileOrFolderToHomeScreen(context, item)
                                    viewModel.showMessage(if (pinned) "Shortcut request sent to Home Screen" else "Pinned shortcut not supported on this launcher")
                                }
                                "checksum" -> checksumTarget = item
                                "vault" -> viewModel.addFileToVault(item)
                                "tags" -> tagTarget = item
                                "clean_exif" -> exifCleanerTarget = item
                                "fast_share" -> viewModel.openFastShare(listOf(item))
                                "shred" -> viewModel.openFileShredder(listOf(item.file))
                                "copy" -> viewModel.copySingle(item)
                                "cut" -> viewModel.cutSingle(item)
                                "rename" -> {
                                    renameTarget = item
                                    renameNewName = item.name
                                }
                                "delete" -> deleteTargets = listOf(item)
                                "details" -> detailsTarget = item
                                "zip" -> {
                                    zipArchiveName = "${item.name}.zip"
                                    zipTargets = listOf(item)
                                }
                                "unzip" -> viewModel.openZipViewer(item.file)
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName)
                            showCreateFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("File Name (e.g. note.txt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createTextFile(newFileName)
                            showCreateFileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameNewName.isNotBlank() && renameNewName != item.name) {
                            viewModel.renameItem(item, renameNewName)
                            renameTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTargets?.let { targets ->
        var moveToBin by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = { Text(if (moveToBin) "Move to Recycle Bin" else "Delete Permanently") },
            text = {
                Column {
                    Text(
                        if (moveToBin)
                            "Move ${targets.size} item(s) to Recycle Bin? You can restore them anytime."
                        else
                            "Permanently delete ${targets.size} item(s)? This action cannot be undone."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { moveToBin = !moveToBin }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = moveToBin,
                            onCheckedChange = { moveToBin = it },
                            colors = CheckboxDefaults.colors(checkedColor = CtOrange)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Send to Recycle Bin (Recommended)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (moveToBin) {
                            viewModel.moveToTrash(targets)
                        } else {
                            viewModel.deleteItems(targets)
                        }
                        deleteTargets = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (moveToBin) CtOrange else Color(0xFFEF4444)
                    )
                ) {
                    Text(if (moveToBin) "Move to Bin" else "Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    detailsTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Name: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Location: ${item.path}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Size: ${item.formattedSize} (${java.lang.String.format(java.util.Locale.US, "%,d", item.effectiveSize)} bytes)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (item.isLarge) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (item.isLarge) CtOrange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (item.isDirectory) {
                        Text(text = "Items count: ${item.itemCount}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(text = "Type: ${item.friendlyTypeLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Modified: ${item.formattedDate}", style = MaterialTheme.typography.bodySmall)
                    if (!item.isDirectory) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                detailsTarget = null
                                com.ct.explorer.utils.PrintHelper.printFile(context, item.file)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Print / Save as PDF")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                checksumTarget = item
                                detailsTarget = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp), tint = CtOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calculate Checksum (MD5/SHA)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsTarget = null }) {
                    Text("OK")
                }
            }
        )
    }

    checksumTarget?.let { item ->
        ChecksumDialog(
            item = item,
            onDismiss = { checksumTarget = null }
        )
    }

    tagTarget?.let { item ->
        val currentTagIds = fileTagsMap[item.path] ?: emptyList()
        val currentTags = currentTagIds.mapNotNull { ColorTag.findTag(it) }
        TagSelectionDialog(
            fileItem = item,
            currentTags = currentTags,
            onToggleTag = { tag ->
                viewModel.toggleTagForFile(item.file, tag.id)
            },
            onDismiss = { tagTarget = null }
        )
    }

    exifCleanerTarget?.let { item ->
        ExifCleanerDialog(
            item = item,
            onDismiss = { exifCleanerTarget = null },
            onCleanSaved = {
                viewModel.refreshCurrentDirectory()
                viewModel.showMessage("Photo EXIF metadata stripped!")
            }
        )
    }

    zipTargets?.let { targets ->
        ZipCompressDialog(
            selectedItems = targets,
            defaultArchiveName = zipArchiveName,
            onDismiss = { zipTargets = null },
            onCompress = { name, level ->
                val destFile = File(storageState.currentDir, name)
                viewModel.compressFilesToZip(targets.map { it.file }, destFile, level)
                zipTargets = null
            },
            onCompressPro = { name, format, level, password ->
                val destFile = File(storageState.currentDir, name)
                viewModel.compressFilesToZip(targets.map { it.file }, destFile, level, format, password)
                zipTargets = null
            }
        )
    }

    openWithTarget?.let { target ->
        val builtInLabel = when (target.category) {
            FileCategory.IMAGE -> "View in Cent Gallery (Built-in)"
            FileCategory.CODE, FileCategory.DOCUMENT -> {
                if (target.extension in listOf("txt", "md", "json", "xml", "kt", "java", "py", "sh", "html", "css", "js", "log", "csv")) {
                    "Edit in Cent Text Editor (Built-in)"
                } else null
            }
            FileCategory.ARCHIVE -> "Inspect & Extract with Cent Zip"
            FileCategory.APK -> "Install / Inspect Package (Built-in)"
            else -> null
        }
        val builtInAction: (() -> Unit)? = when (target.category) {
            FileCategory.IMAGE -> {
                { viewModel.openImageViewer(target.file, storageState.items) }
            }
            FileCategory.CODE, FileCategory.DOCUMENT -> {
                if (target.extension in listOf("txt", "md", "json", "xml", "kt", "java", "py", "sh", "html", "css", "js", "log", "csv")) {
                    { viewModel.openTextEditor(target.file) }
                } else null
            }
            FileCategory.ARCHIVE -> {
                { viewModel.openZipViewer(target.file) }
            }
            FileCategory.APK -> {
                { viewModel.openApkInstallDialog(target.file) }
            }
            else -> null
        }

        OpenFileChooserDialog(
            item = target,
            onDismiss = { openWithTarget = null },
            onOpenBuiltIn = builtInAction,
            builtInActionLabel = builtInLabel
        )
    }

    if (showSortMenu) {
        MiSortBottomSheet(
            currentSortType = storageState.sortType,
            foldersOnTop = storageState.foldersOnTop,
            showHidden = storageState.showHidden,
            filterOnlyBigFiles = storageState.filterOnlyBigFiles,
            onSortTypeChange = { viewModel.setSortType(it) },
            onToggleFoldersOnTop = { viewModel.toggleFoldersOnTop() },
            onToggleShowHidden = { viewModel.toggleShowHidden() },
            onToggleBigFilesFilter = { viewModel.toggleBigFilesFilter() },
            onDismiss = { showSortMenu = false }
        )
    }

    if (showBatchRenameDialog && storageState.selectedItems.size >= 2) {
        BatchRenameDialog(
            selectedFiles = storageState.selectedItems.toList(),
            onDismiss = { showBatchRenameDialog = false },
            onApplyRename = { pairs ->
                viewModel.batchRename(pairs)
                showBatchRenameDialog = false
            }
        )
    }

    if (showToolsSheet) {
        MiToolsBottomSheet(
            onDismiss = { showToolsSheet = false },
            onVaultClick = { viewModel.openVault() },
            onFastShareClick = { viewModel.openFastShare() },
            onNetworkDrivesClick = { viewModel.openNetworkDrives() },
            onTrashClick = { viewModel.openTrash() },
            onAnalyzerClick = { viewModel.openStorageAnalyzer() },
            onDuplicatesClick = { viewModel.openDuplicateFinder() },
            onCleanerClick = { viewModel.openCleaner() },
            onAppManagerClick = { viewModel.openAppManager() },
            onAppInstallerClick = { viewModel.openAppInstaller() },
            onFtpClick = { viewModel.openFtpServer() },
            onDualPaneToggle = { viewModel.toggleDualPane() },
            isDualPaneActive = isDualPaneActive,
            onSocialClick = { viewModel.openSocialHub() },
            onPinWidgetClick = {
                val ok = com.ct.explorer.utils.ShortcutHelper.requestPinStorageWidget(context)
                viewModel.showMessage(if (ok) "Home Screen Storage Widget prompt opened!" else "Long-press Home Screen -> Widgets -> Cent File Manager")
            },
            onPinVideoWidgetClick = {
                viewModel.pinVideoPlayerWidget(context)
            },
            onWebShareClick = { viewModel.openWebShare() },
            onFileShredderClick = { viewModel.openFileShredder() },
            onSmartCollectionsClick = { viewModel.openSmartCollections() },
            onTimeMachineClick = { viewModel.openTimeMachine() },
            onDiagnosticsClick = { viewModel.openDiagnosticsScreen() },
            onAboutClick = { showAboutDialog = true }
        )
    }

    if (showFavoritesSheet) {
        FavoritesBottomSheet(
            favorites = favorites,
            onDismiss = { showFavoritesSheet = false },
            onNavigateTo = { file ->
                viewModel.selectTab(CtTab.STORAGE)
                viewModel.loadDirectory(file, addToHistory = true)
            },
            onOpenFile = { fileItem ->
                if (!viewModel.openFileSmart(fileItem, emptyList())) {
                    openWithTarget = fileItem
                }
            },
            onRemoveFavorite = { file ->
                viewModel.toggleFavorite(file)
            },
            currentFolder = storageState.currentDir,
            onAddCurrentToFavorites = {
                viewModel.toggleFavorite(storageState.currentDir)
            }
        )
    }

    if (showAboutDialog) {
        AboutPrivacyDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesBottomSheet(
    favorites: List<FavoriteItem>,
    onDismiss: () -> Unit,
    onNavigateTo: (File) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onRemoveFavorite: (File) -> Unit,
    currentFolder: File? = null,
    onAddCurrentToFavorites: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("favorites_bottom_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Favourites",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (favorites.isEmpty()) "Quick access to starred files & folders" else "${favorites.size} saved items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Quick action: Star current folder if available
            if (currentFolder != null && onAddCurrentToFavorites != null) {
                val isAlreadyFavorite = favorites.any { it.path == currentFolder.absolutePath }
                if (!isAlreadyFavorite) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAddCurrentToFavorites() },
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Star current folder (${currentFolder.name})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No favourites yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Star any file or folder from its menu to access it here anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favorites, key = { it.path }) { fav ->
                        val isDir = fav.isDirectory
                        val iconVector = if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile
                        val iconColor = if (isDir) Color(0xFFFFB300) else Color(0xFF2563EB)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onDismiss()
                                    if (isDir) {
                                        onNavigateTo(fav.file)
                                    } else {
                                        onOpenFile(FileItem(fav.file))
                                    }
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(iconColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fav.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = fav.path,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveFavorite(fav.file) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Remove from favourites",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
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

@Composable
fun UtilityCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SortOptionRow(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        color = Color.Transparent
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun RecentTabContent(
    recentFiles: List<FileItem>,
    isLoading: Boolean,
    activeFilter: String,
    onFilterSelected: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onMenuAction: (String, FileItem) -> Unit,
    onRefresh: () -> Unit,
    selectedItems: Set<FileItem> = emptySet(),
    onToggleSelect: (FileItem) -> Unit = {}
) {
    val filters = listOf("All", "🔥 Big Files (>10MB)", "Images", "Docs", "APKs", "Archives", "Music")

    val filteredList = remember(recentFiles, activeFilter) {
        when (activeFilter) {
            "🔥 Big Files (>10MB)" -> recentFiles.filter { it.effectiveSize >= 10L * 1024 * 1024 }
            "Images" -> recentFiles.filter { it.category == FileCategory.IMAGE }
            "Docs" -> recentFiles.filter { it.category == FileCategory.DOCUMENT }
            "APKs" -> recentFiles.filter { it.category == FileCategory.APK }
            "Archives" -> recentFiles.filter { it.category == FileCategory.ARCHIVE }
            "Music" -> recentFiles.filter { it.category == FileCategory.AUDIO }
            else -> recentFiles
        }
    }

    val grouped = remember(filteredList) {
        filteredList.groupBy { it.timeGroup }
    }

    var recentViewMode by remember { mutableStateOf(ViewMode.GRID) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recent_tab_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Filter chips bar & View Mode toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { f ->
                        val isSelected = f == activeFilter
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onFilterSelected(f) }
                                .testTag("filter_chip_$f"),
                            color = if (isSelected) CtOrange else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = f,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        recentViewMode = if (recentViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (recentViewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                        contentDescription = "Toggle View Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (isLoading && filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CtOrange)
                }
            }
        } else if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No recent files found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            grouped.forEach { (timeHeader, files) ->
                item {
                    Text(
                        text = timeHeader,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
                    )
                }

                if (recentViewMode == ViewMode.GRID) {
                    val gridColumns = if (activeFilter == "Images") 3 else 4
                    val chunked = files.chunked(gridColumns)
                    items(chunked, key = { row -> "recent_grid_${row.first().path}" }) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { file ->
                                val isSelected = selectedItems.contains(file)
                                val isSelectionMode = selectedItems.isNotEmpty()
                                Box(modifier = Modifier.weight(1f)) {
                                    MiFileGridItem(
                                        item = file,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = { if (isSelectionMode) onToggleSelect(file) else onOpenFile(file) },
                                        onLongClick = { onToggleSelect(file) },
                                        onToggleSelect = { onToggleSelect(file) },
                                        onMenuAction = { onMenuAction(it, file) }
                                    )
                                }
                            }
                            repeat(gridColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(files, key = { it.path }) { file ->
                        val isSelected = selectedItems.contains(file)
                        val isSelectionMode = selectedItems.isNotEmpty()
                        MiFileRow(
                            item = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = { if (isSelectionMode) onToggleSelect(file) else onOpenFile(file) },
                            onLongClick = { onToggleSelect(file) },
                            onToggleSelect = { onToggleSelect(file) },
                            onMenuAction = { onMenuAction(it, file) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageTabContent(
    storageSpace: StorageSpace,
    storageState: StorageTabState,
    rootStorageDir: File,
    favorites: List<FavoriteItem> = emptyList(),
    isDualPaneActive: Boolean = false,
    paneBState: StorageTabState? = null,
    activePaneIndex: Int = 0,
    onSelectPane: (Int) -> Unit = {},
    onNavigatePaneA: (File) -> Unit = {},
    onNavigatePaneB: (File) -> Unit = {},
    onBackPaneA: () -> Unit = {},
    onBackPaneB: () -> Unit = {},
    onCopyAtoB: () -> Unit = {},
    onCopyBtoA: () -> Unit = {},
    onMoveAtoB: () -> Unit = {},
    onMoveBtoA: () -> Unit = {},
    onToggleSelectB: (FileItem) -> Unit = {},
    storageVolumes: List<StorageVolumeItem> = emptyList(),
    selectedVolume: StorageVolumeItem? = null,
    onSwitchVolume: (StorageVolumeItem) -> Unit = {},
    fileTagsMap: Map<String, List<String>> = emptyMap(),
    selectedTagFilter: String? = null,
    onFilterTag: (String?) -> Unit = {},
    onFastShareSelected: () -> Unit = {},
    onCleanClick: () -> Unit,
    onTrashClick: () -> Unit,
    onNetworkDrivesClick: () -> Unit = {},
    onFastShareClick: () -> Unit = {},
    onFtpClick: () -> Unit = {},
    onDualPaneToggle: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onCategoryClick: (FileCategory, String) -> Unit,
    onSocialClick: () -> Unit = {},
    onAppManagerClick: () -> Unit,
    onAppInstallerClick: () -> Unit = {},
    onVaultClick: () -> Unit,
    onDuplicatesClick: () -> Unit,
    onAnalyzerClick: () -> Unit,
    onWebShareClick: () -> Unit = {},
    onFileShredderClick: () -> Unit = {},
    onSmartCollectionsClick: () -> Unit = {},
    onTimeMachineClick: () -> Unit = {},
    onPinWidgetClick: () -> Unit = {},
    onBatchRename: () -> Unit,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onZipSelected: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onToggleViewMode: () -> Unit,
    onShowSortMenu: () -> Unit,
    onToggleBigFilesFilter: () -> Unit = {},
    onMenuAction: (String, FileItem) -> Unit
) {
    if (isDualPaneActive && paneBState != null) {
        DualPaneView(
            paneAState = storageState,
            paneBState = paneBState,
            activePane = activePaneIndex,
            onSelectPane = onSelectPane,
            onNavigateA = onNavigatePaneA,
            onNavigateB = onNavigatePaneB,
            onBackA = onBackPaneA,
            onBackB = onBackPaneB,
            onCopyAtoB = onCopyAtoB,
            onCopyBtoA = onCopyBtoA,
            onMoveAtoB = onMoveAtoB,
            onMoveBtoA = onMoveBtoA,
            onToggleSelectA = onToggleSelect,
            onToggleSelectB = onToggleSelectB,
            onClearSelectA = onClearSelection,
            onOpenFile = onOpenFile
        )
        return
    }

    val isRoot = storageState.currentDir == rootStorageDir

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage_tab_list"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Root Home Dashboard View
        if (isRoot) {
            // 1. Sleek Storage Card with integrated volume selector (Zero duplicate chips)
            item {
                StorageCard(
                    storageSpace = storageSpace,
                    onCleanClick = onCleanClick,
                    storageVolumes = storageVolumes,
                    selectedVolume = selectedVolume,
                    onSwitchVolume = onSwitchVolume,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 2. 12-tile Cent Category Grid (with Media, Vault, Favourites, Cleaner, and Utilities)
            item {
                CategoryGrid(
                    onCategoryClick = onCategoryClick,
                    onToolsClick = onToolsClick,
                    onSocialClick = onSocialClick,
                    onVaultClick = onVaultClick,
                    onFavoritesClick = onFavoritesClick,
                    onCleanerClick = onCleanClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Section header for folder actions / filter chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (!isRoot) Arrangement.SpaceBetween else Arrangement.End
            ) {
                if (!isRoot) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = storageState.currentDir.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${storageState.displayItems.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = storageState.filterOnlyBigFiles,
                        onClick = onToggleBigFilesFilter,
                        label = {
                            Text(
                                text = "🔥 Big (>10MB)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (storageState.filterOnlyBigFiles) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CtOrange,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (storageState.bigItemsCount > 0) CtOrange.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = CtOrange,
                            enabled = true,
                            selected = storageState.filterOnlyBigFiles
                        )
                    )

                    if (selectedTagFilter != null) {
                        Surface(
                            color = CtOrange.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onFilterTag(null) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tag: $selectedTagFilter ✕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CtOrange
                                )
                            }
                        }
                    }
                }
            }
        }

        // Color Tags Quick Filter (When browsing folders or when a tag is active)
        if (!isRoot || selectedTagFilter != null) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTagFilter == null,
                            onClick = { onFilterTag(null) },
                            label = { Text("All Files") }
                        )
                    }
                    items(ColorTag.PRESET_TAGS) { tag ->
                        val isSelected = selectedTagFilter == tag.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterTag(if (isSelected) null else tag.id) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(tag.composeColor)
                                )
                            },
                            label = { Text(tag.name) }
                        )
                    }
                }
            }
        }

        // Folder Path Breadcrumbs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRoot) {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("navigate_up_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Up",
                            tint = CtOrange
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                MiBreadcrumbs(
                    currentDir = storageState.currentDir,
                    rootStorageDir = rootStorageDir,
                    onNavigateTo = onNavigateTo,
                    modifier = Modifier.weight(1f)
                )

                // Interactive Sort Pill
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onShowSortMenu)
                        .testTag("sort_pill_button"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = CtOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = storageState.sortType.chipLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onToggleViewMode, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (storageState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                        contentDescription = "View Mode"
                    )
                }
            }
        }

        // Action Toolbar (New Folder, New File, Select All)
        if (!storageState.isSelectionMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onNewFolder,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp).testTag("action_new_folder")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Folder", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledTonalButton(
                            onClick = onNewFile,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(32.dp).testTag("action_new_file")
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New File", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    TextButton(onClick = onSelectAll) {
                        Text("Select All", style = MaterialTheme.typography.labelMedium, color = CtOrange)
                    }
                }
            }
        }

        // Folder files listing
        if (storageState.isLoading && storageState.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CtOrange)
                }
            }
        } else if (storageState.displayItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (storageState.filterOnlyBigFiles) "No big items (>10MB) in this folder" else "This folder is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (storageState.filterOnlyBigFiles) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onToggleBigFilesFilter) {
                                Text("Show all ${storageState.items.size} items", color = CtOrange)
                            }
                        }
                    }
                }
            }
        } else if (storageState.viewMode == ViewMode.GRID) {
            val gridColumns = 4
            val chunked = storageState.displayItems.chunked(gridColumns)
            items(chunked, key = { row -> "storage_grid_${row.first().path}" }) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { item ->
                        val isSelected = storageState.selectedItems.contains(item)
                        val itemTagIds = fileTagsMap[item.path] ?: emptyList()
                        val itemTags = itemTagIds.mapNotNull { ColorTag.findTag(it) }
                        Box(modifier = Modifier.weight(1f)) {
                            MiFileGridItem(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = storageState.isSelectionMode,
                                tags = itemTags,
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigateTo(item.file)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                                onLongClick = { onToggleSelect(item) },
                                onToggleSelect = { onToggleSelect(item) },
                                onMenuAction = { onMenuAction(it, item) }
                            )
                        }
                    }
                    repeat(gridColumns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(storageState.displayItems, key = { it.path }) { item ->
                val isSelected = storageState.selectedItems.contains(item)
                val itemTagIds = fileTagsMap[item.path] ?: emptyList()
                val itemTags = itemTagIds.mapNotNull { ColorTag.findTag(it) }
                MiFileRow(
                    item = item,
                    isSelected = isSelected,
                    isSelectionMode = storageState.isSelectionMode,
                    tags = itemTags,
                    onClick = {
                        if (item.isDirectory) {
                            onNavigateTo(item.file)
                        } else {
                            onOpenFile(item)
                        }
                    },
                    onLongClick = { onToggleSelect(item) },
                    onToggleSelect = { onToggleSelect(item) },
                    onMenuAction = { onMenuAction(it, item) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

}

fun handleOpenFile(
    item: FileItem,
    siblingItems: List<FileItem>,
    viewModel: ExplorerViewModel
) {
    when (item.category) {
        FileCategory.CODE, FileCategory.DOCUMENT -> {
            if (item.extension in listOf("txt", "md", "json", "xml", "kt", "java", "py", "sh", "html", "css", "js", "log", "csv")) {
                viewModel.openTextEditor(item.file)
            } else {
                viewModel.showMessage("Opening ${item.name}...")
            }
        }
        FileCategory.IMAGE -> {
            viewModel.openImageViewer(item.file, siblingItems)
        }
        FileCategory.ARCHIVE -> {
            viewModel.openZipViewer(item.file)
        }
        FileCategory.APK -> {
            viewModel.openApkInstallDialog(item.file)
        }
        else -> {
            viewModel.openTextEditor(item.file)
        }
    }
}
