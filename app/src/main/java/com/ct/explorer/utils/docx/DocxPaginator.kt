package com.ct.explorer.utils.docx

import android.print.PrintAttributes

/**
 * Standard Physical Paper Sizes for Document Layout & Printing.
 */
enum class PaperSize(
    val title: String,
    val subtitle: String,
    val widthMm: Float,
    val heightMm: Float,
    val widthInches: Float,
    val heightInches: Float,
    val cssPageSize: String
) {
    A4(
        title = "A4",
        subtitle = "210 × 297 mm (ISO 216)",
        widthMm = 210f,
        heightMm = 297f,
        widthInches = 8.27f,
        heightInches = 11.69f,
        cssPageSize = "A4"
    ),
    LETTER(
        title = "Letter",
        subtitle = "8.5 × 11.0 in (216 × 279 mm)",
        widthMm = 215.9f,
        heightMm = 279.4f,
        widthInches = 8.5f,
        heightInches = 11.0f,
        cssPageSize = "letter"
    ),
    LEGAL(
        title = "Legal",
        subtitle = "8.5 × 14.0 in (216 × 356 mm)",
        widthMm = 215.9f,
        heightMm = 355.6f,
        widthInches = 8.5f,
        heightInches = 14.0f,
        cssPageSize = "legal"
    );

    /**
     * Ratio of width to height for UI canvas scaling.
     */
    fun widthToHeightRatio(orientation: PageOrientation): Float {
        return if (orientation == PageOrientation.PORTRAIT) {
            widthMm / heightMm
        } else {
            heightMm / widthMm
        }
    }

    /**
     * Vertical content budget units for pagination calculations.
     */
    fun contentBudgetUnits(orientation: PageOrientation): Int {
        return if (orientation == PageOrientation.PORTRAIT) {
            when (this) {
                A4 -> 820
                LETTER -> 760
                LEGAL -> 1020
            }
        } else {
            when (this) {
                A4 -> 530
                LETTER -> 540
                LEGAL -> 540
            }
        }
    }

    /**
     * Maps to Android PrintManager MediaSize.
     */
    fun getPrintMediaSize(orientation: PageOrientation): PrintAttributes.MediaSize {
        val base = when (this) {
            A4 -> PrintAttributes.MediaSize.ISO_A4
            LETTER -> PrintAttributes.MediaSize.NA_LETTER
            LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
        }
        return if (orientation == PageOrientation.PORTRAIT) {
            base
        } else {
            base.asLandscape()
        }
    }
}

enum class PageOrientation(val title: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

/**
 * Represents a single physical page in the document.
 */
data class DocxPage(
    val pageNumber: Int,
    val totalPages: Int,
    val paperSize: PaperSize,
    val orientation: PageOrientation,
    val elements: List<DocxElement>,
    val isFirstPage: Boolean = false
)

/**
 * Paginate DocxDocument elements into real physical pages matching A4, Letter, or Legal sheets.
 */
object DocxPaginator {

    fun paginate(
        document: DocxDocument,
        paperSize: PaperSize,
        orientation: PageOrientation,
        fontSizeMultiplier: Float = 1.0f
    ): List<DocxPage> {
        val totalBudget = (paperSize.contentBudgetUnits(orientation) / fontSizeMultiplier.coerceIn(0.7f, 2.0f)).toInt()
        val charsPerLine = if (orientation == PageOrientation.PORTRAIT) 75 else 105

        val rawPages = mutableListOf<MutableList<DocxElement>>()
        var currentPageElements = mutableListOf<DocxElement>()
        var currentUsedUnits = 0
        var isFirstPage = true

        // Account for Document Title banner on first page
        if (document.title.isNotBlank()) {
            currentUsedUnits += 70
        }

        for (element in document.elements) {
            when (element) {
                is DocxElement.PageBreak -> {
                    // Force start of a new physical page
                    if (currentPageElements.isNotEmpty()) {
                        rawPages.add(currentPageElements)
                        currentPageElements = mutableListOf()
                        currentUsedUnits = 0
                        isFirstPage = false
                    }
                }
                is DocxElement.Heading -> {
                    val headingUnits = when (element.level) {
                        1 -> 48
                        2 -> 38
                        else -> 30
                    }
                    if (currentUsedUnits + headingUnits > totalBudget && currentPageElements.isNotEmpty()) {
                        rawPages.add(currentPageElements)
                        currentPageElements = mutableListOf()
                        currentUsedUnits = 0
                        isFirstPage = false
                    }
                    currentPageElements.add(element)
                    currentUsedUnits += headingUnits
                }
                is DocxElement.Paragraph -> {
                    val textLen = element.fullText.length
                    val estimatedLines = (textLen / charsPerLine).coerceAtLeast(1)
                    val paragraphUnits = (estimatedLines * 22) + 12 + (if (element.isBullet) 6 else 0)

                    if (currentUsedUnits + paragraphUnits <= totalBudget || currentPageElements.isEmpty()) {
                        currentPageElements.add(element)
                        currentUsedUnits += paragraphUnits
                    } else if (paragraphUnits > totalBudget) {
                        // Very large paragraph exceeding an entire page: split runs or text across pages
                        val sentences = element.fullText.split(Regex("(?<=[.!?])\\s+")).ifEmpty { listOf(element.fullText) }
                        var partialTextSb = StringBuilder()
                        var partialUnits = 12

                        for (sentence in sentences) {
                            val sentenceLines = (sentence.length / charsPerLine).coerceAtLeast(1)
                            val sentenceUnits = sentenceLines * 22
                            if (currentUsedUnits + partialUnits + sentenceUnits > totalBudget && currentPageElements.isNotEmpty()) {
                                if (partialTextSb.isNotBlank()) {
                                    currentPageElements.add(DocxElement.Paragraph(emptyList(), partialTextSb.toString().trim(), element.isBullet))
                                }
                                rawPages.add(currentPageElements)
                                currentPageElements = mutableListOf()
                                currentUsedUnits = 0
                                isFirstPage = false
                                partialTextSb = StringBuilder()
                                partialUnits = 12
                            }
                            partialTextSb.append(sentence).append(" ")
                            partialUnits += sentenceUnits
                        }
                        if (partialTextSb.isNotBlank()) {
                            currentPageElements.add(DocxElement.Paragraph(emptyList(), partialTextSb.toString().trim(), element.isBullet))
                            currentUsedUnits += partialUnits
                        }
                    } else {
                        // Move to next page
                        rawPages.add(currentPageElements)
                        currentPageElements = mutableListOf(element)
                        currentUsedUnits = paragraphUnits
                        isFirstPage = false
                    }
                }
                is DocxElement.Table -> {
                    val tableUnits = 40 + (element.rows.size * 26)
                    if (currentUsedUnits + tableUnits > totalBudget && currentPageElements.isNotEmpty()) {
                        rawPages.add(currentPageElements)
                        currentPageElements = mutableListOf()
                        currentUsedUnits = 0
                        isFirstPage = false
                    }
                    currentPageElements.add(element)
                    currentUsedUnits += tableUnits
                }
                DocxElement.Divider -> {
                    val divUnits = 20
                    if (currentUsedUnits + divUnits > totalBudget && currentPageElements.isNotEmpty()) {
                        rawPages.add(currentPageElements)
                        currentPageElements = mutableListOf()
                        currentUsedUnits = 0
                        isFirstPage = false
                    }
                    currentPageElements.add(element)
                    currentUsedUnits += divUnits
                }
            }
        }

        if (currentPageElements.isNotEmpty() || rawPages.isEmpty()) {
            rawPages.add(currentPageElements)
        }

        val totalPagesCount = rawPages.size
        return rawPages.mapIndexed { idx, elementsList ->
            DocxPage(
                pageNumber = idx + 1,
                totalPages = totalPagesCount,
                paperSize = paperSize,
                orientation = orientation,
                elements = elementsList,
                isFirstPage = (idx == 0)
            )
        }
    }
}
