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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.excel.ExcelParser
import com.ct.explorer.utils.excel.ExcelSheet
import com.ct.explorer.utils.excel.ExcelWorkbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ExcelGreen = Color(0xFF107C41)
val ExcelGreenDark = Color(0xFF0B582E)
val ExcelGreenLight = Color(0xFFE8F5E9)
val GridBorderColor = Color(0xFFCBD5E1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by viewModel.excelViewerState.collectAsStateWithLifecycle()
    val file = state.file

    var workbook by remember { mutableStateOf<ExcelWorkbook?>(null) }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fullscreen & Original Page View states (opens in fullscreen by default)
    var isFullScreen by remember { mutableStateOf(true) }
    var isOriginalPageView by remember { mutableStateOf(false) }

    // Zoom & pan state for Original Page View
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

    // Search & Inspection state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCellCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (row, col)

    val listState = rememberLazyListState()
    val pageListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val pageHorizontalScrollState = rememberScrollState()

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
            errorMessage = "Spreadsheet file not found"
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            val result = ExcelParser.parse(file)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    workbook = result.getOrNull()
                    selectedSheetIndex = 0
                    selectedCellCoords = null
                    isLoading = false
                } else {
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to parse spreadsheet"
                    isLoading = false
                }
            }
        }
    }

    val currentSheet: ExcelSheet? = workbook?.sheets?.getOrNull(selectedSheetIndex)

    // Calculate auto stats for active sheet
    val sheetStats = remember(currentSheet) {
        if (currentSheet == null) null
        else {
            val rowCount = currentSheet.rows.size
            val colCount = currentSheet.maxColumns
            var numSum = 0.0
            var numCount = 0

            for (row in currentSheet.rows) {
                for (cell in row) {
                    val clean = cell.replace(",", "").trim()
                    val num = clean.toDoubleOrNull()
                    if (num != null) {
                        numSum += num
                        numCount++
                    }
                }
            }

            Triple(rowCount, colCount, if (numCount > 0) Pair(numSum, numSum / numCount) else null)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("excel_viewer_screen"),
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
                                .testTag("excel_fullscreen_back_button")
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

                        // File Title in Center
                        Text(
                            text = state.title.ifEmpty { file?.name ?: "Spreadsheet" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )

                        // Original Page View Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isOriginalPageView) ExcelGreen else Color.White.copy(alpha = 0.16f),
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
                                    .testTag("excel_original_page_view_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOriginalPageView) Icons.Default.GridOn else Icons.Default.Description,
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
                                    .testTag("excel_exit_fullscreen_button")
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
                                    color = ExcelGreen,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = workbook?.fileType ?: "XLSX",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = state.title.ifEmpty { file?.name ?: "Spreadsheet" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (currentSheet != null) {
                                Text(
                                    text = "${currentSheet.rows.size} rows • ${currentSheet.maxColumns} cols • ${if (isOriginalPageView) "Original Page View" else "Grid View"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.handleBackPress() },
                            modifier = Modifier.testTag("excel_toolbar_back_button")
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
                                selectedContainerColor = ExcelGreenLight,
                                selectedLabelColor = ExcelGreenDark,
                                selectedLeadingIconColor = ExcelGreenDark
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
                                contentDescription = "Search in Sheet",
                                tint = if (isSearchActive) ExcelGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Print / Save as PDF
                        if (file != null) {
                            IconButton(onClick = {
                                com.ct.explorer.utils.PrintHelper.printFile(context, file)
                            }) {
                                Icon(Icons.Default.Print, contentDescription = "Print / Save as PDF")
                            }
                        }

                        // Open in External App (Office / Google Sheets)
                        if (file != null) {
                            IconButton(onClick = {
                                FileOpener.openWithChooser(context, FileItem(file))
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open in Sheets/Office")
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
        },
        bottomBar = {
            // Sheet Tabs Switcher (if multiple sheets)
            val wb = workbook
            if (wb != null && wb.sheets.size > 1 && !isLoading) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSheetIndex,
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = ExcelGreen
                    ) {
                        wb.sheets.forEachIndexed { index, sheet ->
                            Tab(
                                selected = selectedSheetIndex == index,
                                onClick = {
                                    selectedSheetIndex = index
                                    selectedCellCoords = null
                                },
                                text = {
                                    Text(
                                        text = sheet.name,
                                        fontWeight = if (selectedSheetIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedSheetIndex == index) ExcelGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isOriginalPageView) Color(0xFFE2E8F0) else Color(0xFFF8FAFC))
        ) {
            // Search Bar
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
                            placeholder = { Text("Find cell data...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ExcelGreen) },
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

            // Cell Inspector / Formula Bar & Stats Pill Bar (shown in Grid View)
            AnimatedVisibility(visible = !isOriginalPageView && !isFullScreen) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val selectedCell = selectedCellCoords
                    val cellValue = if (selectedCell != null && currentSheet != null) {
                        val (r, c) = selectedCell
                        currentSheet.rows.getOrNull(r)?.getOrNull(c).orEmpty()
                    } else null

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, GridBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val cellRef = if (selectedCell != null) {
                                "${ExcelParser.indexToColumnLetter(selectedCell.second)}${selectedCell.first + 1}"
                            } else "fx"

                            Surface(
                                color = if (selectedCell != null) ExcelGreenLight else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = cellRef,
                                    color = if (selectedCell != null) ExcelGreenDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = if (!cellValue.isNullOrBlank()) cellValue else if (selectedCell != null) "(Empty cell)" else "Tap any cell to inspect or copy",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = if (selectedCell != null) FontFamily.Monospace else FontFamily.Default,
                                color = if (cellValue.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (!cellValue.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Cell Value", cellValue))
                                        Toast.makeText(context, "Copied \"$cellValue\"", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Value", tint = ExcelGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    sheetStats?.third?.let { (sum, avg) ->
                        Surface(
                            color = ExcelGreenLight.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SUM: ${"%.2f".format(sum)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = ExcelGreenDark
                                )
                                Text(
                                    text = "AVG: ${"%.2f".format(avg)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = ExcelGreenDark
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ExcelGreen)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading spreadsheet...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
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
                                text = "Cannot Preview Spreadsheet",
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
                                    colors = ButtonDefaults.buttonColors(containerColor = ExcelGreen)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open in Sheets/Office")
                                }
                                OutlinedButton(onClick = { viewModel.handleBackPress() }) {
                                    Text("Back")
                                }
                            }
                        }
                    }
                }
            } else if (currentSheet == null || currentSheet.rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sheet is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            } else if (isOriginalPageView) {
                // ORIGINAL PAGE VIEW (Clean Print / Document Page Layout without A/B/C & 1/2/3 grid chrome)
                val columnCount = currentSheet.maxColumns.coerceAtLeast(1)
                val pageCellWidth = 136.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = pageTransformState)
                ) {
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = pageScale,
                                scaleY = pageScale,
                                translationX = pageOffset.x,
                                translationY = pageOffset.y
                            ),
                        contentPadding = PaddingValues(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp)
                                ) {
                                    // Original Page Document Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = workbook?.title ?: file?.nameWithoutExtension ?: "Spreadsheet",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = ExcelGreenDark
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Sheet: ${currentSheet.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF475569),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = "Original Page View",
                                            fontSize = 11.sp,
                                            color = ExcelGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(thickness = 2.dp, color = ExcelGreen)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Clean Printed Page Table
                                    Box(modifier = Modifier.horizontalScroll(pageHorizontalScrollState)) {
                                        Column(
                                            modifier = Modifier.border(1.dp, Color(0xFF94A3B8))
                                        ) {
                                            currentSheet.rows.forEachIndexed { rowIndex, row ->
                                                val isHeaderRow = rowIndex == 0
                                                Row(
                                                    modifier = Modifier.background(
                                                        when {
                                                            isHeaderRow -> Color(0xFFDCFCE7)
                                                            rowIndex % 2 == 1 -> Color(0xFFF8FAFC)
                                                            else -> Color.White
                                                        }
                                                    )
                                                ) {
                                                    for (colIndex in 0 until columnCount) {
                                                        val cellText = row.getOrNull(colIndex).orEmpty()
                                                        val isSearchMatch = searchQuery.isNotBlank() &&
                                                            cellText.contains(searchQuery, ignoreCase = true)

                                                        Box(
                                                            modifier = Modifier
                                                                .width(pageCellWidth)
                                                                .heightIn(min = 36.dp)
                                                                .background(if (isSearchMatch) Color(0xFFFEF08A) else Color.Transparent)
                                                                .border(0.5.dp, Color(0xFF94A3B8))
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            Text(
                                                                text = cellText,
                                                                fontSize = 12.sp,
                                                                lineHeight = 16.sp,
                                                                maxLines = 4,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = if (isHeaderRow) Color(0xFF166534) else Color(0xFF0F172A),
                                                                fontWeight = if (isHeaderRow) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${currentSheet.rows.size} rows × ${currentSheet.maxColumns} columns",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = "Page 1",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium
                                        )
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
                // Interactive Spreadsheet Data Grid
                val columnCount = currentSheet.maxColumns.coerceAtLeast(1)
                val defaultCellWidth = 110.dp
                val rowHeaderWidth = 44.dp

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        Column {
                            // Column Letters Header Row (A, B, C, D...)
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFE2E8F0))
                                    .border(0.5.dp, GridBorderColor)
                            ) {
                                // Top-Left blank corner box
                                Box(
                                    modifier = Modifier
                                        .width(rowHeaderWidth)
                                        .height(30.dp)
                                        .background(Color(0xFFCBD5E1))
                                        .border(0.5.dp, GridBorderColor)
                                )

                                for (c in 0 until columnCount) {
                                    val colLetter = ExcelParser.indexToColumnLetter(c)
                                    val isColSelected = selectedCellCoords?.second == c
                                    Box(
                                        modifier = Modifier
                                            .width(defaultCellWidth)
                                            .height(30.dp)
                                            .background(if (isColSelected) ExcelGreenLight else Color(0xFFE2E8F0))
                                            .border(0.5.dp, GridBorderColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = colLetter,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isColSelected) ExcelGreenDark else Color(0xFF334155),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // Data Rows
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                itemsIndexed(currentSheet.rows) { rowIndex, row ->
                                    val isRowSelected = selectedCellCoords?.first == rowIndex
                                    Row(
                                        modifier = Modifier
                                            .background(if (rowIndex % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                                    ) {
                                        // Row Index Header (1, 2, 3...)
                                        Box(
                                            modifier = Modifier
                                                .width(rowHeaderWidth)
                                                .height(34.dp)
                                                .background(if (isRowSelected) ExcelGreenLight else Color(0xFFE2E8F0))
                                                .border(0.5.dp, GridBorderColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${rowIndex + 1}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isRowSelected) ExcelGreenDark else Color(0xFF475569),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        // Data Cells
                                        for (colIndex in 0 until columnCount) {
                                            val cellText = row.getOrNull(colIndex).orEmpty()
                                            val isCellSelected = selectedCellCoords?.first == rowIndex && selectedCellCoords?.second == colIndex
                                            val isSearchMatch = searchQuery.isNotBlank() && cellText.contains(searchQuery, ignoreCase = true)

                                            val cellBg = when {
                                                isCellSelected -> ExcelGreenLight
                                                isSearchMatch -> Color(0xFFFEF08A)
                                                else -> Color.Transparent
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .width(defaultCellWidth)
                                                    .height(34.dp)
                                                    .background(cellBg)
                                                    .border(
                                                        width = if (isCellSelected) 2.dp else 0.5.dp,
                                                        color = if (isCellSelected) ExcelGreen else GridBorderColor
                                                    )
                                                    .clickable {
                                                        selectedCellCoords = Pair(rowIndex, colIndex)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = cellText,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isCellSelected) ExcelGreenDark else Color(0xFF0F172A),
                                                    fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal
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
        }
    }
}
