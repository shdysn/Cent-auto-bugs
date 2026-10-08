package com.ct.explorer.domain.usecase

import com.ct.explorer.core.operations.FileOperationBus
import com.ct.explorer.core.operations.FileOperationProgress
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DeleteFilesUseCase(
    private val fileRepository: FileRepository
) {
    suspend fun deleteItems(
        items: List<FileItem>,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext Result.success(0)
        try {
            var count = 0
            val total = items.size

            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Deleting items...",
                    currentFileName = items.firstOrNull()?.name ?: "",
                    completedItems = 0,
                    totalItems = total
                ).also { onProgress?.invoke(it) }
            )

            for (item in items) {
                FileOperationBus.updateProgress(
                    FileOperationProgress(
                        isRunning = true,
                        taskName = "Deleting items...",
                        currentFileName = item.name,
                        completedItems = count,
                        totalItems = total
                    ).also { onProgress?.invoke(it) }
                )

                if (fileRepository.delete(item).getOrDefault(false)) {
                    count++
                }
            }

            FileRepository.invalidateAllFileCaches()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun deleteFilesPermanently(
        files: List<File>,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext Result.success(0)
        try {
            var count = 0
            val total = files.size

            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Permanently deleting files...",
                    currentFileName = files.firstOrNull()?.name ?: "",
                    completedItems = 0,
                    totalItems = total
                ).also { onProgress?.invoke(it) }
            )

            for (f in files) {
                FileOperationBus.updateProgress(
                    FileOperationProgress(
                        isRunning = true,
                        taskName = "Permanently deleting files...",
                        currentFileName = f.name,
                        completedItems = count,
                        totalItems = total
                    ).also { onProgress?.invoke(it) }
                )

                if (!f.canWrite()) {
                    runCatching { f.setWritable(true) }
                }
                val deleted = if (f.isDirectory) f.deleteRecursively() else f.delete()
                if (deleted) count++
            }

            FileRepository.invalidateAllFileCaches()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun deleteEmptyFolders(folders: List<FileItem>): Result<Int> = withContext(Dispatchers.IO) {
        if (folders.isEmpty()) return@withContext Result.success(0)
        try {
            var count = 0
            for (folder in folders) {
                if (fileRepository.deleteEmptyFolder(folder).getOrDefault(false)) {
                    count++
                }
            }
            FileRepository.invalidateAllFileCaches()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(items: List<FileItem>): Result<Int> = deleteItems(items)
}
