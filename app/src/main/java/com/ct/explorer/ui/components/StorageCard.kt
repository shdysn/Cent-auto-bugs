package com.ct.explorer.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.StorageSpace
import com.ct.explorer.data.model.StorageVolumeItem
import com.ct.explorer.data.model.VolumeType
import com.ct.explorer.ui.theme.CtOrange

/**
 * Modern, compact storage card.
 * Uses an efficient horizontal layout with inline progress bar and volume switcher,
 * minimizing vertical screen footprint so files and content stay prominently in view.
 */
@Composable
fun StorageCard(
    storageSpace: StorageSpace,
    onCleanClick: () -> Unit,
    modifier: Modifier = Modifier,
    storageVolumes: List<StorageVolumeItem> = emptyList(),
    selectedVolume: StorageVolumeItem? = null,
    onSwitchVolume: (StorageVolumeItem) -> Unit = {}
) {
    val usedFraction = storageSpace.usedPercentage
    val usedPercent = (usedFraction * 100).toInt().coerceIn(0, 100)
    var showVolumeMenu by remember { mutableStateOf(false) }

    val volumeName = selectedVolume?.name ?: "Internal Storage"
    val hasMultipleVolumes = storageVolumes.size > 1

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("mi_storage_card"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Storage Squircle Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CtOrange.copy(alpha = 0.12f))
                    .then(
                        if (hasMultipleVolumes) {
                            Modifier.clickable { showVolumeMenu = true }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (selectedVolume?.type) {
                        VolumeType.SD_CARD -> Icons.Default.SdCard
                        VolumeType.USB_OTG -> Icons.Default.Usb
                        else -> Icons.Default.Storage
                    },
                    contentDescription = "Storage",
                    tint = CtOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Main Storage Details & Inline Progress Bar
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.then(
                        if (hasMultipleVolumes) {
                            Modifier.clickable { showVolumeMenu = true }
                        } else Modifier
                    )
                ) {
                    Text(
                        text = volumeName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (hasMultipleVolumes) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch Storage",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${storageSpace.formattedFree} free",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = CtOrange
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Inline Slim Progress Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = usedFraction.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (usedFraction > 0.9f) Color(0xFFEF4444) else CtOrange
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$usedPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (hasMultipleVolumes) {
                    DropdownMenu(
                        expanded = showVolumeMenu,
                        onDismissRequest = { showVolumeMenu = false },
                        containerColor = Color.White
                    ) {
                        storageVolumes.forEach { vol ->
                            DropdownMenuItem(
                                text = { Text("${vol.name} (${vol.formattedFree} free)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (vol.type) {
                                            VolumeType.INTERNAL -> Icons.Default.PhoneAndroid
                                            VolumeType.SD_CARD -> Icons.Default.SdCard
                                            VolumeType.USB_OTG -> Icons.Default.Usb
                                            VolumeType.ROOT -> Icons.Default.Security
                                        },
                                        contentDescription = null,
                                        tint = if (vol.id == selectedVolume?.id) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showVolumeMenu = false
                                    onSwitchVolume(vol)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Compact "Clean" Pill Button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onCleanClick)
                    .testTag("storage_card_clean_button"),
                color = CtOrange.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = CtOrange,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clean",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CtOrange,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}
