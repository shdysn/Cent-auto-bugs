package com.ct.explorer.data.repository

import android.content.Context
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.TrashItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

class TrashRepository(private val context: Context) {

    private val trashDir: File = File(context.filesDir, "recycle_bin").apply { mkdirs() }
    private val manifestFile: File = File(context.filesDir, "trash_manifest.json")
    private val mutex = Mutex()

    suspend fun getTrashItems(autoPurge: Boolean = true): List<TrashItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadTrashItemsLocked(autoPurge)
        }
    }

    private fun loadTrashItemsLocked(autoPurge: Boolean): List<TrashItem> {
        if (!manifestFile.exists()) return emptyList()
        return try {
            val jsonStr = manifestFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TrashItem>()
            val now = System.currentTimeMillis()
            val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
            val expiredItems = mutableListOf<TrashItem>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val trashFileName = obj.getString("trashFileName")
                val fileInTrash = File(trashDir, trashFileName)
                val deletedTime = obj.optLong("deletedTimestamp", now)

                if (fileInTrash.exists()) {
                    val item = TrashItem(
                        id = id,
                        originalPath = obj.getString("originalPath"),
                        trashFileName = trashFileName,
                        displayName = obj.getString("displayName"),
                        size = obj.optLong("size", fileInTrash.length()),
                        deletedTimestamp = deletedTime,
                        isDirectory = obj.optBoolean("isDirectory", fileInTrash.isDirectory)
                    )

                    if (autoPurge && (now - deletedTime > thirtyDaysMillis)) {
                        expiredItems.add(item)
                    } else {
                        list.add(item)
                    }
                }
            }

            // Remove expired items automatically (Auto-purge 30 days)
            if (expiredItems.isNotEmpty()) {
                for (exp in expiredItems) {
                    val f = File(trashDir, exp.trashFileName)
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
                saveManifestLocked(list)
            }

            list.sortedByDescending { it.deletedTimestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun purgeExpiredItems(retentionDays: Int = 30): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000)
            val all = loadTrashItemsLocked(autoPurge = false)
            val toDelete = all.filter { it.deletedTimestamp < cutoff }
            for (item in toDelete) {
                val fileInTrash = File(trashDir, item.trashFileName)
                if (fileInTrash.exists()) {
                    if (fileInTrash.isDirectory) fileInTrash.deleteRecursively() else fileInTrash.delete()
                }
            }
            val remaining = all.filterNot { it.deletedTimestamp < cutoff }
            saveManifestLocked(remaining)
            toDelete.size
        }
    }

    private fun saveManifestLocked(items: List<TrashItem>) {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("originalPath", item.originalPath)
                put("trashFileName", item.trashFileName)
                put("displayName", item.displayName)
                put("size", item.size)
                put("deletedTimestamp", item.deletedTimestamp)
                put("isDirectory", item.isDirectory)
            }
            array.put(obj)
        }
        val tmpFile = File(context.filesDir, "trash_manifest.json.tmp")
        try {
            tmpFile.writeText(array.toString())
            if (!tmpFile.renameTo(manifestFile)) {
                manifestFile.writeText(array.toString())
                tmpFile.delete()
            }
        } catch (_: Exception) {
            try { manifestFile.writeText(array.toString()) } catch (_: Exception) {}
        }
    }

    suspend fun moveToTrash(fileItem: FileItem): Result<TrashItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                // Pre-check storage space to prevent ENOSPC out-of-storage crash on cross-filesystem moves
                val requiredSpace = if (fileItem.file.isDirectory) {
                    try {
                        fileItem.file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } catch (_: Exception) {
                        1024L * 1024L
                    }
                } else {
                    fileItem.file.length()
                }
                val usable = context.filesDir.usableSpace
                if (usable < requiredSpace + (10L * 1024 * 1024)) { // 10MB safety margin
                    return@withLock Result.failure(IOException("Insufficient internal storage to move item to Recycle Bin"))
                }

                val id = UUID.randomUUID().toString()
                val trashName = "${id}_${fileItem.name}"
                val destFile = File(trashDir, trashName)

                val moved = if (fileItem.file.renameTo(destFile)) {
                    true
                } else {
                    // Fallback copy then delete if across filesystems
                    if (fileItem.file.isDirectory) {
                        val copied = fileItem.file.copyRecursively(destFile, overwrite = true)
                        if (copied) fileItem.file.deleteRecursively() else false
                    } else {
                        val copied = try {
                            fileItem.file.copyTo(destFile, overwrite = true)
                            true
                        } catch (_: Exception) {
                            false
                        }
                        if (copied) fileItem.file.delete() else false
                    }
                }

                if (!moved) {
                    if (destFile.exists()) {
                        if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                    }
                    return@withLock Result.failure(Exception("Could not move file to Recycle Bin"))
                }

                val trashItem = TrashItem(
                    id = id,
                    originalPath = fileItem.path,
                    trashFileName = trashName,
                    displayName = fileItem.name,
                    size = fileItem.size,
                    deletedTimestamp = System.currentTimeMillis(),
                    isDirectory = fileItem.isDirectory
                )

                val currentItems = loadTrashItemsLocked(autoPurge = false).toMutableList()
                currentItems.add(0, trashItem)
                saveManifestLocked(currentItems)

                Result.success(trashItem)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun restoreItem(trashItem: TrashItem): Result<File> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val sourceFile = File(trashDir, trashItem.trashFileName)
                if (!sourceFile.exists()) {
                    return@withLock Result.failure(Exception("File missing in Recycle Bin"))
                }

                var destFile = File(trashItem.originalPath)
                destFile.parentFile?.mkdirs()

                // If a file with the same name already exists in target location, rename with (Restored)
                if (destFile.exists()) {
                    val parent = destFile.parentFile ?: trashDir
                    val nameWithoutExt = destFile.nameWithoutExtension
                    val ext = destFile.extension
                    val newName = if (ext.isNotEmpty()) "$nameWithoutExt (Restored).$ext" else "$nameWithoutExt (Restored)"
                    destFile = File(parent, newName)
                }

                val restored = if (sourceFile.renameTo(destFile)) {
                    true
                } else {
                    if (sourceFile.isDirectory) {
                        val copied = sourceFile.copyRecursively(destFile, overwrite = true)
                        if (copied) sourceFile.deleteRecursively() else false
                    } else {
                        val copied = try {
                            sourceFile.copyTo(destFile, overwrite = true)
                            true
                        } catch (_: Exception) {
                            false
                        }
                        if (copied) sourceFile.delete() else false
                    }
                }

                if (!restored) {
                    if (destFile.exists()) {
                        if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                    }
                    return@withLock Result.failure(Exception("Failed to restore file"))
                }

                val currentItems = loadTrashItemsLocked(autoPurge = false).filterNot { it.id == trashItem.id }
                saveManifestLocked(currentItems)

                Result.success(destFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deletePermanently(trashItem: TrashItem): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val fileInTrash = File(trashDir, trashItem.trashFileName)
                if (fileInTrash.exists()) {
                    if (fileInTrash.isDirectory) fileInTrash.deleteRecursively() else fileInTrash.delete()
                }
                val currentItems = loadTrashItemsLocked(autoPurge = false).filterNot { it.id == trashItem.id }
                saveManifestLocked(currentItems)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun emptyTrash(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                trashDir.listFiles()?.forEach {
                    if (it.isDirectory) it.deleteRecursively() else it.delete()
                }
                saveManifestLocked(emptyList())
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
