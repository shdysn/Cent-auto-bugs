package com.ct.explorer.domain.usecase

import com.ct.explorer.core.operations.FileOperationBus
import com.ct.explorer.core.operations.FileOperationProgress
import com.ct.explorer.data.repository.FileRepository
import com.ct.explorer.utils.shredder.FileShredderHelper
import com.ct.explorer.utils.shredder.ShredMethod
import com.ct.explorer.utils.shredder.ShredProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ShredFilesUseCase(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        targets: List<File>,
        method: ShredMethod = ShredMethod.DOD_3PASS,
        onProgress: ((ShredProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext Result.success(0)
        try {
            val totalFiles = targets.size
            val totalBytes = targets.sumOf { if (it.isDirectory) 0L else it.length() }
            val startTime = System.currentTimeMillis()

            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Secure Shredding (${method.title})...",
                    currentFileName = targets.firstOrNull()?.name ?: "",
                    completedItems = 0,
                    totalItems = totalFiles,
                    totalBytes = totalBytes
                )
            )

            val result = FileShredderHelper.shredFiles(targets, method) { p ->
                onProgress?.invoke(p)
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                val speed = if (totalBytes > 0L) ((totalBytes * p.overallFraction) / elapsedSec).toLong() else 0L

                FileOperationBus.updateProgress(
                    FileOperationProgress(
                        isRunning = true,
                        taskName = "Secure Shredding (${method.title})...",
                        currentFileName = p.fileName,
                        completedItems = p.currentFileIndex,
                        totalItems = p.totalFiles,
                        bytesProcessed = (totalBytes * p.overallFraction).toLong(),
                        totalBytes = totalBytes,
                        speedBytesPerSec = speed
                    )
                )
            }

            FileRepository.invalidateAllFileCaches()
            result
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }
}
