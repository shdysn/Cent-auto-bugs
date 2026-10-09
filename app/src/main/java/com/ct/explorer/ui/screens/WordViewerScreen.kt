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
import com.ct.explorer.utils.docx.DocxDocument
import com.ct.explorer.utils.docx.DocxElement
import com.ct.explorer.utils.docx.DocxParser
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

    // Fullscreen & Original Page View states (opens in fullscreen by default)
    var isFullScreen by remember { mutableStateOf(true) }
    var isOriginalPageView by remember { mutableStateOf(false) }

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
                    document = result.getOrNull()
                    isLoading = false
                } else {
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to parse Word document"
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("word_viewer_screen"),
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
                                .testTag("word_fullscreen_back_button")
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

                        // Document Title in Center
                        Text(
                            text = state.title.ifEmpty { file?.name ?: "Word Document" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )

                        // Original Page View Button + Exit Fullscreen Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isOriginalPageView) WordBlue else Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isOriginalPageView) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        isOriginalPageView = !isOriginalPageView
                                        pageScale = 1f
                                        pageOffset = Offset.Zero
                                    }
                                    .testTag("word_original_page_view_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOriginalPageView) Icons.Default.ViewStream else Icons.Default.Description,
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
                                    .testTag("word_exit_fullscreen_button")
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = WordBlue,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "DOCX",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = state.title.ifEmpty { file?.name ?: "Word Document" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            document?.let { doc ->
                                Text(
                                    text = "${doc.wordCount} words • ${if (isOriginalPageView) "Original Page View" else "~${doc.estimatedReadMinutes} min read"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.handleBackPress() },
                            modifier = Modifier.testTag("word_toolbar_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Original Page View Toggle Chip in Toolbar
                        FilterChip(
                            selected = isOriginalPageView,
                            onClick = {
                                isOriginalPageView = !isOriginalPageView
                                pageScale = 1f
                                pageOffset = Offset.Zero
                            },
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
                                selectedContainerColor = WordBlueLight,
                                selectedLabelColor = WordBlue,
                                selectedLeadingIconColor = WordBlue
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

                        // Search toggle
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search in Document",
                                tint = if (isSearchActive) WordBlue else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Theme selector (Paper, Sepia, Dark)
                        if (!isOriginalPageView) {
                            IconButton(onClick = {
                                readingTheme = when (readingTheme) {
                                    WordTheme.PAPER -> WordTheme.SEPIA
                                    WordTheme.SEPIA -> WordTheme.DARK
                                    WordTheme.DARK -> WordTheme.PAPER
                                }
                            }) {
                                Icon(
                                    imageVector = when (readingTheme) {
                                        WordTheme.PAPER -> Icons.Default.LightMode
                                        WordTheme.SEPIA -> Icons.Default.MenuBook
                                        WordTheme.DARK -> Icons.Default.DarkMode
                                    },
                                    contentDescription = "Switch Theme"
                                )
                            }
                        }

                        // Font Size toggle
                        IconButton(onClick = {
                            fontSizeMultiplier = when (fontSizeMultiplier) {
                                0.85f -> 1.0f
                                1.0f -> 1.2f
                                1.2f -> 1.4f
                                else -> 0.85f
                            }
                        }) {
                            Icon(Icons.Default.FormatSize, contentDescription = "Font Size")
                        }

                        // Table of Contents / Headings
                        val headings = document?.elements?.filterIsInstance<DocxElement.Heading>().orEmpty()
                        if (headings.isNotEmpty()) {
                            IconButton(onClick = { showOutlineSheet = true }) {
                                Icon(Icons.Default.ListAlt, contentDescription = "Table of Contents")
                            }
                        }

                        // Print / Save as PDF
                        if (file != null) {
                            IconButton(onClick = {
                                com.ct.explorer.utils.PrintHelper.printFile(context, file)
                            }) {
                                Icon(Icons.Default.Print, contentDescription = "Print / Save as PDF")
                            }
                        }

                        // Open in External App (Office / Google Docs)
                        if (file != null) {
                            IconButton(onClick = {
                                FileOpener.openWithChooser(context, FileItem(file))
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open with External App")
                            }
                        }

                        // Share file
                        if (file != null) {
                            IconButton(onClick = {
                                FileOpener.shareFile(context, FileItem(file))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isOriginalPageView) Color(0xFFCBD5E1) else readingTheme.bg)
        ) {
            // Animated Search Bar
            AnimatedVisibility(visible = isSearchActive && !isFullScreen) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in document...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }
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
                    // ORIGINAL PAGE VIEW (Authentic Microsoft Word A4/Letter Print Page Layout)
                    val pages = remember(doc.elements) {
                        doc.elements.chunked(28).ifEmpty { listOf(emptyList()) }
                    }

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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(pages) { pageIdx, pageElements ->
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = Color.White,
                                    shadowElevation = 6.dp,
                                    border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                                    modifier = Modifier
                                        .widthIn(max = 680.dp)
                                        .fillMaxWidth()
                                        .heightIn(min = 520.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 28.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (pageIdx == 0) {
                                                // Original Document Title on Page 1
                                                Text(
                                                    text = doc.title,
                                                    fontSize = (24.sp * fontSizeMultiplier),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E3A8A)
                                                )
                                                HorizontalDivider(
                                                    thickness = 1.5.dp,
                                                    color = Color(0xFFCBD5E1),
                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                )
                                            }

                                            pageElements.forEach { element ->
                                                when (element) {
                                                    is DocxElement.Heading -> {
                                                        Column(modifier = Modifier.padding(top = 6.dp)) {
                                                            Text(
                                                                text = element.text,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = (when (element.level) {
                                                                    1 -> 20.sp
                                                                    2 -> 17.sp
                                                                    else -> 15.sp
                                                                }) * fontSizeMultiplier,
                                                                color = when (element.level) {
                                                                    1 -> Color(0xFF1E3A8A)
                                                                    2 -> Color(0xFF1D4ED8)
                                                                    else -> Color(0xFF0F172A)
                                                                }
                                                            )
                                                            if (element.level == 1) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                HorizontalDivider(color = Color(0xFFE2E8F0))
                                                            }
                                                        }
                                                    }

                                                    is DocxElement.Paragraph -> {
                                                        val annotated = buildAnnotatedString {
                                                            if (element.isBullet) {
                                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                                                                    append("    •  ")
                                                                }
                                                            }
                                                            if (element.runs.isNotEmpty()) {
                                                                for (run in element.runs) {
                                                                    val isMatch = searchQuery.isNotBlank() &&
                                                                        run.text.contains(searchQuery, ignoreCase = true)
                                                                    val runColor = parseDocxHexColor(run.colorHex) ?: Color(0xFF0F172A)
                                                                    withStyle(
                                                                        SpanStyle(
                                                                            fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                                                                            fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                                            textDecoration = if (run.isUnderline) TextDecoration.Underline else null,
                                                                            background = if (isMatch) Color(0xFFFEF08A) else Color.Transparent,
                                                                            color = if (isMatch) Color.Black else runColor
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
                                                            fontSize = 14.sp * fontSizeMultiplier,
                                                            lineHeight = 22.sp * fontSizeMultiplier,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                    }

                                                    is DocxElement.Table -> {
                                                        OriginalPageTableComponent(
                                                            table = element,
                                                            fontSizeMultiplier = fontSizeMultiplier
                                                        )
                                                    }

                                                    DocxElement.Divider -> {
                                                        HorizontalDivider(
                                                            color = Color(0xFFCBD5E1),
                                                            modifier = Modifier.padding(vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Original Page Footer
                                        Column(modifier = Modifier.padding(top = 28.dp)) {
                                            HorizontalDivider(color = Color(0xFFE2E8F0))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = doc.title,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                                Text(
                                                    text = "Page ${pageIdx + 1} of ${pages.size}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }
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
