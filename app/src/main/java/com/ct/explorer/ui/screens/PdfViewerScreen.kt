package com.ct.explorer.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val coroutineScope = rememberCoroutineScope()
    val pdfState by viewModel.pdfViewerState.collectAsStateWithLifecycle()
    val file = pdfState.file

    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val renderedPages = remember { mutableStateMapOf<Int, Bitmap>() }

    // PDF Pro Toolkit States
    var isNightInvertMode by remember { mutableStateOf(false) }
    var showThumbnailBar by remember { mutableStateOf(true) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // ColorMatrix for Night / Inverted Reading Mode
    val invertColorFilter = remember(isNightInvertMode) {
        if (isNightInvertMode) {
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
        } else null
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

    LaunchedEffect(listState.firstVisibleItemIndex) {
        currentPageIndex = listState.firstVisibleItemIndex
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
            // Evict pages far from viewport to bound memory usage
            val toEvict = renderedPages.keys.filter { it < currentPageIndex - 4 || it > currentPageIndex + 5 }
            for (k in toEvict) {
                val bmp = renderedPages.remove(k)
                if (bmp != null) {
                    try { if (!bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
                }
            }

            // Render pages in active viewing range
            val start = (currentPageIndex - 2).coerceAtLeast(0)
            val end = (currentPageIndex + 3).coerceAtMost(pageCount - 1)
            for (idx in start..end) {
                if (!renderedPages.containsKey(idx)) {
                    renderSinglePage(renderer, idx, renderedPages)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("pdf_viewer_screen"),
        topBar = {
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
                            Text(
                                text = "Page ${currentPageIndex + 1} of $pageCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.handleBackPress() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (scale > 1f) {
                        IconButton(onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        }) {
                            Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom")
                        }
                    }

                    // Night Reading Mode Toggle
                    IconButton(onClick = { isNightInvertMode = !isNightInvertMode }) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = "Night Reading Mode",
                            tint = if (isNightInvertMode) CtOrange else MaterialTheme.colorScheme.onSurface
                        )
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
                    IconButton(onClick = { showThumbnailBar = !showThumbnailBar }) {
                        Icon(
                            imageVector = Icons.Default.ViewCarousel,
                            contentDescription = "Page Thumbnails",
                            tint = if (showThumbnailBar) CtOrange else MaterialTheme.colorScheme.onSurface
                        )
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
        },
        bottomBar = {
            AnimatedVisibility(visible = showThumbnailBar && pageCount > 1 && !isLoading) {
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
                                            colorFilter = invertColorFilter,
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
                .background(if (isNightInvertMode) Color(0xFF0F172A) else Color(0xFFE5E7EB))
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CtOrange)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading PDF document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isNightInvertMode) Color.White else Color.DarkGray
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
                                color = if (isNightInvertMode) Color(0xFF1E293B) else Color.White
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        colorFilter = invertColorFilter,
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
                                listState.scrollToItem(pageNum - 1)
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
            val scaleFactor = 1.3f
            val destWidth = (page.width * scaleFactor).toInt().coerceAtMost(1080)
            val destHeight = (page.height * scaleFactor).toInt().coerceAtMost(1600)

            val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            targetMap[index] = bitmap
        }
    } catch (_: Exception) {}
}
