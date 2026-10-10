package com.ct.explorer.ui.components.office

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize

val PreviewBarBlue = Color(0xFF2563EB)
val PreviewBarBg = Color.White
val PreviewBarBorder = Color(0xFFE2E8F0)

/**
 * Reusable Document Preview Control Bar matching authentic multi-page print preview specifications.
 * Features page size selector (A4, Letter, Legal with exact mm/in dimensions),
 * orientation toggle, and Print / Save as PDF action.
 */
@Composable
fun DocumentPreviewControlBar(
    title: String,
    documentType: String,
    selectedPaperSize: PaperSize,
    selectedOrientation: PageOrientation,
    pageCount: Int,
    onPaperSizeChange: (PaperSize) -> Unit,
    onOrientationChange: (PageOrientation) -> Unit,
    onPrintClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = PreviewBarBlue
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        color = PreviewBarBg,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, PreviewBarBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_preview_control_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Row 1: Header (Icon, Title, Document Name) & Print Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(text = "📄", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ڈاکیومنٹ پریویو",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Preview",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = accentColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = documentType.uppercase(),
                                    color = accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (title.isNotBlank()) "$title • $pageCount Pages" else "$pageCount Pages",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary Print / Save as PDF Button
                Button(
                    onClick = onPrintClick,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("preview_print_save_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Print or Save as PDF",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "پرنٹ / محفوظ کریں (PDF)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = PreviewBarBorder)
            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Page Size Selector (A4, Letter, Legal) & Orientation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Page Size Label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "صفحہ کا سائز:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "Size:",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Dropdown trigger showing current size with exact dimensions
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isDropdownExpanded = true }
                            .testTag("page_size_dropdown_trigger")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = selectedPaperSize.formattedSize,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        PaperSize.values().forEach { size ->
                            val isSelected = selectedPaperSize == size
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = size.formattedSize,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = if (isSelected) accentColor else Color(0xFF1E293B)
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = size.urduFormattedSize,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                },
                                onClick = {
                                    onPaperSizeChange(size)
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Quick Pills for Instant 1-Tap Switching (A4, Letter, Legal)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaperSize.values().forEach { size ->
                        val isSelected = selectedPaperSize == size
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) accentColor else Color(0xFFF1F5F9),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) accentColor else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPaperSizeChange(size) }
                                .testTag("paper_size_pill_${size.name.lowercase()}")
                        ) {
                            Text(
                                text = size.title,
                                color = if (isSelected) Color.White else Color(0xFF334155),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Orientation Switcher (Portrait / Landscape)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val next = if (selectedOrientation == PageOrientation.PORTRAIT)
                                PageOrientation.LANDSCAPE else PageOrientation.PORTRAIT
                            onOrientationChange(next)
                        }
                        .testTag("preview_orientation_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedOrientation == PageOrientation.PORTRAIT)
                                Icons.Default.StayCurrentPortrait else Icons.Default.StayCurrentLandscape,
                            contentDescription = "Orientation",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (selectedOrientation == PageOrientation.PORTRAIT) "پورٹریٹ Portrait" else "لینڈ اسکیپ Landscape",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }
    }
}
