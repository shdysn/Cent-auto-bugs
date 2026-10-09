package com.ct.explorer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.utils.docx.DocxElement
import com.ct.explorer.utils.docx.DocxPaginator
import com.ct.explorer.utils.docx.DocxParser
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize
import com.ct.explorer.utils.excel.ExcelParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Universal Print & "Print to PDF" Helper for Cent File Manager.
 * Seamlessly hooks into Android's native PrintManager to display the system
 * print / "Save as PDF" dialog for all file formats with zero external dependencies.
 */
object PrintHelper {

    /**
     * Entrypoint: Prints any file (PDF, Image, Word, Excel, HTML, Text, Code).
     */
    fun printFile(context: Context, file: File) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Cannot print: File is empty or does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        val ext = file.extension.lowercase()
        val item = FileItem(file)

        when {
            ext == "pdf" -> {
                printPdfFile(context, file)
            }
            item.category == FileCategory.IMAGE || ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif") -> {
                printImageFile(context, file)
            }
            ext in listOf("html", "htm") -> {
                printHtmlFile(context, file)
            }
            ext in listOf("docx", "doc") -> {
                printWordFile(context, file)
            }
            ext in listOf("xlsx", "xls", "csv") -> {
                printSpreadsheetFile(context, file)
            }
            else -> {
                // Text, Code, or any other file
                printTextFile(context, file)
            }
        }
    }

    /**
     * Prints a native PDF file by direct streaming into Android's system Print Spooler.
     */
    fun printPdfFile(context: Context, file: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: run {
            Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "${file.nameWithoutExtension}_Print"

        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()

                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Missing destination file descriptor")
                    return
                }

                var input: FileInputStream? = null
                var output: FileOutputStream? = null

                try {
                    input = FileInputStream(file)
                    output = FileOutputStream(destination.fileDescriptor)

                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            return
                        }
                        output.write(buffer, 0, bytesRead)
                    }

                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage ?: "Failed to write PDF print data")
                } finally {
                    try { input?.close() } catch (_: Exception) {}
                    try { output?.close() } catch (_: Exception) {}
                }
            }
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

        printManager.print(jobName, printAdapter, attributes)
    }

    /**
     * Prints an image file by drawing it onto a standard PrintedPdfDocument page.
     */
    fun printImageFile(context: Context, file: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "${file.nameWithoutExtension}_Image_Print"

        val adapter = object : PrintDocumentAdapter() {
            private var printedPdfDocument: PrintedPdfDocument? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }

                printedPdfDocument = PrintedPdfDocument(context, newAttributes ?: PrintAttributes.Builder().build())

                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                val pdfDoc = printedPdfDocument
                if (pdfDoc == null || destination == null) {
                    callback?.onWriteFailed("Printer setup failed")
                    return
                }

                try {
                    val page: PdfDocument.Page = pdfDoc.startPage(0)
                    val canvas = page.canvas

                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    val reqWidth = canvas.width
                    val reqHeight = canvas.height
                    var sampleSize = 1
                    while (options.outWidth / (sampleSize * 2) >= reqWidth && options.outHeight / (sampleSize * 2) >= reqHeight) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)

                    if (bitmap != null) {
                        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                        val scale = minOf(
                            reqWidth.toFloat() / bitmap.width.toFloat(),
                            reqHeight.toFloat() / bitmap.height.toFloat()
                        )
                        val scaledW = (bitmap.width * scale).toInt()
                        val scaledH = (bitmap.height * scale).toInt()
                        val left = (reqWidth - scaledW) / 2
                        val top = (reqHeight - scaledH) / 2
                        val dstRect = Rect(left, top, left + scaledW, top + scaledH)

                        canvas.drawBitmap(bitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        bitmap.recycle()
                    }

                    pdfDoc.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                    }

                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                } finally {
                    pdfDoc.close()
                    printedPdfDocument = null
                }
            }
        }

        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    /**
     * Prints HTML document via WebView.createPrintDocumentAdapter.
     */
    fun printHtmlFile(context: Context, file: File) {
        val htmlContent = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
        printHtmlContent(context, file.nameWithoutExtension, htmlContent, file.parentFile)
    }

    /**
     * Prints raw HTML string with optional base folder for images/CSS and target paper media size.
     */
    fun printHtmlContent(
        context: Context,
        jobTitle: String,
        html: String,
        baseDir: File? = null,
        mediaSize: PrintAttributes.MediaSize = PrintAttributes.MediaSize.ISO_A4
    ) {
        Handler(Looper.getMainLooper()).post {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return@post
                val webView = WebView(context)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val printAdapter = webView.createPrintDocumentAdapter(jobTitle)
                        val attributes = PrintAttributes.Builder()
                            .setMediaSize(mediaSize)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                            .build()
                        printManager.print("$jobTitle-Print", printAdapter, attributes)
                    }
                }

                val baseUrl = baseDir?.toURI()?.toString() ?: "file:///"
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Prints plain text or code file by formatting into clean, paginated printable HTML.
     */
    fun printTextFile(context: Context, file: File) {
        val content = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            "Unable to read file content"
        }
        printTextContent(context, file.nameWithoutExtension, content, file.name)
    }

    /**
     * Prints text content wrapped in an elegant printable HTML layout.
     */
    fun printTextContent(context: Context, jobTitle: String, text: String, fileName: String = jobTitle) {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val formattedHtml = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>$fileName</title>
              <style>
                @page { margin: 20mm 15mm; }
                body {
                  font-family: 'Consolas', 'Courier New', monospace;
                  font-size: 11pt;
                  line-height: 1.5;
                  color: #111;
                  margin: 0;
                  padding: 0;
                }
                header {
                  border-bottom: 2px solid #2563eb;
                  padding-bottom: 8px;
                  margin-bottom: 18px;
                  display: flex;
                  justify-content: space-between;
                  font-family: sans-serif;
                }
                .title { font-weight: bold; font-size: 14pt; color: #1e293b; }
                .meta { font-size: 9pt; color: #64748b; }
                pre {
                  white-space: pre-wrap;
                  word-break: break-word;
                  margin: 0;
                }
              </style>
            </head>
            <body>
              <header>
                <div class="title">$fileName</div>
                <div class="meta">Cent File Manager • Printed Document</div>
              </header>
              <pre>$escaped</pre>
            </body>
            </html>
        """.trimIndent()

        printHtmlContent(context, jobTitle, formattedHtml)
    }

    /**
     * Prints MS Word (.docx) document formatted with styled headings and tables into HTML print spooler,
     * fully supporting physical page sizes (A4, Letter, Legal) and orientations.
     */
    fun printWordFile(
        context: Context,
        file: File,
        paperSize: PaperSize = PaperSize.A4,
        orientation: PageOrientation = PageOrientation.PORTRAIT
    ) {
        val docResult = DocxParser.parse(file)
        if (docResult.isFailure) {
            // Fallback to text
            printTextFile(context, file)
            return
        }

        val doc = docResult.getOrNull() ?: return
        val paginatedPages = DocxPaginator.paginate(doc, paperSize, orientation)
        val sb = StringBuilder()

        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>${doc.title}</title>
              <style>
                @page {
                  size: ${paperSize.cssPageSize} ${orientation.name.lowercase()};
                  margin: 20mm 16mm;
                }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                  font-size: 11pt;
                  line-height: 1.5;
                  color: #0f172a;
                  margin: 0;
                  padding: 0;
                }
                .page {
                  box-sizing: border-box;
                  width: 100%;
                  position: relative;
                }
                .page-header {
                  display: flex;
                  justify-content: space-between;
                  font-size: 8.5pt;
                  color: #64748b;
                  border-bottom: 1px solid #e2e8f0;
                  padding-bottom: 4px;
                  margin-bottom: 14px;
                }
                .page-footer {
                  display: flex;
                  justify-content: space-between;
                  font-size: 8.5pt;
                  color: #64748b;
                  border-top: 1px solid #e2e8f0;
                  padding-top: 6px;
                  margin-top: 20px;
                }
                .page-break {
                  page-break-after: always;
                  break-after: page;
                  height: 0;
                  margin: 0;
                  padding: 0;
                }
                h1.doc-title {
                  color: #1e3a8a;
                  font-size: 18pt;
                  border-bottom: 1.5px solid #cbd5e1;
                  padding-bottom: 6px;
                  margin-top: 0;
                  margin-bottom: 14px;
                }
                h1 { color: #1e3a8a; font-size: 16pt; margin-top: 14px; margin-bottom: 8px; }
                h2 { color: #1d4ed8; font-size: 14pt; margin-top: 12px; margin-bottom: 6px; }
                h3 { color: #2563eb; font-size: 12pt; margin-top: 10px; margin-bottom: 4px; }
                p { margin-top: 0; margin-bottom: 10px; }
                table {
                  width: 100%;
                  border-collapse: collapse;
                  margin: 12px 0;
                }
                th, td {
                  border: 1px solid #cbd5e1;
                  padding: 6px 10px;
                  text-align: left;
                  font-size: 10pt;
                }
                th { background-color: #f1f5f9; font-weight: bold; }
                tr:nth-child(even) { background-color: #f8fafc; }
                .bullet { margin-left: 18px; }
              </style>
            </head>
            <body>
        """.trimIndent())

        paginatedPages.forEachIndexed { pageIdx, page ->
            sb.append("<div class=\"page\">\n")
            sb.append("<div class=\"page-header\"><span>${doc.title}</span><span>${paperSize.title} • ${orientation.title}</span></div>\n")

            if (pageIdx == 0 && doc.title.isNotBlank()) {
                sb.append("<h1 class=\"doc-title\">${doc.title}</h1>\n")
            }

            for (el in page.elements) {
                when (el) {
                    is DocxElement.Heading -> {
                        sb.append("<h${el.level}>${el.text}</h${el.level}>\n")
                    }
                    is DocxElement.Paragraph -> {
                        val pClass = if (el.isBullet) " class=\"bullet\"" else ""
                        val bulletPrefix = if (el.isBullet) "• " else ""
                        sb.append("<p$pClass>$bulletPrefix${el.fullText}</p>\n")
                    }
                    is DocxElement.Table -> {
                        sb.append("<table>\n")
                        el.rows.forEachIndexed { rIdx, row ->
                            sb.append("<tr>\n")
                            row.forEach { cell ->
                                val tag = if (rIdx == 0) "th" else "td"
                                sb.append("<$tag>$cell</$tag>\n")
                            }
                            sb.append("</tr>\n")
                        }
                        sb.append("</table>\n")
                    }
                    DocxElement.Divider -> {
                        sb.append("<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 12px 0;\" />\n")
                    }
                    DocxElement.PageBreak -> {
                        // Handled by pagination container
                    }
                }
            }

            sb.append("<div class=\"page-footer\"><span>${doc.title}</span><span>Page ${page.pageNumber} of ${paginatedPages.size}</span></div>\n")
            sb.append("</div>\n")

            if (pageIdx < paginatedPages.size - 1) {
                sb.append("<div class=\"page-break\"></div>\n")
            }
        }

        sb.append("</body></html>")
        val printMedia = paperSize.getPrintMediaSize(orientation)
        printHtmlContent(context, doc.title, sb.toString(), null, printMedia)
    }

    /**
     * Prints an Excel or CSV spreadsheet formatted as a clean grid table into HTML print spooler.
     */
    fun printSpreadsheetFile(context: Context, file: File) {
        val result = ExcelParser.parse(file)
        if (result.isFailure) {
            printTextFile(context, file)
            return
        }

        val workbook = result.getOrNull() ?: return
        val sb = StringBuilder()

        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>${workbook.title}</title>
              <style>
                @page { size: landscape; margin: 15mm 10mm; }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                  font-size: 10pt;
                  color: #0f172a;
                  margin: 0;
                }
                h2 { color: #15803d; border-bottom: 2px solid #16a34a; padding-bottom: 4px; margin-top: 24px; }
                table {
                  width: 100%;
                  border-collapse: collapse;
                  margin: 12px 0 24px 0;
                }
                th, td {
                  border: 1px solid #94a3b8;
                  padding: 6px 8px;
                  font-size: 9pt;
                }
                th {
                  background-color: #dcfce7;
                  color: #166534;
                  font-weight: bold;
                }
                tr:nth-child(even) { background-color: #f8fafc; }
              </style>
            </head>
            <body>
              <h1 style="color: #166534;">${workbook.title}</h1>
        """.trimIndent())

        for (sheet in workbook.sheets) {
            sb.append("<h2>${sheet.name}</h2>\n")
            sb.append("<table>\n")
            sheet.rows.forEachIndexed { rIdx, row ->
                sb.append("<tr>\n")
                for (c in 0 until sheet.maxColumns) {
                    val cellVal = row.getOrNull(c).orEmpty()
                    val tag = if (rIdx == 0) "th" else "td"
                    sb.append("<$tag>$cellVal</$tag>\n")
                }
                sb.append("</tr>\n")
            }
            sb.append("</table>\n")
        }

        sb.append("</body></html>")
        printHtmlContent(context, workbook.title, sb.toString())
    }
}
