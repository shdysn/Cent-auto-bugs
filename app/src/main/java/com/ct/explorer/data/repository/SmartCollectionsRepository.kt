package com.ct.explorer.data.repository

import android.content.Context
import android.os.Environment
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SmartCollection(
    val id: String,
    val title: String,
    val description: String,
    val iconTag: String,
    val colorHex: String,
    val isPreset: Boolean = false,
    val autoKeywords: List<String> = emptyList(),
    val manualFilePaths: List<String> = emptyList()
) {
    fun matchesFile(file: File): Boolean {
        val nameLower = file.name.lowercase()
        return autoKeywords.any { kw -> nameLower.contains(kw) } || manualFilePaths.contains(file.absolutePath)
    }
}

data class CollectionWithFiles(
    val collection: SmartCollection,
    val items: List<FileItem>,
    val totalBytes: Long
) {
    val formattedTotalSize: String get() = FileItem.formatBytes(totalBytes)
}

class SmartCollectionsRepository(private val context: Context) {

    private val storageFile = File(context.filesDir, "smart_collections.json")

    private val defaultCollections = listOf(
        SmartCollection(
            id = "preset_ids",
            title = "Official IDs & Passports",
            description = "Passports, CNIC, licenses, resumes, and personal documents",
            iconTag = "badge",
            colorHex = "#3B82F6",
            isPreset = true,
            autoKeywords = listOf("cnic", "passport", "id_card", "license", "resume", "cv", "certificate", "degree", "national_id")
        ),
        SmartCollection(
            id = "preset_receipts",
            title = "Receipts & Invoices",
            description = "Expense receipts, bank statements, bills, and transaction slips",
            iconTag = "receipt",
            colorHex = "#10B981",
            isPreset = true,
            autoKeywords = listOf("receipt", "invoice", "bill", "slip", "payment", "statement", "order_", "transaction", "tax")
        ),
        SmartCollection(
            id = "preset_work",
            title = "Work & Projects",
            description = "Spreadsheets, presentations, project briefs, and source files",
            iconTag = "work",
            colorHex = "#FF6700",
            isPreset = true,
            autoKeywords = listOf("project", "presentation", "report", "proposal", "budget", "client", "meeting", "roadmap")
        ),
        SmartCollection(
            id = "preset_screenshots",
            title = "Screenshots & Snaps",
            description = "All captured device screenshots organized in one place",
            iconTag = "camera",
            colorHex = "#8B5CF6",
            isPreset = true,
            autoKeywords = listOf("screenshot", "screen_shot", "screencap", "capture")
        )
    )

    suspend fun getCollections(): List<SmartCollection> = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) {
            saveCollections(defaultCollections)
            return@withContext defaultCollections
        }

        try {
            val json = storageFile.readText()
            val array = JSONArray(json)
            val list = mutableListOf<SmartCollection>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val kwList = mutableListOf<String>()
                val kwArr = obj.optJSONArray("autoKeywords")
                if (kwArr != null) {
                    for (k in 0 until kwArr.length()) kwList.add(kwArr.getString(k))
                }

                val pathList = mutableListOf<String>()
                val pathArr = obj.optJSONArray("manualFilePaths")
                if (pathArr != null) {
                    for (p in 0 until pathArr.length()) pathList.add(pathArr.getString(p))
                }

                list.add(
                    SmartCollection(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        iconTag = obj.optString("iconTag", "folder"),
                        colorHex = obj.optString("colorHex", "#FF6700"),
                        isPreset = obj.optBoolean("isPreset", false),
                        autoKeywords = kwList,
                        manualFilePaths = pathList
                    )
                )
            }
            if (list.isEmpty()) defaultCollections else list
        } catch (e: Exception) {
            defaultCollections
        }
    }

    suspend fun saveCollections(collections: List<SmartCollection>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        collections.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("description", c.description)
                put("iconTag", c.iconTag)
                put("colorHex", c.colorHex)
                put("isPreset", c.isPreset)
                put("autoKeywords", JSONArray(c.autoKeywords))
                put("manualFilePaths", JSONArray(c.manualFilePaths))
            }
            array.put(obj)
        }
        storageFile.writeText(array.toString())
    }

    suspend fun addFileToCollection(collectionId: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        val current = getCollections().toMutableList()
        val index = current.indexOfFirst { it.id == collectionId }
        if (index >= 0) {
            val coll = current[index]
            if (!coll.manualFilePaths.contains(filePath)) {
                val updatedPaths = coll.manualFilePaths + filePath
                current[index] = coll.copy(manualFilePaths = updatedPaths)
                saveCollections(current)
                return@withContext true
            }
        }
        false
    }

    suspend fun removeFileFromCollection(collectionId: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        val current = getCollections().toMutableList()
        val index = current.indexOfFirst { it.id == collectionId }
        if (index >= 0) {
            val coll = current[index]
            val updated = coll.manualFilePaths.filter { it != filePath }
            current[index] = coll.copy(manualFilePaths = updated)
            saveCollections(current)
            return@withContext true
        }
        false
    }

    suspend fun createCustomCollection(title: String, description: String, colorHex: String, iconTag: String): SmartCollection = withContext(Dispatchers.IO) {
        val current = getCollections().toMutableList()
        val newColl = SmartCollection(
            id = "custom_${System.currentTimeMillis()}",
            title = title,
            description = description,
            iconTag = iconTag,
            colorHex = colorHex,
            isPreset = false
        )
        current.add(newColl)
        saveCollections(current)
        newColl
    }

    suspend fun loadCollectionFiles(collection: SmartCollection): CollectionWithFiles = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val matchedFiles = mutableListOf<File>()

        // 1. Add manual files that exist
        collection.manualFilePaths.forEach { path ->
            val f = File(path)
            if (f.exists() && f.isFile) matchedFiles.add(f)
        }

        // 2. Scan standard directories for auto keywords
        if (collection.autoKeywords.isNotEmpty()) {
            val scanDirs = listOf(
                File(root, "Download"),
                File(root, "Documents"),
                File(root, "DCIM"),
                File(root, "Pictures")
            )

            val perDirLimit = 100
            scanDirs.forEach { dir ->
                val dirMatches = mutableListOf<File>()
                scanRecursiveForKeywords(dir, collection.autoKeywords, dirMatches, maxFiles = perDirLimit, currentDepth = 0, maxDepth = 4)
                matchedFiles.addAll(dirMatches)
            }
        }

        val items = matchedFiles.distinctBy { it.absolutePath }.map { FileItem(it) }
        val totalBytes = items.sumOf { it.size }
        CollectionWithFiles(collection, items, totalBytes)
    }

    private fun scanRecursiveForKeywords(
        dir: File,
        keywords: List<String>,
        out: MutableList<File>,
        maxFiles: Int,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (!dir.exists() || !dir.isDirectory || out.size >= maxFiles || currentDepth > maxDepth) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (out.size >= maxFiles) break
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanRecursiveForKeywords(f, keywords, out, maxFiles, currentDepth + 1, maxDepth)
            } else if (f.isFile) {
                val nameLower = f.name.lowercase()
                if (keywords.any { nameLower.contains(it) }) {
                    out.add(f)
                }
            }
        }
    }
}
