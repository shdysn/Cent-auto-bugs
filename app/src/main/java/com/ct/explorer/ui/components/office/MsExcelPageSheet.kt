package com.ct.explorer.ui.components.office

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.utils.excel.ExcelPage
import com.ct.explorer.utils.excel.ExcelParser

val ExcelHeaderBg = Color(0xFFF1F5F9)
val ExcelHeaderBorder = Color(0xFFCBD5E1)
val ExcelGridLine = Color(0xFFD4D4D4)
val ExcelSelectedBorder = Color(0xFF107C41)
val ExcelCellWidth = 110.dp
val ExcelRowHeaderWidth = 42.dp

/**
 * Authentic Microsoft Excel Page Layout View Sheet matching physical printer paper (A4, Letter, Legal).
 */
@Composable
fun MsExcelPageSheet(
    page: ExcelPage,
    workbookTitle: String,
    selectedCellCoords: Pair<Int, Int>?,
    searchQuery: String,
    onCellClick: (row: Int, col: Int, ref: String, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val aspectRatio = page.paperSize.widthToHeightRatio(page.orientation)
    val paddingDp = page.margins.paddingDp.dp

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(0.75.dp, Color(0xFFCBD5E1)),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingDp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. MS Excel 3-Zone Header Box (Left, Center, Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExcelHeaderFooterBox(
                    text = workbookTitle.ifBlank { "دستاویز • Workbook" },
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                ExcelHeaderFooterBox(
                    text = "Sheet: ${page.sheetName}",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                ExcelHeaderFooterBox(
                    text = "${page.paperSize.formattedSize} • ${page.orientation.title}",
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Worksheet Table with Column & Row Headers
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, ExcelGridLine)
                ) {
                    val colCount = page.headerRow?.size ?: (page.rows.firstOrNull()?.size ?: 1)

                    // Column Letters Row: [ ◿ ] [ A ] [ B ] [ C ] [ D ] ...
                    Row(
                        modifier = Modifier
                            .background(ExcelHeaderBg)
                            .height(26.dp)
                    ) {
                        // Top-left Select All corner
                        Box(
                            modifier = Modifier
                                .width(ExcelRowHeaderWidth)
                                .fillMaxHeight()
                                .border(0.5.dp, ExcelHeaderBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("◿", fontSize = 10.sp, color = Color(0xFF64748B))
                        }

                        // Column headers: A, B, C, D...
                        for (cIdx in 0 until colCount) {
                            val colLetter = ExcelParser.indexToColumnLetter(cIdx)
                            val isColSelected = selectedCellCoords?.second == cIdx
                            Box(
                                modifier = Modifier
                                    .width(ExcelCellWidth)
                                    .fillMaxHeight()
                                    .background(if (isColSelected) Color(0xFFDCFCE7) else ExcelHeaderBg)
                                    .border(0.5.dp, ExcelHeaderBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = colLetter,
                                    fontSize = 11.sp,
                                    fontWeight = if (isColSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isColSelected) Color(0xFF166534) else Color(0xFF334155)
                                )
                            }
                        }
                    }

                    // Repeated Column Titles Row (if document has named headers)
                    if (page.headerRow != null && page.headerRow.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFE2E8F0))
                                .height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(ExcelRowHeaderWidth)
                                    .fillMaxHeight()
                                    .border(0.5.dp, ExcelHeaderBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Title", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            }
                            page.headerRow.forEachIndexed { cIdx, title ->
                                Box(
                                    modifier = Modifier
                                        .width(ExcelCellWidth)
                                        .fillMaxHeight()
                                        .border(0.5.dp, ExcelHeaderBorder)
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Data Rows with Row Numbers (1, 2, 3...)
                    page.rows.forEachIndexed { rIdx, row ->
                        val globalRowNumber = page.startRowIndex + rIdx + 1
                        val isRowSelected = selectedCellCoords?.first == (globalRowNumber - 1)

                        Row(
                            modifier = Modifier
                                .heightIn(min = 26.dp)
                                .background(if (rIdx % 2 == 1) Color(0xFFF8FAFC) else Color.White)
                        ) {
                            // Row Number Header on Left
                            Box(
                                modifier = Modifier
                                    .width(ExcelRowHeaderWidth)
                                    .heightIn(min = 26.dp)
                                    .background(if (isRowSelected) Color(0xFFDCFCE7) else ExcelHeaderBg)
                                    .border(0.5.dp, ExcelHeaderBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$globalRowNumber",
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isRowSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isRowSelected) Color(0xFF166534) else Color(0xFF475569)
                                )
                            }

                            // Data Cells in this row
                            for (cIdx in 0 until colCount) {
                                val cellVal = row.getOrNull(cIdx).orEmpty()
                                val isSelected = selectedCellCoords?.first == (globalRowNumber - 1) && selectedCellCoords.second == cIdx
                                val isSearchHit = searchQuery.isNotBlank() && cellVal.contains(searchQuery, ignoreCase = true)
                                val colLetter = ExcelParser.indexToColumnLetter(cIdx)
                                val cellRef = "$colLetter$globalRowNumber"

                                Box(
                                    modifier = Modifier
                                        .width(ExcelCellWidth)
                                        .heightIn(min = 26.dp)
                                        .background(if (isSearchHit) Color(0xFFFEF08A) else Color.Transparent)
                                        .border(
                                            if (isSelected) 2.dp else 0.5.dp,
                                            if (isSelected) ExcelSelectedBorder else ExcelGridLine
                                        )
                                        .clickable { onCellClick(globalRowNumber - 1, cIdx, cellRef, cellVal) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cellVal,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF0F172A),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Solid green fill handle square at bottom-right of selected cell
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(5.dp)
                                                .background(ExcelSelectedBorder)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. MS Excel 3-Zone Footer Box (Left, Center, Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExcelHeaderFooterBox(
                    text = "صفحہ ${page.pageNumber} از ${page.totalPages} • Page ${page.pageNumber} of ${page.totalPages}",
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1.5f)
                )
                ExcelHeaderFooterBox(
                    text = "${page.paperSize.title} (${page.rows.size} rows)",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                ExcelHeaderFooterBox(
                    text = "دستاویز • Cent Explorer",
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExcelHeaderFooterBox(
    text: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            color = Color(0xFF64748B),
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
