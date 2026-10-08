package com.ct.explorer.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ZipEntryItem(
    val name: String,
    val fullPath: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val lastModified: Long
) {
    val formattedSize: String get() = com.ct.explorer.data.model.FileItem.formatBytes(size)
    val extension: String get() = name.substringAfterLast(".", "").lowercase()
    val ratioPercentage: Int get() {
        if (size <= 0) return 0
        val saved = size - compressedSize
        return if (saved > 0) ((saved * 100) / size).toInt() else 0
    }
}

data class ZipArchiveInfo(
    val sourceFile: File?,
    val sourceUri: Uri?,
    val archiveName: String,
    val totalEntries: Int,
    val totalUncompressedBytes: Long,
    val totalArchiveBytes: Long,
    val entries: List<ZipEntryItem>
) {
    val formattedUncompressedSize: String get() = com.ct.explorer.data.model.FileItem.formatBytes(totalUncompressedBytes)
    val formattedArchiveSize: String get() = com.ct.explorer.data.model.FileItem.formatBytes(totalArchiveBytes)
    val overallSavingsPercentage: Int get() {
        if (totalUncompressedBytes <= 0) return 0
        val saved = totalUncompressedBytes - totalArchiveBytes
        return if (saved > 0) ((saved * 100) / totalUncompressedBytes).toInt() else 0
    }
}

class ZipRepository(private val context: Context) {

    suspend fun inspectZipFile(file: File): Result<ZipArchiveInfo> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(FileNotFoundException("Zip file does not exist"))

            val entriesList = mutableListOf<ZipEntryItem>()
            var totalUncompressed = 0L

            ZipFile(file).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val itemName = entry.name.trimEnd('/').substringAfterLast('/')
                    val item = ZipEntryItem(
                        name = if (itemName.isEmpty()) entry.name else itemName,
                        fullPath = entry.name,
                        size = entry.size.coerceAtLeast(0L),
                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                        isDirectory = entry.isDirectory,
                        lastModified = entry.time
                    )
                    entriesList.add(item)
                    if (!entry.isDirectory && entry.size > 0) {
                        totalUncompressed += entry.size
                    }
                }
            }

            Result.success(
                ZipArchiveInfo(
                    sourceFile = file,
                    sourceUri = null,
                    archiveName = file.name,
                    totalEntries = entriesList.size,
                    totalUncompressedBytes = totalUncompressed,
                    totalArchiveBytes = file.length(),
                    entries = entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.fullPath }))
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectZipUri(uri: Uri, displayName: String): Result<ZipArchiveInfo> = withContext(Dispatchers.IO) {
        try {
            val entriesList = mutableListOf<ZipEntryItem>()
            var totalUncompressed = 0L

            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val itemName = entry.name.trimEnd('/').substringAfterLast('/')
                        val item = ZipEntryItem(
                            name = if (itemName.isEmpty()) entry.name else itemName,
                            fullPath = entry.name,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = entry.isDirectory,
                            lastModified = entry.time
                        )
                        entriesList.add(item)
                        if (!entry.isDirectory && entry.size > 0) {
                            totalUncompressed += entry.size
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext Result.failure(IOException("Cannot open stream for URI"))

            // Temporary cache copy for reliable extraction
            val cacheFile = File(context.cacheDir, "incoming_${System.currentTimeMillis()}_$displayName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            Result.success(
                ZipArchiveInfo(
                    sourceFile = cacheFile,
                    sourceUri = uri,
                    archiveName = displayName,
                    totalEntries = entriesList.size,
                    totalUncompressedBytes = totalUncompressed,
                    totalArchiveBytes = cacheFile.length(),
                    entries = entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.fullPath }))
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractArchive(
        zipFile: File,
        targetDir: File,
        selectedPaths: Set<String>? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val destCanonical = targetDir.canonicalFile
            var extractedCount = 0

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val shouldExtract = selectedPaths == null || selectedPaths.contains(entry.name)

                    if (shouldExtract) {
                        val destFile = File(targetDir, entry.name)
                        val outCanonical = destFile.canonicalFile
                        if (!outCanonical.path.startsWith(destCanonical.path + File.separator) && outCanonical != destCanonical) {
                            throw SecurityException("Zip Slip detected: Entry attempts to escape target directory (${entry.name})")
                        }
                        if (entry.isDirectory) {
                            destFile.mkdirs()
                        } else {
                            destFile.parentFile?.mkdirs()
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            extractedCount++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Result.success(extractedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compressFiles(
        items: List<File>,
        destinationZip: File,
        compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationZip.parentFile?.mkdirs()
            val tempZip = File(destinationZip.parentFile, "${destinationZip.name}.tmp")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
                zos.setLevel(compressionLevel)
                for (file in items) {
                    addFileToZip("", file, zos)
                }
            }

            if (destinationZip.exists()) destinationZip.delete()
            tempZip.renameTo(destinationZip)
            Result.success(destinationZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addFileToZip(baseDir: String, file: File, zos: ZipOutputStream) {
        val entryPath = if (baseDir.isEmpty()) file.name else "$baseDir/${file.name}"
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryPath/"))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addFileToZip(entryPath, child, zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }
}
