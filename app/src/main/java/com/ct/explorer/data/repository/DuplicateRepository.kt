package com.ct.explorer.data.repository

import android.content.Context
import android.os.Environment
import com.ct.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

data class DuplicateGroup(
    val original: FileItem,
    val duplicates: List<FileItem>
) {
    val wastedBytes: Long get() = duplicates.sumOf { it.size }
    val totalFiles: Int get() = duplicates.size + 1
}

data class DuplicateScanResult(
    val groups: List<DuplicateGroup>,
    val totalWastedBytes: Long
)

class DuplicateRepository(private val context: Context) {

    suspend fun findDuplicates(): DuplicateScanResult = withContext(Dispatchers.IO) {
        val searchDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        ).filter { it.exists() && it.canRead() }

        val collectedFiles = mutableListOf<File>()
        for (dir in searchDirs) {
            collectFiles(dir, collectedFiles, maxFiles = 1000, depth = 0, maxDepth = 3)
        }

        val allFiles = collectedFiles.distinctBy { it.canonicalPath }

        // Group 1: Group by file size (> 10KB)
        val sizeMap = allFiles
            .filter { it.length() > 10 * 1024L }
            .groupBy { it.length() }
            .filter { it.value.size > 1 }

        val duplicateGroups = mutableListOf<DuplicateGroup>()

        // Group 2: For items with same size, compute partial hash (fast)
        for ((_, candidateFiles) in sizeMap) {
            val distinctCandidates = candidateFiles.distinctBy { it.canonicalPath }
            if (distinctCandidates.size < 2) continue

            val hashMap = mutableMapOf<String, MutableList<File>>()
            for (file in distinctCandidates) {
                val hash = getPartialHash(file)
                if (hash != null) {
                    hashMap.getOrPut(hash) { mutableListOf() }.add(file)
                }
            }

            for ((_, matchingFiles) in hashMap) {
                if (matchingFiles.size > 1) {
                    val verifiedSets = mutableListOf<MutableList<File>>()
                    for (file in matchingFiles) {
                        var added = false
                        for (set in verifiedSets) {
                            if (set.any { it.canonicalPath == file.canonicalPath }) {
                                added = true
                                break
                            }
                            if (isExactDuplicate(set.first(), file)) {
                                set.add(file)
                                added = true
                                break
                            }
                        }
                        if (!added) {
                            verifiedSets.add(mutableListOf(file))
                        }
                    }

                    for (set in verifiedSets) {
                        if (set.size > 1) {
                            val sorted = set.sortedBy { it.lastModified() }
                            val original = FileItem(sorted.first())
                            val dupes = sorted.drop(1).map { FileItem(it) }
                            duplicateGroups.add(DuplicateGroup(original, dupes))
                        }
                    }
                }
            }
        }

        val totalWasted = duplicateGroups.sumOf { it.wastedBytes }
        DuplicateScanResult(
            groups = duplicateGroups.sortedByDescending { it.wastedBytes },
            totalWastedBytes = totalWasted
        )
    }

    private fun collectFiles(dir: File, result: MutableList<File>, maxFiles: Int, depth: Int, maxDepth: Int) {
        if (depth > maxDepth || result.size >= maxFiles) return
        if (dir.name.startsWith(".") || dir.name.equals("Android", ignoreCase = true)) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                collectFiles(f, result, maxFiles, depth + 1, maxDepth)
            } else {
                result.add(f)
            }
        }
    }

    private fun getPartialHash(file: File): String? {
        return try {
            val length = file.length()
            if (length <= 0) return null
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(16 * 1024)
            RandomAccessFile(file, "r").use { raf ->
                // 1. Head chunk (16KB)
                val headRead = raf.read(buffer)
                if (headRead > 0) md.update(buffer, 0, headRead)
                // 2. Tail chunk (16KB) if file > 32KB
                if (length > 32 * 1024) {
                    raf.seek(length - (16 * 1024))
                    val tailRead = raf.read(buffer)
                    if (tailRead > 0) md.update(buffer, 0, tailRead)
                }
            }
            // Include file length in hash
            md.digest().joinToString("") { "%02x".format(it) } + "_$length"
        } catch (e: Exception) {
            null
        }
    }

    private fun isExactDuplicate(f1: File, f2: File): Boolean {
        if (f1.canonicalPath == f2.canonicalPath) return false
        if (f1.length() != f2.length()) return false
        return try {
            java.io.BufferedInputStream(FileInputStream(f1)).use { in1 ->
                java.io.BufferedInputStream(FileInputStream(f2)).use { in2 ->
                    val buf1 = ByteArray(64 * 1024)
                    val buf2 = ByteArray(64 * 1024)
                    var remaining = f1.length()
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buf1.size.toLong()).toInt()
                        val r1 = readFully(in1, buf1, toRead)
                        val r2 = readFully(in2, buf2, toRead)
                        if (r1 != r2) return false
                        for (i in 0 until r1) {
                            if (buf1[i] != buf2[i]) return false
                        }
                        remaining -= r1
                    }
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readFully(stream: java.io.InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = stream.read(buffer, total, length - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    suspend fun deleteFiles(files: List<FileItem>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (item in files) {
            try {
                if (item.file.delete()) count++
            } catch (e: Exception) {
                // ignore
            }
        }
        count
    }
}
