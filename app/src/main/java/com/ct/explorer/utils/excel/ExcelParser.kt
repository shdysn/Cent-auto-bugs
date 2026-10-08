package com.ct.explorer.utils.excel

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ExcelSheet(
    val name: String,
    val rows: List<List<String>>,
    val maxColumns: Int
)

data class ExcelWorkbook(
    val title: String,
    val sheets: List<ExcelSheet>,
    val fileType: String, // "XLSX" or "CSV"
    val totalCells: Int
)

object ExcelParser {

    private fun createPullParser(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        return factory.newPullParser()
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

    fun parse(file: File): Result<ExcelWorkbook> = runCatching {
        if (!file.exists()) throw IllegalArgumentException("File does not exist: ${file.name}")

        val ext = file.extension.lowercase()
        if (ext == "csv") {
            return@runCatching parseCsv(file)
        } else if (ext == "xlsx") {
            return@runCatching parseXlsx(file)
        } else {
            throw IllegalArgumentException("Unsupported spreadsheet format: $ext")
        }
    }

    private fun parseCsv(file: File): ExcelWorkbook {
        val rows = mutableListOf<List<String>>()
        var maxCols = 0
        var totalCells = 0

        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    val parsedLine = parseCsvLine(line)
                    rows.add(parsedLine)
                    maxCols = maxOf(maxCols, parsedLine.size)
                    totalCells += parsedLine.count { it.isNotBlank() }
                }
                line = reader.readLine()
            }
        }

        val sheet = ExcelSheet(
            name = file.nameWithoutExtension,
            rows = rows,
            maxColumns = maxCols
        )

        return ExcelWorkbook(
            title = file.nameWithoutExtension,
            sheets = listOf(sheet),
            fileType = "CSV",
            totalCells = totalCells
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false

        val delimiter = when {
            line.contains("\t") -> '\t'
            line.contains(";") && !line.contains(",") -> ';'
            else -> ','
        }

        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    insideQuotes = !insideQuotes
                }
            } else if (c == delimiter && !insideQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun parseXlsx(file: File): ExcelWorkbook {
        val sharedStrings = mutableListOf<String>()
        val sheetNames = mutableListOf<String>()
        val sheetXmlMap = mutableMapOf<String, ByteArray>()

        ZipInputStream(FileInputStream(file)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> {
                        val bytes = zipIn.readBytes()
                        sharedStrings.addAll(parseSharedStrings(bytes))
                    }
                    name == "xl/workbook.xml" -> {
                        val bytes = zipIn.readBytes()
                        sheetNames.addAll(parseWorkbookSheets(bytes))
                    }
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                        val bytes = zipIn.readBytes()
                        sheetXmlMap[name] = bytes
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        val sheets = mutableListOf<ExcelSheet>()
        var globalTotalCells = 0

        val sortedSheetKeys = sheetXmlMap.keys.sortedBy { key ->
            key.filter { it.isDigit() }.toIntOrNull() ?: 0
        }

        for ((index, key) in sortedSheetKeys.withIndex()) {
            val sheetBytes = sheetXmlMap[key] ?: continue
            val sheetName = sheetNames.getOrNull(index) ?: "Sheet ${index + 1}"
            val (sheetRows, maxCols, cellCount) = parseWorksheet(sheetBytes, sharedStrings)
            globalTotalCells += cellCount
            sheets.add(
                ExcelSheet(
                    name = sheetName,
                    rows = sheetRows,
                    maxColumns = maxCols
                )
            )
        }

        if (sheets.isEmpty()) {
            sheets.add(ExcelSheet(name = "Sheet 1", rows = emptyList(), maxColumns = 0))
        }

        return ExcelWorkbook(
            title = file.nameWithoutExtension,
            sheets = sheets,
            fileType = "XLSX",
            totalCells = globalTotalCells
        )
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = createPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }

        var eventType = parser.eventType
        val currentStr = StringBuilder()
        var insideSi = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = localTag(parser.name)
                    if (tag == "si") {
                        insideSi = true
                        currentStr.clear()
                    } else if (insideSi && tag == "t") {
                        currentStr.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag(parser.name) == "si") {
                        strings.add(currentStr.toString())
                        insideSi = false
                    }
                }
            }
            eventType = parser.next()
        }

        return strings
    }

    private fun parseWorkbookSheets(bytes: ByteArray): List<String> {
        val sheetNames = mutableListOf<String>()
        val parser = createPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && localTag(parser.name) == "sheet") {
                val name = getAttr(parser, "name")
                if (!name.isNullOrBlank()) {
                    sheetNames.add(name)
                }
            }
            eventType = parser.next()
        }
        return sheetNames
    }

    private fun parseWorksheet(
        bytes: ByteArray,
        sharedStrings: List<String>
    ): Triple<List<List<String>>, Int, Int> {
        val rows = mutableListOf<List<String>>()
        var maxColumns = 0
        var populatedCells = 0

        val parser = createPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }

        var eventType = parser.eventType
        var currentRowCells = mutableMapOf<Int, String>()
        var currentCellCol = 0
        var currentCellType = ""
        var insideCell = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = localTag(parser.name)
                    when (tag) {
                        "row" -> {
                            currentRowCells = mutableMapOf()
                        }
                        "c" -> {
                            insideCell = true
                            val ref = getAttr(parser, "r") ?: ""
                            currentCellCol = columnRefToIndex(ref)
                            currentCellType = getAttr(parser, "t") ?: ""
                        }
                        "v" -> {
                            if (insideCell) {
                                val value = parser.nextText()
                                val resolvedValue = when (currentCellType) {
                                    "s" -> {
                                        val idx = value.toIntOrNull() ?: -1
                                        sharedStrings.getOrElse(idx) { value }
                                    }
                                    else -> value
                                }
                                currentRowCells[currentCellCol] = resolvedValue
                                populatedCells++
                            }
                        }
                        "t" -> {
                            // Inline string <is><t>
                            if (insideCell) {
                                val text = parser.nextText()
                                currentRowCells[currentCellCol] = text
                                populatedCells++
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = localTag(parser.name)
                    when (tag) {
                        "c" -> {
                            insideCell = false
                        }
                        "row" -> {
                            if (currentRowCells.isNotEmpty()) {
                                val maxColInRow = (currentRowCells.keys.maxOrNull() ?: 0) + 1
                                maxColumns = maxOf(maxColumns, maxColInRow)

                                val rowList = ArrayList<String>(maxColInRow)
                                for (col in 0 until maxColInRow) {
                                    rowList.add(currentRowCells[col] ?: "")
                                }
                                rows.add(rowList)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return Triple(rows, maxColumns, populatedCells)
    }

    /**
     * Converts an Excel cell reference like "A1" or "BC15" to 0-based column index.
     */
    fun columnRefToIndex(ref: String): Int {
        val colPart = ref.filter { it.isLetter() }.uppercase()
        if (colPart.isEmpty()) return 0
        var col = 0
        for (ch in colPart) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return (col - 1).coerceAtLeast(0)
    }

    /**
     * Converts a 0-based column index to letter like 0 -> "A", 27 -> "AB".
     */
    fun indexToColumnLetter(index: Int): String {
        var num = index + 1
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A' + rem))
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }
}
