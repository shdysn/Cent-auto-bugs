package com.ct.explorer.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.FavoriteItem
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.ThumbnailLoader
import java.io.File
import java.util.Locale

enum class GalleryTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    PHOTOS("Photos", Icons.Default.PhotoLibrary),
    ALBUMS("Albums", Icons.Default.FolderSpecial),
    FAVORITES("Favorites", Icons.Default.Star)
}

enum class GalleryDensity(val columns: Int, val label: String) {
    MAGAZINE(2, "Magazine (2 cols)"),
    STANDARD(3, "Standard (3 cols)"),
    COMPACT(4, "Compact (4 cols)")
}

data class GalleryAlbum(
    val folderName: String,
    val folderPath: String,
    val coverItem: FileItem,
    val items: List<FileItem>,
    val totalSize: Long
)

@Composable
fun MiImageGalleryView(
    items: List<FileItem>,
    favorites: List<FavoriteItem>,
    onOpenImage: (FileItem, List<FileItem>) -> Unit,
    onMenuAction: (String, FileItem) -> Unit,
    onToggleFavorite: (File) -> Unit,
    onBatchDelete: (List<FileItem>) -> Unit,
    onBatchShare: (List<FileItem>) -> Unit,
    onBatchFavorite: (List<FileItem>) -> Unit,
    onBatchVault: (List<FileItem>) -> Unit,
    onBatchCleanExif: (List<FileItem>) -> Unit,
    modifier: Modifier = Modifier
) = CtImageGalleryView(
    items = items,
    favorites = favorites,
    onOpenImage = onOpenImage,
    onMenuAction = onMenuAction,
    onToggleFavorite = onToggleFavorite,
    onBatchDelete = onBatchDelete,
    onBatchShare = onBatchShare,
    onBatchFavorite = onBatchFavorite,
    onBatchVault = onBatchVault,
    onBatchCleanExif = onBatchCleanExif,
    modifier = modifier
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CtImageGalleryView(
    items: List<FileItem>,
    favorites: List<FavoriteItem>,
    onOpenImage: (FileItem, List<FileItem>) -> Unit,
    onMenuAction: (String, FileItem) -> Unit,
    onToggleFavorite: (File) -> Unit,
    onBatchDelete: (List<FileItem>) -> Unit,
    onBatchShare: (List<FileItem>) -> Unit,
    onBatchFavorite: (List<FileItem>) -> Unit,
    onBatchVault: (List<FileItem>) -> Unit,
    onBatchCleanExif: (List<FileItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(GalleryTab.PHOTOS) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedDensity by remember { mutableStateOf(GalleryDensity.STANDARD) }
    var activeAlbumFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Multi-Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPaths = remember { mutableStateListOf<String>() }

    val isFavoriteMap = remember(favorites) {
        val set = favorites.map { it.path }.toSet()
        set
    }

    // Filter items based on active tab, filter chip, album, and search query
    val displayItems = remember(items, selectedTab, selectedFilter, activeAlbumFilter, searchQuery, isFavoriteMap) {
        var baseList = when (selectedTab) {
            GalleryTab.PHOTOS -> items
            GalleryTab.ALBUMS -> {
                if (activeAlbumFilter != null) {
                    items.filter { it.file.parentFile?.name.equals(activeAlbumFilter, ignoreCase = true) }
                } else {
                    items
                }
            }
            GalleryTab.FAVORITES -> items.filter { isFavoriteMap.contains(it.path) }
        }

        // Apply album filter if set in Photos tab
        if (activeAlbumFilter != null && selectedTab == GalleryTab.PHOTOS) {
            baseList = baseList.filter { it.file.parentFile?.name.equals(activeAlbumFilter, ignoreCase = true) }
        }

        // Apply quick filter chips
        baseList = when (selectedFilter) {
            "Screenshots" -> baseList.filter {
                it.name.lowercase(Locale.ROOT).contains("screenshot") ||
                it.path.lowercase(Locale.ROOT).contains("screenshot")
            }
            "Camera (DCIM)" -> baseList.filter {
                it.path.lowercase(Locale.ROOT).contains("dcim") ||
                it.path.lowercase(Locale.ROOT).contains("camera")
            }
            "Large (>5MB)" -> baseList.filter { it.size >= 5L * 1024 * 1024 }
            "PNG" -> baseList.filter { it.extension == "png" }
            "GIF" -> baseList.filter { it.extension == "gif" }
            "WebP" -> baseList.filter { it.extension == "webp" }
            else -> baseList
        }

        // Apply search query
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            baseList = baseList.filter { it.name.lowercase(Locale.ROOT).contains(q) || it.extension.contains(q) }
        }

        baseList
    }

    // Compute Albums grouped by parent folder
    val albums = remember(items) {
        items.groupBy { it.file.parentFile?.name ?: "Images" }
            .map { (folderName, folderItems) ->
                val folderPath = folderItems.firstOrNull()?.file?.parentFile?.absolutePath ?: ""
                val cover = folderItems.maxByOrNull { it.lastModified } ?: folderItems.first()
                val size = folderItems.sumOf { it.size }
                GalleryAlbum(
                    folderName = folderName,
                    folderPath = folderPath,
                    coverItem = cover,
                    items = folderItems,
                    totalSize = size
                )
            }
            .sortedByDescending { it.items.size }
    }

    // Group photos chronologically for timeline display
    val groupedSections = remember(displayItems) {
        displayItems.groupBy { it.timeGroup }
    }

    fun toggleSelection(item: FileItem) {
        if (selectedPaths.contains(item.path)) {
            selectedPaths.remove(item.path)
            if (selectedPaths.isEmpty()) {
                isSelectionMode = false
            }
        } else {
            selectedPaths.add(item.path)
            isSelectionMode = true
        }
    }

    fun selectAll(select: Boolean) {
        selectedPaths.clear()
        if (select) {
            selectedPaths.addAll(displayItems.map { it.path })
            isSelectionMode = true
        } else {
            isSelectionMode = false
        }
    }

    val selectedItems = remember(selectedPaths.toList(), displayItems) {
        displayItems.filter { selectedPaths.contains(it.path) }
    }

    Box(modifier = modifier.fillMaxSize().testTag("mi_image_gallery_view")) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. Professional Top Navigation Header with Tabs
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {

                    // Search Bar (if active)
                    AnimatedVisibility(visible = isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search photos by name or extension...", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CtOrange) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Search")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("gallery_search_input")
                        )
                    }

                    // Modern Segmented Gallery Tab Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GalleryTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            val countLabel = when (tab) {
                                GalleryTab.PHOTOS -> items.size
                                GalleryTab.ALBUMS -> albums.size
                                GalleryTab.FAVORITES -> isFavoriteMap.size
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) CtOrange else Color.Transparent,
                                tonalElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedTab = tab
                                        if (tab != GalleryTab.ALBUMS) activeAlbumFilter = null
                                    }
                                    .testTag("gallery_tab_${tab.name.lowercase(Locale.ROOT)}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${tab.title} ($countLabel)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Secondary Bar: Filter Chips & Density Control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album filter breadcrumb if active
                        if (activeAlbumFilter != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CtOrange.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, CtOrange.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { activeAlbumFilter = null }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = CtOrange, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Album: $activeAlbumFilter", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CtOrange)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, contentDescription = "Clear Filter", tint = CtOrange, modifier = Modifier.size(14.dp))
                                }
                            }
                        } else if (selectedTab == GalleryTab.PHOTOS) {
                            // Quick Filter Chips Row
                            val filterOptions = listOf("All", "Screenshots", "Camera (DCIM)", "Large (>5MB)", "PNG", "GIF", "WebP")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(filterOptions) { chip ->
                                    val isSelected = selectedFilter == chip
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedFilter = chip }
                                            .testTag("gallery_chip_${chip.lowercase(Locale.ROOT)}")
                                    ) {
                                        Text(
                                            text = chip,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Right action buttons: Layout Density & Multi-select & Search
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isSearchActive) {
                                IconButton(
                                    onClick = { isSearchActive = true },
                                    modifier = Modifier.size(32.dp).testTag("gallery_search_button")
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Density Dropdown Menu (2, 3, or 4 columns)
                            var showDensityMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showDensityMenu = true },
                                    modifier = Modifier.size(32.dp).testTag("gallery_density_toggle")
                                ) {
                                    Icon(
                                        imageVector = when (selectedDensity) {
                                            GalleryDensity.MAGAZINE -> Icons.Default.ViewAgenda
                                            GalleryDensity.STANDARD -> Icons.Default.GridView
                                            GalleryDensity.COMPACT -> Icons.Default.Apps
                                        },
                                        contentDescription = "Change Layout",
                                        tint = CtOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showDensityMenu,
                                    onDismissRequest = { showDensityMenu = false }
                                ) {
                                    GalleryDensity.values().forEach { density ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (selectedDensity == density) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = CtOrange, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                    } else {
                                                        Spacer(modifier = Modifier.width(24.dp))
                                                    }
                                                    Text(density.label, fontWeight = if (selectedDensity == density) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            },
                                            onClick = {
                                                selectedDensity = density
                                                showDensityMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Multi-select mode toggle button
                            IconButton(
                                onClick = {
                                    if (isSelectionMode) {
                                        isSelectionMode = false
                                        selectedPaths.clear()
                                    } else {
                                        isSelectionMode = true
                                    }
                                },
                                modifier = Modifier.size(32.dp).testTag("gallery_multi_select_button")
                            ) {
                                Icon(
                                    imageVector = if (isSelectionMode) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                                    contentDescription = "Multi Select",
                                    tint = if (isSelectionMode) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Main Content View
            if (displayItems.isEmpty() && selectedTab != GalleryTab.ALBUMS) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (selectedTab) {
                                GalleryTab.FAVORITES -> Icons.Default.StarOutline
                                else -> Icons.Default.ImageNotSupported
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when {
                                selectedTab == GalleryTab.FAVORITES -> "No favorite photos yet"
                                searchQuery.isNotEmpty() -> "No photos matching \"$searchQuery\""
                                activeAlbumFilter != null -> "No photos in album \"$activeAlbumFilter\""
                                else -> "No photos found in this category"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (selectedTab) {
                                GalleryTab.FAVORITES -> "Tap the star icon on any photo to save it to your Favorites"
                                else -> "Try clearing filters or changing search query"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                when (selectedTab) {
                    GalleryTab.ALBUMS -> {
                        // Albums Grid View
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text(
                                    text = "${albums.size} Albums on Device",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            val albumChunked = albums.chunked(2)
                            items(albumChunked, key = { row -> "album_row_${row.first().folderPath}" }) { rowAlbums ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowAlbums.forEach { album ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            MiAlbumCard(
                                                album = album,
                                                onClick = {
                                                    activeAlbumFilter = album.folderName
                                                    selectedTab = GalleryTab.PHOTOS
                                                }
                                            )
                                        }
                                    }
                                    if (rowAlbums.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }

                    GalleryTab.PHOTOS, GalleryTab.FAVORITES -> {
                        // Chronological Photo Timeline Grid
                        val columns = selectedDensity.columns
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedSections.forEach { (timeHeader, sectionPhotos) ->
                                item(key = "section_header_$timeHeader") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = timeHeader,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${sectionPhotos.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (isSelectionMode) {
                                            val allSectionSelected = sectionPhotos.all { selectedPaths.contains(it.path) }
                                            TextButton(
                                                onClick = {
                                                    if (allSectionSelected) {
                                                        sectionPhotos.forEach { selectedPaths.remove(it.path) }
                                                    } else {
                                                        sectionPhotos.forEach {
                                                            if (!selectedPaths.contains(it.path)) selectedPaths.add(it.path)
                                                        }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (allSectionSelected) "Deselect" else "Select All",
                                                    fontSize = 11.sp,
                                                    color = CtOrange
                                                )
                                            }
                                        }
                                    }
                                }

                                val chunked = sectionPhotos.chunked(columns)
                                items(chunked, key = { row -> "grid_row_${row.first().path}" }) { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            val isSelected = selectedPaths.contains(item.path)
                                            val isFavorite = isFavoriteMap.contains(item.path)
                                            Box(modifier = Modifier.weight(1f)) {
                                                MiModernPhotoCard(
                                                    item = item,
                                                    isFavorite = isFavorite,
                                                    isSelected = isSelected,
                                                    isSelectionMode = isSelectionMode,
                                                    density = selectedDensity,
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            toggleSelection(item)
                                                        } else {
                                                            onOpenImage(item, displayItems)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        toggleSelection(item)
                                                    },
                                                    onToggleFavorite = {
                                                        onToggleFavorite(item.file)
                                                    },
                                                    onMenuAction = { action ->
                                                        onMenuAction(action, item)
                                                    }
                                                )
                                            }
                                        }
                                        repeat(columns - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }

        // 3. Floating Batch Action Bar when photos are selected
        AnimatedVisibility(
            visible = isSelectionMode && selectedItems.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E2430),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedItems.size} selected",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = FileItem.formatBytes(selectedItems.sumOf { it.size }),
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(28.dp), color = Color.White.copy(alpha = 0.2f))

                    // Share
                    IconButton(
                        onClick = { onBatchShare(selectedItems) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Favorite
                    IconButton(
                        onClick = { onBatchFavorite(selectedItems) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Favorite", tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                    }

                    // Clean EXIF
                    IconButton(
                        onClick = { onBatchCleanExif(selectedItems) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = "Clean EXIF", tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                    }

                    // Vault
                    IconButton(
                        onClick = { onBatchVault(selectedItems) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Move to Vault", tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                    }

                    // Delete
                    IconButton(
                        onClick = { onBatchDelete(selectedItems) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                    }

                    // Close Selection
                    IconButton(
                        onClick = {
                            isSelectionMode = false
                            selectedPaths.clear()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiModernPhotoCard(
    item: FileItem,
    isFavorite: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    density: GalleryDensity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    val thumbnailBitmap by ThumbnailLoader.rememberThumbnailState(
        file = item.file,
        category = FileCategory.IMAGE,
        targetWidth = 400,
        targetHeight = 400
    )

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) BorderStroke(2.5.dp, CtOrange) else null,
        tonalElevation = 2.dp,
        modifier = Modifier
            .aspectRatio(if (density == GalleryDensity.MAGAZINE) 1.2f else 1f)
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Top overlay with Favorite Star & Selection Circle & More Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) CtOrange else Color.Black.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color.White),
                        modifier = Modifier.size(20.dp)
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(2.dp))
                        }
                    }
                } else if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // 3-dots Quick Action Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24)) },
                            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            text = { Text("Share") },
                            onClick = {
                                showMenu = false
                                onMenuAction("fast_share")
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF34D399)) },
                            text = { Text("Clean EXIF Metadata") },
                            onClick = {
                                showMenu = false
                                onMenuAction("clean_exif")
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFA78BFA)) },
                            text = { Text("Move to Private Vault") },
                            onClick = {
                                showMenu = false
                                onMenuAction("vault")
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            text = { Text("Details & EXIF") },
                            onClick = {
                                showMenu = false
                                onMenuAction("details")
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFF87171)) },
                            text = { Text("Delete", color = Color(0xFFF87171)) },
                            onClick = {
                                showMenu = false
                                onMenuAction("delete")
                            }
                        )
                    }
                }
            }

            // Bottom overlay with format and size
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
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
                        text = item.extension.uppercase(Locale.ROOT),
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

@Composable
private fun MiAlbumCard(
    album: GalleryAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverBitmap by ThumbnailLoader.rememberThumbnailState(
        file = album.coverItem.file,
        category = FileCategory.IMAGE,
        targetWidth = 400,
        targetHeight = 400
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("album_card_${album.folderName}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(Color(0xFF1E2430))
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap!!.asImageBitmap(),
                        contentDescription = album.folderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = CtOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Item count badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "${album.items.size}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = album.folderName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${album.items.size} photos • ${FileItem.formatBytes(album.totalSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
