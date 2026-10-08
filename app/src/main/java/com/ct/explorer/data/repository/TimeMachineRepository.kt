package com.ct.explorer.data.repository

import android.os.Environment
import com.ct.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class OnThisDayGroup(
    val year: Int,
    val yearsAgo: Int,
    val items: List<FileItem>
)

data class TimeVelocityBucket(
    val title: String,
    val fileCount: Int,
    val totalBytes: Long,
    val percentageOfTotal: Int
) {
    val formattedBytes: String get() = FileItem.formatBytes(totalBytes)
}

data class TimeMachineData(
    val onThisDayGroups: List<OnThisDayGroup>,
    val velocityBuckets: List<TimeVelocityBucket>,
    val forgottenGiants: List<FileItem>,
    val totalScannedFiles: Int
)

class TimeMachineRepository {

    suspend fun analyzeTimeMachine(): TimeMachineData = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val allFiles = mutableListOf<File>()

        val targetDirs = listOf(
            File(root, "DCIM"),
            File(root, "Pictures"),
            File(root, "Download"),
            File(root, "Documents"),
            File(root, "Movies"),
            File(root, "Music")
        )

        val perDirLimit = 250
        targetDirs.forEach { dir ->
            val dirFiles = mutableListOf<File>()
            collectFiles(dir, dirFiles, maxCount = perDirLimit, currentDepth = 0, maxDepth = 4)
            allFiles.addAll(dirFiles)
        }

        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)

        // 1. "On This Day" Calculation
        val onThisDayMap = mutableMapOf<Int, MutableList<FileItem>>()

        allFiles.forEach { file ->
            val cal = Calendar.getInstance().apply { timeInMillis = file.lastModified() }
            val fileDay = cal.get(Calendar.DAY_OF_MONTH)
            val fileMonth = cal.get(Calendar.MONTH)
            val fileYear = cal.get(Calendar.YEAR)

            if (fileDay == currentDay && fileMonth == currentMonth && fileYear < currentYear) {
                val list = onThisDayMap.getOrPut(fileYear) { mutableListOf() }
                list.add(FileItem(file))
            }
        }

        val onThisDayGroups = onThisDayMap.map { (year, list) ->
            OnThisDayGroup(year = year, yearsAgo = currentYear - year, items = list)
        }.sortedByDescending { it.year }

        // 2. Storage Velocity Buckets
        val nowMs = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        var b24hBytes = 0L; var b24hCount = 0
        var b7dBytes = 0L; var b7dCount = 0
        var b30dBytes = 0L; var b30dCount = 0
        var b6mBytes = 0L; var b6mCount = 0
        var b1yBytes = 0L; var b1yCount = 0
        var bOldBytes = 0L; var bOldCount = 0

        val totalBytes = allFiles.sumOf { it.length() }

        allFiles.forEach { f ->
            val age = (nowMs - f.lastModified()).coerceAtLeast(0)
            val len = f.length()
            when {
                age <= dayMs -> { b24hBytes += len; b24hCount++ }
                age <= 7 * dayMs -> { b7dBytes += len; b7dCount++ }
                age <= 30 * dayMs -> { b30dBytes += len; b30dCount++ }
                age <= 180 * dayMs -> { b6mBytes += len; b6mCount++ }
                age <= 365 * dayMs -> { b1yBytes += len; b1yCount++ }
                else -> { bOldBytes += len; bOldCount++ }
            }
        }

        val velocityBuckets = listOf(
            TimeVelocityBucket("Past 24 Hours", b24hCount, b24hBytes, if (totalBytes > 0) ((b24hBytes * 100) / totalBytes).toInt() else 0),
            TimeVelocityBucket("Past 7 Days", b7dCount, b7dBytes, if (totalBytes > 0) ((b7dBytes * 100) / totalBytes).toInt() else 0),
            TimeVelocityBucket("Past 30 Days", b30dCount, b30dBytes, if (totalBytes > 0) ((b30dBytes * 100) / totalBytes).toInt() else 0),
            TimeVelocityBucket("1 - 6 Months", b6mCount, b6mBytes, if (totalBytes > 0) ((b6mBytes * 100) / totalBytes).toInt() else 0),
            TimeVelocityBucket("6 - 12 Months", b1yCount, b1yBytes, if (totalBytes > 0) ((b1yBytes * 100) / totalBytes).toInt() else 0),
            TimeVelocityBucket("Older than 1 Year", bOldCount, bOldBytes, if (totalBytes > 0) ((bOldBytes * 100) / totalBytes).toInt() else 0)
        ).filter { it.fileCount > 0 }

        // 3. Forgotten Giants: Files > 20MB older than 180 days
        val forgottenGiants = allFiles
            .filter { (nowMs - it.lastModified()) > 180 * dayMs && it.length() >= 20L * 1024 * 1024 }
            .sortedByDescending { it.length() }
            .take(20)
            .map { FileItem(it) }

        TimeMachineData(
            onThisDayGroups = onThisDayGroups,
            velocityBuckets = velocityBuckets,
            forgottenGiants = forgottenGiants,
            totalScannedFiles = allFiles.size
        )
    }

    private fun collectFiles(dir: File, out: MutableList<File>, maxCount: Int, currentDepth: Int, maxDepth: Int) {
        if (!dir.exists() || !dir.isDirectory || out.size >= maxCount || currentDepth > maxDepth) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (out.size >= maxCount) break
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                collectFiles(f, out, maxCount, currentDepth + 1, maxDepth)
            } else if (f.isFile && f.length() > 0) {
                out.add(f)
            }
        }
    }
}
