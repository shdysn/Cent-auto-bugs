package com.ct.explorer.data.model

import java.io.File

data class FavoriteItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    val file: File get() = File(path)
    val exists: Boolean get() = file.exists()
}
