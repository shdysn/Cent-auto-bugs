package com.ct.explorer.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.PrintHelper
import com.ct.explorer.ui.components.office.DocumentPreviewControlBar
import com.ct.explorer.ui.components.office.MsWordHorizontalRuler
import com.ct.explorer.ui.components.office.MsWordPageSheet
import com.ct.explorer.ui.components.office.MsWordRibbon
import com.ct.explorer.ui.components.office.MsWordStatusBar
import com.ct.explorer.utils.docx.DocxDocument
import com.ct.explorer.utils.docx.DocxElement
import com.ct.explorer.utils.docx.DocxPage
import com.ct.explorer.utils.docx.DocxPaginator
import com.ct.explorer.utils.docx.DocxParser
import com.ct.explorer.utils.docx.PageMargins
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val WordBlue = Color(0xFF2B579A)
val WordBlueLight = Color(0xFFDBEAFE)

enum class WordTheme(val title: String, val bg: Color, val cardBg: Color, val text: Color, val accent: Color) {
    PAPER("Crisp", Color(0xFFF1F5F9), Color(0xFFFFFFFF), Color(0xFF0F172A), Color(0xFF2563EB)),
    SEPIA("Sepia", Color(0xFFF4ECD8), Color(0xFFFBF0D9), Color(0xFF433422), Color(0xFFB45309)),
    DARK("Dark", Color(0xFF0B0F17), Color(0xFF1E293B), Color(0xFFF8FAFC), Color(0xFF60A5FA))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordViewerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.wordViewerState.collectAsStateWithLifecycle()
    val file = state.file

    var document by remember { mutableStateOf<DocxDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Physical Page & Paper Configuration (A4, Letter, Legal, Margins)
    var selectedPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var selectedOrientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var selectedMargins by remember { mutableStateOf(PageMargins.NORMAL) }
    var showRuler by remember { mutableStateOf(true) }
    var showPrintSetupDialog by remember { mutableStateOf(false) }
    var showPaperSizeSheet by remember { mutableStateOf(false) }

    // Fullscreen & Original Page View states (defaults to True for authentic A4/Letter/Legal print page layout)
    var isFullScreen by remember { mutableStateOf(true) }
    var isOriginalPageView by remember { mutableStateOf(true) }

    // Zoom & pan state for Original Page View
    var pageScale by remember { mutableFloatStateOf(1f) }
    var pageOffset by remember { mutableStateOf(Offset.Zero) }
    val pageTransformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        pageScale = (pageScale * zoomChange).coerceIn(0.75f, 3.0f)
        if (pageScale != 1f) {
            pageOffset += offsetChange
        } else {
            pageOffset = Offset.Zero
        }
    }

    // Reading preferences
    var readingTheme by remember { mutableStateOf(WordTheme.PAPER) }
    var fontSizeMultiplier by remember { mutableFloatStateOf(1f) } // 0.85f, 1f, 1.2f, 1.4f
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showOutlineSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val originalPageListState = rememberLazyListState()

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

    LaunchedEffect(file) {
        if (file == null || !file.exists()) {
            errorMessage = "Word document not found"
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            val result = DocxParser.parse(file)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val doc = result.getOrNull()
                    document = doc
                    if (doc != null) {
                        selectedPaperSize = doc.detectedPaperSize
                        selectedOrientation = doc.detectedOrientation
                    }
                    isLoading = false
                } else {
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to parse Word document"
                    isLoading = false
                }
            }
        }
    }

    val paginatedPages = remember(document, selectedPaperSize, selectedOrientation, fontSizeMultiplier, selectedMargins) {
        document?.let { doc ->
            DocxPaginator.paginate(doc, selectedPaperSize, selectedOrientation, fontSizeMultiplier, selectedMargins)
        } ?: emptyList()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("word_viewer_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MsWordRibbon(
                title = state.title.ifEmpty { file?.name ?: "Word Document" },
                wordCount = document?.wordCount ?: 0,
                selectedPaperSize = selectedPaperSize,
                selectedOrientation = selectedOrientation,
                selectedMargins = selectedMargins,
                isOriginalPageView = isOriginalPageView,
                showRuler = showRuler,
                fontSizeMultiplier = fontSizeMultiplier,
                onBackClick = { viewModel.handleBackPress() },
                onPaperSizeChange = { selectedPaperSize = it },
                onOrientationChange = { selectedOrientation = it },
                onMarginsChange = { selectedMargins = it },
                onTogglePageView = {
                    isOriginalPageView = !isOriginalPageView
                    pageScale = 1f
                    pageOffset = Offset.Zero
                },
                onToggleRuler = { showRuler = !showRuler },
                onFontSizeChange = { fontSizeMultiplier = it },
                onPrintClick = { showPrintSetupDialog = true },
                onShareClick = {
                    if (file != null) FileOpener.shareFile(context, FileItem(file))
                },
                onOpenExternalClick = {
                    if (file != null) FileOpener.openWithChooser(context, FileItem(file))
                }
            )
        },
        bottomBar = {
            if (isOriginalPageView && document != null) {
                MsWordStatusBar(
                    currentPage = ((originalPageListState.firstVisibleItemIndex + 1).coerceAtMost(paginatedPages.size.coerceAtLeast(1))),
                    totalPages = paginatedPages.size.coerceAtLeast(1),
                    wordCount = document?.wordCount ?: 0,
                    pageScale = pageScale,
                    isOriginalPageView = isOriginalPageView,
                    onTogglePageView = {
                        isOriginalPageView = !isOriginalPageView
                        pageScale = 1f
                        pageOffset = Offset.Zero
                    },
                    onZoomChange = { pageScale = it }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isOriginalPageView) Color(0xFFE2E8F0) else readingTheme.bg)
        ) {
            // Authentic Document Preview Control Bar (Matching user sample with A4, Letter, Legal and PDF print)
            if (isOriginalPageView && document != null) {
                DocumentPreviewControlBar(
                    title = state.title.ifEmpty { file?.name ?: "Word Document" },
                    documentType = "Word (.docx)",
                    selectedPaperSize = selectedPaperSize,
                    selectedOrientation = selectedOrientation,
                    pageCount = paginatedPages.size,
                    onPaperSizeChange = { selectedPaperSize = it },
                    onOrientationChange = { selectedOrientation = it },
                    onPrintClick = {
                        if (file != null) {
                            PrintHelper.printWordFile(context, file, selectedPaperSize, selectedOrientation)
                        } else {
                            showPrintSetupDialog = true
                        }
                    },
                    accentColor = Color(0xFF2563EB)
                )
            }

            // Horizontal Ruler directly below Ribbon in Page View
            if (isOriginalPageView && showRuler) {
                MsWordHorizontalRuler(
                    paperSize = selectedPaperSize,
                    orientation = selectedOrientation,
                    margins = selectedMargins
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = WordBlue)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading Word document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOriginalPageView) Color(0xFF1E293B) else readingTheme.text
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Cannot Preview Document",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        if (file != null) FileOpener.openWithChooser(context, FileItem(file))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WordBlue)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open in Office")
                                }
                                OutlinedButton(onClick = { viewModel.handleBackPress() }) {
                                    Text("Back")
                                }
                            }
                        }
                    }
                }
            } else {
                val doc = document
                if (doc == null || doc.elements.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Document is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = readingTheme.text.copy(alpha = 0.6f)
                        )
                    }
                } else if (isOriginalPageView) {
                    // AUTHENTIC MICROSOFT WORD PAGE VIEW (A4, Letter, Legal with exact aspect ratio)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(state = pageTransformState)
                    ) {
                        LazyColumn(
                            state = originalPageListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = pageScale,
                                    scaleY = pageScale,
                                    translationX = pageOffset.x,
                                    translationY = pageOffset.y
                                ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(paginatedPages) { pageIdx, page ->
                                MsWordPageSheet(
                                    page = page,
                                    documentTitle = doc.title,
                                    fontSizeMultiplier = fontSizeMultiplier,
                                    searchQuery = searchQuery,
                                    modifier = Modifier.widthIn(max = if (selectedOrientation == PageOrientation.PORTRAIT) 620.dp else 840.dp)
                                )
                            }
                        }

                        if (pageScale != 1f) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .clickable {
                                        pageScale = 1f
                                        pageOffset = Offset.Zero
                                    }
                            ) {
                                Text(
                                    text = "Reset Zoom (${(pageScale * 100).toInt()}%)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Document Reader Block Container
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            // Document Header Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = readingTheme.cardBg,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = readingTheme.text
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "${doc.wordCount} words",
                                            fontSize = 12.sp,
                                            color = readingTheme.text.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "•",
                                            fontSize = 12.sp,
                                            color = readingTheme.text.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = "${doc.characterCount} characters",
                                            fontSize = 12.sp,
                                            color = readingTheme.text.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "•",
                                            fontSize = 12.sp,
                                            color = readingTheme.text.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = "${doc.elements.size} blocks",
                                            fontSize = 12.sp,
                                            color = readingTheme.text.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(doc.elements) { _, element ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = readingTheme.cardBg,
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    when (element) {
                                        is DocxElement.Heading -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(24.dp)
                                                        .background(readingTheme.accent, RoundedCornerShape(2.dp))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = element.text,
                                                    style = when (element.level) {
                                                        1 -> MaterialTheme.typography.titleLarge
                                                        2 -> MaterialTheme.typography.titleMedium
                                                        else -> MaterialTheme.typography.titleSmall
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = (when (element.level) {
                                                        1 -> 22.sp
                                                        2 -> 18.sp
                                                        else -> 16.sp
                                                    }) * fontSizeMultiplier,
                                                    color = readingTheme.text
                                                )
                                            }
                                        }

                                        is DocxElement.Paragraph -> {
                                            val annotated = buildAnnotatedString {
                                                if (element.isBullet) {
                                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = readingTheme.accent)) {
                                                        append("•   ")
                                                    }
                                                }
                                                if (element.runs.isNotEmpty()) {
                                                    for (run in element.runs) {
                                                        val isMatch = searchQuery.isNotBlank() &&
                                                                run.text.contains(searchQuery, ignoreCase = true)
                                                        withStyle(
                                                            SpanStyle(
                                                                fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                                                                fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                                textDecoration = if (run.isUnderline) TextDecoration.Underline else null,
                                                                background = if (isMatch) Color(0xFFFEF08A) else Color.Transparent,
                                                                color = if (isMatch) Color.Black else readingTheme.text
                                                            )
                                                        ) {
                                                            append(run.text)
                                                        }
                                                    }
                                                } else {
                                                    append(element.fullText)
                                                }
                                            }

                                            Text(
                                                text = annotated,
                                                fontSize = 15.sp * fontSizeMultiplier,
                                                lineHeight = (22.sp * fontSizeMultiplier),
                                                color = readingTheme.text
                                            )
                                        }

                                        is DocxElement.Table -> {
                                            WordTableComponent(
                                                table = element,
                                                readingTheme = readingTheme,
                                                fontSizeMultiplier = fontSizeMultiplier
                                            )
                                        }

                                        DocxElement.Divider -> {
                                            HorizontalDivider(
                                                color = readingTheme.text.copy(alpha = 0.15f),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }

                                        DocxElement.PageBreak -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                HorizontalDivider(
                                                    modifier = Modifier.weight(1f),
                                                    color = readingTheme.accent.copy(alpha = 0.3f)
                                                )
                                                Text(
                                                    text = " PAGE BREAK ",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = readingTheme.accent.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                                HorizontalDivider(
                                                    modifier = Modifier.weight(1f),
                                                    color = readingTheme.accent.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }

    // Outline / Table of Contents Bottom Sheet
    if (showOutlineSheet) {
        val headings = document?.elements?.filterIsInstance<DocxElement.Heading>().orEmpty()
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = WordBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Document Outline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    itemsIndexed(headings) { index, heading ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    val elementIndex = document?.elements?.indexOf(heading) ?: 0
                                    coroutineScope.launch {
                                        if (isOriginalPageView) {
                                            originalPageListState.animateScrollToItem(elementIndex / 28)
                                        } else {
                                            listState.animateScrollToItem(elementIndex + 1)
                                        }
                                    }
                                    showOutlineSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WordBlue,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = heading.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Print & Page Setup Dialog (A4, Letter, Legal & Orientation for Android Print Spooler)
    if (showPrintSetupDialog && file != null && document != null) {
        PrintPageSetupDialog(
            doc = document!!,
            initialPaperSize = selectedPaperSize,
            initialOrientation = selectedOrientation,
            totalPages = paginatedPages.size,
            onDismiss = { showPrintSetupDialog = false },
            onConfirmPrint = { paperSize, orientation ->
                selectedPaperSize = paperSize
                selectedOrientation = orientation
                showPrintSetupDialog = false
                com.ct.explorer.utils.PrintHelper.printWordFile(
                    context = context,
                    file = file,
                    paperSize = paperSize,
                    orientation = orientation
                )
            }
        )
    }

    // Quick Paper Size & Orientation Sheet
    if (showPaperSizeSheet) {
        PaperSizeSelectionSheet(
            selectedPaperSize = selectedPaperSize,
            selectedOrientation = selectedOrientation,
            onSelectPaperSize = { selectedPaperSize = it },
            onSelectOrientation = { selectedOrientation = it },
            onDismiss = { showPaperSizeSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintPageSetupDialog(
    doc: DocxDocument,
    initialPaperSize: PaperSize,
    initialOrientation: PageOrientation,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirmPrint: (PaperSize, PageOrientation) -> Unit
) {
    var chosenPaperSize by remember { mutableStateOf(initialPaperSize) }
    var chosenOrientation by remember { mutableStateOf(initialOrientation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = WordBlue,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Print & Page Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Native Android Print & Save to PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Paper Size (A4, Letter, Legal)
                Text(
                    text = "Paper Standard",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                PaperSize.values().forEach { size ->
                    val isSelected = chosenPaperSize == size
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) WordBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) WordBlue else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { chosenPaperSize = size }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { chosenPaperSize = size },
                                    colors = RadioButtonDefaults.colors(selectedColor = WordBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = size.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = size.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = WordBlue,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        text = "SELECTED",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Section 2: Orientation
                Text(
                    text = "Page Orientation",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PageOrientation.values().forEach { orient ->
                        val isSelected = chosenOrientation == orient
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) WordBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) WordBlue else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { chosenOrientation = orient }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (orient == PageOrientation.PORTRAIT)
                                        Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                                    contentDescription = null,
                                    tint = if (isSelected) WordBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = orient.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) WordBlue else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Summary Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Print Summary",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Format: ${chosenPaperSize.title} (${chosenOrientation.title})\nDimensions: ${chosenPaperSize.subtitle.substringBefore(" •")}\nTotal Pages: ~$totalPages pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPrint(chosenPaperSize, chosenOrientation) },
                colors = ButtonDefaults.buttonColors(containerColor = WordBlue)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print / Save as PDF")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaperSizeSelectionSheet(
    selectedPaperSize: PaperSize,
    selectedOrientation: PageOrientation,
    onSelectPaperSize: (PaperSize) -> Unit,
    onSelectOrientation: (PageOrientation) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = WordBlue)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Paper Size & Margins",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Select physical sheet format for view and print:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperSize.values().forEach { size ->
                    val isSelected = selectedPaperSize == size
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) WordBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) WordBlue else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectPaperSize(size)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = size.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (size == PaperSize.A4) {
                                        Surface(
                                            color = WordBlueLight,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "STANDARD",
                                                color = WordBlue,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = size.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = WordBlue)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Orientation
            Text(
                text = "Orientation",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PageOrientation.values().forEach { orient ->
                    val isSelected = selectedOrientation == orient
                    OutlinedButton(
                        onClick = { onSelectOrientation(orient) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) WordBlue.copy(alpha = 0.1f) else Color.Transparent
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) WordBlue else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (orient == PageOrientation.PORTRAIT)
                                Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                            contentDescription = null,
                            tint = if (isSelected) WordBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = orient.title,
                            color = if (isSelected) WordBlue else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

private fun parseDocxHexColor(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    return try {
        val rgb = clean.toLong(16)
        Color(0xFF000000L or rgb)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun OriginalPageTableComponent(
    table: DocxElement.Table,
    fontSizeMultiplier: Float
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .horizontalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier.border(1.dp, Color(0xFF94A3B8))
        ) {
            table.rows.forEachIndexed { rowIndex, row ->
                val isHeader = rowIndex == 0
                Row(
                    modifier = Modifier.background(
                        if (isHeader) Color(0xFFEFF6FF)
                        else if (rowIndex % 2 == 1) Color(0xFFF8FAFC)
                        else Color.White
                    )
                ) {
                    row.forEach { cellText ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 100.dp, max = 220.dp)
                                .border(0.5.dp, Color(0xFFCBD5E1))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cellText,
                                fontSize = 13.sp * fontSizeMultiplier,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordTableComponent(
    table: DocxElement.Table,
    readingTheme: WordTheme,
    fontSizeMultiplier: Float
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Table (${table.rows.size} rows)",
                style = MaterialTheme.typography.labelMedium,
                color = readingTheme.accent,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = {
                    val tsv = table.rows.joinToString("\n") { it.joinToString("\t") }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Table Data", tsv))
                    Toast.makeText(context, "Table copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Table", fontSize = 11.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, readingTheme.text.copy(alpha = 0.2f)),
            color = readingTheme.cardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                Column {
                    table.rows.forEachIndexed { rowIndex, row ->
                        val isHeader = rowIndex == 0
                        Row(
                            modifier = Modifier
                                .background(
                                    if (isHeader) readingTheme.accent.copy(alpha = 0.12f)
                                    else if (rowIndex % 2 == 1) readingTheme.text.copy(alpha = 0.04f)
                                    else Color.Transparent
                                )
                        ) {
                            row.forEach { cellText ->
                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 100.dp, max = 220.dp)
                                        .border(0.5.dp, readingTheme.text.copy(alpha = 0.12f))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = cellText,
                                        fontSize = 13.sp * fontSizeMultiplier,
                                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                        color = readingTheme.text
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
