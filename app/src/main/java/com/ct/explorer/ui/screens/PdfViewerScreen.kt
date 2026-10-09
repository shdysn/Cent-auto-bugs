package com.ct.explorer.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.FileOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val pdfState by viewModel.pdfViewerState.collectAsStateWithLifecycle()
    val file = pdfState.file

    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val renderedPages = remember { mutableStateMapOf<Int, Bitmap>() }

    // Fullscreen & Original Page View states (opens in fullscreen by default)
    var isFullScreen by remember { mutableStateOf(true) }
    var isOriginalPageView by remember { mutableStateOf(false) }

    // Manage system bars for immersive fullscreen
    LaunchedEffect(isFullScreen) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isFullScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        viewModel.handleBackPress()
    }

    // PDF Pro Toolkit States
    var pdfReadingTheme by remember { mutableIntStateOf(0) } // 0 = Day (Original), 1 = Sepia, 2 = Night
    var showThumbnailBar by remember { mutableStateOf(true) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount.coerceAtLeast(1) })

    // ColorFilter for Reading Modes (Day/Original = None, Sepia = Warm Amber, Night = Inverted Dark)
    val pageColorFilter = remember(pdfReadingTheme, isOriginalPageView) {
        if (isOriginalPageView) {
            null
        } else {
            when (pdfReadingTheme) {
                1 -> {
                    ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                0.90f, 0.05f, 0.05f, 0f, 25f,
                                0.05f, 0.85f, 0.05f, 0f, 15f,
                                0.02f, 0.02f, 0.70f, 0f, -10f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
                2 -> {
                    ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                -0.88f, 0f, 0f, 0f, 240f,
                                0f, -0.88f, 0f, 0f, 240f,
                                0f, 0f, -0.85f, 0f, 245f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
                else -> null
            }
        }
    }

    // Zoom & pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3.5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfdRef by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(file) {
        onDispose {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { pfdRef?.close() } catch (_: Exception) {}
            renderedPages.values.forEach { bmp ->
                try { if (!bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
            }
            renderedPages.clear()
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, isOriginalPageView) {
        if (!isOriginalPageView) {
            currentPageIndex = listState.firstVisibleItemIndex
        }
    }

    LaunchedEffect(pagerState.currentPage, isOriginalPageView) {
        if (isOriginalPageView) {
            currentPageIndex = pagerState.currentPage
        }
    }

    // Load PDF Initial
    LaunchedEffect(file) {
        if (file == null || !file.exists()) {
            errorMessage = "File not found"
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        renderedPages.clear()

        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pfdRef = pfd
                pdfRenderer = renderer
                val count = renderer.pageCount
                withContext(Dispatchers.Main) {
                    pageCount = count
                    isLoading = false
                }

                val initialBatch = minOf(count, 3)
                for (i in 0 until initialBatch) {
                    renderSinglePage(renderer, i, renderedPages)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Failed to open PDF"
                    isLoading = false
                }
            }
        }
    }

    // Dynamic Sliding Window Rendering around currentPageIndex
    LaunchedEffect(currentPageIndex, pdfRenderer, pageCount) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val toEvict = renderedPages.keys.filter { it < currentPageIndex - 4 || it > currentPageIndex + 5 }
            for (k in toEvict) {
                val bmp = renderedPages.remove(k)
                if (bmp != null) {
                    try { if (!bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
                }
            }

            val start = (currentPageIndex - 2).coerceAtLeast(0)
            val end = (currentPageIndex + 3).coerceAtMost(pageCount - 1)
            for (idx in start..end) {
                if (!renderedPages.containsKey(idx)) {
                    renderSinglePage(renderer, idx, renderedPages)
                }
            }
        }
    }

    val toggleOriginalPageView: () -> Unit = {
        val targetOriginal = !isOriginalPageView
        isOriginalPageView = targetOriginal
        scale = 1f
        offset = Offset.Zero
        if (targetOriginal) {
            pdfReadingTheme = 0
            coroutineScope.launch {
                if (pageCount > 0) {
                    pagerState.scrollToPage(currentPageIndex.coerceIn(0, pageCount - 1))
                }
            }
        } else {
            coroutineScope.launch {
                if (pageCount > 0) {
                    listState.scrollToItem(currentPageIndex.coerceIn(0, pageCount - 1))
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_viewer_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isFullScreen) {
                // Compact Fullscreen Header Bar with Back button & Original Page View button
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back Button
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { viewModel.handleBackPress() }
                                .testTag("pdf_fullscreen_back_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Back",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // PDF Title in Center
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = pdfState.title.ifEmpty { file?.name ?: "PDF Document" },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pageCount > 0) {
                                Text(
                                    text = "Page ${currentPageIndex + 1} / $pageCount",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Original Page View Button + Exit Fullscreen Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isOriginalPageView) CtOrange else Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isOriginalPageView) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { toggleOriginalPageView() }
                                    .testTag("pdf_original_page_view_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOriginalPageView) Icons.Default.ViewDay else Icons.Default.Description,
                                        contentDescription = "Original Page View",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isOriginalPageView) "Original Page ✓" else "Original Page View",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isFullScreen = false },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("pdf_exit_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Show Full Toolbar",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = pdfState.title.ifEmpty { file?.name ?: "PDF Studio" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pageCount > 0) {
                                val progressPercent = ((currentPageIndex + 1) * 100) / pageCount
                                Text(
                                    text = "Page ${currentPageIndex + 1} of $pageCount • ${if (isOriginalPageView) "Original Page View" else "$progressPercent% read"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.handleBackPress() },
                            modifier = Modifier.testTag("pdf_toolbar_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Original Page View Toggle Chip in Toolbar
                        FilterChip(
                            selected = isOriginalPageView,
                            onClick = { toggleOriginalPageView() },
                            label = {
                                Text(
                                    text = "Original Page",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Original Page View",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CtOrange.copy(alpha = 0.16f),
                                selectedLabelColor = CtOrange,
                                selectedLeadingIconColor = CtOrange
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // Fullscreen Toggle
                        IconButton(onClick = { isFullScreen = true }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen"
                            )
                        }

                        if (scale > 1f) {
                            IconButton(onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }) {
                                Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom")
                            }
                        }

                        // Reading Theme Toggle (Day, Sepia, Night)
                        if (!isOriginalPageView) {
                            IconButton(onClick = { pdfReadingTheme = (pdfReadingTheme + 1) % 3 }) {
                                Icon(
                                    imageVector = when (pdfReadingTheme) {
                                        1 -> Icons.Default.MenuBook
                                        2 -> Icons.Default.DarkMode
                                        else -> Icons.Default.LightMode
                                    },
                                    contentDescription = "Switch Reading Theme",
                                    tint = if (pdfReadingTheme != 0) CtOrange else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Print / Save as PDF
                        if (file != null) {
                            IconButton(onClick = {
                                com.ct.explorer.utils.PrintHelper.printFile(context, file)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "Print / Save as PDF",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Open in External App (Adobe Reader / Google Drive)
                        if (file != null) {
                            IconButton(onClick = {
                                FileOpener.openWithChooser(context, FileItem(file))
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open in External PDF Viewer")
                            }
                        }

                        // Export Current Page as Image
                        IconButton(
                            onClick = {
                                val pageBmp = renderedPages[currentPageIndex]
                                if (pageBmp != null && file != null) {
                                    coroutineScope.launch {
                                        val saved = exportPdfPageAsImage(pageBmp, file.nameWithoutExtension, currentPageIndex + 1)
                                        if (saved != null) {
                                            viewModel.showMessage("Exported Page ${currentPageIndex + 1} to Pictures/CentExplorer_PDF/${saved.name}")
                                        } else {
                                            viewModel.showMessage("Failed to export page")
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Export Page as Image")
                        }

                        // Toggle Thumbnail Strip
                        if (!isOriginalPageView) {
                            IconButton(onClick = { showThumbnailBar = !showThumbnailBar }) {
                                Icon(
                                    imageVector = Icons.Default.ViewCarousel,
                                    contentDescription = "Page Thumbnails",
                                    tint = if (showThumbnailBar) CtOrange else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (file != null) {
                            IconButton(onClick = {
                                FileOpener.shareFile(context, FileItem(file))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share PDF")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = showThumbnailBar && !isOriginalPageView && !isFullScreen && pageCount > 1 && !isLoading) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(pageCount) { idx ->
                            val isSelected = idx == currentPageIndex
                            val thumb = renderedPages[idx]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) CtOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(70.dp)
                                    .clickable {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(idx)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = "Thumb ${idx + 1}",
                                            colorFilter = pageColorFilter,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) CtOrange else Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 3.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    if (isOriginalPageView) {
                        Color(0xFF334155)
                    } else {
                        when (pdfReadingTheme) {
                            1 -> Color(0xFFF4ECD8)
                            2 -> Color(0xFF0F172A)
                            else -> Color(0xFFE5E7EB)
                        }
                    }
                )
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CtOrange)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading PDF document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (pdfReadingTheme == 2 || isOriginalPageView) Color.White else Color.DarkGray
                        )
                    }
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.handleBackPress() }) {
                            Text("Go Back")
                        }
                    }
                }
            } else if (isOriginalPageView) {
                // ORIGINAL PAGE VIEW (Whole-Page Fitted Sheet in 100% Original PDF Colors & Aspect Ratio)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentPadding = PaddingValues(16.dp),
                        pageSpacing = 16.dp
                    ) { index ->
                        val bitmap = renderedPages[index]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                shadowElevation = 8.dp,
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFF94A3B8))
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Original Page ${index + 1}",
                                        colorFilter = null, // Always true original PDF colors
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .fillMaxHeight(0.8f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = CtOrange,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Page Navigation Controls in Original Page View
                    if (pageCount > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.82f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage > 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        }
                                    },
                                    enabled = pagerState.currentPage > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Previous Page",
                                        tint = if (pagerState.currentPage > 0) Color.White else Color.Gray
                                    )
                                }

                                Text(
                                    text = "Original Page ${currentPageIndex + 1} / $pageCount",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            jumpPageInput = "${currentPageIndex + 1}"
                                            showJumpPageDialog = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )

                                IconButton(
                                    onClick = {
                                        if (pagerState.currentPage < pageCount - 1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    },
                                    enabled = pagerState.currentPage < pageCount - 1,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next Page",
                                        tint = if (pagerState.currentPage < pageCount - 1) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(pageCount) { index ->
                            val bitmap = renderedPages[index]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 4.dp,
                                color = if (pdfReadingTheme == 2) Color(0xFF1E293B) else if (pdfReadingTheme == 1) Color(0xFFFBF0D9) else Color.White
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        colorFilter = pageColorFilter,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.FillWidth
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(480.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = CtOrange,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Interactive Jump-to-Page Pill
                if (pageCount > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .clip(CircleShape)
                            .clickable {
                                jumpPageInput = "${currentPageIndex + 1}"
                                showJumpPageDialog = true
                            },
                        color = Color.Black.copy(alpha = 0.78f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FindInPage, contentDescription = null, tint = CtOrange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Page ${currentPageIndex + 1} / $pageCount • Tap to Jump",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJumpPageDialog) {
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("Jump to Page (1 - $pageCount)") },
            text = {
                OutlinedTextField(
                    value = jumpPageInput,
                    onValueChange = { jumpPageInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Page Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pageNum = jumpPageInput.toIntOrNull()
                        if (pageNum != null && pageNum in 1..pageCount) {
                            coroutineScope.launch {
                                if (isOriginalPageView) {
                                    pagerState.scrollToPage(pageNum - 1)
                                } else {
                                    listState.scrollToItem(pageNum - 1)
                                }
                            }
                        }
                        showJumpPageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private suspend fun exportPdfPageAsImage(
    pageBitmap: Bitmap,
    pdfBaseName: String,
    pageNumber: Int
): File? = withContext(Dispatchers.IO) {
    try {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val outDir = File(picturesDir, "CentExplorer_PDF").apply { mkdirs() }
        val outFile = File(outDir, "${pdfBaseName}_page_${pageNumber}.jpg")
        FileOutputStream(outFile).use { fos ->
            pageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        outFile
    } catch (_: Exception) {
        null
    }
}

private suspend fun renderSinglePage(
    renderer: PdfRenderer,
    index: Int,
    targetMap: MutableMap<Int, Bitmap>
) = withContext(Dispatchers.IO) {
    try {
        synchronized(renderer) {
            val page = renderer.openPage(index)
            val scaleFactor = 1.8f
            val destWidth = (page.width * scaleFactor).toInt().coerceAtMost(1440)
            val destHeight = (page.height * scaleFactor).toInt().coerceAtMost(2160)

            val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            targetMap[index] = bitmap
        }
    } catch (_: Exception) {}
}
