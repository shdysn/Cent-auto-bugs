package com.ct.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.MiGreen
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.utils.ExifMetadata
import com.ct.explorer.utils.ExifPrivacyCleaner
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExifCleanerDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onCleanSaved: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var metadata by remember { mutableStateOf<ExifMetadata?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item) {
        metadata = ExifPrivacyCleaner.readExif(item.file)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Photo Privacy Cleaner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // GPS Warning or Safe Banner
                val meta = metadata
                if (meta != null) {
                    if (meta.hasGps && meta.latitude != null && meta.longitude != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "GPS Location Exposed!",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                    Text(
                                        text = "Lat: ${"%.5f".format(meta.latitude)}, Lng: ${"%.5f".format(meta.longitude)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = Color(0xFFB91C1C)
                                    )
                                    Text(
                                        text = "Anyone who receives this photo can pinpoint exactly where it was taken.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "No GPS coordinates detected in EXIF data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }

                    // Metadata attributes preview
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Detected Metadata:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CtOrange
                            )
                            ExifRow("Camera Make", meta.cameraMake ?: "Not present")
                            ExifRow("Camera Model", meta.cameraModel ?: "Not present")
                            ExifRow("Date & Time", meta.dateTime ?: "Not present")
                            ExifRow("Software", meta.software ?: "Not present")
                            ExifRow("ISO / Exposure", "${meta.iso ?: "-"} / ${meta.exposureTime ?: "-"}")
                        }
                    }
                }

                if (isProcessing) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MiGreen, modifier = Modifier.size(32.dp))
                    }
                }

                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MiGreen,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Action 1: Share Cleaned Photo
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                ExifPrivacyCleaner.shareCleanImage(context, item.file)
                                isProcessing = false
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Clean Photo (Zero Metadata)")
                    }

                    // Action 2: Save Clean Copy
                    OutlinedButton(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val cleanName = "${item.file.nameWithoutExtension}_clean.${item.file.extension}"
                                val cleanFile = File(item.file.parentFile, cleanName)
                                val res = ExifPrivacyCleaner.stripExif(item.file, cleanFile)
                                isProcessing = false
                                if (res.isSuccess) {
                                    statusMessage = "Saved clean copy: $cleanName"
                                    onCleanSaved(cleanFile)
                                } else {
                                    statusMessage = "Failed: ${res.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Clean Copy (Keep Original)")
                    }

                    // Action 3: Overwrite Original
                    OutlinedButton(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val temp = File(context.cacheDir, "temp_clean_${item.name}")
                                val res = ExifPrivacyCleaner.stripExif(item.file, temp)
                                if (res.isSuccess) {
                                    temp.copyTo(item.file, overwrite = true)
                                    temp.delete()
                                    isProcessing = false
                                    statusMessage = "Original image metadata stripped!"
                                    onCleanSaved(item.file)
                                } else {
                                    isProcessing = false
                                    statusMessage = "Failed: ${res.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Strip Original Image In-Place", color = Color(0xFFEF4444))
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
