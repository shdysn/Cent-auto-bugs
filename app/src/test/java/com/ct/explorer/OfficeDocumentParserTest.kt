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
}
