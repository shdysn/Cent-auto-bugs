package com.ct.explorer.utils.docx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class TextRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val colorHex: String? = null
)

sealed class DocxElement {
    data class Heading(val text: String, val level: Int) : DocxElement()
    data class Paragraph(val runs: List<TextRun>, val fullText: String, val isBullet: Boolean = false) : DocxElement()
    data class Table(val rows: List<List<String>>) : DocxElement()
    object Divider : DocxElement()
}

data class DocxDocument(
    val title: String,
    val elements: List<DocxElement>,
    val wordCount: Int,
    val characterCount: Int,
    val estimatedReadMinutes: Int
)

object DocxParser {

    private fun createPullParser(): XmlPullParser? {
        return try {
            android.util.Xml.newPullParser()
        } catch (_: Throwable) {
            try {
                val factory = XmlPullParserFactory.newInstance().apply {
                    isNamespaceAware = false
                }
                factory.newPullParser()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun localTag(name: String?): String {
        return name?.substringAfter(":")?.lowercase() ?: ""
    }

    private fun getAttr(parser: XmlPullParser, attrName: String): String? {
        val count = parser.attributeCount
        for (i in 0 until count) {
            val aName = parser.getAttributeName(i).substringAfter(":")
            if (aName.equals(attrName, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    fun parse(file: File): Result<DocxDocument> = runCatching {
        if (!file.exists()) throw IllegalArgumentException("File does not exist: ${file.name}")

        var documentXml: String? = null

        // Open zip stream and extract word/document.xml
        ZipInputStream(FileInputStream(file)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zipIn.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        if (documentXml.isNullOrBlank()) {
            throw IllegalStateException("Invalid or empty Word (.docx) document: document.xml not found")
        }

        val pullParser = createPullParser()
        if (pullParser != null) {
            parseWithPullParser(pullParser, documentXml, file)
        } else {
            parseWithDom(documentXml, file)
        }
    }

    private fun parseWithPullParser(parser: XmlPullParser, documentXml: String, file: File): DocxDocument {
        parser.setInput(StringReader(documentXml))
        val elements = mutableListOf<DocxElement>()

        var eventType = parser.eventType
        var totalWords = 0
        var totalChars = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = localTag(parser.name)
                when (tag) {
                    "p" -> {
                        val element = parseParagraph(parser)
                        if (element != null) {
                            elements.add(element)
                            val text = when (element) {
                                is DocxElement.Heading -> element.text
                                is DocxElement.Paragraph -> element.fullText
                                else -> ""
                            }
                            if (text.isNotBlank()) {
                                totalChars += text.length
                                val words = text.split("\\s+".toRegex()).count { it.isNotBlank() }
                                totalWords += words
                            }
                        }
                    }
                    "tbl" -> {
                        val table = parseTable(parser)
                        if (table.rows.isNotEmpty()) {
                            elements.add(table)
                            for (row in table.rows) {
                                for (cell in row) {
                                    totalChars += cell.length
                                    totalWords += cell.split("\\s+".toRegex()).count { it.isNotBlank() }
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val readMinutes = (totalWords / 200).coerceAtLeast(1)

        return DocxDocument(
            title = file.nameWithoutExtension,
            elements = elements,
            wordCount = totalWords,
            characterCount = totalChars,
            estimatedReadMinutes = readMinutes
        )
    }

    private fun parseWithDom(documentXml: String, file: File): DocxDocument {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(documentXml)))

        val body = doc.getElementsByTagName("w:body").item(0) ?: doc.documentElement
        val elements = mutableListOf<DocxElement>()
        var totalWords = 0
        var totalChars = 0

        val children = body.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            val tag = node.nodeName.substringAfter(":")
            if (tag == "p") {
                var headingLevel: Int? = null
                val runs = mutableListOf<TextRun>()
                val fullTextSb = StringBuilder()

                val pChildren = node.childNodes
                for (j in 0 until pChildren.length) {
                    val pChild = pChildren.item(j)
                    val pChildTag = pChild.nodeName.substringAfter(":")
                    if (pChildTag == "pPr") {
                        val pPrChildren = pChild.childNodes
                        for (k in 0 until pPrChildren.length) {
                            val pPrChild = pPrChildren.item(k)
                            if (pPrChild.nodeName.substringAfter(":") == "pStyle") {
                                val style = (pPrChild as? org.w3c.dom.Element)?.getAttribute("w:val")
                                    ?: (pPrChild as? org.w3c.dom.Element)?.getAttribute("val") ?: ""
                                if (style.startsWith("Heading", ignoreCase = true) || style.startsWith("Title", ignoreCase = true)) {
                                    headingLevel = style.filter { it.isDigit() }.toIntOrNull() ?: 1
                                }
                            }
                        }
                    } else if (pChildTag == "r") {
                        var isBold = false
                        var isItalic = false
                        val runTextSb = StringBuilder()
                        val rChildren = pChild.childNodes
                        for (k in 0 until rChildren.length) {
                            val rChild = rChildren.item(k)
                            val rChildTag = rChild.nodeName.substringAfter(":")
                            if (rChildTag == "rPr") {
                                val rPrChildren = rChild.childNodes
                                for (l in 0 until rPrChildren.length) {
                                    val rPrChildTag = rPrChildren.item(l).nodeName.substringAfter(":")
                                    if (rPrChildTag == "b") isBold = true
                                    if (rPrChildTag == "i") isItalic = true
                                }
                            } else if (rChildTag == "t") {
                                runTextSb.append(rChild.textContent)
                            }
                        }
                        val rText = runTextSb.toString()
                        if (rText.isNotEmpty()) {
                            runs.add(TextRun(rText, isBold = isBold, isItalic = isItalic))
                            fullTextSb.append(rText)
                        }
                    }
                }
                val text = fullTextSb.toString().trim()
                if (text.isNotEmpty()) {
                    totalChars += text.length
                    totalWords += text.split("\\s+".toRegex()).count { it.isNotBlank() }
                    if (headingLevel != null) {
                        elements.add(DocxElement.Heading(text, headingLevel))
                    } else {
                        elements.add(DocxElement.Paragraph(runs, text))
                    }
                }
            }
        }

        val readMinutes = (totalWords / 200).coerceAtLeast(1)

        return DocxDocument(
            title = file.nameWithoutExtension,
            elements = elements,
            wordCount = totalWords,
            characterCount = totalChars,
            estimatedReadMinutes = readMinutes
        )
    }

    private fun parseParagraph(parser: XmlPullParser): DocxElement? {
        var headingLevel: Int? = null
        var isBullet = false
        val runs = mutableListOf<TextRun>()
        val sbFull = StringBuilder()

        var insideP = true
        while (insideP && parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = localTag(parser.name)
                    when (tag) {
                        "pstyle" -> {
                            val styleVal = getAttr(parser, "val") ?: ""
                            if (styleVal.startsWith("Heading", ignoreCase = true) || styleVal.startsWith("Title", ignoreCase = true)) {
                                val digit = styleVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                headingLevel = digit.coerceIn(1, 4)
                            }
                        }
                        "numpr" -> {
                            isBullet = true
                        }
                        "r" -> {
                            val run = parseRun(parser)
                            if (run != null && run.text.isNotEmpty()) {
                                runs.add(run)
                                sbFull.append(run.text)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag(parser.name) == "p") {
                        insideP = false
                    }
                }
            }
            if (insideP) {
                parser.next()
            }
        }

        val text = sbFull.toString().trim()
        if (text.isEmpty() && headingLevel == null) return null

        return if (headingLevel != null && text.isNotEmpty()) {
            DocxElement.Heading(text = text, level = headingLevel)
        } else {
            DocxElement.Paragraph(runs = runs, fullText = text, isBullet = isBullet)
        }
    }

    private fun parseRun(parser: XmlPullParser): TextRun? {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var colorHex: String? = null
        val runText = StringBuilder()

        var insideR = true
        while (insideR && parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = localTag(parser.name)
                    when (tag) {
                        "b" -> isBold = true
                        "i" -> isItalic = true
                        "u" -> isUnderline = true
                        "color" -> {
                            val color = getAttr(parser, "val")
                            if (!color.isNullOrBlank() && color != "auto") {
                                colorHex = color
                            }
                        }
                        "t" -> {
                            runText.append(parser.nextText())
                        }
                        "br" -> {
                            runText.append("\n")
                        }
                        "tab" -> {
                            runText.append("    ")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag(parser.name) == "r") {
                        insideR = false
                    }
                }
            }
            if (insideR) {
                parser.next()
            }
        }

        return TextRun(
            text = runText.toString(),
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            colorHex = colorHex
        )
    }

    private fun parseTable(parser: XmlPullParser): DocxElement.Table {
        val rows = mutableListOf<List<String>>()

        var insideTable = true
        while (insideTable && parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (localTag(parser.name) == "tr") {
                        val rowCells = parseTableRow(parser)
                        if (rowCells.isNotEmpty()) {
                            rows.add(rowCells)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag(parser.name) == "tbl") {
                        insideTable = false
                    }
                }
            }
            if (insideTable) {
                parser.next()
            }
        }

        return DocxElement.Table(rows)
    }

    private fun parseTableRow(parser: XmlPullParser): List<String> {
        val cells = mutableListOf<String>()

        var insideRow = true
        while (insideRow && parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (localTag(parser.name) == "tc") {
                        val cellText = parseTableCell(parser)
                        cells.add(cellText)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag(parser.name) == "tr") {
                        insideRow = false
                    }
                }
            }
            if (insideRow) {
                parser.next()
            }
        }

        return cells
    }

    private fun parseTableCell(parser: XmlPullParser): String {
        val cellSb = StringBuilder()

        var insideCell = true
        while (insideCell && parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (localTag(parser.name) == "t") {
                        cellSb.append(parser.nextText()).append(" ")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag(parser.name) == "tc") {
                        insideCell = false
                    }
                }
            }
            if (insideCell) {
                parser.next()
            }
        }

        return cellSb.toString().trim()
    }
}
