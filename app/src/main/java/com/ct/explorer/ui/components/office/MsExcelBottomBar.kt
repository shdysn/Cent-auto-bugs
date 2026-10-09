package com.ct.explorer.ui.components.office

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.utils.excel.ExcelSheet

val TabBarBg = Color(0xFFF1F5F9)
val ActiveTabBg = Color(0xFFFFFFFF)
val ActiveTabLine = Color(0xFF107C41)
val TabBorder = Color(0xFFCBD5E1)
val ExcelStatusBarBg = Color(0xFF107C41)

@Composable
fun MsExcelBottomBar(
    sheets: List<ExcelSheet>,
    selectedSheetIndex: Int,
    stats: Triple<Int, Int, Pair<Double, Double>?>?,
    pageScale: Float,
    isOriginalPageView: Boolean,
    onSheetSelect: (Int) -> Unit,
    onTogglePageView: () -> Unit,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 1. Sheet Tab Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TabBarBg)
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation arrows
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NavigateBefore,
                    contentDescription = "Previous Sheet",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Default.NavigateNext,
                    contentDescription = "Next Sheet",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Sheet Tabs
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sheets.forEachIndexed { idx, sheet ->
                    val isActive = selectedSheetIndex == idx
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(if (isActive) ActiveTabBg else Color.Transparent)
                            .clickable { onSheetSelect(idx) }
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = sheet.name,
                            fontSize = 11.5.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) ActiveTabLine else Color(0xFF334155)
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .background(ActiveTabLine)
                            )
                        }
                    }
                }
            }

            // Add sheet icon (+)
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Sheet",
                tint = Color(0xFF64748B),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 6.dp)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = TabBorder)

        // 2. Status Bar: Ready, SUM, AVG, COUNT, Zoom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ExcelStatusBarBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left info: Ready, SUM, AVERAGE, COUNT
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ready",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                stats?.third?.let { (sum, avg) ->
                    Text(
                        text = "SUM: ${"%.2f".format(sum)}",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "AVG: ${"%.2f".format(avg)}",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Right tools: View Mode & Zoom
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isOriginalPageView) Icons.Default.GridOn else Icons.Default.TableChart,
                    contentDescription = "Toggle View",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onTogglePageView() }
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { onZoomChange((pageScale - 0.15f).coerceAtLeast(0.65f)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = "${(pageScale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onZoomChange(1.0f) }
                        .padding(horizontal = 2.dp)
                )

                IconButton(
                    onClick = { onZoomChange((pageScale + 0.15f).coerceAtMost(2.5f)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
