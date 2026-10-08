package com.ct.explorer.data.repository

import android.content.Context
import com.ct.explorer.data.model.ColorTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TagsRepository(private val context: Context) {

    private val tagsFile = File(context.filesDir, "tags_manifest.json")

    // Map: File path -> List of Tag IDs
    suspend fun getFileTagsMap(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (!tagsFile.exists()) return@withContext emptyMap()
        try {
            val jsonStr = tagsFile.readText()
            val obj = JSONObject(jsonStr)
            val result = mutableMapOf<String, List<String>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val path = keys.next()
                val array = obj.getJSONArray(path)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                result[path] = list
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getTagsForFile(filePath: String): List<ColorTag> {
        val map = getFileTagsMap()
        val tagIds = map[filePath] ?: return emptyList()
        return tagIds.mapNotNull { ColorTag.findTag(it) }
    }

    suspend fun toggleTagForFile(filePath: String, tagId: String): List<ColorTag> = withContext(Dispatchers.IO) {
        val currentMap = getFileTagsMap().toMutableMap()
        val currentTags = currentMap[filePath]?.toMutableList() ?: mutableListOf()
        if (currentTags.contains(tagId)) {
            currentTags.remove(tagId)
        } else {
            currentTags.add(tagId)
        }

        if (currentTags.isEmpty()) {
            currentMap.remove(filePath)
        } else {
            currentMap[filePath] = currentTags
        }

        saveMap(currentMap)
        currentTags.mapNotNull { ColorTag.findTag(it) }
    }

    suspend fun getFilesForTag(tagId: String): List<String> {
        val map = getFileTagsMap()
        return map.filter { it.value.contains(tagId) }.keys.toList()
    }

    private fun saveMap(map: Map<String, List<String>>) {
        val obj = JSONObject()
        for ((path, tags) in map) {
            val array = JSONArray()
            tags.forEach { array.put(it) }
            obj.put(path, array)
        }
        tagsFile.writeText(obj.toString())
    }
}
