package com.ct.explorer.data.repository

import android.content.Context
import android.os.Environment
import com.ct.explorer.data.model.FavoriteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FavoritesRepository(private val context: Context) {

    private val favoritesFile = File(context.filesDir, "favorites.json")

    suspend fun getFavorites(): List<FavoriteItem> = withContext(Dispatchers.IO) {
        if (!favoritesFile.exists()) {
            val defaults = generateDefaultFavorites()
            saveFavorites(defaults)
            return@withContext defaults
        }

        try {
            val jsonStr = favoritesFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<FavoriteItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.getString("path")
                val f = File(path)
                list.add(
                    FavoriteItem(
                        path = path,
                        name = obj.getString("name"),
                        isDirectory = obj.optBoolean("isDirectory", f.isDirectory),
                        addedTimestamp = obj.optLong("addedTimestamp", 0L)
                    )
                )
            }
            if (list.isEmpty()) {
                val defaults = generateDefaultFavorites()
                saveFavorites(defaults)
                defaults
            } else {
                list
            }
        } catch (e: Exception) {
            generateDefaultFavorites()
        }
    }

    private fun generateDefaultFavorites(): List<FavoriteItem> {
        val root = Environment.getExternalStorageDirectory()
        val standardFolders = listOf(
            File(root, Environment.DIRECTORY_DOWNLOADS) to "Downloads",
            File(root, Environment.DIRECTORY_DCIM) to "DCIM / Camera",
            File(root, Environment.DIRECTORY_DOCUMENTS) to "Documents",
            File(root, Environment.DIRECTORY_PICTURES) to "Pictures",
            File(root, Environment.DIRECTORY_MUSIC) to "Music"
        )
        return standardFolders.map { (file, label) ->
            FavoriteItem(
                path = file.absolutePath,
                name = label,
                isDirectory = true,
                addedTimestamp = System.currentTimeMillis()
            )
        }
    }

    private fun saveFavorites(items: List<FavoriteItem>) {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("path", item.path)
                put("name", item.name)
                put("isDirectory", item.isDirectory)
                put("addedTimestamp", item.addedTimestamp)
            }
            array.put(obj)
        }
        favoritesFile.writeText(array.toString())
    }

    suspend fun toggleFavorite(file: File, customName: String? = null): Boolean = withContext(Dispatchers.IO) {
        val current = getFavorites().toMutableList()
        val existingIndex = current.indexOfFirst { it.path == file.absolutePath }
        val nowFavorite = if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            false
        } else {
            current.add(
                FavoriteItem(
                    path = file.absolutePath,
                    name = customName ?: file.name.ifEmpty { "Storage" },
                    isDirectory = file.isDirectory,
                    addedTimestamp = System.currentTimeMillis()
                )
            )
            true
        }
        saveFavorites(current)
        nowFavorite
    }

    suspend fun isFavorite(path: String): Boolean = withContext(Dispatchers.IO) {
        getFavorites().any { it.path == path }
    }
}
