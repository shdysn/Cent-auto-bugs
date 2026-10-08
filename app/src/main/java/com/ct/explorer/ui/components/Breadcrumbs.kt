package com.ct.explorer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ct.explorer.ui.theme.CtOrange
import java.io.File

@Composable
fun MiBreadcrumbs(
    currentDir: File,
    rootStorageDir: File,
    onNavigateTo: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val crumbs = mutableListOf<File>()
    var current: File? = currentDir
    while (current != null) {
        crumbs.add(0, current)
        if (current == rootStorageDir || current.parentFile == null) {
            break
        }
        current = current.parentFile
    }

    LaunchedEffect(currentDir) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp)
            .testTag("mi_breadcrumb_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { index, folder ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }

            val displayName = when (folder.absolutePath) {
                rootStorageDir.absolutePath -> "Internal storage"
                else -> folder.name.ifEmpty { "/" }
            }

            val isLast = index == crumbs.lastIndex

            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isLast) CtOrange else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLast) { onNavigateTo(folder) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
