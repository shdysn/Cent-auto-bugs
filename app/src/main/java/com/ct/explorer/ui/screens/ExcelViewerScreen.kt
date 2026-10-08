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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val state by viewModel.excelViewerState.collectAsStateWithLifecycle()
    val file = state.file

    var workbook by remember { mutableStateOf<ExcelWorkbook?>(null) }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Search & Inspection state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCellCoords by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (row, col)

    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

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
        modifier = modifier.testTag("excel_viewer_screen"),
        topBar = {
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
                                text = "${currentSheet.rows.size} rows • ${currentSheet.maxColumns} cols • ${workbook?.sheets?.size ?: 1} sheet(s)",
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
                .background(Color(0xFFF8FAFC))
        ) {
            // Search Bar
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

            // Cell Inspector / Formula Bar
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
                    // Cell reference pill
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

                    // Content text
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

            // Stats pill bar (Sum / Average if numeric data found)
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

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ExcelGreen)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading spreadsheet grid...",
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
