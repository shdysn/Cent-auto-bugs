package com.ct.explorer.domain.usecase

import com.ct.explorer.core.operations.FileOperationBus
import com.ct.explorer.core.operations.FileOperationProgress
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class CopyFilesUseCase(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        sources: List<FileItem>,
        destinationDir: File,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext Result.success(0)
        try {
            if (!destinationDir.exists()) destinationDir.mkdirs()

            val totalItems = sources.size
            val totalBytes = calculateTotalBytes(sources)
            var bytesProcessed = 0L
            var completedCount = 0
            val startTime = System.currentTimeMillis()

            fun emitProgress(currentName: String) {
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                val speed = if (bytesProcessed > 0L) (bytesProcessed / elapsedSec).toLong() else 0L
                val progress = FileOperationProgress(
                    isRunning = true,
                    taskName = "Copying files...",
                    currentFileName = currentName,
                    completedItems = completedCount,
                    totalItems = totalItems,
                    bytesProcessed = bytesProcessed,
                    totalBytes = totalBytes,
                    speedBytesPerSec = speed
                )
                FileOperationBus.updateProgress(progress)
                onProgress?.invoke(progress)
            }

            emitProgress(sources.first().name)

            val destCanonical = destinationDir.canonicalFile
            for (source in sources) {
                val srcCanonical = source.file.canonicalFile
                if (source.isDirectory) {
                    if (destCanonical.path == srcCanonical.path || destCanonical.path.startsWith(srcCanonical.path + File.separator)) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Cannot copy folder '${source.name}' into itself or its child directory")
                        )
                    }
                }

                val dest = if (srcCanonical.parentFile?.canonicalPath == destCanonical.path || File(destinationDir, source.name).exists()) {
                    generateNonConflictingFile(destinationDir, source.name)
                } else {
                    File(destinationDir, source.name)
                }

                emitProgress(source.name)

                if (source.isDirectory) {
                    val copied = copyDirectoryRecursively(source.file, dest) { chunkBytes ->
                        bytesProcessed += chunkBytes
                        emitProgress(source.name)
                    }
                    if (!copied) throw java.io.IOException("Failed to copy directory: ${source.name}")
                } else {
                    copyFileStream(source.file, dest) { chunkBytes ->
                        bytesProcessed += chunkBytes
                        emitProgress(source.name)
                    }
                }
                completedCount++
            }

            FileRepository.invalidateAllFileCaches()
            fileRepository.invalidateFolderSize(destinationDir)
            Result.success(completedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            FileOperationBus.clear()
        }
    }

    private fun calculateTotalBytes(items: List<FileItem>): Long {
        return items.sumOf { item ->
            if (item.isDirectory) calculateDirSize(item.file) else item.size
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

    private fun copyFileStream(src: File, dest: File, onBytesCopied: (Long) -> Unit) {
        dest.parentFile?.mkdirs()
        FileInputStream(src).use { fis ->
            FileOutputStream(dest).use { fos ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    onBytesCopied(bytesRead.toLong())
                }
                fos.flush()
            }
        }
    }

    private fun copyDirectoryRecursively(srcDir: File, destDir: File, onBytesCopied: (Long) -> Unit): Boolean {
        if (!destDir.exists() && !destDir.mkdirs()) return false
        val children = srcDir.listFiles() ?: return true
        for (child in children) {
            val targetChild = File(destDir, child.name)
            if (child.isDirectory) {
                if (!copyDirectoryRecursively(child, targetChild, onBytesCopied)) return false
            } else {
                copyFileStream(child, targetChild, onBytesCopied)
            }
        }
        return true
    }

    private fun generateNonConflictingFile(parentDir: File, originalName: String): File {
        var candidate = File(parentDir, originalName)
        if (!candidate.exists()) return candidate
        val nameWithoutExt = originalName.substringBeforeLast(".")
        val ext = if (originalName.contains(".")) ".${originalName.substringAfterLast(".")}" else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parentDir, "$nameWithoutExt ($counter)$ext")
            counter++
        }
        return candidate
    }
}
