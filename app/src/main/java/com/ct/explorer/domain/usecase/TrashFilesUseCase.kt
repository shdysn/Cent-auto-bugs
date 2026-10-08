package com.ct.explorer.domain.usecase

import com.ct.explorer.core.operations.FileOperationBus
import com.ct.explorer.core.operations.FileOperationProgress
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.TrashItem
import com.ct.explorer.data.repository.FileRepository
import com.ct.explorer.data.repository.TrashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrashFilesUseCase(
    private val trashRepository: TrashRepository,
    private val fileRepository: FileRepository
) {
    suspend fun moveToTrash(
        items: List<FileItem>,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext Result.success(0)
        try {
            var successCount = 0
            val total = items.size

            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Moving to Recycle Bin...",
                    currentFileName = items.firstOrNull()?.name ?: "",
                    completedItems = 0,
                    totalItems = total
                ).also { onProgress?.invoke(it) }
            )

            for (item in items) {
                FileOperationBus.updateProgress(
                    FileOperationProgress(
                        isRunning = true,
                        taskName = "Moving to Recycle Bin...",
                        currentFileName = item.name,
                        completedItems = successCount,
                        totalItems = total
                    ).also { onProgress?.invoke(it) }
                )

                val res = trashRepository.moveToTrash(item)
                if (res.isSuccess) successCount++
            }

            FileRepository.invalidateAllFileCaches()
            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun restoreItems(
        items: List<TrashItem>,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext Result.success(0)
        try {
            var restoredCount = 0
            val total = items.size

            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Restoring from Recycle Bin...",
                    currentFileName = items.firstOrNull()?.displayName ?: "",
                    completedItems = 0,
                    totalItems = total
                ).also { onProgress?.invoke(it) }
            )

            for (item in items) {
                FileOperationBus.updateProgress(
                    FileOperationProgress(
                        isRunning = true,
                        taskName = "Restoring from Recycle Bin...",
                        currentFileName = item.displayName,
                        completedItems = restoredCount,
                        totalItems = total
                    ).also { onProgress?.invoke(it) }
                )

                val res = trashRepository.restoreItem(item)
                if (res.isSuccess) restoredCount++
            }

            FileRepository.invalidateAllFileCaches()
            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun deletePermanently(
        items: List<TrashItem>,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext Result.success(0)
        try {
            var deletedCount = 0
            val total = items.size

            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Permanently deleting from Trash...",
                    currentFileName = items.firstOrNull()?.displayName ?: "",
                    completedItems = 0,
                    totalItems = total
                ).also { onProgress?.invoke(it) }
            )

            for (item in items) {
                FileOperationBus.updateProgress(
                    FileOperationProgress(
                        isRunning = true,
                        taskName = "Permanently deleting from Trash...",
                        currentFileName = item.displayName,
                        completedItems = deletedCount,
                        totalItems = total
                    ).also { onProgress?.invoke(it) }
                )

                if (trashRepository.deletePermanently(item)) deletedCount++
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun emptyTrash(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Emptying Recycle Bin..."
                )
            )
            val success = trashRepository.emptyTrash()
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }
}
