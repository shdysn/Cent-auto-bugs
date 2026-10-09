package com.ct.explorer.utils.text

import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize

/**
 * Represents a single physical printed sheet of text or code content.
 */
data class TextPage(
    val pageNumber: Int,
    val totalPages: Int,
    val paperSize: PaperSize,
    val orientation: PageOrientation,
    val lines: List<String>,
    val startLineNumber: Int,
    val endLineNumber: Int,
    val isFirstPage: Boolean = false
)

/**
 * Paginates plain text or code content into real physical pages matching A4, Letter, or Legal paper sheets.
 */
object TextPaginator {

    /**
     * Calculates the estimated number of monospace text lines per physical page
     * taking standard 20mm/15mm margins, 10.5pt typography, 1.45 line-height, header, and footer into account.
     */
    fun linesPerPage(paperSize: PaperSize, orientation: PageOrientation): Int {
        return if (orientation == PageOrientation.PORTRAIT) {
            when (paperSize) {
                PaperSize.A4 -> 54
                PaperSize.LETTER -> 48
                PaperSize.LEGAL -> 68
            }
        } else {
            when (paperSize) {
                PaperSize.A4 -> 34
                PaperSize.LETTER -> 34
                PaperSize.LEGAL -> 34
            }
        }
    }

    /**
     * Paginates document text into physical sheets.
     */
    fun paginate(
        text: String,
        paperSize: PaperSize,
        orientation: PageOrientation
    ): List<TextPage> {
        val rawLines = text.split("\n")
        if (rawLines.isEmpty() || (rawLines.size == 1 && rawLines.first().isEmpty())) {
            return listOf(
                TextPage(
                    pageNumber = 1,
                    totalPages = 1,
                    paperSize = paperSize,
                    orientation = orientation,
                    lines = listOf(""),
                    startLineNumber = 1,
                    endLineNumber = 1,
                    isFirstPage = true
                )
            )
        }

        val maxLines = linesPerPage(paperSize, orientation)
        val chunked = rawLines.chunked(maxLines)
        val totalPagesCount = chunked.size

        return chunked.mapIndexed { idx, pageLines ->
            val startLine = (idx * maxLines) + 1
            val endLine = startLine + pageLines.size - 1
            TextPage(
                pageNumber = idx + 1,
                totalPages = totalPagesCount,
                paperSize = paperSize,
                orientation = orientation,
                lines = pageLines,
                startLineNumber = startLine,
                endLineNumber = endLine,
                isFirstPage = (idx == 0)
            )
        }
    }
}
