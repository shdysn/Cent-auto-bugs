package com.ct.explorer.ui.components.office

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.utils.docx.PageMargins
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize

val OfficeRulerGray = Color(0xFFD6D3D1)
val OfficeRulerWhite = Color(0xFFFFFFFF)
val OfficeRulerBorder = Color(0xFFCBD5E1)
val OfficeRulerTicks = Color(0xFF64748B)
val OfficeIndentColor = Color(0xFF475569)

/**
 * Authentic Microsoft Word Horizontal Ruler with tick marks, margin zones, and indent markers.
 */
@Composable
fun MsWordHorizontalRuler(
    paperSize: PaperSize,
    orientation: PageOrientation,
    margins: PageMargins,
    modifier: Modifier = Modifier
) {
    val totalWidthInches = if (orientation == PageOrientation.PORTRAIT) paperSize.widthInches else paperSize.heightInches
    val leftMarginInches = margins.leftInch
    val rightMarginInches = margins.rightInch
    val printableInches = (totalWidthInches - leftMarginInches - rightMarginInches).coerceAtLeast(1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFFF1F5F9))
            .border(0.5.dp, OfficeRulerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left non-printable margin shaded area
            Box(
                modifier = Modifier
                    .weight(leftMarginInches / totalWidthInches)
                    .fillMaxHeight()
                    .background(OfficeRulerGray)
                    .border(0.5.dp, OfficeRulerBorder)
            )

            // Printable body with numbered tick marks & indent markers
            Box(
                modifier = Modifier
                    .weight(printableInches / totalWidthInches)
                    .fillMaxHeight()
                    .background(OfficeRulerWhite)
                    .border(0.5.dp, OfficeRulerBorder)
            ) {
                // Tick Marks & Numbers
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val widthPx = size.width
                    val heightPx = size.height
                    val totalUnits = printableInches.toInt().coerceAtLeast(1)

                    // Draw tick marks across the printable area
                    val ticksPerInch = 8
                    val totalTicks = (printableInches * ticksPerInch).toInt()

                    for (i in 0..totalTicks) {
                        val x = (i.toFloat() / totalTicks) * widthPx
                        val isInch = i % ticksPerInch == 0
                        val isHalf = i % (ticksPerInch / 2) == 0
                        val isQuarter = i % (ticksPerInch / 4) == 0

                        val tickHeight = when {
                            isInch -> heightPx * 0.55f
                            isHalf -> heightPx * 0.38f
                            isQuarter -> heightPx * 0.25f
                            else -> heightPx * 0.16f
                        }

                        drawLine(
                            color = OfficeRulerTicks,
                            start = Offset(x, heightPx - tickHeight),
                            end = Offset(x, heightPx),
                            strokeWidth = if (isInch) 1.5f else 1.0f
                        )
                    }

                    // Left First-Line Indent marker (Downward triangle ▼ at top)
                    val trianglePathTop = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(10f, 0f)
                        lineTo(5f, 10f)
                        close()
                    }
                    drawPath(trianglePathTop, OfficeIndentColor)

                    // Left Hanging Indent marker (Upward triangle ▲ at bottom)
                    val trianglePathBottom = Path().apply {
                        moveTo(0f, heightPx)
                        lineTo(10f, heightPx)
                        lineTo(5f, heightPx - 10f)
                        close()
                    }
                    drawPath(trianglePathBottom, OfficeIndentColor)

                    // Right Margin Indent marker (Upward triangle ▲ at bottom-right)
                    val rightTrianglePath = Path().apply {
                        moveTo(widthPx - 10f, heightPx)
                        lineTo(widthPx, heightPx)
                        lineTo(widthPx - 5f, heightPx - 10f)
                        close()
                    }
                    drawPath(rightTrianglePath, OfficeIndentColor)
                }

                // Ruler inch numbers: 1, 2, 3, 4, 5, 6...
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    val maxNumbers = printableInches.toInt()
                    for (num in 1..maxNumbers) {
                        Text(
                            text = "$num",
                            color = OfficeRulerTicks,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Right non-printable margin shaded area
            Box(
                modifier = Modifier
                    .weight(rightMarginInches / totalWidthInches)
                    .fillMaxHeight()
                    .background(OfficeRulerGray)
                    .border(0.5.dp, OfficeRulerBorder)
            )
        }
    }
}
