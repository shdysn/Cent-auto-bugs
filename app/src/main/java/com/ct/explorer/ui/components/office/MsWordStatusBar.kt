package com.ct.explorer.ui.components.office

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val WordStatusBg = Color(0xFF185ABD)
val WordStatusText = Color(0xFFFFFFFF)

@Composable
fun MsWordStatusBar(
    currentPage: Int,
    totalPages: Int,
    wordCount: Int,
    pageScale: Float,
    isOriginalPageView: Boolean,
    onTogglePageView: () -> Unit,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WordStatusBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left info items: Page number, word count, language
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Page $currentPage of $totalPages",
                color = WordStatusText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "•",
                color = WordStatusText.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Text(
                text = "$wordCount words",
                color = WordStatusText,
                fontSize = 11.sp
            )
        }

        // Right tools: View Mode icons & Zoom controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // View Mode toggle
            Icon(
                imageVector = if (isOriginalPageView) Icons.Default.Description else Icons.Default.ViewStream,
                contentDescription = "Switch View",
                tint = WordStatusText,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onTogglePageView() }
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Zoom Out [-]
            IconButton(
                onClick = { onZoomChange((pageScale - 0.15f).coerceAtLeast(0.65f)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = WordStatusText,
                    modifier = Modifier.size(14.dp)
                )
            }

            Text(
                text = "${(pageScale * 100).toInt()}%",
                color = WordStatusText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onZoomChange(1.0f) }
                    .padding(horizontal = 2.dp)
            )

            // Zoom In [+]
            IconButton(
                onClick = { onZoomChange((pageScale + 0.15f).coerceAtMost(2.5f)) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = WordStatusText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
