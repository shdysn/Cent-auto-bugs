package com.ct.explorer.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.StorageSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CategoryUsage(
    val category: FileCategory,
    val title: String,
    val sizeBytes: Long,
    val percentageOfUsed: Float
) {
    val formattedSize: String get() = FileItem.formatBytes(sizeBytes)
}

data class FolderUsage(
    val folder: File,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val percentageOfUsed: Float
) {
    val formattedSize: String get() = FileItem.formatBytes(sizeBytes)
}

data class StorageAnalysisResult(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val categories: List<CategoryUsage>,
    val topFolders: List<FolderUsage>,
    val largestFiles: List<FileItem>
)

class StorageAnalyzerRepository(private val context: Context) {

    suspend fun analyzeStorage(): StorageAnalysisResult = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val stat = StatFs(root.path)
        val blockSize = stat.blockSizeLong
        val totalBytes = stat.blockCountLong * blockSize
        val availableBytes = stat.availableBlocksLong * blockSize
        val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

        // 1. Gather Media sizes via fast MediaStore
        val imageBytes = queryMediaCategorySize(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videoBytes = queryMediaCategorySize(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val audioBytes = queryMediaCategorySize(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

        // 2. Scan top directories in storage to compute folder weights
        val topFolders = mutableListOf<FolderUsage>()
        val largestFiles = mutableListOf<FileItem>()

        val subDirs = root.listFiles()?.filter {
            it.isDirectory && !it.name.startsWith(".") && !it.name.equals("Android", ignoreCase = true)
        } ?: emptyList()

        for (dir in subDirs) {
            val dirSize = getFastFolderSize(dir, largestFiles, maxDepth = 2)
            if (dirSize > 1024 * 1024L) { // > 1MB
                val pct = if (usedBytes > 0) (dirSize.toFloat() / usedBytes.toFloat()).coerceIn(0f, 1f) else 0f
                topFolders.add(
                    FolderUsage(
                        folder = dir,
                        name = dir.name,
                        path = dir.absolutePath,
                        sizeBytes = dirSize,
                        percentageOfUsed = pct
                    )
                )
            }
        }

        topFolders.sortByDescending { it.sizeBytes }
        largestFiles.sortByDescending { it.size }

        // Category breakdown
        val catList = mutableListOf<CategoryUsage>()
        fun addCat(cat: FileCategory, title: String, size: Long) {
            val pct = if (usedBytes > 0) (size.toFloat() / usedBytes.toFloat()).coerceIn(0f, 1f) else 0f
            catList.add(CategoryUsage(cat, title, size, pct))
        }

        addCat(FileCategory.VIDEO, "Videos", videoBytes)
        addCat(FileCategory.IMAGE, "Images", imageBytes)
        addCat(FileCategory.AUDIO, "Audio", audioBytes)

        val knownSum = imageBytes + videoBytes + audioBytes
        val otherBytes = (usedBytes - knownSum).coerceAtLeast(0L)
        addCat(FileCategory.UNKNOWN, "Apps & System", otherBytes)

        StorageAnalysisResult(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = availableBytes,
            categories = catList,
            topFolders = topFolders.take(10),
            largestFiles = largestFiles.take(15)
        )
    }

    private fun queryMediaCategorySize(uri: android.net.Uri): Long {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.SIZE)
            var total = 0L
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (sizeIdx != -1) {
                    while (cursor.moveToNext()) {
                        total += cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
            total
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFastFolderSize(dir: File, largestFiles: MutableList<FileItem>, maxDepth: Int, depth: Int = 0): Long {
        if (depth > maxDepth) return 0L
        var total = 0L
        val list = dir.listFiles() ?: return 0L
        for (f in list) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                total += getFastFolderSize(f, largestFiles, maxDepth, depth + 1)
            } else {
                val len = f.length()
                total += len
                if (len > 30 * 1024 * 1024L) { // > 30MB
                    largestFiles.add(FileItem(f))
                }
            }
        }
        return total
    }
}
