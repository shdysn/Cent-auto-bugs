package com.ct.explorer.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import java.io.File
import java.util.Locale

/**
 * Metadata descriptor representing a file or directory with Material Icons,
 * branded colors, category labels, and optional format badge chips.
 */
data class FileIconDescriptor(
    val icon: ImageVector,
    val tintColor: Color,
    val backgroundColor: Color = tintColor.copy(alpha = 0.14f),
    val categoryLabel: String,
    val badgeText: String? = null
)

/**
 * Centralized helper for determining and displaying appropriate Material Design 3 icons,
 * colors, and type badges for all file and folder types in Cent File Manager.
 */
object FileIconHelper {

    // Palette Constants
    val FolderAmber = Color(0xFFFFB300)
    val PdfRed = Color(0xFFEF4444)
    val DocBlue = Color(0xFF2563EB)
    val SheetGreen = Color(0xFF10B981)
    val SlideOrange = Color(0xFFF97316)
    val TextSlate = Color(0xFF64748B)
    val MarkdownTeal = Color(0xFF0D9488)
    val AudioAmber = Color(0xFFF59E0B)
    val AudioRecordingRose = Color(0xFFF43F5E)
    val ImageSky = Color(0xFF0284C7)
    val ImageGifPurple = Color(0xFF8B5CF6)
    val ImageRawOrange = Color(0xFFEA580C)
    val VideoPurple = Color(0xFF7C3AED)
    val ArchiveAmber = Color(0xFFD97706)
    val ArchiveBlue = Color(0xFF1D4ED8)
    val ApkGreen = Color(0xFF22C55E)
    val XapkCyan = Color(0xFF06B6D4)
    val CodeIndigo = Color(0xFF6366F1)
    val ConfigTeal = Color(0xFF14B8A6)
    val UnknownGray = Color(0xFF94A3B8)

    private val folderDescriptorCache = java.util.concurrent.ConcurrentHashMap<String, FileIconDescriptor>()
    private val fileDescriptorCache = java.util.concurrent.ConcurrentHashMap<String, FileIconDescriptor>()

    /**
     * Determine the icon descriptor for a FileItem.
     */
    fun getDescriptor(item: FileItem): FileIconDescriptor {
        return if (item.isDirectory) {
            folderDescriptorCache.getOrPut(item.name.lowercase(Locale.ROOT).trim()) {
                getFolderDescriptor(item.name)
            }
        } else {
            val key = "${item.extension.lowercase(Locale.ROOT).trim()}_${item.category.name}"
            fileDescriptorCache.getOrPut(key) {
                getFileDescriptor(item.name, item.extension, item.category)
            }
        }
    }

    /**
     * Determine the icon descriptor for a java.io.File.
     */
    fun getDescriptor(file: File): FileIconDescriptor {
        return if (file.isDirectory) {
            folderDescriptorCache.getOrPut(file.name.lowercase(Locale.ROOT).trim()) {
                getFolderDescriptor(file.name)
            }
        } else {
            val ext = file.extension.lowercase(Locale.ROOT).trim()
            val category = FileItem(file).category
            val key = "${ext}_${category.name}"
            fileDescriptorCache.getOrPut(key) {
                getFileDescriptor(file.name, ext, category)
            }
        }
    }

    /**
     * Determine descriptor based on directory name (supports standard system folders & social apps).
     */
    fun getFolderDescriptor(dirName: String): FileIconDescriptor {
        val lower = dirName.lowercase(Locale.ROOT).trim()

        return when {
            // Social Media Folders
            lower.contains("whatsapp") -> FileIconDescriptor(Icons.Default.Chat, Color(0xFF25D366), categoryLabel = "WhatsApp")
            lower.contains("telegram") -> FileIconDescriptor(Icons.AutoMirrored.Filled.Send, Color(0xFF24A1DE), categoryLabel = "Telegram")
            lower.contains("instagram") -> FileIconDescriptor(Icons.Default.PhotoCamera, Color(0xFFE1306C), categoryLabel = "Instagram")
            lower.contains("facebook") || lower == "fb" -> FileIconDescriptor(Icons.Default.ThumbUp, Color(0xFF1877F2), categoryLabel = "Facebook")
            lower.contains("messenger") -> FileIconDescriptor(Icons.Default.FlashOn, Color(0xFF0084FF), categoryLabel = "Messenger")
            lower.contains("tiktok") || lower.contains("musically") -> FileIconDescriptor(Icons.Default.MusicVideo, Color(0xFFFE2C55), categoryLabel = "TikTok")
            lower.contains("snapchat") || lower.contains("snap") -> FileIconDescriptor(Icons.Default.AutoAwesome, Color(0xFFEAB308), categoryLabel = "Snapchat")
            lower.contains("twitter") || lower == "x" -> FileIconDescriptor(Icons.Default.Tag, Color(0xFF1DA1F2), categoryLabel = "Twitter")
            lower.contains("youtube") || lower == "yt" -> FileIconDescriptor(Icons.Default.SmartDisplay, Color(0xFFFF0000), categoryLabel = "YouTube")
            lower.contains("reddit") -> FileIconDescriptor(Icons.Default.Forum, Color(0xFFFF4500), categoryLabel = "Reddit")
            lower.contains("discord") -> FileIconDescriptor(Icons.Default.SportsEsports, Color(0xFF5865F2), categoryLabel = "Discord")
            lower.contains("pinterest") -> FileIconDescriptor(Icons.Default.PushPin, Color(0xFFE60023), categoryLabel = "Pinterest")
            lower.contains("linkedin") -> FileIconDescriptor(Icons.Default.Work, Color(0xFF0A66C2), categoryLabel = "LinkedIn")
            lower.contains("wechat") || lower.contains("weixin") -> FileIconDescriptor(Icons.Default.Forum, Color(0xFF07C160), categoryLabel = "WeChat")
            lower.contains("shareme") || lower.contains("shareit") || lower.contains("midrop") -> FileIconDescriptor(Icons.Default.WifiTethering, Color(0xFF0284C7), categoryLabel = "ShareMe")

            // Standard Android Folders
            lower == "download" || lower == "downloads" -> FileIconDescriptor(Icons.Default.FileDownload, Color(0xFF2563EB), categoryLabel = "Downloads")
            lower == "dcim" || lower == "camera" -> FileIconDescriptor(Icons.Default.PhotoCamera, Color(0xFF7C3AED), categoryLabel = "Camera")
            lower.contains("pictures") || lower.contains("photos") || lower.contains("screenshots") -> FileIconDescriptor(Icons.Default.PhotoLibrary, Color(0xFF059669), categoryLabel = "Photos")
            lower.contains("music") || lower.contains("audio") || lower.contains("podcasts") -> FileIconDescriptor(Icons.Default.Headphones, Color(0xFFD97706), categoryLabel = "Music")
            lower.contains("movies") || lower.contains("videos") || lower == "video" -> FileIconDescriptor(Icons.Default.Movie, Color(0xFFE11D48), categoryLabel = "Movies")
            lower.contains("documents") || lower == "docs" -> FileIconDescriptor(Icons.Default.Description, Color(0xFFEA580C), categoryLabel = "Documents")
            lower.contains("bluetooth") -> FileIconDescriptor(Icons.Default.Bluetooth, Color(0xFF2563EB), categoryLabel = "Bluetooth")
            lower == "android" -> FileIconDescriptor(Icons.Default.Android, Color(0xFF10B981), categoryLabel = "Android System")
            lower.contains("backup") -> FileIconDescriptor(Icons.Default.Backup, Color(0xFF6366F1), categoryLabel = "Backup")
            lower.contains("record") || lower.contains("voice") -> FileIconDescriptor(Icons.Default.Mic, Color(0xFFE11D48), categoryLabel = "Voice Recordings")
            lower.contains("alldownloader") || lower.contains("torrent") -> FileIconDescriptor(Icons.Default.CloudDownload, Color(0xFF0284C7), categoryLabel = "Torrents")
            lower.contains("vault") || lower.contains("private") || lower.contains("secret") -> FileIconDescriptor(Icons.Default.Lock, Color(0xFFD97706), categoryLabel = "Private Vault")

            // Generic Folder
            else -> FileIconDescriptor(Icons.Default.Folder, FolderAmber, categoryLabel = "Folder")
        }
    }

    /**
     * Determine descriptor for files based on extension, name, and fallback category.
     */
    fun getFileDescriptor(
        fileName: String,
        extension: String,
        categoryFallback: FileCategory = FileCategory.UNKNOWN
    ): FileIconDescriptor {
        val ext = extension.lowercase(Locale.ROOT).trim()

        return when {
            // ==========================================
            // 1. Documents & Office Files
            // ==========================================
            ext == "pdf" -> FileIconDescriptor(
                icon = Icons.Default.PictureAsPdf,
                tintColor = PdfRed,
                categoryLabel = "PDF Document",
                badgeText = "PDF"
            )
            ext in listOf("doc", "docx", "dot", "dotx", "odt", "rtf", "wps", "pages") -> FileIconDescriptor(
                icon = Icons.Default.Description,
                tintColor = DocBlue,
                categoryLabel = "Word Document",
                badgeText = "DOC"
            )
            ext in listOf("xls", "xlsx", "csv", "tsv", "ods", "xlsm", "numbers") -> FileIconDescriptor(
                icon = Icons.Default.TableChart,
                tintColor = SheetGreen,
                categoryLabel = "Spreadsheet",
                badgeText = "XLS"
            )
            ext in listOf("ppt", "pptx", "pps", "odp", "key") -> FileIconDescriptor(
                icon = Icons.Default.Slideshow,
                tintColor = SlideOrange,
                categoryLabel = "Presentation",
                badgeText = "PPT"
            )
            ext in listOf("txt", "log", "tex") -> FileIconDescriptor(
                icon = Icons.AutoMirrored.Filled.Article,
                tintColor = TextSlate,
                categoryLabel = "Text Document",
                badgeText = "TXT"
            )
            ext in listOf("md", "markdown") -> FileIconDescriptor(
                icon = Icons.Default.Notes,
                tintColor = MarkdownTeal,
                categoryLabel = "Markdown",
                badgeText = "MD"
            )
            ext in listOf("epub", "mobi", "azw", "azw3", "fb2", "djvu") -> FileIconDescriptor(
                icon = Icons.Default.MenuBook,
                tintColor = CodeIndigo,
                categoryLabel = "E-Book",
                badgeText = "BOOK"
            )

            // ==========================================
            // 2. Audio Files
            // ==========================================
            ext in listOf("mp3", "wav", "flac", "aac", "m4a", "wma", "alac", "aiff", "mid", "midi") -> FileIconDescriptor(
                icon = Icons.Default.MusicNote,
                tintColor = AudioAmber,
                categoryLabel = "Music Track",
                badgeText = ext.uppercase(Locale.ROOT)
            )
            ext in listOf("opus", "ogg", "amr", "m4b", "3ga", "awb") -> FileIconDescriptor(
                icon = Icons.Default.Mic,
                tintColor = AudioRecordingRose,
                categoryLabel = "Voice Recording",
                badgeText = "VOICE"
            )
            ext in listOf("m3u", "m3u8", "pls", "wpl") -> FileIconDescriptor(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                tintColor = VideoPurple,
                categoryLabel = "Audio Playlist",
                badgeText = "LIST"
            )

            // ==========================================
            // 3. Image Files
            // ==========================================
            ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif") -> FileIconDescriptor(
                icon = Icons.Default.Image,
                tintColor = ImageSky,
                categoryLabel = "Photo Image",
                badgeText = if (ext == "png" || ext == "webp") ext.uppercase(Locale.ROOT) else "IMG"
            )
            ext == "gif" -> FileIconDescriptor(
                icon = Icons.Default.Gif,
                tintColor = ImageGifPurple,
                categoryLabel = "Animated GIF",
                badgeText = "GIF"
            )
            ext in listOf("svg", "vector") -> FileIconDescriptor(
                icon = Icons.Default.Brush,
                tintColor = SheetGreen,
                categoryLabel = "Vector Graphic",
                badgeText = "SVG"
            )
            ext in listOf("raw", "dng", "cr2", "nef", "arw") -> FileIconDescriptor(
                icon = Icons.Default.CameraAlt,
                tintColor = ImageRawOrange,
                categoryLabel = "Camera RAW",
                badgeText = "RAW"
            )
            ext in listOf("psd", "ai", "xd", "sketch", "fig") -> FileIconDescriptor(
                icon = Icons.Default.Palette,
                tintColor = Color(0xFF0284C7),
                categoryLabel = "Design File",
                badgeText = ext.uppercase(Locale.ROOT)
            )
            ext == "ico" -> FileIconDescriptor(
                icon = Icons.Default.CropOriginal,
                tintColor = ImageSky,
                categoryLabel = "Icon File",
                badgeText = "ICO"
            )

            // ==========================================
            // 4. Video Files
            // ==========================================
            ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "mpg", "mpeg") -> FileIconDescriptor(
                icon = Icons.Default.Movie,
                tintColor = VideoPurple,
                categoryLabel = "Video File",
                badgeText = if (ext in listOf("mp4", "mkv", "avi")) ext.uppercase(Locale.ROOT) else "VID"
            )

            // ==========================================
            // 5. Compressed Archives
            // ==========================================
            ext in listOf("zip", "jar") -> FileIconDescriptor(
                icon = Icons.Default.FolderZip,
                tintColor = ArchiveAmber,
                categoryLabel = "ZIP Archive",
                badgeText = "ZIP"
            )
            ext == "rar" -> FileIconDescriptor(
                icon = Icons.Default.FolderZip,
                tintColor = Color(0xFF7E22CE),
                categoryLabel = "RAR Archive",
                badgeText = "RAR"
            )
            ext == "7z" -> FileIconDescriptor(
                icon = Icons.Default.FolderZip,
                tintColor = ArchiveBlue,
                categoryLabel = "7Z Archive",
                badgeText = "7Z"
            )
            ext in listOf("tar", "gz", "tgz", "bz2", "xz") || fileName.lowercase(Locale.ROOT).endsWith(".tar.gz") -> FileIconDescriptor(
                icon = Icons.Default.Archive,
                tintColor = Color(0xFF57534E),
                categoryLabel = "Tarball Archive",
                badgeText = "TAR"
            )
            ext in listOf("iso", "img", "bin") -> FileIconDescriptor(
                icon = Icons.Default.Album,
                tintColor = TextSlate,
                categoryLabel = "Disc Image",
                badgeText = "ISO"
            )

            // ==========================================
            // 6. Application Packages & Installers
            // ==========================================
            ext == "apk" -> FileIconDescriptor(
                icon = Icons.Default.Android,
                tintColor = ApkGreen,
                categoryLabel = "Android App Package",
                badgeText = "APK"
            )
            ext in listOf("xapk", "apks", "aab") -> FileIconDescriptor(
                icon = Icons.Default.Layers,
                tintColor = XapkCyan,
                categoryLabel = "Split App Bundle",
                badgeText = ext.uppercase(Locale.ROOT)
            )

            // ==========================================
            // 7. Source Code & Scripts
            // ==========================================
            ext in listOf("kt", "kts") -> FileIconDescriptor(
                icon = Icons.Default.Code,
                tintColor = Color(0xFF7F52FF),
                categoryLabel = "Kotlin Source",
                badgeText = "KT"
            )
            ext == "java" -> FileIconDescriptor(
                icon = Icons.Default.Coffee,
                tintColor = Color(0xFFEA580C),
                categoryLabel = "Java Source",
                badgeText = "JAVA"
            )
            ext in listOf("html", "htm") -> FileIconDescriptor(
                icon = Icons.Default.Html,
                tintColor = Color(0xFFE44D26),
                categoryLabel = "HTML Webpage",
                badgeText = "HTML"
            )
            ext in listOf("css", "scss", "sass") -> FileIconDescriptor(
                icon = Icons.Default.Css,
                tintColor = Color(0xFF264DE4),
                categoryLabel = "Stylesheet",
                badgeText = "CSS"
            )
            ext in listOf("js", "ts", "jsx", "tsx") -> FileIconDescriptor(
                icon = Icons.Default.Javascript,
                tintColor = Color(0xFFF7DF1E),
                categoryLabel = "JavaScript/TypeScript",
                badgeText = if (ext.startsWith("t")) "TS" else "JS"
            )
            ext == "py" -> FileIconDescriptor(
                icon = Icons.Default.Terminal,
                tintColor = Color(0xFF38BDF8),
                categoryLabel = "Python Script",
                badgeText = "PY"
            )
            ext in listOf("c", "cpp", "h", "hpp", "cs", "go", "rs", "swift") -> FileIconDescriptor(
                icon = Icons.Default.Code,
                tintColor = Color(0xFF6366F1),
                categoryLabel = "${ext.uppercase(Locale.ROOT)} Source Code",
                badgeText = ext.uppercase(Locale.ROOT)
            )
            ext in listOf("json", "xml", "yaml", "yml", "properties", "env", "ini", "conf") -> FileIconDescriptor(
                icon = Icons.Default.DataObject,
                tintColor = ConfigTeal,
                categoryLabel = "Configuration/Data",
                badgeText = if (ext.length <= 4) ext.uppercase(Locale.ROOT) else null
            )
            ext in listOf("sh", "bash", "bat", "cmd") -> FileIconDescriptor(
                icon = Icons.Default.Terminal,
                tintColor = TextSlate,
                categoryLabel = "Shell Script",
                badgeText = "SH"
            )
            ext in listOf("sql", "db", "sqlite", "sqlite3") -> FileIconDescriptor(
                icon = Icons.Default.Storage,
                tintColor = SheetGreen,
                categoryLabel = "Database / SQL",
                badgeText = "SQL"
            )

            // ==========================================
            // 8. Certificates & Security Keys
            // ==========================================
            ext in listOf("cer", "crt", "pem", "key", "keystore", "jks", "p12") -> FileIconDescriptor(
                icon = Icons.Default.VpnKey,
                tintColor = AudioAmber,
                categoryLabel = "Security Certificate",
                badgeText = "KEY"
            )

            // ==========================================
            // 9. Fallback via FileCategory
            // ==========================================
            else -> when (categoryFallback) {
                FileCategory.FOLDER -> FileIconDescriptor(Icons.Default.Folder, FolderAmber, categoryLabel = "Folder")
                FileCategory.IMAGE -> FileIconDescriptor(Icons.Default.Image, ImageSky, categoryLabel = "Image")
                FileCategory.AUDIO -> FileIconDescriptor(Icons.Default.Audiotrack, AudioAmber, categoryLabel = "Audio")
                FileCategory.VIDEO -> FileIconDescriptor(Icons.Default.Movie, VideoPurple, categoryLabel = "Video")
                FileCategory.DOCUMENT -> FileIconDescriptor(Icons.Default.Description, DocBlue, categoryLabel = "Document")
                FileCategory.ARCHIVE -> FileIconDescriptor(Icons.Default.Archive, ArchiveAmber, categoryLabel = "Archive")
                FileCategory.APK -> FileIconDescriptor(Icons.Default.Android, ApkGreen, categoryLabel = "APK Package", badgeText = "APK")
                FileCategory.CODE -> FileIconDescriptor(Icons.Default.Code, CodeIndigo, categoryLabel = "Source Code")
                FileCategory.UNKNOWN -> FileIconDescriptor(Icons.Default.InsertDriveFile, UnknownGray, categoryLabel = "File")
            }
        }
    }

    /**
     * Get a representative descriptor for a FileCategory.
     */
    fun getCategoryDescriptor(category: FileCategory): FileIconDescriptor {
        return when (category) {
            FileCategory.FOLDER -> FileIconDescriptor(Icons.Default.Folder, FolderAmber, categoryLabel = "Folders")
            FileCategory.IMAGE -> FileIconDescriptor(Icons.Default.Image, ImageSky, categoryLabel = "Images")
            FileCategory.AUDIO -> FileIconDescriptor(Icons.Default.Audiotrack, AudioAmber, categoryLabel = "Audio")
            FileCategory.VIDEO -> FileIconDescriptor(Icons.Default.Movie, VideoPurple, categoryLabel = "Videos")
            FileCategory.DOCUMENT -> FileIconDescriptor(Icons.Default.Description, DocBlue, categoryLabel = "Documents")
            FileCategory.ARCHIVE -> FileIconDescriptor(Icons.Default.FolderZip, ArchiveAmber, categoryLabel = "Archives")
            FileCategory.APK -> FileIconDescriptor(Icons.Default.Android, ApkGreen, categoryLabel = "APKs", badgeText = "APK")
            FileCategory.CODE -> FileIconDescriptor(Icons.Default.Code, CodeIndigo, categoryLabel = "Code")
            FileCategory.UNKNOWN -> FileIconDescriptor(Icons.Default.InsertDriveFile, UnknownGray, categoryLabel = "Files")
        }
    }

    // =========================================================================
    // COMPOSE UI DISPLAY HELPERS
    // =========================================================================

    /**
     * Standard Material 3 squircle icon badge for a FileItem.
     * Automatically loads and displays real thumbnail previews for Photos, Videos, and APKs,
     * while showing distinct Material Design 3 icons and format chips for all other files.
     */
    @Composable
    fun FileIconBadge(
        item: FileItem,
        modifier: Modifier = Modifier,
        size: Dp = 44.dp,
        iconSize: Dp = 24.dp,
        shape: Shape = RoundedCornerShape(14.dp),
        showFormatBadge: Boolean = true
    ) {
        val descriptor = getDescriptor(item)
        val shouldLoadThumbnail = !item.isDirectory && (
            item.category == FileCategory.IMAGE ||
            item.category == FileCategory.VIDEO ||
            item.category == FileCategory.APK
        )

        val thumbnailBitmap by if (shouldLoadThumbnail) {
            ThumbnailLoader.rememberThumbnailState(item.file, item.category)
        } else {
            remember { mutableStateOf(null) }
        }

        FileIconBadge(
            descriptor = descriptor,
            thumbnail = thumbnailBitmap,
            isVideo = item.category == FileCategory.VIDEO,
            modifier = modifier,
            size = size,
            iconSize = iconSize,
            shape = shape,
            showFormatBadge = showFormatBadge && (item.category != FileCategory.IMAGE || thumbnailBitmap == null)
        )
    }

    /**
     * Standard Material 3 squircle icon badge for a java.io.File.
     */
    @Composable
    fun FileIconBadge(
        file: File,
        modifier: Modifier = Modifier,
        size: Dp = 44.dp,
        iconSize: Dp = 24.dp,
        shape: Shape = RoundedCornerShape(14.dp),
        showFormatBadge: Boolean = true
    ) {
        val descriptor = getDescriptor(file)
        val category = if (file.isDirectory) FileCategory.FOLDER else FileItem(file).category
        val shouldLoadThumbnail = !file.isDirectory && (
            category == FileCategory.IMAGE ||
            category == FileCategory.VIDEO ||
            category == FileCategory.APK
        )

        val reqPx = (size.value * 3f).toInt().coerceAtLeast(240)
        val thumbnailBitmap by if (shouldLoadThumbnail) {
            ThumbnailLoader.rememberThumbnailState(file, category, targetWidth = reqPx, targetHeight = reqPx)
        } else {
            remember { mutableStateOf(null) }
        }

        FileIconBadge(
            descriptor = descriptor,
            thumbnail = thumbnailBitmap,
            isVideo = category == FileCategory.VIDEO,
            modifier = modifier,
            size = size,
            iconSize = iconSize,
            shape = shape,
            showFormatBadge = showFormatBadge && (category != FileCategory.IMAGE || thumbnailBitmap == null)
        )
    }

    /**
     * Standard Material 3 squircle icon badge for a FileIconDescriptor.
     * Renders either the media thumbnail or the tinted Material icon squircle.
     */
    @Composable
    fun FileIconBadge(
        descriptor: FileIconDescriptor,
        modifier: Modifier = Modifier,
        thumbnail: android.graphics.Bitmap? = null,
        isVideo: Boolean = false,
        size: Dp = 44.dp,
        iconSize: Dp = 24.dp,
        shape: Shape = RoundedCornerShape(14.dp),
        showFormatBadge: Boolean = true
    ) {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                // Real Image / Video / APK Thumbnail!
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = descriptor.categoryLabel,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // If it's a video, overlay a translucent play indicator
                    if (isVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(iconSize.coerceAtMost(20.dp))
                            )
                        }
                    }
                }
            } else {
                // Background container with tinted color & Material Icon
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(descriptor.backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = descriptor.icon,
                        contentDescription = descriptor.categoryLabel,
                        tint = descriptor.tintColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            // Optional miniature format chip badge (e.g. "PDF", "XLS", "APK", "ZIP")
            if (showFormatBadge && descriptor.badgeText != null) {
                Surface(
                    color = descriptor.tintColor,
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                ) {
                    Text(
                        text = descriptor.badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
    }

    /**
     * Compact icon element (without full background box) for tight row spaces or buttons.
     */
    @Composable
    fun CompactFileIcon(
        item: FileItem,
        modifier: Modifier = Modifier,
        size: Dp = 20.dp
    ) {
        val descriptor = getDescriptor(item)
        Icon(
            imageVector = descriptor.icon,
            contentDescription = descriptor.categoryLabel,
            tint = descriptor.tintColor,
            modifier = modifier.size(size)
        )
    }

    /**
     * Compact icon element for a java.io.File.
     */
    @Composable
    fun CompactFileIcon(
        file: File,
        modifier: Modifier = Modifier,
        size: Dp = 20.dp
    ) {
        val descriptor = getDescriptor(file)
        Icon(
            imageVector = descriptor.icon,
            contentDescription = descriptor.categoryLabel,
            tint = descriptor.tintColor,
            modifier = modifier.size(size)
        )
    }
}
