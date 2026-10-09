package com.ct.explorer

import com.ct.explorer.utils.docx.DocxElement
import com.ct.explorer.utils.docx.DocxParser
import com.ct.explorer.utils.excel.ExcelParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficeDocumentParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCsvParserSingleAndMultiColumn() {
        val csvFile = tempFolder.newFile("sample.csv")
        csvFile.writeText(
            """
            Product,Price,Quantity
            Laptop,1200,5
            Phone,800,10
            "Headphones, Wireless",150,20
            """.trimIndent()
        )

        val result = ExcelParser.parse(csvFile)
        assertTrue(result.isSuccess)
        val workbook = result.getOrNull()
        assertNotNull(workbook)
        assertEquals("CSV", workbook?.fileType)
        assertEquals(1, workbook?.sheets?.size)

        val sheet = workbook?.sheets?.first()
        assertEquals(4, sheet?.rows?.size)
        assertEquals(3, sheet?.maxColumns)
        assertEquals("Product", sheet?.rows?.get(0)?.get(0))
        assertEquals("Headphones, Wireless", sheet?.rows?.get(3)?.get(0))
        assertEquals("150", sheet?.rows?.get(3)?.get(1))
    }

    @Test
    fun testExcelColumnLetterConversion() {
        assertEquals(0, ExcelParser.columnRefToIndex("A1"))
        assertEquals(1, ExcelParser.columnRefToIndex("B12"))
        assertEquals(25, ExcelParser.columnRefToIndex("Z99"))
        assertEquals(26, ExcelParser.columnRefToIndex("AA1"))

        assertEquals("A", ExcelParser.indexToColumnLetter(0))
        assertEquals("B", ExcelParser.indexToColumnLetter(1))
        assertEquals("Z", ExcelParser.indexToColumnLetter(25))
        assertEquals("AA", ExcelParser.indexToColumnLetter(26))
    }

    @Test
    fun testDocxParserWithHeadingsAndParagraphs() {
        val docxFile = tempFolder.newFile("test_doc.docx")
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr>
                            <w:pStyle w:val="Heading1"/>
                        </w:pPr>
                        <w:r>
                            <w:t>Project Executive Summary</w:t>
                        </w:r>
                    </w:p>
                    <w:p>
                        <w:r>
                            <w:rPr><w:b/></w:rPr>
                            <w:t>Welcome </w:t>
                        </w:r>
                        <w:r>
                            <w:t>to Cent File Manager.</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = DocxParser.parse(docxFile)
        if (result.isFailure) {
            result.exceptionOrNull()?.printStackTrace()
        }
        assertTrue(result.isSuccess)
        val doc = result.getOrNull()
        assertNotNull(doc)
        assertEquals("test_doc", doc?.title)
        assertEquals(2, doc?.elements?.size)

        val heading = doc?.elements?.get(0) as? DocxElement.Heading
        assertNotNull(heading)
        assertEquals("Project Executive Summary", heading?.text)
        assertEquals(1, heading?.level)

        val paragraph = doc?.elements?.get(1) as? DocxElement.Paragraph
        assertNotNull(paragraph)
        assertEquals("Welcome to Cent File Manager.", paragraph?.fullText)
        assertEquals(2, paragraph?.runs?.size)
        assertTrue(paragraph?.runs?.get(0)?.isBold == true)
        assertFalse(paragraph?.runs?.get(1)?.isBold == true)
    }

    @Test
    fun testDocxPaginatorWithA4LetterAndLegalPaperSizes() {
        // Create sample document with multiple paragraphs and a page break
        val elements = listOf(
            DocxElement.Heading("Title of Document", 1),
            DocxElement.Paragraph(emptyList(), "Paragraph 1 line of introductory text."),
            DocxElement.PageBreak,
            DocxElement.Heading("Section 2 on New Page", 2),
            DocxElement.Paragraph(emptyList(), "Paragraph 2 content following the page break.")
        )
        val doc = com.ct.explorer.utils.docx.DocxDocument(
            title = "Test Print Doc",
            elements = elements,
            wordCount = 20,
            characterCount = 120,
            detectedPaperSize = com.ct.explorer.utils.docx.PaperSize.A4,
            detectedOrientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )

        // Test A4 pagination
        val a4Pages = com.ct.explorer.utils.docx.DocxPaginator.paginate(
            doc,
            com.ct.explorer.utils.docx.PaperSize.A4,
            com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )
        assertEquals(2, a4Pages.size)
        assertEquals(1, a4Pages[0].pageNumber)
        assertEquals(2, a4Pages[0].totalPages)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.A4, a4Pages[0].paperSize)
        assertTrue(a4Pages[0].isFirstPage)
        assertFalse(a4Pages[1].isFirstPage)
        assertEquals(2, a4Pages[1].pageNumber)

        // Test Letter pagination
        val letterPages = com.ct.explorer.utils.docx.DocxPaginator.paginate(
            doc,
            com.ct.explorer.utils.docx.PaperSize.LETTER,
            com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )
        assertEquals(2, letterPages.size)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.LETTER, letterPages[0].paperSize)

        // Test Legal pagination
        val legalPages = com.ct.explorer.utils.docx.DocxPaginator.paginate(
            doc,
            com.ct.explorer.utils.docx.PaperSize.LEGAL,
            com.ct.explorer.utils.docx.PageOrientation.LANDSCAPE
        )
        assertEquals(2, legalPages.size)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.LEGAL, legalPages[0].paperSize)
        assertEquals(com.ct.explorer.utils.docx.PageOrientation.LANDSCAPE, legalPages[0].orientation)

        // Verify dimensions and aspect ratios
        val a4RatioPort = com.ct.explorer.utils.docx.PaperSize.A4.widthToHeightRatio(com.ct.explorer.utils.docx.PageOrientation.PORTRAIT)
        val a4RatioLand = com.ct.explorer.utils.docx.PaperSize.A4.widthToHeightRatio(com.ct.explorer.utils.docx.PageOrientation.LANDSCAPE)
        assertTrue(a4RatioPort < 1.0f) // 210 / 297 ≈ 0.707
        assertTrue(a4RatioLand > 1.0f) // 297 / 210 ≈ 1.414

        val letterRatioPort = com.ct.explorer.utils.docx.PaperSize.LETTER.widthToHeightRatio(com.ct.explorer.utils.docx.PageOrientation.PORTRAIT)
        assertTrue(letterRatioPort in 0.76f..0.78f) // 8.5 / 11.0 ≈ 0.772

        val legalRatioPort = com.ct.explorer.utils.docx.PaperSize.LEGAL.widthToHeightRatio(com.ct.explorer.utils.docx.PageOrientation.PORTRAIT)
        assertTrue(legalRatioPort in 0.60f..0.62f) // 8.5 / 14.0 ≈ 0.607
    }

    @Test
    fun testDocxPaginatorAutoOverflowsLongContent() {
        // Create document with 50 paragraphs that should overflow into multiple pages
        val elements = (1..60).map { i ->
            DocxElement.Paragraph(
                runs = emptyList(),
                fullText = "Paragraph $i: This is a sufficiently long line of text intended to test pagination overflows across physical sheets of paper in standard office formats.",
                isBullet = (i % 5 == 0)
            )
        }
        val doc = com.ct.explorer.utils.docx.DocxDocument(
            title = "Long Document",
            elements = elements,
            wordCount = elements.size * 25,
            characterCount = elements.size * 150
        )

        val pages = com.ct.explorer.utils.docx.DocxPaginator.paginate(
            doc,
            com.ct.explorer.utils.docx.PaperSize.A4,
            com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )

        // Multiple physical pages generated
        assertTrue(pages.size >= 2)
        assertEquals(1, pages.first().pageNumber)
        assertEquals(pages.size, pages.last().pageNumber)
        assertEquals(pages.size, pages.first().totalPages)
    }

    @Test
    fun testExcelPaginatorWithA4LetterAndLegal() {
        // Create sample sheet with 1 header row and 75 data rows
        val header = listOf("ID", "Customer Name", "Order Amount", "Status")
        val dataRows = (1..75).map { i ->
            listOf("$i", "Customer #$i", "$${i * 15}.00", if (i % 2 == 0) "Completed" else "Pending")
        }
        val sheet = com.ct.explorer.utils.excel.ExcelSheet(
            name = "Orders 2026",
            rows = listOf(header) + dataRows,
            maxColumns = 4
        )

        // 1. A4 Landscape (Default recommended for spreadsheets)
        val a4LandscapePages = com.ct.explorer.utils.excel.ExcelPaginator.paginate(
            sheet = sheet,
            paperSize = com.ct.explorer.utils.docx.PaperSize.A4,
            orientation = com.ct.explorer.utils.docx.PageOrientation.LANDSCAPE
        )
        assertTrue(a4LandscapePages.size >= 3)
        assertEquals(1, a4LandscapePages[0].pageNumber)
        assertEquals(a4LandscapePages.size, a4LandscapePages[0].totalPages)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.A4, a4LandscapePages[0].paperSize)
        assertEquals(com.ct.explorer.utils.docx.PageOrientation.LANDSCAPE, a4LandscapePages[0].orientation)
        assertEquals(header, a4LandscapePages[0].headerRow)
        assertEquals(header, a4LandscapePages[1].headerRow) // Header repeated on page 2
        assertTrue(a4LandscapePages[0].isFirstPage)
        assertFalse(a4LandscapePages[1].isFirstPage)

        // 2. Letter Landscape
        val letterPages = com.ct.explorer.utils.excel.ExcelPaginator.paginate(
            sheet = sheet,
            paperSize = com.ct.explorer.utils.docx.PaperSize.LETTER,
            orientation = com.ct.explorer.utils.docx.PageOrientation.LANDSCAPE
        )
        assertTrue(letterPages.size >= 3)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.LETTER, letterPages[0].paperSize)

        // 3. Legal Landscape
        val legalPages = com.ct.explorer.utils.excel.ExcelPaginator.paginate(
            sheet = sheet,
            paperSize = com.ct.explorer.utils.docx.PaperSize.LEGAL,
            orientation = com.ct.explorer.utils.docx.PageOrientation.LANDSCAPE
        )
        assertTrue(legalPages.size >= 3)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.LEGAL, legalPages[0].paperSize)

        // 4. Portrait format has more rows per page than landscape
        val a4PortraitPages = com.ct.explorer.utils.excel.ExcelPaginator.paginate(
            sheet = sheet,
            paperSize = com.ct.explorer.utils.docx.PaperSize.A4,
            orientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )
        assertTrue(a4PortraitPages.size < a4LandscapePages.size)
    }

    @Test
    fun testTextPaginatorWithA4LetterAndLegal() {
        // Create 120 lines of sample source code or text
        val textLines = (1..120).map { i ->
            "fun processTask$i(): Result<String> = Result.success(\"Task #$i completed successfully\")"
        }
        val fullText = textLines.joinToString("\n")

        // 1. A4 Portrait (Standard document layout)
        val a4Pages = com.ct.explorer.utils.text.TextPaginator.paginate(
            text = fullText,
            paperSize = com.ct.explorer.utils.docx.PaperSize.A4,
            orientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )
        assertTrue(a4Pages.size >= 2)
        assertEquals(1, a4Pages[0].pageNumber)
        assertEquals(1, a4Pages[0].startLineNumber)
        assertTrue(a4Pages[0].endLineNumber > 1)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.A4, a4Pages[0].paperSize)
        assertEquals(com.ct.explorer.utils.docx.PageOrientation.PORTRAIT, a4Pages[0].orientation)
        assertEquals(a4Pages.size, a4Pages[0].totalPages)
        assertTrue(a4Pages[0].isFirstPage)
        assertFalse(a4Pages[1].isFirstPage)

        // Verify line continuity
        assertEquals(a4Pages[0].endLineNumber + 1, a4Pages[1].startLineNumber)

        // 2. Letter Portrait
        val letterPages = com.ct.explorer.utils.text.TextPaginator.paginate(
            text = fullText,
            paperSize = com.ct.explorer.utils.docx.PaperSize.LETTER,
            orientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )
        assertTrue(letterPages.size >= 2)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.LETTER, letterPages[0].paperSize)

        // 3. Legal Portrait (Longer page fits more lines)
        val legalPages = com.ct.explorer.utils.text.TextPaginator.paginate(
            text = fullText,
            paperSize = com.ct.explorer.utils.docx.PaperSize.LEGAL,
            orientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )
        assertTrue(legalPages.size <= a4Pages.size)
        assertEquals(com.ct.explorer.utils.docx.PaperSize.LEGAL, legalPages[0].paperSize)

        // 4. Empty text edge case
        val emptyPages = com.ct.explorer.utils.text.TextPaginator.paginate(
            text = "",
            paperSize = com.ct.explorer.utils.docx.PaperSize.A4,
            orientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT
        )
        assertEquals(1, emptyPages.size)
        assertEquals(1, emptyPages[0].pageNumber)
    }

    @Test
    fun testPageMarginsPresetsAndOfficePaginatorIntegration() {
        val normal = com.ct.explorer.utils.docx.PageMargins.NORMAL
        val narrow = com.ct.explorer.utils.docx.PageMargins.NARROW
        val moderate = com.ct.explorer.utils.docx.PageMargins.MODERATE
        val wide = com.ct.explorer.utils.docx.PageMargins.WIDE

        assertEquals("Normal", normal.title)
        assertEquals(1.0f, normal.topInch)
        assertEquals(1.0f, normal.bottomInch)
        assertEquals(1.0f, normal.leftInch)
        assertEquals(1.0f, normal.rightInch)

        assertEquals("Narrow", narrow.title)
        assertEquals(0.5f, narrow.topInch)

        assertEquals("Moderate", moderate.title)
        assertEquals(0.75f, moderate.leftInch)

        assertEquals("Wide", wide.title)
        assertEquals(2.0f, wide.leftInch)

        // Test Text Paginator with Narrow vs Wide Margins (Wide margins hold fewer lines per page)
        val sampleText = (1..150).joinToString("\n") { "Line $it: testing office margin presets" }
        val narrowPages = com.ct.explorer.utils.text.TextPaginator.paginate(
            text = sampleText,
            paperSize = com.ct.explorer.utils.docx.PaperSize.A4,
            orientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT,
            margins = narrow
        )
        val widePages = com.ct.explorer.utils.text.TextPaginator.paginate(
            text = sampleText,
            paperSize = com.ct.explorer.utils.docx.PaperSize.A4,
            orientation = com.ct.explorer.utils.docx.PageOrientation.PORTRAIT,
            margins = wide
        )
        assertEquals(narrow, narrowPages[0].margins)
        assertEquals(wide, widePages[0].margins)
        assertTrue("Wide margins should produce more or equal pages than narrow margins", widePages.size >= narrowPages.size)
    }
}
