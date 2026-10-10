package com.ct.explorer.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.ui.components.office.DocumentPreviewControlBar
import com.ct.explorer.ui.components.office.MsTextPageSheet
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.PrintHelper
import com.ct.explorer.utils.docx.PageMargins
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize
import com.ct.explorer.utils.text.TextPage
import com.ct.explorer.utils.text.TextPaginator

enum class EditorViewMode {
    CODE_ONLY,
    SPLIT_VIEW,
    CHROMIUM_PREVIEW,
    PAGE_VIEW
}

enum class DeviceViewport(val title: String, val widthDp: Int?) {
    RESPONSIVE("Responsive", null),
    MOBILE("Mobile (375px)", 375),
    TABLET("Tablet (768px)", 768)
}

data class ConsoleLogItem(
    val message: String,
    val lineNumber: Int,
    val level: ConsoleMessage.MessageLevel
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.textEditorState.collectAsStateWithLifecycle()

    // TextField state with cursor tracking for quick snippet insertion
    var textFieldValue by remember(state.content) {
        mutableStateOf(TextFieldValue(state.content, TextRange(state.content.length)))
    }

    // Keep state synchronized with TextField
    LaunchedEffect(state.content) {
        if (state.content != textFieldValue.text) {
            textFieldValue = TextFieldValue(state.content, TextRange(state.content.length))
        }
    }

    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    // Mode: Code, Split, Preview, Page View
    var viewMode by remember(state.isHtmlMode) {
        mutableStateOf(if (state.isHtmlMode) EditorViewMode.SPLIT_VIEW else EditorViewMode.CODE_ONLY)
    }

    // Physical Paper & Print Setup (A4, Letter, Legal)
    var selectedPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var selectedOrientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var selectedMargins by remember { mutableStateOf(PageMargins.NORMAL) }
    var showPrintSetupDialog by remember { mutableStateOf(false) }

    val paginatedTextPages: List<TextPage> = remember(textFieldValue.text, selectedPaperSize, selectedOrientation, selectedMargins) {
        TextPaginator.paginate(textFieldValue.text, selectedPaperSize, selectedOrientation, selectedMargins)
    }

    // Zoom & pan state for Physical Page View
    var pageScale by remember { mutableFloatStateOf(1f) }
    var pageOffset by remember { mutableStateOf(Offset.Zero) }
    val pageTransformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        pageScale = (pageScale * zoomChange).coerceIn(0.65f, 3.0f)
        if (pageScale != 1f) {
            pageOffset += offsetChange
        } else {
            pageOffset = Offset.Zero
        }
    }
    val pageListState = rememberLazyListState()

    var selectedViewport by remember { mutableStateOf(DeviceViewport.RESPONSIVE) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var webProgress by remember { mutableIntStateOf(100) }
    var consoleLogs by remember { mutableStateOf<List<ConsoleLogItem>>(emptyList()) }
    var showConsoleSheet by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.handleBackPress()
    }

    val displayTitle = state.title.ifEmpty { state.file?.name ?: "Editor" }

    Scaffold(
        modifier = modifier.testTag("text_editor_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.isHtmlMode) {
                                Surface(
                                    color = Color(0xFFE44D26), // HTML5 Official Brand Orange
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "HTML5",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                            if (state.isModified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = CtOrange.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Edited",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CtOrange,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (state.isHtmlMode) {
                                when (viewMode) {
                                    EditorViewMode.CODE_ONLY -> "Source Code (${state.lineCount} lines)"
                                    EditorViewMode.SPLIT_VIEW -> "Live Chromium Split (${state.lineCount} lines)"
                                    EditorViewMode.CHROMIUM_PREVIEW -> "Chromium Web Render"
                                    EditorViewMode.PAGE_VIEW -> "Page View • ${paginatedTextPages.size} pages (${selectedPaperSize.title})"
                                }
                            } else {
                                if (viewMode == EditorViewMode.PAGE_VIEW) {
                                    "Physical Page View • ${paginatedTextPages.size} pages (${selectedPaperSize.title} • ${selectedOrientation.title})"
                                } else {
                                    "${state.lineCount} lines • ${state.charCount} characters"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("text_editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // View Mode Switcher Tabs
                    if (state.isHtmlMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ViewModeTabButton(
                                    icon = Icons.Default.Code,
                                    label = "Code",
                                    selected = viewMode == EditorViewMode.CODE_ONLY,
                                    onClick = { viewMode = EditorViewMode.CODE_ONLY }
                                )
                                ViewModeTabButton(
                                    icon = Icons.Default.VerticalSplit,
                                    label = "Split",
                                    selected = viewMode == EditorViewMode.SPLIT_VIEW,
                                    onClick = { viewMode = EditorViewMode.SPLIT_VIEW }
                                )
                                ViewModeTabButton(
                                    icon = Icons.Default.Language,
                                    label = "Live",
                                    selected = viewMode == EditorViewMode.CHROMIUM_PREVIEW,
                                    onClick = { viewMode = EditorViewMode.CHROMIUM_PREVIEW }
                                )
                                ViewModeTabButton(
                                    icon = Icons.Default.Description,
                                    label = "Pages",
                                    selected = viewMode == EditorViewMode.PAGE_VIEW,
                                    onClick = { viewMode = EditorViewMode.PAGE_VIEW }
                                )
                            }
                        }
                    } else {
                        // Regular Text / Code / Markdown files: Editor vs Physical Page View
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ViewModeTabButton(
                                    icon = Icons.Default.Edit,
                                    label = "Edit",
                                    selected = viewMode != EditorViewMode.PAGE_VIEW,
                                    onClick = { viewMode = EditorViewMode.CODE_ONLY }
                                )
                                ViewModeTabButton(
                                    icon = Icons.Default.Description,
                                    label = "Pages",
                                    selected = viewMode == EditorViewMode.PAGE_VIEW,
                                    onClick = { viewMode = EditorViewMode.PAGE_VIEW }
                                )
                            }
                        }
                    }

                    // Refresh / Live Run button for HTML
                    if (state.isHtmlMode && viewMode != EditorViewMode.CODE_ONLY && viewMode != EditorViewMode.PAGE_VIEW) {
                        IconButton(onClick = { reloadTrigger++ }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload Chromium Preview",
                                tint = CtOrange
                            )
                        }

                        // Open in External Browser
                        IconButton(onClick = {
                            val activeFile = state.file
                            if (activeFile != null) {
                                FileOpener.openWithChooser(context, FileItem(activeFile))
                            } else {
                                Toast.makeText(context, "Save file to open in browser", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in Chrome / Browser",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Word Wrap Toggle (when editor is visible)
                    if (viewMode == EditorViewMode.CODE_ONLY || viewMode == EditorViewMode.SPLIT_VIEW) {
                        IconButton(
                            onClick = { viewModel.toggleEditorWordWrap() },
                            modifier = Modifier.testTag("text_editor_wrap_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.WrapText,
                                contentDescription = "Toggle Wrap",
                                tint = if (state.wordWrap) CtOrange else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Print / Save as PDF
                    IconButton(
                        onClick = { showPrintSetupDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print / Save as PDF",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Save Button
                    Button(
                        onClick = { viewModel.saveEditorFile() },
                        enabled = !state.isSaving && !state.isReadOnly,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isReadOnly) MaterialTheme.colorScheme.surfaceVariant else CtOrange,
                            contentColor = if (state.isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("text_editor_save_button")
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else if (state.isReadOnly) {
                            Text("Read Only", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (viewMode == EditorViewMode.PAGE_VIEW) Color(0xFFE2E8F0) else Color(0xFF13141F))
        ) {
            // Document Preview Control Bar (Matching user sample with A4, Letter, Legal and PDF print)
            if (viewMode == EditorViewMode.PAGE_VIEW) {
                DocumentPreviewControlBar(
                    title = displayTitle,
                    documentType = if (state.isHtmlMode) "HTML" else "Text / Code",
                    selectedPaperSize = selectedPaperSize,
                    selectedOrientation = selectedOrientation,
                    pageCount = paginatedTextPages.size,
                    onPaperSizeChange = { selectedPaperSize = it },
                    onOrientationChange = { selectedOrientation = it },
                    onPrintClick = {
                        val activeFile = state.file
                        if (activeFile != null) {
                            PrintHelper.printTextFile(context, activeFile, selectedPaperSize, selectedOrientation)
                        } else {
                            PrintHelper.printTextContent(context, displayTitle, textFieldValue.text, displayTitle, selectedPaperSize, selectedOrientation)
                        }
                    },
                    accentColor = Color(0xFF2563EB)
                )
            }

            // HTML Snippet Toolbar (Only when editor is active)
            if (state.isHtmlMode && viewMode != EditorViewMode.CHROMIUM_PREVIEW && viewMode != EditorViewMode.PAGE_VIEW) {
                HtmlSnippetToolbar(
                    onInsertSnippet = { snippet, cursorOffset ->
                        val currentText = textFieldValue.text
                        val selection = textFieldValue.selection
                        val start = selection.min
                        val end = selection.max

                        val newText = currentText.substring(0, start) + snippet + currentText.substring(end)
                        val newCursor = start + (cursorOffset ?: snippet.length)
                        textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                        viewModel.updateEditorContent(newText)
                    },
                    onInsertBoilerplate = {
                        val boilerplate = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${state.title.ifEmpty { "HTML Preview" }}</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      margin: 0;
      padding: 24px;
      background: #f8fafc;
      color: #0f172a;
    }
    .card {
      background: #ffffff;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
      margin-top: 16px;
    }
    h1 { color: #2563eb; margin-top: 0; }
    button {
      background: #2563eb;
      color: white;
      border: none;
      padding: 10px 18px;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
    }
  </style>
</head>
<body>
  <h1>⚡ Cent Chromium Preview</h1>
  <p>Live, responsive modern HTML5 and CSS3 preview.</p>
  <div class="card">
    <p>Tap the button below to test JavaScript interactivity:</p>
    <button onclick="alert('Hello from Chromium Engine!')">Interactive Alert</button>
  </div>
</body>
</html>
                        """.trimIndent()
                        textFieldValue = TextFieldValue(boilerplate, TextRange(boilerplate.length))
                        viewModel.updateEditorContent(boilerplate)
                    }
                )
            }

            // Main Editor & Render Display based on viewMode
            when {
                // 1. FULL PREVIEW MODE
                viewMode == EditorViewMode.CHROMIUM_PREVIEW && state.isHtmlMode -> {
                    ChromiumPreviewContainer(
                        htmlContent = textFieldValue.text,
                        file = state.file,
                        reloadTrigger = reloadTrigger,
                        viewport = selectedViewport,
                        onViewportChange = { selectedViewport = it },
                        webProgress = webProgress,
                        onProgressChange = { webProgress = it },
                        consoleLogs = consoleLogs,
                        onNewConsoleLog = { consoleLogs = consoleLogs + it },
                        onOpenConsole = { showConsoleSheet = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. SPLIT VIEW MODE (Top: Code Editor, Bottom: Live Chromium)
                viewMode == EditorViewMode.SPLIT_VIEW && state.isHtmlMode -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Half: Code Editor
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            CodeEditorComponent(
                                textFieldValue = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                    viewModel.updateEditorContent(it.text)
                                },
                                wordWrap = state.wordWrap,
                                scrollState = scrollState,
                                hScrollState = hScrollState,
                                lineCount = state.lineCount
                            )
                        }

                        // Divider with live status pill
                        Surface(
                            color = Color(0xFF1E2030),
                            border = BorderStroke(1.dp, Color(0xFF2A2D45)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CHROMIUM LIVE RENDER",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                TextButton(
                                    onClick = { reloadTrigger++ },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp), tint = CtOrange)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live Sync", fontSize = 11.sp, color = CtOrange, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Bottom Half: Live Chromium WebView
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            ChromiumPreviewContainer(
                                htmlContent = textFieldValue.text,
                                file = state.file,
                                reloadTrigger = reloadTrigger,
                                viewport = selectedViewport,
                                onViewportChange = { selectedViewport = it },
                                webProgress = webProgress,
                                onProgressChange = { webProgress = it },
                                consoleLogs = consoleLogs,
                                onNewConsoleLog = { consoleLogs = consoleLogs + it },
                                onOpenConsole = { showConsoleSheet = true },
                                isCompact = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // 3. PAGE VIEW MODE (Authentic A4, Letter, Legal physical sheets)
                viewMode == EditorViewMode.PAGE_VIEW -> {
                    TextOriginalPageView(
                        pages = paginatedTextPages,
                        fileName = displayTitle,
                        paperSize = selectedPaperSize,
                        orientation = selectedOrientation,
                        pageScale = pageScale,
                        pageOffset = pageOffset,
                        pageTransformState = pageTransformState,
                        listState = pageListState,
                        onResetZoom = {
                            pageScale = 1f
                            pageOffset = Offset.Zero
                        }
                    )
                }

                // 4. CODE ONLY MODE (Default for plain text or full screen code editing)
                else -> {
                    CodeEditorComponent(
                        textFieldValue = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.updateEditorContent(it.text)
                        },
                        wordWrap = state.wordWrap,
                        scrollState = scrollState,
                        hScrollState = hScrollState,
                        lineCount = state.lineCount
                    )
                }
            }
        }
    }

    // Console Logs Bottom Sheet
    if (showConsoleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConsoleSheet = false },
            containerColor = Color(0xFF1E1E2E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = CtOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chromium Console Logs (${consoleLogs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    if (consoleLogs.isNotEmpty()) {
                        TextButton(onClick = { consoleLogs = emptyList() }) {
                            Text("Clear", color = Color(0xFF94A3B8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (consoleLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No JavaScript errors or console logs.", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        consoleLogs.forEach { log ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (log.level) {
                                    ConsoleMessage.MessageLevel.ERROR -> Color(0xFF3B1219)
                                    ConsoleMessage.MessageLevel.WARNING -> Color(0xFF38280B)
                                    else -> Color(0xFF181825)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Line ${log.lineNumber}:",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (log.level) {
                                            ConsoleMessage.MessageLevel.ERROR -> Color(0xFFF87171)
                                            ConsoleMessage.MessageLevel.WARNING -> Color(0xFFFBBF24)
                                            else -> Color(0xFF60A5FA)
                                        },
                                        modifier = Modifier.width(65.dp)
                                    )
                                    Text(
                                        text = log.message,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Print & Page Setup Dialog (A4, Letter, Legal & Orientation for Android Print Spooler)
    if (showPrintSetupDialog) {
        PrintTextSetupDialog(
            fileName = displayTitle,
            totalLines = state.lineCount,
            initialPaperSize = selectedPaperSize,
            initialOrientation = selectedOrientation,
            totalPages = paginatedTextPages.size,
            onDismiss = { showPrintSetupDialog = false },
            onConfirmPrint = { paperSize, orientation ->
                selectedPaperSize = paperSize
                selectedOrientation = orientation
                showPrintSetupDialog = false
                com.ct.explorer.utils.PrintHelper.printTextContent(
                    context = context,
                    jobTitle = displayTitle,
                    text = textFieldValue.text,
                    fileName = state.file?.name ?: displayTitle,
                    paperSize = paperSize,
                    orientation = orientation
                )
            }
        )
    }
}

@Composable
private fun TextOriginalPageView(
    pages: List<TextPage>,
    fileName: String,
    paperSize: PaperSize,
    orientation: PageOrientation,
    pageScale: Float,
    pageOffset: Offset,
    pageTransformState: androidx.compose.foundation.gestures.TransformableState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onResetZoom: () -> Unit
) {
    val maxPageWidth = if (orientation == PageOrientation.PORTRAIT) 640.dp else 860.dp
    val minPageHeight = if (orientation == PageOrientation.PORTRAIT) {
        when (paperSize) {
            PaperSize.A4 -> 860.dp
            PaperSize.LETTER -> 800.dp
            PaperSize.LEGAL -> 1020.dp
        }
    } else {
        when (paperSize) {
            PaperSize.A4 -> 590.dp
            PaperSize.LETTER -> 610.dp
            PaperSize.LEGAL -> 610.dp
        }
    }

    if (pages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Document is empty",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0))
            .transformable(state = pageTransformState)
    ) {
        LazyColumn(
            state = listState,
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
            itemsIndexed(pages) { pageIdx, page ->
                MsTextPageSheet(
                    page = page,
                    title = fileName,
                    modifier = Modifier.widthIn(max = maxPageWidth)
                )
            }
        }

        // Reset Zoom Button
        if (pageScale != 1f) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clickable { onResetZoom() }
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
}

@Composable
private fun PrintTextSetupDialog(
    fileName: String,
    totalLines: Int,
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
                    color = CtOrange,
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
                // Section 1: Paper Standard (A4, Letter, Legal)
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
                        color = if (isSelected) CtOrange.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) CtOrange else MaterialTheme.colorScheme.outlineVariant
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
                                    colors = RadioButtonDefaults.colors(selectedColor = CtOrange)
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
                                    color = CtOrange,
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

                // Section 2: Page Orientation
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
                            color = if (isSelected) CtOrange.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) CtOrange else MaterialTheme.colorScheme.outlineVariant
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
                                    tint = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = orient.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (orient == PageOrientation.PORTRAIT) {
                                        Text(
                                            text = "Best for Text",
                                            fontSize = 9.sp,
                                            color = CtOrange,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
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
                            text = "File: $fileName\nFormat: ${chosenPaperSize.title} (${chosenOrientation.title})\nTotal Lines: $totalLines lines\nEstimated Pages: ~$totalPages pages",
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
                colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
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

@Composable
private fun ViewModeTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(if (selected) CtOrange else Color.Transparent, label = "TabBg")
    val contentColor by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "TabContent")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = contentColor)
        }
    }
}

@Composable
private fun HtmlSnippetToolbar(
    onInsertSnippet: (String, Int?) -> Unit,
    onInsertBoilerplate: () -> Unit
) {
    val snippets = listOf(
        SnippetItem("<div>", "<div>\n    \n</div>", 10),
        SnippetItem("<p>", "<p></p>", 3),
        SnippetItem("<h1>", "<h1></h1>", 4),
        SnippetItem("<span>", "<span></span>", 6),
        SnippetItem("<a>", "<a href=\"\"></a>", 9),
        SnippetItem("<button>", "<button></button>", 8),
        SnippetItem("<img>", "<img src=\"\" alt=\"\" />", 10),
        SnippetItem("<ul>", "<ul>\n    <li></li>\n</ul>", 13),
        SnippetItem("<table>", "<table border=\"1\">\n    <tr>\n        <td></td>\n    </tr>\n</table>", 37),
        SnippetItem("<style>", "<style>\n    \n</style>", 12),
        SnippetItem("<script>", "<script>\n    \n</script>", 13),
        SnippetItem("class=\"\"", "class=\"\"", 7),
        SnippetItem("id=\"\"", "id=\"\"", 4)
    )

    Surface(
        color = Color(0xFF1A1C29),
        border = BorderStroke(0.5.dp, Color(0xFF2A2D45)),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE44D26).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFFE44D26).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onInsertBoilerplate() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFFE44D26), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HTML5 Template", color = Color(0xFFFF8A65), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(snippets) { item ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF25283B),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onInsertSnippet(item.code, item.cursorOffset) }
                ) {
                    Text(
                        text = item.label,
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private data class SnippetItem(
    val label: String,
    val code: String,
    val cursorOffset: Int? = null
)

@Composable
private fun CodeEditorComponent(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    wordWrap: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    hScrollState: androidx.compose.foundation.ScrollState,
    lineCount: Int
) {
    val boundedLineCount = lineCount.coerceIn(1, 3000)
    val lineNumbersText = remember(boundedLineCount) {
        (1..boundedLineCount).joinToString("\n")
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(Color(0xFF13141F))
    ) {
        // Line numbers gutter
        Text(
            text = lineNumbersText,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = Color(0xFF475569)
            ),
            modifier = Modifier
                .background(Color(0xFF181A28))
                .border(BorderStroke(0.5.dp, Color(0xFF23263B)))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )

        val editorModifier = if (wordWrap) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .horizontalScroll(hScrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = Color(0xFFF1F5F9)
            ),
            cursorBrush = SolidColor(CtOrange),
            modifier = editorModifier.testTag("text_editor_field")
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChromiumPreviewContainer(
    htmlContent: String,
    file: java.io.File?,
    reloadTrigger: Int,
    viewport: DeviceViewport,
    onViewportChange: (DeviceViewport) -> Unit,
    webProgress: Int,
    onProgressChange: (Int) -> Unit,
    consoleLogs: List<ConsoleLogItem>,
    onNewConsoleLog: (ConsoleLogItem) -> Unit,
    onOpenConsole: () -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = modifier.background(Color(0xFF0F111A))) {
        // Chromium Status & Controls Bar
        Surface(
            color = Color(0xFF181A28),
            border = BorderStroke(0.5.dp, Color(0xFF262942)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CHROMIUM",
                            color = Color(0xFF10B981),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (webProgress in 1..99) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            color = CtOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Middle: Viewport Switcher (if not compact)
                if (!isCompact) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeviceViewport.values().forEach { vp ->
                            val isSelected = viewport == vp
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) CtOrange.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, CtOrange) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onViewportChange(vp) }
                            ) {
                                Text(
                                    text = vp.title.substringBefore(" "),
                                    color = if (isSelected) CtOrange else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Right: Console trigger
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (consoleLogs.any { it.level == ConsoleMessage.MessageLevel.ERROR }) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF334155),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenConsole() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "Console", tint = Color.White, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${consoleLogs.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Web Loading Progress Line
        if (webProgress in 1..99) {
            LinearProgressIndicator(
                progress = { webProgress / 100f },
                color = CtOrange,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        // Chromium Render Box (With viewport simulation if Mobile/Tablet chosen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (viewport.widthDp != null) Color(0xFF0A0C14) else Color.White),
            contentAlignment = Alignment.TopCenter
        ) {
            val viewportModifier = if (viewport.widthDp != null) {
                Modifier
                    .width(viewport.widthDp.dp)
                    .fillMaxHeight()
                    .padding(vertical = 10.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
            } else {
                Modifier.fillMaxSize()
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowFileAccess = true
                            allowContentAccess = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                onProgressChange(20)
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                onProgressChange(100)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                onProgressChange(newProgress)
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                onNewConsoleLog(
                                    ConsoleLogItem(
                                        message = consoleMessage.message() ?: "",
                                        lineNumber = consoleMessage.lineNumber(),
                                        level = consoleMessage.messageLevel() ?: ConsoleMessage.MessageLevel.LOG
                                    )
                                )
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }

                        val baseUrl = file?.parentFile?.toURI()?.toString() ?: "file:///"
                        loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                    val baseUrl = file?.parentFile?.toURI()?.toString() ?: "file:///"
                    webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                },
                modifier = viewportModifier
            )
        }
    }
}
