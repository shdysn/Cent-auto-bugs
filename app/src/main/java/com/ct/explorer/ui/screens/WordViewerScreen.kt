package com.ct.explorer.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.wordViewerState.collectAsStateWithLifecycle()
    val file = state.file

    var document by remember { mutableStateOf<DocxDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Reading preferences
    var readingTheme by remember { mutableStateOf(WordTheme.PAPER) }
    var fontSizeMultiplier by remember { mutableFloatStateOf(1f) } // 0.85f, 1f, 1.2f, 1.4f
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showOutlineSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

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
        modifier = modifier.testTag("word_viewer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF2B579A),
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
                                text = "${doc.wordCount} words • ~${doc.estimatedReadMinutes} min read",
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
                    // Search toggle
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in Document",
                            tint = if (isSearchActive) Color(0xFF2B579A) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Theme selector (Paper, Sepia, Dark)
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(readingTheme.bg)
        ) {
            // Animated Search Bar
            AnimatedVisibility(visible = isSearchActive) {
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
                        CircularProgressIndicator(color = Color(0xFF2B579A))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading Word document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = readingTheme.text
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B579A))
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
                } else {
                    // Document Paper Container
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
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF2B579A))
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
                                        listState.animateScrollToItem(elementIndex + 1)
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
                                    color = Color(0xFF2B579A),
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
