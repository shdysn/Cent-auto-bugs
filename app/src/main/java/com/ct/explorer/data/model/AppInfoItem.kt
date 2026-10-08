package com.ct.explorer.data.model

import android.graphics.drawable.Drawable

data class AppInfoItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long = 0L,
    val isSystemApp: Boolean = false,
    val apkSize: Long = 0L,
    val icon: Drawable? = null,
    val splitApksCount: Int = 0,
    val sourceDir: String = "",
    val isBackedUp: Boolean = false,
    val backedUpVersionCount: Int = 0
) {
    val formattedSize: String
        get() = FileItem.formatBytes(apkSize)
}

