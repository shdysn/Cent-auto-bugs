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
import com.ct.explorer.ui.components.office.MsExcelBottomBar
import com.ct.explorer.ui.components.office.MsExcelFormulaBar
import com.ct.explorer.ui.components.office.MsExcelPageSheet
import com.ct.explorer.ui.components.office.MsExcelRibbon
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.docx.PageMargins
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize
import com.ct.explorer.utils.excel.ExcelPage
import com.ct.explorer.utils.excel.ExcelParser
import com.ct.explorer.utils.excel.ExcelPaginator
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

    // Physical Page & Paper Configuration (A4, Letter, Legal, Margins)
    var selectedPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var selectedOrientation by remember { mutableStateOf(PageOrientation.LANDSCAPE) }
    var selectedMargins by remember { mutableStateOf(PageMargins.NORMAL) }
    var showPrintSetupDialog by remember { mutableStateOf(false) }

    var selectedCellRef by remember { mutableStateOf("A1") }
    var selectedCellValue by remember { mutableStateOf("") }

    // Fullscreen & Original Page View states (defaults to True for authentic A4/Letter/Legal print page layout)
    var isFullScreen by remember { mutableStateOf(true) }
    var isOriginalPageView by remember { mutableStateOf(true) }

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

    // Real Physical Pagination (A4, Letter, Legal, Margins)
    val paginatedPages: List<ExcelPage> = remember(currentSheet, selectedPaperSize, selectedOrientation, selectedMargins) {
        if (currentSheet == null) emptyList()
        else ExcelPaginator.paginate(currentSheet, selectedPaperSize, selectedOrientation, selectedMargins)
    }

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
                // Compact Fullscreen Header Bar with Back, Print Setup & Original Page View buttons
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

                        // Action Buttons: Print & Original Page View
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Print Button
                            IconButton(
                                onClick = { showPrintSetupDialog = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "Print / PDF Setup",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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

                        // Paper Size Quick Bar in Fullscreen
                        if (isOriginalPageView) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Paper:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                        PaperSize.values().forEach { size ->
                                            val isSelected = selectedPaperSize == size
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) ExcelGreen else Color.White.copy(alpha = 0.15f),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { selectedPaperSize = size }
                                            ) {
                                                Text(
                                                    text = size.title,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.White.copy(alpha = 0.15f),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedOrientation = if (selectedOrientation == PageOrientation.LANDSCAPE)
                                                        PageOrientation.PORTRAIT else PageOrientation.LANDSCAPE
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (selectedOrientation == PageOrientation.PORTRAIT)
                                                        Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = selectedOrientation.title,
                                                    fontSize = 11.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
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
                                showPrintSetupDialog = true
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

            // Paper Size Quick Bar (when in original page view and not in fullscreen)
            AnimatedVisibility(visible = isOriginalPageView && !isFullScreen) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Paper:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PaperSize.values().forEach { size ->
                                val isSelected = selectedPaperSize == size
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) ExcelGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, if (isSelected) ExcelGreen else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedPaperSize = size }
                                ) {
                                    Text(
                                        text = size.title,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Orientation toggle
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedOrientation = if (selectedOrientation == PageOrientation.LANDSCAPE)
                                            PageOrientation.PORTRAIT else PageOrientation.LANDSCAPE
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selectedOrientation == PageOrientation.PORTRAIT)
                                            Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                                        contentDescription = null,
                                        tint = ExcelGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedOrientation.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Print Setup button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ExcelGreenLight,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showPrintSetupDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = null,
                                        tint = ExcelGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Print Setup",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ExcelGreen
                                    )
                                }
                            }
                        }
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
                // AUTHENTIC PHYSICAL PAGE VIEW (A4, Letter, Legal Print Page Layout)
                val maxPageWidth = if (selectedOrientation == PageOrientation.PORTRAIT) 640.dp else 860.dp
                val minPageHeight = if (selectedOrientation == PageOrientation.PORTRAIT) {
                    when (selectedPaperSize) {
                        PaperSize.A4 -> 860.dp
                        PaperSize.LETTER -> 800.dp
                        PaperSize.LEGAL -> 1020.dp
                    }
                } else {
                    when (selectedPaperSize) {
                        PaperSize.A4 -> 590.dp
                        PaperSize.LETTER -> 610.dp
                        PaperSize.LEGAL -> 610.dp
                    }
                }
                val pageCellWidth = 130.dp

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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(paginatedPages) { pageIdx, page ->
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = Color.White,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                                modifier = Modifier
                                    .widthIn(max = maxPageWidth)
                                    .fillMaxWidth()
                                    .heightIn(min = minPageHeight)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Physical Page Header
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                                Text(
                                                    text = workbook?.title ?: file?.nameWithoutExtension ?: "Spreadsheet",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ExcelGreenDark,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Sheet: ${page.sheetName}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF475569),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Surface(
                                                color = Color(0xFFDCFCE7),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(0.5.dp, Color(0xFF86EFAC))
                                            ) {
                                                Text(
                                                    text = "${selectedPaperSize.title} • ${selectedOrientation.title}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = ExcelGreenDark,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        HorizontalDivider(thickness = 1.2.dp, color = ExcelGreen)
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Sheet Table with repeated header row
                                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                            Column(
                                                modifier = Modifier.border(1.dp, Color(0xFF94A3B8))
                                            ) {
                                                // Repeated Header row
                                                if (page.headerRow != null && page.headerRow.isNotEmpty()) {
                                                    Row(modifier = Modifier.background(Color(0xFFDCFCE7))) {
                                                        page.headerRow.forEach { cellText ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(pageCellWidth)
                                                                    .border(0.5.dp, Color(0xFF86EFAC))
                                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                Text(
                                                                    text = cellText,
                                                                    fontSize = 11.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF166534),
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Data rows for this page
                                                page.rows.forEachIndexed { rIdx, row ->
                                                    Row(
                                                        modifier = Modifier.background(
                                                            if (rIdx % 2 == 1) Color(0xFFF8FAFC) else Color.White
                                                        )
                                                    ) {
                                                        val colCount = page.headerRow?.size ?: row.size
                                                        for (colIndex in 0 until colCount) {
                                                            val cellText = row.getOrNull(colIndex).orEmpty()
                                                            val isMatch = searchQuery.isNotBlank() &&
                                                                cellText.contains(searchQuery, ignoreCase = true)
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(pageCellWidth)
                                                                    .heightIn(min = 32.dp)
                                                                    .background(if (isMatch) Color(0xFFFEF08A) else Color.Transparent)
                                                                    .border(0.5.dp, Color(0xFFCBD5E1))
                                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                Text(
                                                                    text = cellText,
                                                                    fontSize = 11.sp,
                                                                    lineHeight = 15.sp,
                                                                    maxLines = 3,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    color = Color(0xFF0F172A)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Physical Page Footer
                                    Column(modifier = Modifier.padding(top = 16.dp)) {
                                        HorizontalDivider(thickness = 0.8.dp, color = Color(0xFFE2E8F0))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Sheet: ${page.sheetName} (Rows ${page.startRowIndex}–${page.endRowIndex})",
                                                fontSize = 9.5.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Text(
                                                text = "Page ${page.pageNumber} of ${paginatedPages.size}",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF475569)
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

    // Print & Page Setup Dialog (A4, Letter, Legal & Orientation for Android Print Spooler)
    if (showPrintSetupDialog && file != null && workbook != null) {
        PrintSpreadsheetSetupDialog(
            workbook = workbook!!,
            activeSheetIndex = selectedSheetIndex,
            initialPaperSize = selectedPaperSize,
            initialOrientation = selectedOrientation,
            totalPages = paginatedPages.size,
            onDismiss = { showPrintSetupDialog = false },
            onConfirmPrint = { paperSize, orientation, printCurrentSheetOnly ->
                selectedPaperSize = paperSize
                selectedOrientation = orientation
                showPrintSetupDialog = false
                com.ct.explorer.utils.PrintHelper.printSpreadsheetFile(
                    context = context,
                    file = file,
                    paperSize = paperSize,
                    orientation = orientation,
                    sheetIndex = if (printCurrentSheetOnly) selectedSheetIndex else null
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintSpreadsheetSetupDialog(
    workbook: ExcelWorkbook,
    activeSheetIndex: Int,
    initialPaperSize: PaperSize,
    initialOrientation: PageOrientation,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirmPrint: (PaperSize, PageOrientation, Boolean) -> Unit
) {
    var chosenPaperSize by remember { mutableStateOf(initialPaperSize) }
    var chosenOrientation by remember { mutableStateOf(initialOrientation) }
    var printCurrentSheetOnly by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = ExcelGreen,
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
                        color = if (isSelected) ExcelGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) ExcelGreen else MaterialTheme.colorScheme.outlineVariant
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
                                    colors = RadioButtonDefaults.colors(selectedColor = ExcelGreen)
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
                                    color = ExcelGreen,
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

                // Section 2: Orientation (Landscape recommended for sheets)
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
                            color = if (isSelected) ExcelGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ExcelGreen else MaterialTheme.colorScheme.outlineVariant
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
                                    tint = if (isSelected) ExcelGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = orient.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) ExcelGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (orient == PageOrientation.LANDSCAPE) {
                                        Text(
                                            text = "Best for Tables",
                                            fontSize = 9.sp,
                                            color = ExcelGreenDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Scope: Current Sheet vs All Sheets (if workbook has multiple sheets)
                if (workbook.sheets.size > 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Sheet Scope",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (printCurrentSheetOnly) ExcelGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (printCurrentSheetOnly) ExcelGreen else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { printCurrentSheetOnly = true }
                        ) {
                            Text(
                                text = "Current Sheet (${workbook.sheets.getOrNull(activeSheetIndex)?.name})",
                                fontSize = 11.sp,
                                fontWeight = if (printCurrentSheetOnly) FontWeight.Bold else FontWeight.Normal,
                                color = if (printCurrentSheetOnly) ExcelGreen else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (!printCurrentSheetOnly) ExcelGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (!printCurrentSheetOnly) ExcelGreen else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { printCurrentSheetOnly = false }
                        ) {
                            Text(
                                text = "All Sheets (${workbook.sheets.size})",
                                fontSize = 11.sp,
                                fontWeight = if (!printCurrentSheetOnly) FontWeight.Bold else FontWeight.Normal,
                                color = if (!printCurrentSheetOnly) ExcelGreen else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
                            )
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
                onClick = { onConfirmPrint(chosenPaperSize, chosenOrientation, printCurrentSheetOnly) },
                colors = ButtonDefaults.buttonColors(containerColor = ExcelGreen)
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
