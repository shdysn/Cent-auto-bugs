package com.ct.explorer.ui.components.office

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.utils.docx.DocxElement
import com.ct.explorer.utils.docx.DocxPage
import com.ct.explorer.utils.docx.PageMargins
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize

val WordPageShadowBorder = Color(0xFFCBD5E1)
val WordCropMarkColor = Color(0xFF94A3B8)
val WordHeaderFooterText = Color(0xFF64748B)

/**
 * Authentic Microsoft Word Print Page Sheet with exact aspect ratio, margin crop marks,
 * and true document formatting.
 */
@Composable
fun MsWordPageSheet(
    page: DocxPage,
    documentTitle: String,
    fontSizeMultiplier: Float,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val aspectRatio = page.paperSize.widthToHeightRatio(page.orientation)
    val paddingDp = page.margins.paddingDp.dp

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(0.75.dp, WordPageShadowBorder),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Authentic L-shaped Margin Crop Marks at the 4 corners of the printable area
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padPx = paddingDp.toPx()
                val markLen = 12.dp.toPx()
                val strokeW = 1.0.dp.toPx()

                // Top-Left ┌
                drawLine(WordCropMarkColor, Offset(padPx - markLen, padPx), Offset(padPx, padPx), strokeW)
                drawLine(WordCropMarkColor, Offset(padPx, padPx - markLen), Offset(padPx, padPx), strokeW)

                // Top-Right ┐
                drawLine(WordCropMarkColor, Offset(size.width - padPx, padPx), Offset(size.width - padPx + markLen, padPx), strokeW)
                drawLine(WordCropMarkColor, Offset(size.width - padPx, padPx - markLen), Offset(size.width - padPx, padPx), strokeW)

                // Bottom-Left └
                drawLine(WordCropMarkColor, Offset(padPx - markLen, size.height - padPx), Offset(padPx, size.height - padPx), strokeW)
                drawLine(WordCropMarkColor, Offset(padPx, size.height - padPx), Offset(padPx, size.height - padPx + markLen), strokeW)

                // Bottom-Right ┘
                drawLine(WordCropMarkColor, Offset(size.width - padPx, size.height - padPx), Offset(size.width - padPx + markLen, size.height - padPx), strokeW)
                drawLine(WordCropMarkColor, Offset(size.width - padPx, size.height - padPx), Offset(size.width - padPx, size.height - padPx + markLen), strokeW)
            }

            // 2. Page Content Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingDp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Zone
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = documentTitle,
                            fontSize = 9.5.sp,
                            color = WordHeaderFooterText,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "${page.paperSize.formattedSize} • ${page.orientation.title}",
                            fontSize = 8.5.sp,
                            color = WordHeaderFooterText.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (page.isFirstPage && documentTitle.isNotBlank()) {
                        Text(
                            text = documentTitle,
                            fontSize = (22.sp * fontSizeMultiplier),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF185ABD),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFFCBD5E1), modifier = Modifier.padding(bottom = 8.dp))
                    }

                    // Elements in this physical page
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        page.elements.forEach { element ->
                            when (element) {
                                is DocxElement.Heading -> {
                                    Text(
                                        text = element.text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (when (element.level) {
                                            1 -> 18.sp
                                            2 -> 15.sp
                                            else -> 13.5.sp
                                        }) * fontSizeMultiplier,
                                        color = when (element.level) {
                                            1 -> Color(0xFF185ABD)
                                            2 -> Color(0xFF2B579A)
                                            else -> Color(0xFF0F172A)
                                        },
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    if (element.level == 1) {
                                        HorizontalDivider(thickness = 0.75.dp, color = Color(0xFFE2E8F0), modifier = Modifier.padding(top = 2.dp))
                                    }
                                }

                                is DocxElement.Paragraph -> {
                                    val annotated = buildAnnotatedString {
                                        if (element.isBullet) {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                                                append("  •  ")
                                            }
                                        }
                                        if (element.runs.isNotEmpty()) {
                                            for (run in element.runs) {
                                                val isMatch = searchQuery.isNotBlank() && run.text.contains(searchQuery, ignoreCase = true)
                                                val runColor = parseDocxHex(run.colorHex) ?: Color(0xFF0F172A)
                                                withStyle(
                                                    SpanStyle(
                                                        fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                                                        fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                        textDecoration = if (run.isUnderline) TextDecoration.Underline else null,
                                                        background = if (isMatch) Color(0xFFFEF08A) else Color.Transparent,
                                                        color = if (isMatch) Color.Black else runColor
                                                    )
                                                ) {
                                                    append(run.text)
                                                }
                                            }
                                        } else {
                                            append(element.fullText)
                                        }
                                    }

                                    Text(
                                        text = annotated,
                                        fontSize = 12.5.sp * fontSizeMultiplier,
                                        lineHeight = 17.5.sp * fontSizeMultiplier,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                is DocxElement.Table -> {
                                    MsWordTable(table = element, fontSizeMultiplier = fontSizeMultiplier)
                                }

                                DocxElement.Divider -> {
                                    HorizontalDivider(color = Color(0xFFCBD5E1), modifier = Modifier.padding(vertical = 4.dp))
                                }

                                DocxElement.PageBreak -> {}
                            }
                        }
                    }
                }

                // Bottom Footer Zone
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (documentTitle.isNotBlank()) documentTitle else "دستاویز",
                            fontSize = 9.sp,
                            color = WordHeaderFooterText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "صفحہ ${page.pageNumber} از ${page.totalPages} • Page ${page.pageNumber} of ${page.totalPages}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WordHeaderFooterText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MsWordTable(
    table: DocxElement.Table,
    fontSizeMultiplier: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0xFF8EA9DB))
        ) {
            table.rows.forEachIndexed { rIdx, row ->
                val isHeader = rIdx == 0
                Row(
                    modifier = Modifier.background(
                        if (isHeader) Color(0xFFD9E1F2) else if (rIdx % 2 == 1) Color(0xFFF9FAFB) else Color.White
                    )
                ) {
                    row.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 90.dp, max = 160.dp)
                                .border(0.5.dp, Color(0xFFCBD5E1))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = cell,
                                fontSize = (if (isHeader) 11.5.sp else 11.sp) * fontSizeMultiplier,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHeader) Color(0xFF1F4E79) else Color(0xFF0F172A),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseDocxHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        val clean = hex.removePrefix("#").trim()
        val parsed = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or parsed)
        } else if (clean.length == 8) {
            Color(parsed)
        } else null
    } catch (_: Exception) {
        null
    }
}
