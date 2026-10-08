package com.ct.explorer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.ApkTab
import com.ct.explorer.data.model.ColorTag
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.SortType
import com.ct.explorer.data.model.ViewMode
import com.ct.explorer.data.model.sortFileList
import com.ct.explorer.ui.components.ChecksumDialog
import com.ct.explorer.ui.components.ExifCleanerDialog
import com.ct.explorer.ui.components.MiFileGridItem
import com.ct.explorer.ui.components.MiFileRow
import com.ct.explorer.ui.components.MiImageGalleryView
import com.ct.explorer.ui.components.MiSortBottomSheet
import com.ct.explorer.ui.components.OpenFileChooserDialog
import com.ct.explorer.ui.components.TagSelectionDialog
import com.ct.explorer.ui.components.ZipCompressDialog
import com.ct.explorer.ui.theme.MiGreen
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.ThumbnailLoader
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryViewScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.categoryViewState.collectAsStateWithLifecycle()
    val fileTagsMap by viewModel.fileTagsMap.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    var openWithTarget by remember { mutableStateOf<FileItem?>(null) }
    var checksumTarget by remember { mutableStateOf<FileItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var tagTarget by remember { mutableStateOf<FileItem?>(null) }
    var exifCleanerTarget by remember { mutableStateOf<FileItem?>(null) }
    var zipTargets by remember { mutableStateOf<List<FileItem>?>(null) }
    var zipArchiveName by remember { mutableStateOf("") }

    var showCategorySort by remember { mutableStateOf(false) }
    var categorySortType by remember { mutableStateOf(SortType.DATE_NEWEST) }
    var foldersOnTop by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var filterOnlyBigFiles by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }

    val sortedItems = remember(state.items, categorySortType, foldersOnTop, showHidden, filterOnlyBigFiles) {
        val filtered = state.items.filter { item ->
            val hiddenMatch = showHidden || !item.name.startsWith(".")
            val bigMatch = !filterOnlyBigFiles || item.effectiveSize >= 10L * 1024 * 1024
            hiddenMatch && bigMatch
        }
        sortFileList(filtered, categorySortType, foldersOnTop = foldersOnTop)
    }

    fun handleCategoryMenuAction(action: String, item: FileItem) {
        when (action) {
            "open" -> {
                if (!viewModel.openFileSmart(item, sortedItems)) {
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
            "vault" -> {
                viewModel.addFileToVault(item)
                viewModel.refreshCategory()
            }
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
            "delete" -> deleteTarget = item
            "details" -> detailsTarget = item
            "zip" -> {
                zipArchiveName = "${item.name}.zip"
                zipTargets = listOf(item)
            }
            "unzip" -> viewModel.openZipViewer(item.file)
        }
    }

    Scaffold(
        modifier = modifier.testTag("category_view_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.category == FileCategory.IMAGE) "Photos & Gallery" else state.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (state.category == FileCategory.IMAGE)
                                "${sortedItems.size} photos • ${FileItem.formatBytes(sortedItems.sumOf { it.size })}"
                            else
                                "${sortedItems.size} files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("category_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.category != FileCategory.IMAGE) {
                        IconButton(onClick = { viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle View Mode",
                                tint = CtOrange
                            )
                        }
                    }
                    IconButton(onClick = { showCategorySort = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = CtOrange)
                    }
                    if (state.category == FileCategory.APK) {
                        FilledTonalButton(
                            onClick = { viewModel.openAppInstaller() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("APKs", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CtOrange)
                }
            } else if (state.category == FileCategory.IMAGE) {
                MiImageGalleryView(
                    items = sortedItems,
                    favorites = favorites,
                    onOpenImage = { item, list ->
                        viewModel.openImageViewer(item.file, list)
                    },
                    onMenuAction = { action, item ->
                        handleCategoryMenuAction(action, item)
                    },
                    onToggleFavorite = { file ->
                        viewModel.toggleFavorite(file)
                    },
                    onBatchDelete = { targets ->
                        viewModel.deleteItems(targets)
                    },
                    onBatchShare = { targets ->
                        FileOpener.shareMultipleFiles(context, targets)
                    },
                    onBatchFavorite = { targets ->
                        viewModel.toggleFavorites(targets)
                    },
                    onBatchVault = { targets ->
                        viewModel.addFilesToVault(targets)
                        viewModel.refreshCategory()
                    },
                    onBatchCleanExif = { targets ->
                        scope.launch {
                            for (target in targets) {
                                val cleanFile = File(target.file.parentFile, "clean_${target.file.name}")
                                com.ct.explorer.utils.ExifPrivacyCleaner.stripExif(target.file, cleanFile)
                            }
                            viewModel.refreshCategory()
                            viewModel.showMessage("Cleaned EXIF for ${targets.size} photos")
                        }
                    }
                )
            } else if (sortedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (filterOnlyBigFiles) "No big ${state.title} (>10MB) found" else "No ${state.title} found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (filterOnlyBigFiles) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { filterOnlyBigFiles = false }) {
                                Text("Show all ${state.items.size} items", color = CtOrange)
                            }
                        }
                    }
                }
            } else if (viewMode == ViewMode.GRID) {
                val isImageCategory = state.category == FileCategory.IMAGE
                val gridColumns = if (isImageCategory) 3 else 4
                val chunked = sortedItems.chunked(gridColumns)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chunked, key = { row -> "cat_grid_${row.first().path}" }) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { item ->
                                val itemTagIds = fileTagsMap[item.path] ?: emptyList()
                                val itemTags = itemTagIds.mapNotNull { ColorTag.findTag(it) }
                                Box(modifier = Modifier.weight(1f)) {
                                    if (isImageCategory) {
                                        MiImageGalleryItem(
                                            item = item,
                                            onClick = { viewModel.openImageViewer(item.file, sortedItems) },
                                            onLongClick = { openWithTarget = item },
                                            onMenuAction = { action -> handleCategoryMenuAction(action, item) }
                                        )
                                    } else {
                                        MiFileGridItem(
                                            item = item,
                                            isSelected = false,
                                            isSelectionMode = false,
                                            tags = itemTags,
                                            onClick = {
                                                if (!viewModel.openFileSmart(item, sortedItems)) {
                                                    openWithTarget = item
                                                }
                                            },
                                            onLongClick = {},
                                            onToggleSelect = {},
                                            onMenuAction = { action -> handleCategoryMenuAction(action, item) }
                                        )
                                    }
                                }
                            }
                            repeat(gridColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sortedItems, key = { it.path }) { item ->
                        val itemTagIds = fileTagsMap[item.path] ?: emptyList()
                        val itemTags = itemTagIds.mapNotNull { ColorTag.findTag(it) }
                        MiFileRow(
                            item = item,
                            isSelected = false,
                            isSelectionMode = false,
                            tags = itemTags,
                            onClick = {
                                if (!viewModel.openFileSmart(item, sortedItems)) {
                                    openWithTarget = item
                                }
                            },
                            onLongClick = {},
                            onToggleSelect = {},
                            onMenuAction = { action -> handleCategoryMenuAction(action, item) }
                        )
                    }
                }
            }
        }
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
                { viewModel.openImageViewer(target.file, sortedItems) }
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

    checksumTarget?.let { item ->
        ChecksumDialog(
            item = item,
            onDismiss = { checksumTarget = null }
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
                            viewModel.refreshCategory()
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

    detailsTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Name: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Location: ${item.path}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Size: ${item.formattedSize} (${java.lang.String.format(java.util.Locale.US, "%,d", item.effectiveSize)} bytes)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (item.isLarge) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (item.isLarge) CtOrange else MaterialTheme.colorScheme.onSurface
                    )
                    Text(text = "Type: ${item.friendlyTypeLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Modified: ${item.formattedDate}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsTarget = null }) {
                    Text("OK")
                }
            }
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
                viewModel.refreshCategory()
                viewModel.showMessage("Photo EXIF metadata stripped!")
            }
        )
    }

    zipTargets?.let { targets ->
        val parentDir = targets.firstOrNull()?.file?.parentFile ?: viewModel.fileRepository.downloadsDirectory
        ZipCompressDialog(
            selectedItems = targets,
            defaultArchiveName = zipArchiveName,
            onDismiss = { zipTargets = null },
            onCompress = { name, level ->
                val destFile = File(parentDir, name)
                viewModel.compressFilesToZip(targets.map { it.file }, destFile, level)
                zipTargets = null
            },
            onCompressPro = { name, format, level, password ->
                val destFile = File(parentDir, name)
                viewModel.compressFilesToZip(targets.map { it.file }, destFile, level, format, password)
                zipTargets = null
            }
        )
    }

    deleteTarget?.let { item ->
        var moveToBin by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (moveToBin) "Move to Recycle Bin" else "Delete Permanently") },
            text = {
                Column {
                    Text(
                        if (moveToBin)
                            "Move \"${item.name}\" to Recycle Bin? You can restore it anytime."
                        else
                            "Permanently delete \"${item.name}\"? This action cannot be undone."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
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
                            viewModel.moveToTrash(listOf(item))
                        } else {
                            viewModel.deleteItems(listOf(item))
                        }
                        viewModel.refreshCategory()
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (moveToBin) CtOrange else Color(0xFFEF4444)
                    )
                ) {
                    Text(if (moveToBin) "Move to Bin" else "Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategorySort) {
        MiSortBottomSheet(
            currentSortType = categorySortType,
            foldersOnTop = foldersOnTop,
            showHidden = showHidden,
            filterOnlyBigFiles = filterOnlyBigFiles,
            onSortTypeChange = { categorySortType = it },
            onToggleFoldersOnTop = { foldersOnTop = !foldersOnTop },
            onToggleShowHidden = { showHidden = !showHidden },
            onToggleBigFilesFilter = { filterOnlyBigFiles = !filterOnlyBigFiles },
            onDismiss = { showCategorySort = false }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MiImageGalleryItem(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailBitmap by ThumbnailLoader.rememberThumbnailState(
        file = item.file,
        category = FileCategory.IMAGE,
        targetWidth = 400,
        targetHeight = 400
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("gallery_photo_${item.name}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0284C7).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Subtle gradient overlay at the bottom with file format and size
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.extension.uppercase(),
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.formattedSize,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}
