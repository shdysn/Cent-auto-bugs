package com.ct.explorer.data.model

import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SocialFolderType(
    val title: String,
    val brandColorHex: Long
) {
    WHATSAPP("WhatsApp", 0xFF25D366),
    TELEGRAM("Telegram", 0xFF24A1DE),
    INSTAGRAM("Instagram", 0xFFE1306C),
    FACEBOOK("Facebook", 0xFF1877F2),
    MESSENGER("Messenger", 0xFF0084FF),
    TIKTOK("TikTok", 0xFFFE2C55),
    SNAPCHAT("Snapchat", 0xFFEAB308),
    TWITTER("X / Twitter", 0xFF1DA1F2),
    YOUTUBE("YouTube", 0xFFFF0000),
    REDDIT("Reddit", 0xFFFF4500),
    DISCORD("Discord", 0xFF5865F2),
    PINTEREST("Pinterest", 0xFFE60023),
    LINKEDIN("LinkedIn", 0xFF0A66C2),
    WECHAT("WeChat", 0xFF07C160),
    SHAREME("ShareMe", 0xFF0284C7)
}

enum class FileCategory {
    FOLDER,
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    ARCHIVE,
    APK,
    CODE,
    UNKNOWN
}

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val isHidden: Boolean = file.isHidden || file.name.startsWith("."),
    val extension: String = if (file.isDirectory) "" else file.extension.lowercase(Locale.ROOT),
    val itemCount: Int = 0,
    val folderSize: Long? = null
) {
    val effectiveSize: Long
        get() = if (isDirectory) (folderSize ?: size) else size

    val isHuge: Boolean get() = effectiveSize >= 500L * 1024 * 1024
    val isVeryLarge: Boolean get() = effectiveSize >= 100L * 1024 * 1024
    val isLarge: Boolean get() = effectiveSize >= 25L * 1024 * 1024
    val isBig: Boolean get() = effectiveSize >= 10L * 1024 * 1024

    val sizeBadgeText: String?
        get() = when {
            isHuge -> "🔥 HUGE"
            isVeryLarge -> "⚡ BIG"
            isLarge -> "LARGE"
            isBig -> ">10MB"
            else -> null
        }
    val category: FileCategory
        get() = when {
            isDirectory -> FileCategory.FOLDER
            extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "dng", "svg", "raw") -> FileCategory.IMAGE
            extension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "opus", "amr", "m4b", "mid", "midi") -> FileCategory.AUDIO
            extension in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "mpg", "mpeg") -> FileCategory.VIDEO
            extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub") -> FileCategory.DOCUMENT
            extension in listOf("zip", "rar", "7z", "tar", "gz") -> FileCategory.ARCHIVE
            extension in listOf("apk", "xapk", "apks") -> FileCategory.APK
            extension in listOf("kt", "java", "py", "js", "html", "css", "json", "xml", "c", "cpp", "sh", "md") -> FileCategory.CODE
            else -> FileCategory.UNKNOWN
        }

    val mimeType: String
        get() {
            if (isDirectory) return "resource/folder"
            val map = MimeTypeMap.getSingleton()
            val extMime = map.getMimeTypeFromExtension(extension)
            if (!extMime.isNullOrEmpty()) return extMime

            return when (extension) {
                "apk", "xapk", "apks" -> "application/vnd.android.package-archive"
                "json" -> "application/json"
                "md" -> "text/markdown"
                "html", "htm" -> "text/html"
                "xml" -> "text/xml"
                "csv" -> "text/csv"
                "kt", "java", "py", "js", "css", "c", "cpp", "sh", "log", "txt" -> "text/plain"
                "pdf" -> "application/pdf"
                "epub" -> "application/epub+zip"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "7z" -> "application/x-7z-compressed"
                "tar" -> "application/x-tar"
                "gz" -> "application/gzip"
                "mp3" -> "audio/mpeg"
                "m4a", "aac" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "*/*"
            }
        }

    val socialType: SocialFolderType?
        get() {
            if (!isDirectory) return null
            val lower = name.lowercase(Locale.ROOT)
            return when {
                lower.contains("whatsapp") -> SocialFolderType.WHATSAPP
                lower.contains("telegram") -> SocialFolderType.TELEGRAM
                lower.contains("instagram") -> SocialFolderType.INSTAGRAM
                lower.contains("facebook") || lower == "fb" -> SocialFolderType.FACEBOOK
                lower.contains("messenger") -> SocialFolderType.MESSENGER
                lower.contains("tiktok") || lower.contains("musically") -> SocialFolderType.TIKTOK
                lower.contains("snapchat") || lower.contains("snap") -> SocialFolderType.SNAPCHAT
                lower.contains("twitter") || lower == "x" -> SocialFolderType.TWITTER
                lower.contains("youtube") || lower == "yt" -> SocialFolderType.YOUTUBE
                lower.contains("reddit") -> SocialFolderType.REDDIT
                lower.contains("discord") -> SocialFolderType.DISCORD
                lower.contains("pinterest") -> SocialFolderType.PINTEREST
                lower.contains("linkedin") -> SocialFolderType.LINKEDIN
                lower.contains("wechat") || lower.contains("weixin") -> SocialFolderType.WECHAT
                lower.contains("shareme") || lower.contains("shareit") || lower.contains("midrop") -> SocialFolderType.SHAREME
                else -> null
            }
        }

    val friendlyTypeLabel: String
        get() = when (category) {
            FileCategory.FOLDER -> socialType?.let { "${it.title} Folder" } ?: "Folder"
            FileCategory.IMAGE -> "${extension.uppercase(Locale.ROOT)} Image"
            FileCategory.AUDIO -> "${extension.uppercase(Locale.ROOT)} Audio"
            FileCategory.VIDEO -> "${extension.uppercase(Locale.ROOT)} Video"
            FileCategory.DOCUMENT -> when (extension) {
                "pdf" -> "PDF Document"
                "doc", "docx" -> "Word Document"
                "xls", "xlsx" -> "Excel Spreadsheet"
                "ppt", "pptx" -> "PowerPoint Presentation"
                "txt" -> "Text File"
                else -> "${extension.uppercase(Locale.ROOT)} Document"
            }
            FileCategory.ARCHIVE -> "Compressed Archive (${extension.uppercase(Locale.ROOT)})"
            FileCategory.APK -> "Android Application (APK)"
            FileCategory.CODE -> "${extension.uppercase(Locale.ROOT)} Source Code"
            FileCategory.UNKNOWN -> if (extension.isNotEmpty()) "${extension.uppercase(Locale.ROOT)} File" else "File"
        }

    val formattedSize: String
        get() = if (isDirectory) {
            val s = effectiveSize
            if (s > 0L) {
                val itemStr = "$itemCount item${if (itemCount == 1) "" else "s"}"
                "${formatBytes(s)} • $itemStr"
            } else if (itemCount > 0) {
                "$itemCount item${if (itemCount == 1) "" else "s"}"
            } else {
                "Folder"
            }
        } else {
            formatBytes(size)
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val formattedDateOnly: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val timeGroup: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - lastModified
            val oneDay = 24 * 60 * 60 * 1000L
            return when {
                diff < oneDay -> "Today"
                diff < 2 * oneDay -> "Yesterday"
                diff < 7 * oneDay -> "Earlier this week"
                diff < 30 * oneDay -> "This month"
                else -> "Earlier"
            }
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            if (gb < 1024.0) return String.format(Locale.US, "%.2f GB", gb)
            val tb = gb / 1024.0
            return String.format(Locale.US, "%.2f TB", tb)
        }
    }
}

data class StorageSpace(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedTotal: String get() = FileItem.formatBytes(totalBytes)
    val formattedUsed: String get() = FileItem.formatBytes(usedBytes)
    val formattedFree: String get() = FileItem.formatBytes(freeBytes)
}

enum class SortCriteria {
    NAME,
    SIZE,
    DATE,
    TYPE
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

enum class SortType(val criteria: SortCriteria, val direction: SortDirection) {
    NAME_ASC(SortCriteria.NAME, SortDirection.ASCENDING),
    NAME_DESC(SortCriteria.NAME, SortDirection.DESCENDING),
    DATE_NEWEST(SortCriteria.DATE, SortDirection.DESCENDING),
    DATE_OLDEST(SortCriteria.DATE, SortDirection.ASCENDING),
    SIZE_LARGEST(SortCriteria.SIZE, SortDirection.DESCENDING),
    SIZE_SMALLEST(SortCriteria.SIZE, SortDirection.ASCENDING),
    TYPE_ASC(SortCriteria.TYPE, SortDirection.ASCENDING),
    TYPE_DESC(SortCriteria.TYPE, SortDirection.DESCENDING),
    TYPE(SortCriteria.TYPE, SortDirection.ASCENDING);

    val label: String
        get() = when (this) {
            NAME_ASC -> "Name (A to Z)"
            NAME_DESC -> "Name (Z to A)"
            DATE_NEWEST -> "Date (Newest first)"
            DATE_OLDEST -> "Date (Oldest first)"
            SIZE_LARGEST -> "Size (Largest first)"
            SIZE_SMALLEST -> "Size (Smallest first)"
            TYPE_ASC, TYPE -> "Type (A to Z)"
            TYPE_DESC -> "Type (Z to A)"
        }

    val chipLabel: String
        get() = when (this) {
            NAME_ASC -> "Name ↑"
            NAME_DESC -> "Name ↓"
            DATE_NEWEST -> "Date ↓"
            DATE_OLDEST -> "Date ↑"
            SIZE_LARGEST -> "Size ↓"
            SIZE_SMALLEST -> "Size ↑"
            TYPE_ASC, TYPE -> "Type ↑"
            TYPE_DESC -> "Type ↓"
        }

    companion object {
        fun from(criteria: SortCriteria, direction: SortDirection): SortType {
            return when (criteria) {
                SortCriteria.NAME -> if (direction == SortDirection.ASCENDING) NAME_ASC else NAME_DESC
                SortCriteria.SIZE -> if (direction == SortDirection.DESCENDING) SIZE_LARGEST else SIZE_SMALLEST
                SortCriteria.DATE -> if (direction == SortDirection.DESCENDING) DATE_NEWEST else DATE_OLDEST
                SortCriteria.TYPE -> if (direction == SortDirection.ASCENDING) TYPE_ASC else TYPE_DESC
            }
        }
    }
}

fun sortFileList(
    items: List<FileItem>,
    sortType: SortType,
    foldersOnTop: Boolean = true
): List<FileItem> {
    val comparator: Comparator<FileItem> = when (sortType) {
        SortType.NAME_ASC -> compareBy { it.name.lowercase(Locale.ROOT) }
        SortType.NAME_DESC -> compareByDescending { it.name.lowercase(Locale.ROOT) }
        SortType.DATE_NEWEST -> compareByDescending { it.lastModified }
        SortType.DATE_OLDEST -> compareBy { it.lastModified }
        SortType.SIZE_LARGEST -> compareByDescending { it.effectiveSize }
        SortType.SIZE_SMALLEST -> compareBy { it.effectiveSize }
        SortType.TYPE_ASC, SortType.TYPE -> compareBy<FileItem>({ it.extension }, { it.name.lowercase(Locale.ROOT) })
        SortType.TYPE_DESC -> compareByDescending<FileItem>({ it.extension }).thenBy { it.name.lowercase(Locale.ROOT) }
    }

    return if (foldersOnTop) {
        val folders = items.filter { it.isDirectory }.sortedWith(comparator)
        val files = items.filter { !it.isDirectory }.sortedWith(comparator)
        folders + files
    } else {
        items.sortedWith(comparator)
    }
}

enum class ViewMode {
    LIST,
    GRID
}

data class ClipboardState(
    val items: List<FileItem>,
    val isCut: Boolean = false
)
