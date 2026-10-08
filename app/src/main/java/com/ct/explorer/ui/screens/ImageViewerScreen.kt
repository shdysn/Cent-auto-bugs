package com.ct.explorer.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.components.ExifCleanerDialog
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class ImageFilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid HDR"),
    WARM("Warm Sun"),
    COOL("Cyber Cool"),
    BW_NOIR("B&W Noir"),
    SEPIA("Vintage")
}

enum class ImageCropRatio(val label: String, val ratio: Float?) {
    ORIGINAL("Free / Full", null),
    SQUARE("1:1 Square", 1f),
    WIDE_16_9("16:9 Cinema", 16f / 9f),
    STANDARD_4_3("4:3 Classic", 4f / 3f),
    STORY_9_16("9:16 Story", 9f / 16f)
}

internal object FullscreenImageCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 4).coerceAtLeast(32 * 1024)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(path: String): Bitmap? = cache.get(path)
    fun put(path: String, bitmap: Bitmap) { cache.put(path, bitmap) }
    fun remove(path: String) { cache.remove(path) }
    fun clear() { cache.evictAll() }
}

internal suspend fun loadFullscreenBitmap(file: File): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists() || !file.canRead() || file.isDirectory) return@withContext null
    val path = file.absolutePath
    FullscreenImageCache.get(path)?.let { return@withContext it }

    try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        val sample = if (maxDim > 2560) (maxDim / 2560).coerceAtLeast(1) else 1
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: return@withContext null

        val finalBitmap = try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        } catch (_: Exception) {
            decoded
        }

        FullscreenImageCache.put(path, finalBitmap)
        finalBitmap
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.imageViewerState.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showExifCleaner by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isEditStudioOpen by remember { mutableStateOf(false) }
    var editTabIndex by remember { mutableIntStateOf(0) } // 0=Transform/Crop, 1=Filters, 2=Adjust

    // Image Transform & Filter State
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(ImageFilterPreset.ORIGINAL) }
    var selectedCropRatio by remember { mutableStateOf(ImageCropRatio.ORIGINAL) }
    var brightnessAdj by remember { mutableFloatStateOf(0f) }   // -80f..+80f
    var contrastAdj by remember { mutableFloatStateOf(1f) }     // 0.5f..1.8f
    var saturationAdj by remember { mutableFloatStateOf(1f) }   // 0f..2.0f

    // Pinch-to-Zoom & Pan State
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newZoom = (zoomScale * zoomChange).coerceIn(1f, 4f)
        zoomScale = newZoom
        if (newZoom > 1.05f) {
            panOffset += offsetChange
        } else {
            panOffset = Offset.Zero
        }
    }

    var reloadTrigger by remember { mutableIntStateOf(0) }

    val images = state.imageList
    val pageCount = images.size.coerceAtLeast(1)
    val initialPage = remember(images) {
        state.currentIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )
    val thumbListState = rememberLazyListState()

    // Sync external index changes with pager (when opening new image or changing from outside)
    var lastSettledIndex by remember { mutableIntStateOf(state.currentIndex) }
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex in 0 until pageCount && pagerState.currentPage != state.currentIndex && state.currentIndex != lastSettledIndex && !pagerState.isScrollInProgress) {
            lastSettledIndex = state.currentIndex
            pagerState.scrollToPage(state.currentIndex)
        }
    }

    // Sync pager swipe with viewModel ONLY after page has settled (prevents gesture interruptions)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (images.isNotEmpty() && settled in images.indices && settled != state.currentIndex) {
                lastSettledIndex = settled
                viewModel.setImageIndex(settled)
            }
        }
    }

    // Reset edits and zoom, and scroll thumbnail strip when settled page changes
    LaunchedEffect(pagerState.settledPage) {
        zoomScale = 1f
        panOffset = Offset.Zero
        rotationDegrees = 0f
        flipHorizontal = false
        flipVertical = false
        selectedFilter = ImageFilterPreset.ORIGINAL
        selectedCropRatio = ImageCropRatio.ORIGINAL
        brightnessAdj = 0f
        contrastAdj = 1f
        saturationAdj = 1f
        if (images.isNotEmpty() && pagerState.settledPage in images.indices) {
            thumbListState.animateScrollToItem((pagerState.settledPage - 2).coerceAtLeast(0))
        }
    }

    var activeBitmap by remember(state.currentFile?.absolutePath, reloadTrigger) {
        mutableStateOf<Bitmap?>(state.currentFile?.let { FullscreenImageCache.get(it.absolutePath) })
    }

    LaunchedEffect(state.currentFile?.absolutePath, reloadTrigger) {
        val file = state.currentFile ?: return@LaunchedEffect
        val loaded = loadFullscreenBitmap(file)
        if (loaded != null) {
            activeBitmap = loaded
        }
    }

    val composeColorMatrix = remember(selectedFilter, brightnessAdj, contrastAdj, saturationAdj) {
        buildComposeColorMatrix(selectedFilter, brightnessAdj, contrastAdj, saturationAdj)
    }

    val hasUnsavedEdits = rotationDegrees != 0f ||
        flipHorizontal ||
        flipVertical ||
        selectedFilter != ImageFilterPreset.ORIGINAL ||
        selectedCropRatio != ImageCropRatio.ORIGINAL ||
        brightnessAdj != 0f ||
        contrastAdj != 1f ||
        saturationAdj != 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("image_viewer_screen")
    ) {
        // 1. Fullscreen Horizontal Pager with Images
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = zoomScale <= 1.05f,
            beyondViewportPageCount = 2,
            key = { page -> images.getOrNull(page)?.path ?: page.toString() },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageItem = images.getOrNull(page)
            val pageFile = pageItem?.file ?: (if (page == state.currentIndex) state.currentFile else null)
            val isCurrent = page == pagerState.currentPage

            if (pageFile != null) {
                ImageViewerPageItem(
                    file = pageFile,
                    isCurrentPage = isCurrent,
                    zoomScale = if (isCurrent) zoomScale else 1f,
                    panOffset = if (isCurrent) panOffset else Offset.Zero,
                    transformState = transformState,
                    rotationDegrees = if (isCurrent) rotationDegrees else 0f,
                    flipHorizontal = if (isCurrent) flipHorizontal else false,
                    flipVertical = if (isCurrent) flipVertical else false,
                    cropRatio = if (isCurrent) selectedCropRatio.ratio else null,
                    composeColorMatrix = composeColorMatrix,
                    onSingleTap = {
                        if (!isEditStudioOpen) {
                            areControlsVisible = !areControlsVisible
                        }
                    },
                    onDoubleTap = {
                        if (isCurrent) {
                            if (zoomScale > 1.1f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                zoomScale = 2.25f
                            }
                        }
                    },
                    onBitmapReady = { loadedBmp ->
                        if (page == pagerState.currentPage) {
                            activeBitmap = loadedBmp
                        }
                    }
                )
            }
        }

        // 2. Left Navigation Button (Previous Picture)
        if (areControlsVisible && pagerState.currentPage > 0 && zoomScale <= 1.05f) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Picture",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 3. Right Navigation Button (Next Picture)
        if (areControlsVisible && pagerState.currentPage < pageCount - 1 && zoomScale <= 1.05f) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Picture",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 4. Floating Reset Zoom Badge when zoomed in
        if (zoomScale > 1.05f) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (areControlsVisible) 110.dp else 24.dp)
                    .clickable {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOutMap,
                        contentDescription = "Reset Zoom",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}% • Double tap to reset",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 5. Overlaid Modern Top Bar (Animates In/Out)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("image_viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = state.currentFile?.name ?: "Photos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.imageList.isNotEmpty()) {
                            val currentBmp = activeBitmap
                            val dimensions = if (currentBmp != null) " • ${currentBmp.width}×${currentBmp.height}" else ""
                            val sizeText = state.currentFile?.let { " • ${FileItem.formatBytes(it.length())}" } ?: ""
                            Text(
                                text = "${state.currentIndex + 1} of ${state.imageList.size}$dimensions$sizeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
                    val isFavorite = remember(state.currentFile?.absolutePath, favorites) {
                        state.currentFile?.let { f -> favorites.any { it.path == f.absolutePath } } == true
                    }
                    IconButton(
                        onClick = { state.currentFile?.let { viewModel.toggleFavorite(it) } },
                        modifier = Modifier.testTag("image_viewer_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFBBF24) else Color.White
                        )
                    }

                    if (state.currentFile != null) {
                        IconButton(
                            onClick = { FileOpener.shareFile(context, FileItem(state.currentFile!!)) },
                            modifier = Modifier.testTag("image_viewer_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                    }

                    Box {
                        var showMenu by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("image_viewer_more_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF1E2430))
                                .widthIn(min = 230.dp)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = CtOrange, modifier = Modifier.size(20.dp))
                                },
                                text = {
                                    Column {
                                        Text("Details", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("File size, resolution & path", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showInfoDialog = true
                                }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Compress, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                },
                                text = {
                                    Column {
                                        Text("Resize & Compress", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Reduce size & optimize", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showCompressDialog = true
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                                },
                                text = {
                                    Column {
                                        Text("EXIF Privacy Cleaner", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Remove GPS & camera info", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showExifCleaner = true
                                }
                            )

                            if (state.currentFile != null) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(20.dp))
                                    },
                                    text = {
                                        Column {
                                            Text("Open with...", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("External gallery or app", color = Color.LightGray, fontSize = 11.sp)
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        FileOpener.openWithChooser(context, FileItem(state.currentFile!!))
                                    }
                                )

                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
                                    },
                                    text = {
                                        Column {
                                            Text("Delete", color = Color(0xFFF87171), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("Remove from device", color = Color.LightGray, fontSize = 11.sp)
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Overlaid Modern Bottom Controls / Editor (Animates In/Out)
        AnimatedVisibility(
            visible = areControlsVisible || isEditStudioOpen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    if (isEditStudioOpen) {
                        // Pro Image Editor Drawer
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top Header with Close, Reset & Save Copy
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { isEditStudioOpen = false },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Editor", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    listOf("Transform" to 0, "Filters" to 1, "Adjust" to 2).forEach { (tabTitle, idx) ->
                                        val selected = editTabIndex == idx
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) CtOrange else Color.White.copy(alpha = 0.1f),
                                            modifier = Modifier.clickable { editTabIndex = idx }
                                        ) {
                                            Text(
                                                text = tabTitle,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (hasUnsavedEdits) {
                                        TextButton(
                                            onClick = {
                                                rotationDegrees = 0f
                                                flipHorizontal = false
                                                flipVertical = false
                                                selectedFilter = ImageFilterPreset.ORIGINAL
                                                selectedCropRatio = ImageCropRatio.ORIGINAL
                                                brightnessAdj = 0f
                                                contrastAdj = 1f
                                                saturationAdj = 1f
                                            }
                                        ) {
                                            Text("Reset", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val srcBmp = activeBitmap ?: return@Button
                                            val curFile = state.currentFile ?: return@Button
                                            scope.launch {
                                                val savedFile = renderAndSaveEditedBitmap(
                                                    sourceBitmap = srcBmp,
                                                    sourceFile = curFile,
                                                    rotationDegrees = rotationDegrees,
                                                    flipH = flipHorizontal,
                                                    flipV = flipVertical,
                                                    cropRatio = selectedCropRatio.ratio,
                                                    filter = selectedFilter,
                                                    brightness = brightnessAdj,
                                                    contrast = contrastAdj,
                                                    saturation = saturationAdj,
                                                    scaleFactor = 1.0f,
                                                    quality = 95
                                                )
                                                if (savedFile != null) {
                                                    viewModel.refreshCurrentDirectory()
                                                    viewModel.showMessage("Saved edited photo: ${savedFile.name}")
                                                    isEditStudioOpen = false
                                                } else {
                                                    viewModel.showMessage("Failed to save edited photo")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CtOrange),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            when (editTabIndex) {
                                0 -> {
                                    // Rotate, Flip & Aspect Crop
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        OutlinedButton(
                                            onClick = { rotationDegrees = (rotationDegrees - 90f) % 360f },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left", tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("-90°", color = Color.White, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f },
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right", tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+90°", color = Color.White, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { flipHorizontal = !flipHorizontal },
                                            border = BorderStroke(1.dp, if (flipHorizontal) CtOrange else Color.White.copy(alpha = 0.25f))
                                        ) {
                                            Icon(Icons.Default.Flip, contentDescription = "Flip H", tint = if (flipHorizontal) CtOrange else Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Flip H", color = Color.White, fontSize = 12.sp)
                                        }
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(ImageCropRatio.values().toList()) { crop ->
                                            val active = selectedCropRatio == crop
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (active) CtOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                                border = BorderStroke(1.dp, if (active) CtOrange else Color.Transparent),
                                                modifier = Modifier.clickable { selectedCropRatio = crop }
                                            ) {
                                                Text(
                                                    text = crop.label,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    // Studio Filters
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(ImageFilterPreset.values().toList()) { preset ->
                                            val active = selectedFilter == preset
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (active) CtOrange else Color.White.copy(alpha = 0.1f),
                                                modifier = Modifier.clickable { selectedFilter = preset }
                                            ) {
                                                Text(
                                                    text = preset.label,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    // Brightness, Contrast & Saturation Sliders
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Brightness", fontSize = 12.sp, modifier = Modifier.width(76.dp))
                                            Slider(
                                                value = brightnessAdj,
                                                onValueChange = { brightnessAdj = it },
                                                valueRange = -80f..80f,
                                                colors = SliderDefaults.colors(thumbColor = CtOrange, activeTrackColor = CtOrange),
                                                modifier = Modifier.weight(1f).height(26.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Contrast", fontSize = 12.sp, modifier = Modifier.width(76.dp))
                                            Slider(
                                                value = contrastAdj,
                                                onValueChange = { contrastAdj = it },
                                                valueRange = 0.5f..1.8f,
                                                colors = SliderDefaults.colors(thumbColor = CtOrange, activeTrackColor = CtOrange),
                                                modifier = Modifier.weight(1f).height(26.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Saturation", fontSize = 12.sp, modifier = Modifier.width(76.dp))
                                            Slider(
                                                value = saturationAdj,
                                                onValueChange = { saturationAdj = it },
                                                valueRange = 0f..2.0f,
                                                colors = SliderDefaults.colors(thumbColor = CtOrange, activeTrackColor = CtOrange),
                                                modifier = Modifier.weight(1f).height(26.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Horizontal Thumbnail Filmstrip when Edit Studio is closed
                        if (images.size > 1) {
                            LazyRow(
                                state = thumbListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(images, key = { index, item -> "thumb_${item.path}_$index" }) { index, item ->
                                    val isSelected = index == pagerState.currentPage
                                    val thumbBitmap by ThumbnailLoader.rememberThumbnailState(
                                        file = item.file,
                                        category = FileCategory.IMAGE,
                                        targetWidth = 140,
                                        targetHeight = 140
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1F2937),
                                        border = if (isSelected) BorderStroke(2.5.dp, CtOrange) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .size(if (isSelected) 46.dp else 38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            }
                                    ) {
                                        if (thumbBitmap != null) {
                                            Image(
                                                bitmap = thumbBitmap!!.asImageBitmap(),
                                                contentDescription = item.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF374151)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Modern Quick Action Dock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ViewerActionButton(
                                icon = Icons.Default.Tune,
                                label = "Edit",
                                tint = if (hasUnsavedEdits) CtOrange else Color.White,
                                onClick = { isEditStudioOpen = true }
                            )

                            ViewerActionButton(
                                icon = Icons.Default.Share,
                                label = "Share",
                                tint = Color.White,
                                onClick = {
                                    if (state.currentFile != null) {
                                        FileOpener.shareFile(context, FileItem(state.currentFile!!))
                                    }
                                }
                            )

                            ViewerActionButton(
                                icon = Icons.Default.Compress,
                                label = "Compress",
                                tint = Color(0xFF38BDF8),
                                onClick = { showCompressDialog = true }
                            )

                            ViewerActionButton(
                                icon = Icons.Default.Info,
                                label = "Details",
                                tint = Color.White,
                                onClick = { showInfoDialog = true }
                            )

                            ViewerActionButton(
                                icon = Icons.Default.DeleteOutline,
                                label = "Delete",
                                tint = Color(0xFFF87171),
                                onClick = { showDeleteDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Smart Image Compressor & Resizer Dialog
    val compressBmp = activeBitmap
    if (showCompressDialog && state.currentFile != null && compressBmp != null) {
        val currentFile = state.currentFile!!
        val originalBytes = currentFile.length()
        var selectedQuality by remember { mutableIntStateOf(80) }
        var selectedScale by remember { mutableFloatStateOf(0.75f) }

        val estimatedBytes = remember(originalBytes, selectedQuality, selectedScale) {
            (originalBytes * selectedScale * selectedScale * (selectedQuality / 100f) * 0.7f).toLong().coerceAtLeast(4096L)
        }

        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Compress, contentDescription = null, tint = CtOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Image Compressor & Resizer")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Original: ${compressBmp.width}×${compressBmp.height} (${FileItem.formatBytes(originalBytes)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f)
                    ) {
                        val newW = (compressBmp.width * selectedScale).roundToInt()
                        val newH = (compressBmp.height * selectedScale).roundToInt()
                        Text(
                            text = "Estimated Output: ${newW}×${newH} (~${FileItem.formatBytes(estimatedBytes)})",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text("Resolution Scale", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("100%" to 1.0f, "75%" to 0.75f, "50%" to 0.5f).forEach { (label, sc) ->
                            val active = selectedScale == sc
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) CtOrange else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedScale = sc }
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Text("JPEG Compression Quality ($selectedQuality%)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Slider(
                        value = selectedQuality.toFloat(),
                        onValueChange = { selectedQuality = it.roundToInt() },
                        valueRange = 25f..95f,
                        colors = SliderDefaults.colors(thumbColor = CtOrange, activeTrackColor = CtOrange)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val out = renderAndSaveEditedBitmap(
                                sourceBitmap = compressBmp,
                                sourceFile = currentFile,
                                rotationDegrees = rotationDegrees,
                                flipH = flipHorizontal,
                                flipV = flipVertical,
                                cropRatio = selectedCropRatio.ratio,
                                filter = selectedFilter,
                                brightness = brightnessAdj,
                                contrast = contrastAdj,
                                saturation = saturationAdj,
                                scaleFactor = selectedScale,
                                quality = selectedQuality
                            )
                            showCompressDialog = false
                            if (out != null) {
                                viewModel.refreshCurrentDirectory()
                                viewModel.showMessage("Saved compressed image: ${out.name} (${FileItem.formatBytes(out.length())})")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Compress & Save Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInfoDialog && state.currentFile != null) {
        val f = state.currentFile!!
        val infoBmp = activeBitmap
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Photo Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "File: ${f.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Path: ${f.absolutePath}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Size: ${FileItem.formatBytes(f.length())}", style = MaterialTheme.typography.bodyMedium)
                    if (infoBmp != null) {
                        Text(text = "Resolution: ${infoBmp.width} × ${infoBmp.height}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showExifCleaner && state.currentFile != null) {
        ExifCleanerDialog(
            item = FileItem(state.currentFile!!),
            onDismiss = { showExifCleaner = false },
            onCleanSaved = {
                state.currentFile?.let { FullscreenImageCache.remove(it.absolutePath) }
                reloadTrigger++
                viewModel.showMessage("Photo metadata stripped successfully!")
            }
        )
    }
}

@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ImageViewerPageItem(
    file: File,
    isCurrentPage: Boolean,
    zoomScale: Float,
    panOffset: Offset,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    rotationDegrees: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    cropRatio: Float?,
    composeColorMatrix: ColorMatrix,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onBitmapReady: (Bitmap) -> Unit
) {
    var pageBitmap by remember(file.absolutePath) {
        mutableStateOf<Bitmap?>(FullscreenImageCache.get(file.absolutePath))
    }

    // Instant thumbnail fallback so swiping between photos never shows a blank frame
    val thumbBitmap by ThumbnailLoader.rememberThumbnailState(
        file = file,
        category = FileCategory.IMAGE,
        targetWidth = 500,
        targetHeight = 500
    )

    LaunchedEffect(file.absolutePath) {
        val cached = FullscreenImageCache.get(file.absolutePath)
        if (cached != null) {
            pageBitmap = cached
            if (isCurrentPage) onBitmapReady(cached)
        } else {
            val decoded = loadFullscreenBitmap(file)
            pageBitmap = decoded
            if (decoded != null && isCurrentPage) {
                onBitmapReady(decoded)
            }
        }
    }

    LaunchedEffect(isCurrentPage, pageBitmap) {
        val bmp = pageBitmap
        if (isCurrentPage && bmp != null) {
            onBitmapReady(bmp)
        }
    }

    // CRITICAL: Only attach transformable when zoomed in, so horizontal swipes navigate pages smoothly
    val isZoomed = isCurrentPage && zoomScale > 1.05f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isZoomed) {
                    Modifier.transformable(
                        state = transformState,
                        enabled = true
                    )
                } else {
                    Modifier
                }
            )
            .pointerInput(isCurrentPage, isZoomed) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onSingleTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val displayBitmap = pageBitmap ?: thumbBitmap
        if (displayBitmap != null) {
            val cropModifier = if (isCurrentPage && cropRatio != null) {
                Modifier.aspectRatio(cropRatio, matchHeightConstraintsFirst = cropRatio < 1f)
            } else {
                Modifier.fillMaxSize()
            }

            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = file.name,
                colorFilter = if (isCurrentPage) ColorFilter.colorMatrix(composeColorMatrix) else null,
                modifier = cropModifier
                    .graphicsLayer(
                        scaleX = if (isCurrentPage) zoomScale * (if (flipHorizontal) -1f else 1f) else 1f,
                        scaleY = if (isCurrentPage) zoomScale * (if (flipVertical) -1f else 1f) else 1f,
                        rotationZ = if (isCurrentPage) rotationDegrees else 0f,
                        translationX = if (isCurrentPage) panOffset.x else 0f,
                        translationY = if (isCurrentPage) panOffset.y else 0f
                    ),
                contentScale = if (isCurrentPage && cropRatio != null) ContentScale.Crop else ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = CtOrange,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

private fun buildComposeColorMatrix(
    filter: ImageFilterPreset,
    brightness: Float,
    contrast: Float,
    saturation: Float
): ColorMatrix {
    val cm = ColorMatrix()
    val effectiveSat = when (filter) {
        ImageFilterPreset.BW_NOIR -> 0f
        ImageFilterPreset.VIVID -> (saturation * 1.35f).coerceAtMost(2.5f)
        else -> saturation
    }
    cm.setToSaturation(effectiveSat)

    val c = when (filter) {
        ImageFilterPreset.VIVID -> contrast * 1.15f
        ImageFilterPreset.BW_NOIR -> contrast * 1.25f
        else -> contrast
    }
    val b = brightness
    val rScale = when (filter) {
        ImageFilterPreset.WARM -> 1.12f
        ImageFilterPreset.SEPIA -> 1.18f
        else -> 1f
    }
    val bScale = when (filter) {
        ImageFilterPreset.COOL -> 1.18f
        ImageFilterPreset.WARM -> 0.88f
        ImageFilterPreset.SEPIA -> 0.78f
        else -> 1f
    }

    val adjustMatrix = ColorMatrix(
        floatArrayOf(
            c * rScale, 0f, 0f, 0f, b,
            0f, c, 0f, 0f, b,
            0f, 0f, c * bScale, 0f, b,
            0f, 0f, 0f, 1f, 0f
        )
    )
    cm.timesAssign(adjustMatrix)
    return cm
}

private suspend fun renderAndSaveEditedBitmap(
    sourceBitmap: Bitmap,
    sourceFile: File,
    rotationDegrees: Float,
    flipH: Boolean,
    flipV: Boolean,
    cropRatio: Float?,
    filter: ImageFilterPreset,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    scaleFactor: Float,
    quality: Int
): File? = withContext(Dispatchers.IO) {
    try {
        // 1. Apply Rotation & Flip Matrix
        val matrix = Matrix().apply {
            if (flipH || flipV) {
                postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
            }
            if (rotationDegrees != 0f) {
                postRotate(rotationDegrees)
            }
            if (scaleFactor != 1.0f) {
                postScale(scaleFactor, scaleFactor)
            }
        }
        var working = Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            sourceBitmap.height,
            matrix,
            true
        )

        // 2. Apply Center Aspect Ratio Crop if requested
        if (cropRatio != null) {
            val w = working.width
            val h = working.height
            val currentRatio = w.toFloat() / h.toFloat()
            if (kotlin.math.abs(currentRatio - cropRatio) > 0.02f) {
                val targetW: Int
                val targetH: Int
                if (currentRatio > cropRatio) {
                    targetH = h
                    targetW = (h * cropRatio).roundToInt().coerceIn(1, w)
                } else {
                    targetW = w
                    targetH = (w / cropRatio).roundToInt().coerceIn(1, h)
                }
                val startX = ((w - targetW) / 2).coerceAtLeast(0)
                val startY = ((h - targetH) / 2).coerceAtLeast(0)
                working = Bitmap.createBitmap(working, startX, startY, targetW, targetH)
            }
        }

        // 3. Apply ColorFilter via Canvas
        val finalBitmap = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(finalBitmap)
        val composeMatrix = buildComposeColorMatrix(filter, brightness, contrast, saturation)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = AndroidColorMatrixFilter(AndroidColorMatrix(composeMatrix.values))
        }
        canvas.drawBitmap(working, 0f, 0f, paint)

        val outFile = File(
            sourceFile.parentFile,
            "${sourceFile.nameWithoutExtension}_pro_${System.currentTimeMillis() % 10000}.jpg"
        )
        FileOutputStream(outFile).use { fos ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), fos)
        }
        outFile
    } catch (_: Exception) {
        null
    }
}
