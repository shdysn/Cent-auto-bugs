package com.ct.explorer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.FileItem

@Composable
fun MiSelectionTopBar(
    selectedCount: Int,
    subtitle: String,
    isAllSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) = CtSelectionTopBar(
    selectedCount = selectedCount,
    subtitle = subtitle,
    isAllSelected = isAllSelected,
    onClose = onClose,
    onToggleSelectAll = onToggleSelectAll,
    modifier = modifier
)

@Composable
fun MiSelectionBottomBar(
    selectedItems: List<FileItem>,
    onSend: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onCopy: () -> Unit,
    onMakePrivate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onOpenInAnotherApp: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) = CtSelectionBottomBar(
    selectedItems = selectedItems,
    onSend = onSend,
    onMove = onMove,
    onDelete = onDelete,
    onCopyToClipboard = onCopyToClipboard,
    onCopy = onCopy,
    onMakePrivate = onMakePrivate,
    onToggleFavorite = onToggleFavorite,
    onRename = onRename,
    onOpenInAnotherApp = onOpenInAnotherApp,
    onDetails = onDetails,
    modifier = modifier
)

/**
 * Top bar displayed when 1 or more files are selected.
 */
@Composable
fun CtSelectionTopBar(
    selectedCount: Int,
    subtitle: String,
    isAllSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel selection",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$selectedCount item${if (selectedCount > 1) "s" else ""} selected",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onToggleSelectAll, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = "Toggle select all",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * Bottom action bar shown in selection mode (Send, Move, Delete, More).
 */
@Composable
fun CtSelectionBottomBar(
    selectedItems: List<FileItem>,
    onSend: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCopyToClipboard: () -> Unit,
    onCopy: () -> Unit,
    onMakePrivate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onOpenInAnotherApp: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Send
            SelectionActionItem(
                icon = Icons.Outlined.Share,
                label = "Send",
                onClick = onSend
            )

            // 2. Move
            SelectionActionItem(
                icon = Icons.Outlined.DriveFileMove,
                label = "Move",
                onClick = onMove
            )

            // 3. Delete
            SelectionActionItem(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                onClick = onDelete
            )

            // 4. More Options
            Box {
                SelectionActionItem(
                    icon = Icons.Outlined.MoreHoriz,
                    label = "More",
                    onClick = { showMoreMenu = true }
                )

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy", fontSize = 15.sp) },
                        onClick = {
                            showMoreMenu = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Path", fontSize = 15.sp) },
                        onClick = {
                            showMoreMenu = false
                            onCopyToClipboard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Private Vault", fontSize = 15.sp) },
                        onClick = {
                            showMoreMenu = false
                            onMakePrivate()
                        }
                    )
                    if (selectedItems.size == 1) {
                        DropdownMenuItem(
                            text = { Text("Rename", fontSize = 15.sp) },
                            onClick = {
                                showMoreMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Favorite", fontSize = 15.sp) },
                            onClick = {
                                showMoreMenu = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in another app", fontSize = 15.sp) },
                            onClick = {
                                showMoreMenu = false
                                onOpenInAnotherApp()
                            }
                        )
                    }
                    if (selectedItems.size == 1) {
                        DropdownMenuItem(
                            text = { Text("Details", fontSize = 15.sp) },
                            onClick = {
                                showMoreMenu = false
                                onDetails()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
