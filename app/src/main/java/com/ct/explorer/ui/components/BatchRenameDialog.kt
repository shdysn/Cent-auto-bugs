package com.ct.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

enum class RenameMode {
    NUMBERING,
    FIND_REPLACE,
    DATE_PREFIX
}

@Composable
fun BatchRenameDialog(
    selectedFiles: List<FileItem>,
    onDismiss: () -> Unit,
    onApplyRename: (List<Pair<FileItem, String>>) -> Unit
) {
    var mode by remember { mutableStateOf(RenameMode.NUMBERING) }
    var prefixText by remember { mutableStateOf("File") }
    var startNumber by remember { mutableStateOf("1") }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    // Compute preview
    val previewList = remember(selectedFiles, mode, prefixText, startNumber, findText, replaceText) {
        val startNum = startNumber.toIntOrNull() ?: 1
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        selectedFiles.mapIndexed { index, item ->
            val ext = if (!item.isDirectory && item.extension.isNotEmpty()) ".${item.extension}" else ""
            val nameWithoutExt = if (!item.isDirectory && item.extension.isNotEmpty()) item.name.substringBeforeLast(".") else item.name
            val newName = when (mode) {
                RenameMode.NUMBERING -> {
                    val numStr = String.format("%02d", startNum + index)
                    "${prefixText.trim()}_$numStr$ext"
                }
                RenameMode.FIND_REPLACE -> {
                    if (findText.isNotEmpty()) {
                        val replaced = nameWithoutExt.replace(findText, replaceText)
                        "$replaced$ext"
                    } else item.name
                }
                RenameMode.DATE_PREFIX -> {
                    "${todayStr}_${item.name}"
                }
            }
            Pair(item, newName)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = CtOrange)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Batch Rename (${selectedFiles.size} items)")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mode selector tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val modes = listOf(
                        Triple(RenameMode.NUMBERING, "Numbering", Icons.Default.FormatListNumbered),
                        Triple(RenameMode.FIND_REPLACE, "Replace", Icons.Default.FindReplace),
                        Triple(RenameMode.DATE_PREFIX, "Date", Icons.Default.CalendarToday)
                    )
                    modes.forEach { (m, title, icon) ->
                        val isSelected = mode == m
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            color = if (isSelected) CtOrange else Color.Transparent,
                            onClick = { mode = m }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode inputs
                when (mode) {
                    RenameMode.NUMBERING -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = prefixText,
                                onValueChange = { prefixText = it },
                                label = { Text("Base Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1.8f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = startNumber,
                                onValueChange = { startNumber = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Start #") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    RenameMode.FIND_REPLACE -> {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            label = { Text("Find text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            label = { Text("Replace with") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    RenameMode.DATE_PREFIX -> {
                        Text(
                            text = "Adds today's date (YYYY-MM-DD_) as prefix to all selected files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Preview (First ${minOf(4, previewList.size)} of ${previewList.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Preview list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    items(previewList.take(4)) { (original, newName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = original.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(14.dp),
                                tint = CtOrange
                            )
                            Text(
                                text = newName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplyRename(previewList)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
            ) {
                Text("Apply Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
