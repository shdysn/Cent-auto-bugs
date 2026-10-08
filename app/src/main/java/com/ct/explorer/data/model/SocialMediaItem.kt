package com.ct.explorer.data.model

import java.io.File

data class SocialFolderEntry(
    val name: String,
    val folder: File,
    val fileCount: Int,
    val sizeBytes: Long,
    val lastModified: Long,
    val categoryHint: String = "Media"
) {
    val formattedSize: String get() = FileItem.formatBytes(sizeBytes)
}

data class SocialAppGroup(
    val socialType: SocialFolderType,
    val appName: String = socialType.title,
    val packageName: String,
    val isAppInstalled: Boolean,
    val iconHexColor: Long = socialType.brandColorHex,
    val folders: List<SocialFolderEntry>
) {
    val totalFiles: Int get() = folders.sumOf { it.fileCount }
    val totalSizeBytes: Long get() = folders.sumOf { it.sizeBytes }
    val formattedTotalSize: String get() = FileItem.formatBytes(totalSizeBytes)
}

data class SocialHubState(
    val apps: List<SocialAppGroup> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: String = "All",
    val searchQuery: String = ""
)
