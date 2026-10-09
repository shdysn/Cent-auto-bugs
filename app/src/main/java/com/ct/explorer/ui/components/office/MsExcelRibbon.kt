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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.utils.docx.PageMargins
import com.ct.explorer.utils.docx.PageOrientation
import com.ct.explorer.utils.docx.PaperSize

val ExcelBrandGreen = Color(0xFF107C41)
val ExcelDarkGreen = Color(0xFF0B582E)
val ExcelLightGreen = Color(0xFFE8F5E9)

enum class ExcelRibbonTab(val title: String) {
    PAGE_LAYOUT("Page Layout"),
    HOME("Home"),
    VIEW("View")
}

@Composable
fun MsExcelRibbon(
    title: String,
    sheetCount: Int,
    selectedPaperSize: PaperSize,
    selectedOrientation: PageOrientation,
    selectedMargins: PageMargins,
    isOriginalPageView: Boolean,
    onBackClick: () -> Unit,
    onPaperSizeChange: (PaperSize) -> Unit,
    onOrientationChange: (PageOrientation) -> Unit,
    onMarginsChange: (PageMargins) -> Unit,
    onTogglePageView: () -> Unit,
    onPrintClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenExternalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(ExcelRibbonTab.PAGE_LAYOUT) }
    var isRibbonExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ExcelBrandGreen)
    ) {
        // 1. Excel Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "EXCEL",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Column {
                    Text(
                        text = title.ifEmpty { "Spreadsheet.xlsx" },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "AutoSave • $sheetCount sheets",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }

            // Action icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onPrintClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onOpenExternalClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open Office", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }

        // 2. Ribbon Tabs: [Page Layout] [Home] [View]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ExcelDarkGreen)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExcelRibbonTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                Surface(
                    color = if (isSelected) Color(0xFFF8FAFC) else Color.Transparent,
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    modifier = Modifier.clickable {
                        selectedTab = tab
                        isRibbonExpanded = true
                    }
                ) {
                    Text(
                        text = tab.title,
                        color = if (isSelected) ExcelBrandGreen else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { isRibbonExpanded = !isRibbonExpanded },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isRibbonExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Ribbon",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 3. Ribbon Controls
        AnimatedVisibility(visible = isRibbonExpanded) {
            Surface(
                color = Color(0xFFF8FAFC),
                border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        ExcelRibbonTab.PAGE_LAYOUT -> {
                            // Paper Size
                            ExcelSection("Page Size") {
                                PaperSize.values().forEach { size ->
                                    val isSel = selectedPaperSize == size
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { onPaperSizeChange(size) },
                                        label = { Text(size.title, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ExcelLightGreen,
                                            selectedLabelColor = ExcelBrandGreen
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }

                            // Orientation
                            ExcelSection("Orientation") {
                                PageOrientation.values().forEach { orient ->
                                    val isSel = selectedOrientation == orient
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { onOrientationChange(orient) },
                                        label = { Text(orient.title, fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (orient == PageOrientation.LANDSCAPE) Icons.Default.StayCurrentLandscape else Icons.Default.StayCurrentPortrait,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ExcelLightGreen,
                                            selectedLabelColor = ExcelBrandGreen
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }

                            // Margins
                            ExcelSection("Margins") {
                                PageMargins.values().forEach { margins ->
                                    val isSel = selectedMargins == margins
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { onMarginsChange(margins) },
                                        label = { Text(margins.title, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ExcelLightGreen,
                                            selectedLabelColor = ExcelBrandGreen
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                        }

                        ExcelRibbonTab.HOME -> {
                            ExcelSection("Font & Align") {
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, Color(0xFFCBD5E1))
                                ) {
                                    Row(modifier = Modifier.padding(2.dp)) {
                                        Text("B", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        Text("U", textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }

                        ExcelRibbonTab.VIEW -> {
                            ExcelSection("Workbook Views") {
                                FilterChip(
                                    selected = isOriginalPageView,
                                    onClick = onTogglePageView,
                                    label = { Text("Page Layout (A4/Letter)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ExcelLightGreen,
                                        selectedLabelColor = ExcelBrandGreen
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                                FilterChip(
                                    selected = !isOriginalPageView,
                                    onClick = onTogglePageView,
                                    label = { Text("Normal Grid", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ExcelLightGreen,
                                        selectedLabelColor = ExcelBrandGreen
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcelSection(
    title: String,
    content: @Composable RowScope.() -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 9.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.Medium
        )
    }
}
