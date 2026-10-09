package com.ct.explorer.utils.excel

import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize

/**
 * Represents a single physical printed sheet of spreadsheet data.
 */
data class ExcelPage(
    val pageNumber: Int,
    val totalPages: Int,
    val sheetName: String,
    val paperSize: PaperSize,
    val orientation: PageOrientation,
    val headerRow: List<String>?,
    val rows: List<List<String>>,
    val startRowIndex: Int,
    val endRowIndex: Int,
    val isFirstPage: Boolean = false
)

/**
 * Paginate ExcelSheet into real physical pages matching standard A4, Letter, or Legal sheets.
 */
object ExcelPaginator {

    /**
     * Calculates the estimated number of table rows that comfortably fit on a physical page
     * considering paper height, margins, header banner, column headers, and footer.
     */
    fun rowsPerPage(paperSize: PaperSize, orientation: PageOrientation): Int {
        return if (orientation == PageOrientation.LANDSCAPE) {
            when (paperSize) {
                PaperSize.A4 -> 26
                PaperSize.LETTER -> 24
                PaperSize.LEGAL -> 24
            }
        } else {
            when (paperSize) {
                PaperSize.A4 -> 40
                PaperSize.LETTER -> 36
                PaperSize.LEGAL -> 50
            }
        }
    }

    /**
     * Paginates a sheet's rows into physical page blocks.
     * Repeats the column header row on each page so data is always readable when printed.
     */
    fun paginate(
        sheet: ExcelSheet,
        paperSize: PaperSize,
        orientation: PageOrientation
    ): List<ExcelPage> {
        if (sheet.rows.isEmpty()) {
            return listOf(
                ExcelPage(
                    pageNumber = 1,
                    totalPages = 1,
                    sheetName = sheet.name,
                    paperSize = paperSize,
                    orientation = orientation,
                    headerRow = null,
                    rows = emptyList(),
                    startRowIndex = 0,
                    endRowIndex = 0,
                    isFirstPage = true
                )
            )
        }

        val maxRows = rowsPerPage(paperSize, orientation)
        val hasHeader = sheet.rows.size > 1
        val headerRow = if (hasHeader) sheet.rows.first() else null
        val dataRows = if (hasHeader) sheet.rows.drop(1) else sheet.rows

        val chunkedRows = if (dataRows.isEmpty()) {
            listOf(emptyList())
        } else {
            dataRows.chunked(maxRows)
        }

        val totalPagesCount = chunkedRows.size
        return chunkedRows.mapIndexed { idx, pageRows ->
            val startIdx = if (hasHeader) (idx * maxRows) + 1 else (idx * maxRows)
            val endIdx = startIdx + pageRows.size - 1
            ExcelPage(
                pageNumber = idx + 1,
                totalPages = totalPagesCount,
                sheetName = sheet.name,
                paperSize = paperSize,
                orientation = orientation,
                headerRow = headerRow,
                rows = pageRows,
                startRowIndex = startIdx,
                endRowIndex = endIdx.coerceAtLeast(startIdx),
                isFirstPage = (idx == 0)
            )
        }
    }
}
