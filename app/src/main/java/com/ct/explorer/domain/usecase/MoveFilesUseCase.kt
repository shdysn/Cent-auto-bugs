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

class MoveFilesUseCase(
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
            val totalBytes = sources.sumOf { if (it.isDirectory) 0L else it.size }
            var bytesProcessed = 0L
            var completedCount = 0
            val startTime = System.currentTimeMillis()

            fun emitProgress(currentName: String) {
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                val speed = if (bytesProcessed > 0L) (bytesProcessed / elapsedSec).toLong() else 0L
                val progress = FileOperationProgress(
                    isRunning = true,
                    taskName = "Moving files...",
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
                            IllegalArgumentException("Cannot move folder '${source.name}' into itself or its child directory")
                        )
                    }
                }

                val dest = if (File(destinationDir, source.name).exists() && srcCanonical.parentFile?.canonicalPath != destCanonical.path) {
                    generateNonConflictingFile(destinationDir, source.name)
                } else {
                    File(destinationDir, source.name)
                }

                emitProgress(source.name)

                val destCanonicalFile = dest.canonicalFile
                if (srcCanonical.path == destCanonicalFile.path) {
                    // Already at target path
                    completedCount++
                    continue
                }

                val moved = source.file.renameTo(dest)
                if (!moved) {
                    // Cross-filesystem move: copy then delete
                    if (source.isDirectory) {
                        val copied = source.file.copyRecursively(dest, overwrite = true)
                        if (copied) {
                            source.file.deleteRecursively()
                        } else {
                            throw java.io.IOException("Failed to move directory '${source.name}' across volumes")
                        }
                    } else {
                        val copied = try {
                            copyFileStream(source.file, dest) { chunk ->
                                bytesProcessed += chunk
                                emitProgress(source.name)
                            }
                            true
                        } catch (e: Exception) {
                            false
                        }
                        if (copied) {
                            source.file.delete()
                        } else {
                            throw java.io.IOException("Failed to move file '${source.name}' across volumes")
                        }
                    }
                } else {
                    bytesProcessed += if (source.isDirectory) 0L else source.size
                }

                fileRepository.invalidateFolderSize(source.file.parentFile)
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
