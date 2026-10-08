package com.ct.explorer.data.model

import java.io.File

enum class VolumeType {
    INTERNAL,
    SD_CARD,
    USB_OTG,
    ROOT
}

data class StorageVolumeItem(
    val id: String,
    val name: String,
    val file: File,
    val type: VolumeType,
    val totalBytes: Long,
    val freeBytes: Long,
    val isPrimary: Boolean
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercentage: Int get() = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
    val formattedTotal: String get() = FileItem.formatBytes(totalBytes)
    val formattedFree: String get() = FileItem.formatBytes(freeBytes)
    val formattedUsed: String get() = FileItem.formatBytes(usedBytes)
}
