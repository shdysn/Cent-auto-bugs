package com.ct.explorer.domain.usecase

import com.ct.explorer.core.operations.FileOperationBus
import com.ct.explorer.core.operations.FileOperationProgress
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.repository.FileRepository
import com.ct.explorer.utils.ArchiveHelper
import com.ct.explorer.utils.ArchiveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ArchiveFilesUseCase(
    private val fileRepository: FileRepository
) {
    suspend fun compress(
        items: List<File>,
        destinationZip: File,
        compressionLevel: Int = 6,
        format: ArchiveType = ArchiveType.ZIP,
        password: String? = null,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val totalBytes = items.sumOf { if (it.isDirectory) calculateDirSize(it) else it.length() }
            val progress = FileOperationProgress(
                isRunning = true,
                taskName = "Compressing archive...",
                currentFileName = destinationZip.name,
                totalItems = items.size,
                totalBytes = totalBytes
            )
            FileOperationBus.updateProgress(progress)
            onProgress?.invoke(progress)

            val result = ArchiveHelper.compressArchive(
                items = items,
                destinationFile = destinationZip,
                format = format,
                compressionLevel = compressionLevel,
                password = password
            )

            FileRepository.invalidateAllFileCaches()
            fileRepository.invalidateFolderSize(destinationZip.parentFile)
            result
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun extract(
        zipFile: File,
        destDir: File,
        selectedPaths: Set<String>? = null,
        password: String? = null,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val progress = FileOperationProgress(
                isRunning = true,
                taskName = "Extracting archive...",
                currentFileName = zipFile.name
            )
            FileOperationBus.updateProgress(progress)
            onProgress?.invoke(progress)

            val result = ArchiveHelper.extractArchive(
                file = zipFile,
                destDir = destDir,
                password = password,
                selectedPaths = selectedPaths
            ) { fraction, entryName ->
                val update = FileOperationProgress(
                    isRunning = true,
                    taskName = "Extracting archive...",
                    currentFileName = if (entryName.isNotBlank()) entryName else zipFile.name,
                    completedItems = (fraction * 100f).toInt(),
                    totalItems = 100,
                    bytesProcessed = (fraction * 10000f).toLong(),
                    totalBytes = 10000L
                )
                FileOperationBus.updateProgress(update)
                onProgress?.invoke(update)
            }

            FileRepository.invalidateAllFileCaches()
            fileRepository.invalidateFolderSize(destDir)
            result
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun extractSingleEntry(
        archiveFile: File,
        entryPath: String,
        destDir: File,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            FileOperationBus.updateProgress(
                FileOperationProgress(
                    isRunning = true,
                    taskName = "Extracting entry...",
                    currentFileName = entryPath
                )
            )
            val result = ArchiveHelper.extractSingleEntry(
                archiveFile = archiveFile,
                entryPath = entryPath,
                destDir = destDir,
                password = password
            )
            result
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun zipItems(
        items: List<FileItem>,
        zipOutputFile: File,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val progress = FileOperationProgress(
                isRunning = true,
                taskName = "Creating ZIP...",
                currentFileName = zipOutputFile.name,
                totalItems = items.size
            )
            FileOperationBus.updateProgress(progress)
            onProgress?.invoke(progress)

            val result = fileRepository.zip(items, zipOutputFile)
            FileRepository.invalidateAllFileCaches()
            fileRepository.invalidateFolderSize(zipOutputFile.parentFile)
            result
        } finally {
            FileOperationBus.clear()
        }
    }

    suspend fun unzipItem(
        item: FileItem,
        outputDir: File,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val progress = FileOperationProgress(
                isRunning = true,
                taskName = "Extracting ZIP...",
                currentFileName = item.name
            )
            FileOperationBus.updateProgress(progress)
            onProgress?.invoke(progress)

            val result = fileRepository.unzip(item.file, outputDir)
            FileRepository.invalidateAllFileCaches()
            fileRepository.invalidateFolderSize(outputDir)
            result
        } finally {
            FileOperationBus.clear()
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.add(child)
                else size += child.length()
            }
        }
        return size
    }
}
