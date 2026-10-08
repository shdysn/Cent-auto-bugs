package com.ct.explorer.data.model

import android.graphics.drawable.Drawable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApkFileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val size: Long = file.length(),
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long = 0L,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val isBackup: Boolean = false,
    val splitCount: Int = 0,
    val permissions: List<String> = emptyList(),
    val supportedAbis: List<String> = emptyList(),
    val icon: Drawable? = null,
    val lastModified: Long = file.lastModified()
) {
    val formattedSize: String get() = FileItem.formatBytes(size)
    val formattedDate: String get() {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(lastModified))
    }

    val isDowngradeCandidate: Boolean
        get() = isInstalled && installedVersionCode > 0 && versionCode > 0 && versionCode < installedVersionCode

    val isUpgradeCandidate: Boolean
        get() = isInstalled && installedVersionCode > 0 && versionCode > installedVersionCode

    val isCurrentVersion: Boolean
        get() = isInstalled && installedVersionCode > 0 && versionCode == installedVersionCode

    val minSdkLabel: String get() = getAndroidVersionName(minSdk)
    val targetSdkLabel: String get() = getAndroidVersionName(targetSdk)

    val dangerousPermissions: List<String> get() {
        return permissions.filter { perm ->
            val upper = perm.uppercase()
            upper.contains("CAMERA") ||
            upper.contains("LOCATION") ||
            upper.contains("RECORD_AUDIO") ||
            upper.contains("CONTACTS") ||
            upper.contains("SMS") ||
            upper.contains("STORAGE") ||
            upper.contains("NOTIFICATIONS") ||
            upper.contains("PHONE") ||
            upper.contains("BLUETOOTH")
        }.map { it.substringAfterLast('.') }
    }
}

fun getAndroidVersionName(api: Int): String = when (api) {
    36 -> "Android 16"
    35 -> "Android 15"
    34 -> "Android 14"
    33 -> "Android 13"
    32, 31 -> "Android 12"
    30 -> "Android 11"
    29 -> "Android 10"
    28 -> "Android 9.0 Pie"
    27, 26 -> "Android 8.0 Oreo"
    25, 24 -> "Android 7.0 Nougat"
    23 -> "Android 6.0 Marshmallow"
    22, 21 -> "Android 5.0 Lollipop"
    else -> if (api > 0) "Android API $api" else "Universal (All Androids)"
}


data class AppBackupGroup(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val backups: List<ApkFileItem> = emptyList()
) {
    val totalBackupSize: Long get() = backups.sumOf { it.size }
    val formattedTotalSize: String get() = FileItem.formatBytes(totalBackupSize)
    val hasDowngradeOption: Boolean get() = backups.any { it.isDowngradeCandidate }
    val latestBackup: ApkFileItem? get() = backups.maxByOrNull { it.versionCode.takeIf { vc -> vc > 0 } ?: it.lastModified }
    val backupCount: Int get() = backups.size
}

enum class ApkTab {
    INSTALLED_APPS,   // 1-Tap App Extractor & Cloner
    DOWNGRADE_HUB,    // Backup & Downgrade Archive Hub
    APK_FILES         // Storage APKs
}

