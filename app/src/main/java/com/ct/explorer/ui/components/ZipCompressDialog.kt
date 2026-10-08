package com.ct.explorer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.utils.ArchiveType
import java.util.zip.Deflater

enum class ZipCompressionLevel(val title: String, val level: Int, val description: String) {
    FAST("Fast", Deflater.BEST_SPEED, "Faster archiving, slightly larger file"),
    STANDARD("Standard", Deflater.DEFAULT_COMPRESSION, "Balanced speed and size (Recommended)"),
    MAXIMUM("Ultra", Deflater.BEST_COMPRESSION, "Smallest file size, maximum compression")
}

@Composable
fun ZipCompressDialog(
    selectedItems: List<FileItem>,
    defaultArchiveName: String,
    onDismiss: () -> Unit,
    onCompress: (archiveName: String, compressionLevel: Int) -> Unit,
    onCompressPro: ((archiveName: String, format: ArchiveType, compressionLevel: Int, password: String?) -> Unit)? = null
) {
    val baseName = remember(defaultArchiveName) {
        defaultArchiveName
            .removeSuffix(".tar.gz")
            .removeSuffix(".zip")
            .removeSuffix(".tar")
            .ifBlank { "Archive" }
    }

    var selectedFormat by remember { mutableStateOf(ArchiveType.ZIP) }
    var archiveName by remember { mutableStateOf("$baseName.zip") }
    var selectedLevel by remember { mutableStateOf(ZipCompressionLevel.STANDARD) }
    var enablePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val totalBytes = remember(selectedItems) {
        selectedItems.sumOf { it.effectiveSize }
    }

    fun updateFormat(newFormat: ArchiveType) {
        selectedFormat = newFormat
        val cleanBase = archiveName
            .removeSuffix(".tar.gz")
            .removeSuffix(".tgz")
            .removeSuffix(".zip")
            .removeSuffix(".tar")
            .ifBlank { baseName }
        archiveName = "$cleanBase.${newFormat.extension}"
        if (newFormat != ArchiveType.ZIP) {
            enablePassword = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = CtOrange)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Pro Archive Studio", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${selectedItems.size} items (${FileItem.formatBytes(totalBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("Archive Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Archive Format",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ArchiveType.ZIP to "ZIP",
                        ArchiveType.TAR_GZ to "TAR.GZ",
                        ArchiveType.TAR to "TAR"
                    ).forEach { (fmt, label) ->
                        val isSelected = selectedFormat == fmt
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) CtOrange.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) CtOrange else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { updateFormat(fmt) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Compression Level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ZipCompressionLevel.values().forEach { lvl ->
                        val isSelected = selectedLevel == lvl
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            color = if (isSelected) CtOrange else Color.Transparent,
                            onClick = { selectedLevel = lvl }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lvl.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = selectedLevel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // AES-256 Password Protection (Available for ZIP)
                if (selectedFormat == ArchiveType.ZIP) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { enablePassword = !enablePassword }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = CtOrange, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Encrypt with Password", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("AES-256-GCM encryption", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = enablePassword,
                                onCheckedChange = { enablePassword = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CtOrange)
                            )
                        }
                    }

                    AnimatedVisibility(visible = enablePassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Archive Password") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = archiveName.trim().ifBlank { "Archive.${selectedFormat.extension}" }
                    val finalName = if (trimmed.endsWith(".${selectedFormat.extension}", ignoreCase = true)) {
                        trimmed
                    } else {
                        "$trimmed.${selectedFormat.extension}"
                    }
                    val finalPass = if (enablePassword && password.isNotBlank()) password else null
                    if (onCompressPro != null) {
                        onCompressPro(finalName, selectedFormat, selectedLevel.level, finalPass)
                    } else {
                        onCompress(finalName, selectedLevel.level)
                    }
                    onDismiss()
                },
                enabled = !enablePassword || password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
            ) {
                Text("Create Archive")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
