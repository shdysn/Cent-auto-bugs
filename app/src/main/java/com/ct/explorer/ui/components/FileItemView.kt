package com.ct.explorer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.ColorTag
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.SocialFolderType
import com.ct.explorer.ui.theme.*
import java.util.Locale

import com.ct.explorer.utils.FileIconHelper

fun getFileItemIconAndColor(item: FileItem): Pair<ImageVector, Color> {
    val descriptor = FileIconHelper.getDescriptor(item)
    return Pair(descriptor.icon, descriptor.tintColor)
}

fun getMiCategoryColors(category: FileCategory): Pair<ImageVector, Color> {
    val descriptor = FileIconHelper.getCategoryDescriptor(category)
    return Pair(descriptor.icon, descriptor.tintColor)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiFileRow(
    item: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onMenuAction: (String) -> Unit,
    tags: List<ColorTag> = emptyList(),
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val (icon, color) = getFileItemIconAndColor(item)

    val itemBg = if (isSelected) Color(0xFF1E88E5).copy(alpha = 0.08f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(itemBg)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("mi_file_row_${item.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isMedia = item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO
        // Smooth squircle icon badge with Material Icon and format chip (larger for images & videos)
        FileIconHelper.FileIconBadge(
            item = item,
            size = if (isMedia) 54.dp else 44.dp,
            iconSize = if (isMedia) 28.dp else 24.dp,
            shape = RoundedCornerShape(if (isMedia) 10.dp else 14.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val social = item.socialType
                if (social != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = color.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = social.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = color,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        tags.take(3).forEach { t ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(t.composeColor)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            if (item.isDirectory) {
                // Folder: only date, no 'Folder' label and no time
                Text(
                    text = item.formattedDateOnly,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (item.isLarge) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = when {
                            item.isHuge -> Color(0xFFDC2626)
                            item.isVeryLarge -> Color(0xFFEA580C)
                            item.isLarge -> CtOrange
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    item.sizeBadgeText?.let { badge ->
                        Surface(
                            color = when {
                                item.isHuge -> Color(0xFFFEE2E2)
                                item.isVeryLarge -> Color(0xFFFFEDD5)
                                item.isLarge -> CtOrange.copy(alpha = 0.14f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = when {
                                    item.isHuge -> Color(0xFFDC2626)
                                    item.isVeryLarge -> Color(0xFFC2410C)
                                    item.isLarge -> CtOrange
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                    Text(
                        text = item.formattedDateOnly,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onToggleSelect() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E88E5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun FileActionDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    item: FileItem,
    onClick: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        DropdownMenuItem(
            text = { Text("Open") },
            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
            onClick = {
                onDismiss()
                onClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Favorite") },
            leadingIcon = { Icon(Icons.Default.StarBorder, contentDescription = null, tint = Color(0xFFF59E0B)) },
            onClick = {
                onDismiss()
                onMenuAction("toggle_favorite")
            }
        )
        DropdownMenuItem(
            text = { Text("Pin to Home Screen") },
            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = Color(0xFF3B82F6)) },
            onClick = {
                onDismiss()
                onMenuAction("pin_home")
            }
        )
        DropdownMenuItem(
            text = { Text("Color Tags & Labels") },
            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, tint = CtOrange) },
            onClick = {
                onDismiss()
                onMenuAction("tags")
            }
        )
        if (item.category == FileCategory.IMAGE) {
            DropdownMenuItem(
                text = { Text("Photo EXIF Privacy Cleaner") },
                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981)) },
                onClick = {
                    onDismiss()
                    onMenuAction("clean_exif")
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Fast Share (Wi-Fi P2P)") },
            leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF10B981)) },
            onClick = {
                onDismiss()
                onMenuAction("fast_share")
            }
        )
        if (!item.isDirectory) {
            DropdownMenuItem(
                text = { Text("Print / Save as PDF") },
                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF2563EB)) },
                onClick = {
                    onDismiss()
                    onMenuAction("print")
                }
            )
            DropdownMenuItem(
                text = { Text("Open with...") },
                leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onMenuAction("open_with")
                }
            )
            DropdownMenuItem(
                text = { Text("Calculate Checksum") },
                leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = CtOrange) },
                onClick = {
                    onDismiss()
                    onMenuAction("checksum")
                }
            )
            DropdownMenuItem(
                text = { Text("Hide in Vault") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CtOrange) },
                onClick = {
                    onDismiss()
                    onMenuAction("vault")
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = {
                onDismiss()
                onMenuAction("copy")
            }
        )
        DropdownMenuItem(
            text = { Text("Cut") },
            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
            onClick = {
                onDismiss()
                onMenuAction("cut")
            }
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
                onDismiss()
                onMenuAction("rename")
            }
        )
        if (item.category == FileCategory.ARCHIVE) {
            DropdownMenuItem(
                text = { Text("Extract") },
                leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onMenuAction("unzip")
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Compress") },
                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onMenuAction("zip")
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Details") },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = {
                onDismiss()
                onMenuAction("details")
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = Color(0xFFEF4444)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
            onClick = {
                onDismiss()
                onMenuAction("delete")
            }
        )
    }
}

/**
 * Cent Styled Grid Item for files and folders.
 * Features a large centered squircle badge with format chips, two-line title,
 * and quick-access kebab menu, making it ideal for visual browsing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiFileGridItem(
    item: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onMenuAction: (String) -> Unit,
    tags: List<ColorTag> = emptyList(),
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            )
            .testTag("mi_file_grid_${item.name}"),
        color = if (isSelected) CtOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, CtOrange) else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Top Right: Selection Circle or Kebab Menu
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .clickable { onToggleSelect() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E88E5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(1.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isSelectionMode) 14.dp else 6.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isMedia = item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO
                // Centered Squircle Badge with format chip (enlarged for photos & videos)
                FileIconHelper.FileIconBadge(
                    item = item,
                    size = if (isMedia) 74.dp else 52.dp,
                    iconSize = if (isMedia) 34.dp else 28.dp,
                    shape = RoundedCornerShape(if (isMedia) 12.dp else 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // File or folder name (up to 2 lines, centered)
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    ),
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle: Date for folders, size for files
                Text(
                    text = if (item.isDirectory) item.formattedDateOnly else item.formattedSize,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                // Optional color tags dots
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tags.take(3).forEach { t ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(t.composeColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
