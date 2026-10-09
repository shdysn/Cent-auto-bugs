package com.ct.explorer.ui.components.office

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.utils.text.TextPage

/**
 * Authentic MS Word / Print style paper sheet for Text files and Code documents.
 */
@Composable
fun MsTextPageSheet(
    page: TextPage,
    title: String,
    modifier: Modifier = Modifier
) {
    val aspectRatio = page.paperSize.widthToHeightRatio(page.orientation)
    val paddingDp = page.margins.paddingDp.dp

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        border = BorderStroke(0.75.dp, Color(0xFFCBD5E1)),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // L-shaped Corner Crop Marks
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padPx = paddingDp.toPx()
                val markLen = 12.dp.toPx()
                val strokeW = 1.0.dp.toPx()
                val markColor = Color(0xFF94A3B8)

                // Top-Left ┌
                drawLine(markColor, Offset(padPx - markLen, padPx), Offset(padPx, padPx), strokeW)
                drawLine(markColor, Offset(padPx, padPx - markLen), Offset(padPx, padPx), strokeW)

                // Top-Right ┐
                drawLine(markColor, Offset(size.width - padPx, padPx), Offset(size.width - padPx + markLen, padPx), strokeW)
                drawLine(markColor, Offset(size.width - padPx, padPx - markLen), Offset(size.width - padPx, padPx), strokeW)

                // Bottom-Left └
                drawLine(markColor, Offset(padPx - markLen, size.height - padPx), Offset(padPx, size.height - padPx), strokeW)
                drawLine(markColor, Offset(padPx, size.height - padPx), Offset(padPx, size.height - padPx + markLen), strokeW)

                // Bottom-Right ┘
                drawLine(markColor, Offset(size.width - padPx, size.height - padPx), Offset(size.width - padPx + markLen, size.height - padPx), strokeW)
                drawLine(markColor, Offset(size.width - padPx, size.height - padPx), Offset(size.width - padPx, size.height - padPx + markLen), strokeW)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingDp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 9.5.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "${page.paperSize.title} • Lines ${page.startLineNumber}–${page.endLineNumber}",
                            fontSize = 8.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Text Lines
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        page.lines.forEachIndexed { idx, line ->
                            val lineNum = page.startLineNumber + idx
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "$lineNum",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    text = line,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Footer
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 9.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "Page ${page.pageNumber} of ${page.totalPages}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}
