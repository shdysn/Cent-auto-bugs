package com.ct.explorer.core.operations

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FileOperationProgress(
    val isRunning: Boolean = false,
    val taskName: String = "",
    val currentFileName: String = "",
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (bytesProcessed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (totalItems > 0) {
            (completedItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

object FileOperationBus {
    private val _progress = MutableStateFlow(FileOperationProgress())
    val progress: StateFlow<FileOperationProgress> = _progress.asStateFlow()

    fun updateProgress(update: FileOperationProgress) {
        _progress.value = update
    }

    fun clear() {
        _progress.value = FileOperationProgress()
    }
}
