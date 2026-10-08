package com.ct.explorer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.ui.viewmodel.StorageTabState
import java.io.File

@Composable
fun DualPaneView(
    paneAState: StorageTabState,
    paneBState: StorageTabState,
    activePane: Int, // 0 for A, 1 for B
    onSelectPane: (Int) -> Unit,
    onNavigateA: (File) -> Unit,
    onNavigateB: (File) -> Unit,
    onBackA: () -> Unit,
    onBackB: () -> Unit,
    onCopyAtoB: () -> Unit,
    onCopyBtoA: () -> Unit,
    onMoveAtoB: () -> Unit = {},
    onMoveBtoA: () -> Unit = {},
    onToggleSelectA: (FileItem) -> Unit = {},
    onToggleSelectB: (FileItem) -> Unit = {},
    onClearSelectA: () -> Unit = {},
    onClearSelectB: () -> Unit = {},
    onOpenFile: (FileItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            // Horizontal split side-by-side
            Row(modifier = Modifier.fillMaxSize()) {
                SinglePaneContainer(
                    title = "Pane A",
                    state = paneAState,
                    isActive = activePane == 0,
                    onClick = { onSelectPane(0) },
                    onNavigate = onNavigateA,
                    onBack = onBackA,
                    onCopyToOther = onCopyAtoB,
                    onMoveToOther = onMoveAtoB,
                    onToggleSelect = onToggleSelectA,
                    onClearSelection = onClearSelectA,
                    onOpenFile = onOpenFile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                SinglePaneContainer(
                    title = "Pane B",
                    state = paneBState,
                    isActive = activePane == 1,
                    onClick = { onSelectPane(1) },
                    onNavigate = onNavigateB,
                    onBack = onBackB,
                    onCopyToOther = onCopyBtoA,
                    onMoveToOther = onMoveBtoA,
                    onToggleSelect = onToggleSelectB,
                    onClearSelection = onClearSelectB,
                    onOpenFile = onOpenFile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Vertical split stacked
            Column(modifier = Modifier.fillMaxSize()) {
                SinglePaneContainer(
                    title = "Pane A",
                    state = paneAState,
                    isActive = activePane == 0,
                    onClick = { onSelectPane(0) },
                    onNavigate = onNavigateA,
                    onBack = onBackA,
                    onCopyToOther = onCopyAtoB,
                    onMoveToOther = onMoveAtoB,
                    onToggleSelect = onToggleSelectA,
                    onClearSelection = onClearSelectA,
                    onOpenFile = onOpenFile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                SinglePaneContainer(
                    title = "Pane B",
                    state = paneBState,
                    isActive = activePane == 1,
                    onClick = { onSelectPane(1) },
                    onNavigate = onNavigateB,
                    onBack = onBackB,
                    onCopyToOther = onCopyBtoA,
                    onMoveToOther = onMoveBtoA,
                    onToggleSelect = onToggleSelectB,
                    onClearSelection = onClearSelectB,
                    onOpenFile = onOpenFile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SinglePaneContainer(
    title: String,
    state: StorageTabState,
    isActive: Boolean,
    onClick: () -> Unit,
    onNavigate: (File) -> Unit,
    onBack: () -> Unit,
    onCopyToOther: () -> Unit,
    onMoveToOther: () -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onClearSelection: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
    ) {
        // Pane Header
        Surface(
            color = if (isActive) CtOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = state.backStack.isNotEmpty(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) CtOrange else MaterialTheme.colorScheme.onSurface
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = CtOrange,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = state.currentDir.name.ifEmpty { "Storage" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.selectedItems.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onCopyToOther,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Copy (${state.selectedItems.size})", fontSize = 10.sp)
                        }
                        FilledTonalButton(
                            onClick = onMoveToOther,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = CtOrange.copy(alpha = 0.2f)),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = CtOrange, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Move", fontSize = 10.sp, color = CtOrange)
                        }
                        IconButton(onClick = onClearSelection, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Pane Files List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(state.items, key = { it.path }) { item ->
                val isSelected = state.selectedItems.contains(item)
                val (itemIcon, itemColor) = getFileItemIconAndColor(item)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) CtOrange.copy(alpha = 0.14f) else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                onClick()
                                if (state.isSelectionMode) {
                                    onToggleSelect(item)
                                } else if (item.isDirectory) {
                                    onNavigate(item.file)
                                } else {
                                    onOpenFile(item)
                                }
                            },
                            onLongClick = {
                                onClick()
                                onToggleSelect(item)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            onClick()
                            onToggleSelect(item)
                        },
                        colors = CheckboxDefaults.colors(checkedColor = CtOrange),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = itemIcon,
                        contentDescription = null,
                        tint = itemColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (item.isDirectory) item.formattedDateOnly else item.formattedSize,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (item.isLarge) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = when {
                                    item.isHuge -> Color(0xFFDC2626)
                                    item.isVeryLarge -> Color(0xFFEA580C)
                                    item.isLarge -> CtOrange
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 10.sp
                            )
                            item.sizeBadgeText?.let { badge ->
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isHuge) Color(0xFFDC2626) else CtOrange
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
