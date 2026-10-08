package com.ct.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.SortCriteria
import com.ct.explorer.data.model.SortDirection
import com.ct.explorer.data.model.SortType
import com.ct.explorer.ui.theme.CtOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiSortBottomSheet(
    currentSortType: SortType,
    foldersOnTop: Boolean,
    showHidden: Boolean,
    filterOnlyBigFiles: Boolean,
    onSortTypeChange: (SortType) -> Unit,
    onToggleFoldersOnTop: () -> Unit,
    onToggleShowHidden: () -> Unit,
    onToggleBigFilesFilter: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White,
        modifier = Modifier.testTag("sort_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CtOrange.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = null,
                            tint = CtOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sort & Arrange",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Current: ${currentSortType.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Criteria Selection Title
            Text(
                text = "SORT BY",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 4 Sort Criteria Cards
            val criteriaList = listOf(
                Triple(SortCriteria.NAME, "Name", Icons.Default.SortByAlpha),
                Triple(SortCriteria.SIZE, "Size", Icons.Default.DataUsage),
                Triple(SortCriteria.DATE, "Date Modified", Icons.Default.Schedule),
                Triple(SortCriteria.TYPE, "File Type", Icons.Default.Category)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                criteriaList.take(2).forEach { (criteria, title, icon) ->
                    SortCriteriaCard(
                        title = title,
                        icon = icon,
                        isSelected = currentSortType.criteria == criteria,
                        onClick = {
                            val newType = SortType.from(criteria, currentSortType.direction)
                            onSortTypeChange(newType)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                criteriaList.drop(2).forEach { (criteria, title, icon) ->
                    SortCriteriaCard(
                        title = title,
                        icon = icon,
                        isSelected = currentSortType.criteria == criteria,
                        onClick = {
                            val newType = SortType.from(criteria, currentSortType.direction)
                            onSortTypeChange(newType)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Direction Selection Title
            Text(
                text = "ORDER DIRECTION",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Ascending / Descending Direction Row
            val ascLabel = when (currentSortType.criteria) {
                SortCriteria.NAME -> "A to Z (Ascending)"
                SortCriteria.SIZE -> "Smallest First (Ascending)"
                SortCriteria.DATE -> "Oldest First (Ascending)"
                SortCriteria.TYPE -> "A to Z (Ascending)"
            }
            val descLabel = when (currentSortType.criteria) {
                SortCriteria.NAME -> "Z to A (Descending)"
                SortCriteria.SIZE -> "Largest First (Descending)"
                SortCriteria.DATE -> "Newest First (Descending)"
                SortCriteria.TYPE -> "Z to A (Descending)"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectionButton(
                    label = ascLabel,
                    icon = Icons.Default.ArrowUpward,
                    isSelected = currentSortType.direction == SortDirection.ASCENDING,
                    onClick = {
                        val newType = SortType.from(currentSortType.criteria, SortDirection.ASCENDING)
                        onSortTypeChange(newType)
                    },
                    modifier = Modifier.weight(1f)
                )

                DirectionButton(
                    label = descLabel,
                    icon = Icons.Default.ArrowDownward,
                    isSelected = currentSortType.direction == SortDirection.DESCENDING,
                    onClick = {
                        val newType = SortType.from(currentSortType.criteria, SortDirection.DESCENDING)
                        onSortTypeChange(newType)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))

            // Preferences
            Text(
                text = "FOLDER PREFERENCES",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Folders on Top Toggle
            PreferenceRow(
                title = "Keep Folders on Top",
                subtitle = "Folders remain grouped above files",
                icon = Icons.Default.Folder,
                checked = foldersOnTop,
                onCheckedChange = { onToggleFoldersOnTop() }
            )

            // Show Hidden Files Toggle
            PreferenceRow(
                title = "Show Hidden Files",
                subtitle = "Display .files and hidden system items",
                icon = Icons.Default.Visibility,
                checked = showHidden,
                onCheckedChange = { onToggleShowHidden() }
            )

            // Big Files Filter Toggle
            PreferenceRow(
                title = "Filter Big Files Only (>10MB)",
                subtitle = "Spot large storage-consuming items instantly",
                icon = Icons.Default.OfflineBolt,
                checked = filterOnlyBigFiles,
                onCheckedChange = { onToggleBigFilesFilter() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CtOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun SortCriteriaCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) CtOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val bgColor = if (isSelected) CtOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CtOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DirectionButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) CtOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val bgColor = if (isSelected) CtOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CtOrange
            )
        )
    }
}
