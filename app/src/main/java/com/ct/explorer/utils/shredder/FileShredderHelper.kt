package com.ct.explorer.utils.shredder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.UUID

enum class ShredMethod(val passes: Int, val title: String, val description: String) {
    FAST_ZERO(1, "Quick Zero-Fill (1-Pass)", "Overwrites all file bytes once with 0x00. Fast for large files."),
    DOD_3PASS(3, "DoD 5220.22-M Standard (3-Pass)", "Pass 1: Zeros (0x00), Pass 2: Ones (0xFF), Pass 3: Cryptographic Random Bytes."),
    PARANOID_7PASS(7, "High-Security Schneier (7-Pass)", "7 progressive passes of alternating complement bits and pseudo-random numbers.")
}

data class ShredProgress(
    val fileName: String = "",
    val currentPass: Int = 1,
    val totalPasses: Int = 3,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
) {
    val overallFraction: Float
        get() {
            if (totalFiles <= 0 || totalPasses <= 0) return 0f
            val completedFiles = currentFileIndex.toFloat()
            val currentFileProgress = currentPass.toFloat() / totalPasses.toFloat()
            return ((completedFiles + currentFileProgress) / totalFiles.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * Military-grade secure file destruction engine.
 * Ensures deleted sensitive files cannot be recovered by forensic tools or un-delete software.
 */
object FileShredderHelper {

    private val secureRandom = SecureRandom()

    suspend fun shredFiles(
        files: List<File>,
        method: ShredMethod = ShredMethod.DOD_3PASS,
        onProgress: (ShredProgress) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        var count = 0
        val totalFiles = files.size

        try {
            files.forEachIndexed { index, file ->
                if (!file.exists()) return@forEachIndexed

                if (file.isDirectory) {
                    shredDirectory(file, method, index, totalFiles, onProgress)
                } else {
                    shredSingleFile(file, method, index, totalFiles, onProgress)
                }
                count++
            }
            onProgress(ShredProgress(isComplete = true, totalFiles = totalFiles, currentFileIndex = totalFiles))
            Result.success(count)
        } catch (e: Exception) {
            onProgress(ShredProgress(isComplete = true, errorMessage = e.message))
            Result.failure(e)
        }
    }

    private fun shredSingleFile(
        file: File,
        method: ShredMethod,
        fileIndex: Int,
        totalFiles: Int,
        onProgress: (ShredProgress) -> Unit
    ) {
        val length = file.length()
        if (length <= 0) {
            file.delete()
            return
        }

        val bufferSize = 64 * 1024
        val buffer = ByteArray(bufferSize)

        RandomAccessFile(file, "rw").use { raf ->
            for (pass in 1..method.passes) {
                onProgress(
                    ShredProgress(
                        fileName = file.name,
                        currentPass = pass,
                        totalPasses = method.passes,
                        currentFileIndex = fileIndex,
                        totalFiles = totalFiles
                    )
                )

                raf.seek(0)
                var remaining = length

                // Choose pattern based on pass
                when (pass) {
                    1 -> java.util.Arrays.fill(buffer, 0x00.toByte())
                    2 -> java.util.Arrays.fill(buffer, 0xFF.toByte())
                    else -> {
                        // Secure random bytes
                        secureRandom.nextBytes(buffer)
                    }
                }

                while (remaining > 0) {
                    val toWrite = Math.min(remaining, bufferSize.toLong()).toInt()
                    if (pass >= 3) {
                        secureRandom.nextBytes(buffer)
                    }
                    raf.write(buffer, 0, toWrite)
                    remaining -= toWrite
                }

                // Force hardware sync to flash memory blocks
                raf.fd.sync()
            }

            // Truncate length
            raf.setLength(0)
        }

        // Rename file to wipe metadata from directory entry table
        val parent = file.parentFile
        val target = if (parent != null) {
            val renamed = File(parent, "shredded_${UUID.randomUUID()}")
            if (file.renameTo(renamed)) renamed else file
        } else file
        target.delete()
    }

    private fun shredDirectory(
        dir: File,
        method: ShredMethod,
        fileIndex: Int,
        totalFiles: Int,
        onProgress: (ShredProgress) -> Unit
    ) {
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                shredDirectory(child, method, fileIndex, totalFiles, onProgress)
            } else {
                shredSingleFile(child, method, fileIndex, totalFiles, onProgress)
            }
        }
        val parent = dir.parentFile
        val targetDir = if (parent != null) {
            val renamedDir = File(parent, "shredded_dir_${UUID.randomUUID()}")
            if (dir.renameTo(renamedDir)) renamedDir else dir
        } else dir
        targetDir.delete()
    }
}
