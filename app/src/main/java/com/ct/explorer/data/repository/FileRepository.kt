package com.ct.explorer.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.SortType
import com.ct.explorer.data.model.StorageSpace
import com.ct.explorer.data.model.SocialFolderEntry
import com.ct.explorer.data.model.SocialAppGroup
import com.ct.explorer.data.model.SocialFolderType
import com.ct.explorer.data.model.StorageVolumeItem
import com.ct.explorer.data.model.VolumeType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class CleanScanResult(
    val junkFiles: List<FileItem>,
    val largeFiles: List<FileItem>,
    val apkFiles: List<FileItem>,
    val emptyFolders: List<FileItem>
) {
    val totalJunkBytes: Long get() = junkFiles.sumOf { it.size }
    val totalLargeBytes: Long get() = largeFiles.sumOf { it.size }
    val totalApkBytes: Long get() = apkFiles.sumOf { it.size }
    val formattedJunkSize: String get() = FileItem.formatBytes(totalJunkBytes)
}

class FileRepository(private val context: Context) {

    companion object {
        @Volatile
        var cachedRootItems: List<FileItem>? = null
        @Volatile
        var cachedRecentItems: List<FileItem>? = null

        fun invalidateRecentCache() {
            cachedRecentItems = null
        }

        fun invalidateRootCache() {
            cachedRootItems = null
        }

        fun invalidateAllFileCaches() {
            cachedRecentItems = null
            cachedRootItems = null
        }
    }

    val rootStorageDirectory: File by lazy {
        try {
            val ext = Environment.getExternalStorageDirectory()
            if (ext.exists() && ext.canRead()) {
                ext
            } else {
                val sampleDir = File(context.filesDir, "CentExplorer")
                if (sampleDir.exists()) sampleDir else context.filesDir
            }
        } catch (e: Exception) {
            context.filesDir
        }
    }

    fun getFastInitialRootItems(): List<FileItem> {
        cachedRootItems?.let { if (it.isNotEmpty()) return it }
        val root = rootStorageDirectory
        val defaultFolderNames = listOf("Download", "DCIM", "Documents", "Pictures", "Music", "Movies", "Android")
        val now = System.currentTimeMillis()
        return defaultFolderNames.map { fName ->
            val virtualFolder = File(root, fName)
            FileItem(
                file = virtualFolder,
                name = fName,
                path = virtualFolder.absolutePath,
                isDirectory = true,
                size = 0L,
                lastModified = now,
                isHidden = false,
                extension = "",
                itemCount = 0
            )
        }
    }

    val downloadsDirectory: File by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).let {
            if (it.exists()) it else File(rootStorageDirectory, "Download")
        }
    }

    val documentsDirectory: File by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).let {
            if (it.exists()) it else File(rootStorageDirectory, "Documents")
        }
    }

    val picturesDirectory: File by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).let {
            if (it.exists()) it else File(rootStorageDirectory, "Pictures")
        }
    }

    val dcimDirectory: File by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).let {
            if (it.exists()) it else File(rootStorageDirectory, "DCIM")
        }
    }

    suspend fun getStorageVolumes(): List<StorageVolumeItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StorageVolumeItem>()
        // 1. Primary Internal Storage
        val primaryDir = rootStorageDirectory
        val primaryStat = try { StatFs(primaryDir.path) } catch (e: Exception) { null }
        val primaryTotal = primaryStat?.let { it.blockCountLong * it.blockSizeLong } ?: (64L * 1024 * 1024 * 1024)
        val primaryFree = primaryStat?.let { it.availableBlocksLong * it.blockSizeLong } ?: (20L * 1024 * 1024 * 1024)

        list.add(
            StorageVolumeItem(
                id = "internal_storage",
                name = "Internal Storage",
                file = primaryDir,
                type = VolumeType.INTERNAL,
                totalBytes = primaryTotal,
                freeBytes = primaryFree,
                isPrimary = true
            )
        )

        // 2. Query StorageManager on Android 24+ (API 24+) and Android 11+ (API 30+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                storageManager?.storageVolumes?.forEach { volume ->
                    val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else null
                    if (dir != null && dir.exists() && dir.canRead() && dir.absolutePath != primaryDir.absolutePath) {
                        val stat = try { StatFs(dir.path) } catch (e: Exception) { null }
                        val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
                        val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
                        val isRemovable = volume.isRemovable
                        val isUsb = dir.name.lowercase().contains("usb") || dir.path.lowercase().contains("usb")
                        val volType = if (isUsb) VolumeType.USB_OTG else if (isRemovable) VolumeType.SD_CARD else VolumeType.INTERNAL
                        val desc = volume.getDescription(context)
                        val volName = desc.ifBlank { if (isUsb) "USB OTG Drive" else "SD Card (${dir.name})" }

                        if (list.none { it.file.absolutePath == dir.absolutePath }) {
                            list.add(
                                StorageVolumeItem(
                                    id = dir.absolutePath,
                                    name = volName,
                                    file = dir,
                                    type = volType,
                                    totalBytes = total,
                                    freeBytes = free,
                                    isPrimary = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Query removable / secondary directories via context.getExternalFilesDirs(null)
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    val root = getRootOfVolume(dir)
                    if (root != null && root.absolutePath != primaryDir.absolutePath && root.canRead()) {
                        val stat = try { StatFs(root.path) } catch (e: Exception) { null }
                        val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
                        val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
                        val isUsb = root.name.lowercase().contains("usb") || root.path.lowercase().contains("usb")
                        val volType = if (isUsb) VolumeType.USB_OTG else VolumeType.SD_CARD
                        val volName = if (isUsb) "USB OTG Drive" else "SD Card (${root.name})"

                        if (list.none { it.file.absolutePath == root.absolutePath }) {
                            list.add(
                                StorageVolumeItem(
                                    id = root.absolutePath,
                                    name = volName,
                                    file = root,
                                    type = volType,
                                    totalBytes = total,
                                    freeBytes = free,
                                    isPrimary = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        list
    }

    private fun getRootOfVolume(file: File): File? {
        var current: File? = file
        while (current != null && current.parentFile != null) {
            if (current.parentFile?.name == "Android") {
                return current.parentFile?.parentFile
            }
            current = current.parentFile
        }
        return null
    }

    val musicDirectory: File by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).let {
            if (it.exists()) it else File(rootStorageDirectory, "Music")
        }
    }

    val moviesDirectory: File by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).let {
            if (it.exists()) it else File(rootStorageDirectory, "Movies")
        }
    }

    private fun ensureCentExplorerSampleData() {
        try {
            val base = File(context.filesDir, "CentExplorer")
            if (!base.exists()) {
                base.mkdirs()
                File(base, "Welcome_Cent_Explorer.txt").writeText(
                    """
                    Welcome to Cent File Manager!
                    
                    Modern, Fast & Clean Android File Manager:
                    • Recent tab: Quick access to recently created, captured, or downloaded items.
                    • Storage tab: Clear overview of used space with 8 fast category shortcuts.
                    • Deep Clean: Scan for app cache, residual junk, obsolete APKs, and large files.
                    • Built-in Tools: Text Editor, Photo Previewer, APK Inspector, and ZIP manager.
                    • 100% Free: No ads, no subscriptions, fast and lightweight.
                    """.trimIndent()
                )
                File(base, "Cent_Tips.md").writeText(
                    """
                    # Cent File Manager Tips
                    - Switch between Recent and Storage using the top tabs.
                    - Tap the Cleaner button to free up space in seconds.
                    - Use FTP server mode to transfer files directly to your PC without cables!
                    """.trimIndent()
                )
                val docs = File(base, "Documents")
                docs.mkdirs()
                File(docs, "Project_Roadmap.txt").writeText("1. Modern Compose UI\n2. Modern Smooth Squircles\n3. Zero Ads\n4. Lightning fast\n")

                val musicDir = File(base, "Music").apply { mkdirs() }
                val sampleAudio = File(musicDir, "Cent_Melody_Sample.wav")
                if (!sampleAudio.exists()) {
                    createSampleWavFile(sampleAudio)
                }

                // Sample Social Media Folders showcasing custom branded social icons
                val whatsAppDir = File(base, "WhatsApp").apply { mkdirs() }
                File(whatsAppDir, "WhatsApp Images").apply { mkdirs() }
                File(whatsAppDir, "WhatsApp Video").apply { mkdirs() }
                File(base, "Telegram").apply { mkdirs() }
                File(base, "Instagram").apply { mkdirs() }
                File(base, "Download").apply { mkdirs() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createSampleWavFile(file: File) {
        try {
            val sampleRate = 8000
            val numSamples = 4000
            val buffer = ByteArray(numSamples)

            FileOutputStream(file).use { out ->
                val totalDataLen = buffer.size + 36
                val byteRate = sampleRate

                out.write("RIFF".toByteArray(Charsets.US_ASCII))
                out.write(intToByteArray(totalDataLen))
                out.write("WAVE".toByteArray(Charsets.US_ASCII))
                out.write("fmt ".toByteArray(Charsets.US_ASCII))
                out.write(intToByteArray(16))
                out.write(shortToByteArray(1))
                out.write(shortToByteArray(1))
                out.write(intToByteArray(sampleRate))
                out.write(intToByteArray(byteRate))
                out.write(shortToByteArray(1))
                out.write(shortToByteArray(8))
                out.write("data".toByteArray(Charsets.US_ASCII))
                out.write(intToByteArray(buffer.size))
                out.write(buffer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }

    private val folderSizeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>() // path -> (lastModified, sizeBytes)

    fun invalidateFolderSize(dir: File?) {
        if (dir == null) return
        folderSizeCache.remove(dir.absolutePath)
        dir.parentFile?.let { folderSizeCache.remove(it.absolutePath) }
    }

    fun calculateDirectorySize(dir: File, maxDepth: Int = 3): Long {
        if (!dir.isDirectory || !dir.canRead()) return 0L
        val cached = folderSizeCache[dir.absolutePath]
        if (cached != null && cached.first == dir.lastModified()) {
            return cached.second
        }
        val size = computeDirSize(dir, maxDepth, currentDepth = 0)
        folderSizeCache[dir.absolutePath] = Pair(dir.lastModified(), size)
        return size
    }

    private fun computeDirSize(dir: File, maxDepth: Int, currentDepth: Int): Long {
        if (currentDepth > maxDepth) return 0L
        if (dir.name.equals("Android", ignoreCase = true) && currentDepth > 0) return 0L
        var total = 0L
        try {
            val list = dir.listFiles() ?: return 0L
            for (f in list) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    total += computeDirSize(f, maxDepth, currentDepth + 1)
                } else {
                    total += f.length()
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return total
    }

    suspend fun listFiles(
        directory: File,
        showHidden: Boolean = false,
        sortType: SortType = SortType.NAME_ASC,
        foldersOnTop: Boolean = true,
        searchQuery: String = ""
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val files = directory.listFiles() ?: return@withContext emptyList()
        val isSearching = searchQuery.isNotBlank()
        val query = if (isSearching) searchQuery.trim().lowercase(java.util.Locale.ROOT) else ""

        val items = ArrayList<FileItem>(files.size)
        for (file in files) {
            val name = file.name
            val isHidden = name.startsWith(".")
            if (!showHidden && isHidden) continue

            if (isSearching && !name.lowercase(java.util.Locale.ROOT).contains(query)) continue

            val isDir = file.isDirectory
            if (isDir) {
                items.add(
                    FileItem(
                        file = file,
                        name = name,
                        path = file.absolutePath,
                        isDirectory = true,
                        size = 0L,
                        lastModified = file.lastModified(),
                        isHidden = isHidden,
                        extension = "",
                        itemCount = 0
                    )
                )
            } else {
                val dotIndex = name.lastIndexOf('.')
                val ext = if (dotIndex > 0) name.substring(dotIndex + 1).lowercase(java.util.Locale.ROOT) else ""
                items.add(
                    FileItem(
                        file = file,
                        name = name,
                        path = file.absolutePath,
                        isDirectory = false,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        isHidden = isHidden,
                        extension = ext,
                        itemCount = 0
                    )
                )
            }
        }

        val sorted = com.ct.explorer.data.model.sortFileList(items, sortType, foldersOnTop)
        if (directory.absolutePath == rootStorageDirectory.absolutePath && !isSearching && !showHidden) {
            cachedRootItems = sorted
        }
        sorted
    }

    suspend fun getRecentFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        cachedRecentItems?.let { if (it.isNotEmpty()) return@withContext it }
        val list = mutableListOf<FileItem>()
        val seenPaths = HashSet<String>()

        // 1. Instant MediaStore query (20-40ms)
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.SIZE
            )
            val uri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 60"

            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                if (dataCol != -1) {
                    while (cursor.moveToNext() && list.size < 60) {
                        val path = cursor.getString(dataCol)
                        if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                            val f = File(path)
                            val name = f.name
                            if (!name.startsWith(".")) {
                                val dotIdx = name.lastIndexOf('.')
                                val ext = if (dotIdx > 0) name.substring(dotIdx + 1).lowercase(java.util.Locale.ROOT) else ""
                                val fileSize = if (sizeCol != -1) cursor.getLong(sizeCol) else f.length()
                                val fileDate = if (dateCol != -1) {
                                    val sec = cursor.getLong(dateCol)
                                    if (sec > 100000000000L) sec else sec * 1000L
                                } else f.lastModified()
                                list.add(
                                    FileItem(
                                        file = f,
                                        name = name,
                                        path = f.absolutePath,
                                        isDirectory = false,
                                        size = fileSize,
                                        lastModified = fileDate,
                                        isHidden = false,
                                        extension = ext,
                                        itemCount = 0
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // MediaStore fallback
        }

        // 2. Fast shallow scan of primary user directories (Never scan Android/ or deep roots!)
        if (list.size < 30) {
            val keyDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(context.filesDir, "CentExplorer")
            ).filter { it.exists() && it.canRead() }

            for (dir in keyDirs) {
                dir.listFiles()?.forEach { f ->
                    val name = f.name
                    if (!f.isDirectory && !name.startsWith(".") && seenPaths.add(f.absolutePath)) {
                        val dotIdx = name.lastIndexOf('.')
                        val ext = if (dotIdx > 0) name.substring(dotIdx + 1).lowercase(java.util.Locale.ROOT) else ""
                        list.add(
                            FileItem(
                                file = f,
                                name = name,
                                path = f.absolutePath,
                                isDirectory = false,
                                size = f.length(),
                                lastModified = f.lastModified(),
                                isHidden = false,
                                extension = ext,
                                itemCount = 0
                            )
                        )
                    }
                }
            }
        }

        val sorted = list.sortedByDescending { it.lastModified }.take(60)
        cachedRecentItems = sorted
        sorted
    }

    private val categoryCache = java.util.concurrent.ConcurrentHashMap<FileCategory, List<FileItem>>()

    fun getCachedCategory(category: FileCategory): List<FileItem>? = categoryCache[category]

    fun invalidateCategoryCache(category: FileCategory? = null) {
        if (category == null) {
            categoryCache.clear()
        } else {
            categoryCache.remove(category)
        }
    }

    suspend fun getCategoryFiles(category: FileCategory, forceRefresh: Boolean = false): List<FileItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            categoryCache[category]?.let {
                if (it.isNotEmpty()) return@withContext it
            }
        }
        val list = mutableListOf<FileItem>()
        val seenPaths = HashSet<String>()

        // 1. Try fast MediaStore query
        try {
            val (uri, selection, sortOrder) = when (category) {
                FileCategory.IMAGE -> Triple(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )
                FileCategory.VIDEO -> Triple(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    null,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )
                FileCategory.AUDIO -> Triple(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    null,
                    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                )
                FileCategory.APK -> Triple(
                    MediaStore.Files.getContentUri("external"),
                    "${MediaStore.MediaColumns.DATA} LIKE '%.apk' OR ${MediaStore.MediaColumns.DATA} LIKE '%.xapk' OR ${MediaStore.MediaColumns.DATA} LIKE '%.apks' OR ${MediaStore.MediaColumns.MIME_TYPE} = 'application/vnd.android.package-archive'",
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )
                else -> Triple(null, null, null)
            }

            if (uri != null) {
                val projection = arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (dataCol != -1) {
                        while (cursor.moveToNext() && list.size < 2000) {
                            val path = cursor.getString(dataCol)
                            if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                                val f = File(path)
                                val name = f.name
                                if (!name.startsWith(".")) {
                                    val dotIdx = name.lastIndexOf('.')
                                    val ext = if (dotIdx > 0) name.substring(dotIdx + 1).lowercase(java.util.Locale.ROOT) else ""
                                    val fileSize = if (sizeCol != -1) cursor.getLong(sizeCol) else f.length()
                                    val fileDate = if (dateCol != -1) {
                                        val sec = cursor.getLong(dateCol)
                                        if (sec > 100000000000L) sec else sec * 1000L
                                    } else f.lastModified()
                                    list.add(
                                        FileItem(
                                            file = f,
                                            name = name,
                                            path = f.absolutePath,
                                            isDirectory = false,
                                            size = fileSize,
                                            lastModified = fileDate,
                                            isHidden = false,
                                            extension = ext,
                                            itemCount = 0
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // MediaStore fallback
        }

        // 2. Comprehensive direct folder search
        // For images (and other media), ALWAYS merge direct filesystem folders to guarantee camera photos (DCIM/Camera),
        // downloads, WhatsApp, etc. are loaded even if MediaStore only returned screenshots or incomplete index.
        val baseSearchFolders = when (category) {
            FileCategory.IMAGE -> {
                val rootStorage = Environment.getExternalStorageDirectory()
                val rootFolders = rootStorage.listFiles()?.filter {
                    it.isDirectory && !it.name.startsWith(".")
                } ?: emptyList()

                listOfNotNull(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "100ANDRO"),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    File(rootStorage, "DCIM"),
                    File(rootStorage, "DCIM/Camera"),
                    File(rootStorage, "Pictures"),
                    File(rootStorage, "Download"),
                    File(rootStorage, "Downloads"),
                    File(rootStorage, "Bluetooth"),
                    File(rootStorage, "WhatsApp/Media/WhatsApp Images"),
                    File(rootStorage, "WhatsApp/Media/WhatsApp Images/Sent"),
                    File(rootStorage, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"),
                    File(rootStorage, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent"),
                    File(rootStorage, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images"),
                    File(rootStorage, "Telegram/Telegram Images"),
                    File(rootStorage, "Android/media/org.telegram.messenger/Telegram/Telegram Images")
                ) + rootFolders
            }
            FileCategory.VIDEO -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Video"),
                File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
                File(context.filesDir, "CentExplorer/Videos"),
                File(context.filesDir, "CentExplorer")
            )
            FileCategory.AUDIO -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "Music"),
                File(Environment.getExternalStorageDirectory(), "Audio"),
                File(context.filesDir, "CentExplorer/Music"),
                File(context.filesDir, "CentExplorer")
            )
            FileCategory.DOCUMENT -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(context.filesDir, "CentExplorer")
            )
            FileCategory.APK -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Downloads"),
                File(Environment.getExternalStorageDirectory(), "Bluetooth"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Documents"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents"),
                File(Environment.getExternalStorageDirectory(), "ShareMe"),
                File(Environment.getExternalStorageDirectory(), "Apks"),
                File(Environment.getExternalStorageDirectory(), "Apps"),
                File(Environment.getExternalStorageDirectory(), "ADM"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CentExplorer/APKs"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CentExplorer/Backup"),
                File(context.filesDir, "CentExplorer/APKs"),
                File(context.filesDir, "CentExplorer/Backup"),
                Environment.getExternalStorageDirectory()
            )
            else -> listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(context.filesDir, "CentExplorer")
            )
        }

        val searchFolders = baseSearchFolders.filter { it.exists() && it.canRead() }.distinctBy { it.absolutePath }

        val scanDepth = if (list.isNotEmpty()) 1 else 2
        for (dir in searchFolders) {
            scanCategoryFast(dir, category, list, seenPaths, maxDepth = scanDepth, maxResults = 1500)
            if (list.size >= 1500) break
        }

        val sorted = list.sortedByDescending { it.lastModified }
        categoryCache[category] = sorted
        sorted
    }

    private fun scanCategoryFast(
        dir: File,
        category: FileCategory,
        results: MutableList<FileItem>,
        seenPaths: MutableSet<String>,
        maxDepth: Int = 4,
        currentDepth: Int = 0,
        maxResults: Int = 2500
    ) {
        if (currentDepth > maxDepth || results.size >= maxResults) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            val name = f.name
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                // If it's Android directory, only enter media/ subfolder
                if (name.equals("Android", ignoreCase = true)) {
                    val mediaDir = File(f, "media")
                    if (mediaDir.exists() && mediaDir.canRead()) {
                        scanCategoryFast(mediaDir, category, results, seenPaths, maxDepth, currentDepth + 1, maxResults)
                    }
                    continue
                }
                // Skip cache, obb, data
                if (name.equals("cache", ignoreCase = true) || name.equals("obb", ignoreCase = true) || name.equals("data", ignoreCase = true)) {
                    continue
                }
                scanCategoryFast(f, category, results, seenPaths, maxDepth, currentDepth + 1, maxResults)
            } else if (seenPaths.add(f.absolutePath)) {
                val item = FileItem(f)
                if (item.category == category) {
                    results.add(item)
                    if (results.size >= maxResults) return
                }
            }
        }
    }

    suspend fun scanForClean(): CleanScanResult = withContext(Dispatchers.IO) {
        val targetRoots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        ).filter { it.exists() && it.canRead() }.distinct()

        val junk = mutableListOf<FileItem>()
        val large = mutableListOf<FileItem>()
        val apks = mutableListOf<FileItem>()
        val emptyFolders = mutableListOf<FileItem>()

        for (root in targetRoots) {
            scanCleanRecursively(root, junk, large, apks, emptyFolders, depth = 0, maxDepth = 3)
        }

        CleanScanResult(
            junkFiles = junk,
            largeFiles = large.sortedByDescending { it.size },
            apkFiles = apks.sortedByDescending { it.size },
            emptyFolders = emptyFolders
        )
    }

    private fun scanCleanRecursively(
        dir: File,
        junk: MutableList<FileItem>,
        large: MutableList<FileItem>,
        apks: MutableList<FileItem>,
        emptyFolders: MutableList<FileItem>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return
        if (dir.name.startsWith(".") || dir.name.equals("Android", ignoreCase = true)) return
        val files = dir.listFiles() ?: return

        val standardFolderNames = setOf(
            "Download", "Downloads", "DCIM", "Pictures", "Documents",
            "Movies", "Music", "Android", "Ringtones", "Alarms",
            "Notifications", "Podcasts", "Audiobooks"
        )
        val isProtectedFolder = dir == rootStorageDirectory ||
                (dir.parentFile?.canonicalPath == rootStorageDirectory.canonicalPath && standardFolderNames.contains(dir.name)) ||
                dir.parentFile == null

        if (files.isEmpty() && !isProtectedFolder) {
            emptyFolders.add(FileItem(dir))
            return
        }
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanCleanRecursively(f, junk, large, apks, emptyFolders, depth + 1, maxDepth)
            } else {
                val item = FileItem(f)
                val ext = item.extension
                val isJunkExt = ext in listOf("tmp", "temp", "log", "bak", "thumb", "cache")
                val isExplicitCacheFile = f.name.equals("cache", ignoreCase = true) || f.name.equals(".cache", ignoreCase = true)
                if (isJunkExt || isExplicitCacheFile) {
                    junk.add(item)
                }
                if (item.size > 15L * 1024 * 1024) { // > 15MB
                    large.add(item)
                }
                if (ext == "apk") {
                    apks.add(item)
                }
            }
        }
    }

    suspend fun getStorageSpace(): StorageSpace = withContext(Dispatchers.IO) {
        try {
            val path = rootStorageDirectory.path
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            StorageSpace(
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                usedBytes = usedBytes
            )
        } catch (e: Exception) {
            val total = 64L * 1024 * 1024 * 1024
            val used = 26L * 1024 * 1024 * 1024
            StorageSpace(totalBytes = total, freeBytes = total - used, usedBytes = used)
        }
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = name.trim().replace("/", "")
            if (sanitized.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            val target = File(parent, sanitized)
            if (target.exists()) return@withContext Result.failure(IllegalStateException("A folder with this name already exists"))
            if (target.mkdirs()) {
                invalidateFolderSize(parent)
                Result.success(target)
            } else Result.failure(Exception("Failed to create folder"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTextFile(parent: File, name: String, initialContent: String = ""): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = name.trim().replace("/", "")
            if (sanitized.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            val target = File(parent, sanitized)
            if (target.exists()) return@withContext Result.failure(IllegalStateException("A file with this name already exists"))
            target.writeText(initialContent)
            invalidateFolderSize(parent)
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(item: FileItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!item.file.canWrite()) {
                try { item.file.setWritable(true) } catch (_: Exception) {}
            }
            val success = if (item.isDirectory) item.file.deleteRecursively() else item.file.delete()
            invalidateAllFileCaches()
            invalidateCategoryCache()
            invalidateFolderSize(item.file.parentFile)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(item: FileItem, newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = newName.trim().replace("/", "").replace("\\", "").replace("\u0000", "")
            if (sanitized.isEmpty() || sanitized == "." || sanitized == "..") {
                return@withContext Result.failure(IllegalArgumentException("Invalid target file name"))
            }
            val parent = item.file.parentFile ?: return@withContext Result.failure(IllegalStateException("Cannot rename root item"))
            val newFile = File(parent, sanitized)
            if (newFile.exists()) return@withContext Result.failure(IllegalStateException("Target already exists"))
            val success = item.file.renameTo(newFile)
            invalidateAllFileCaches()
            invalidateCategoryCache()
            invalidateFolderSize(parent)
            if (success) Result.success(newFile) else Result.failure(Exception("Rename failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateNonConflictingFile(parentDir: File, originalName: String): File {
        var candidate = File(parentDir, originalName)
        if (!candidate.exists()) return candidate
        val nameWithoutExt = originalName.substringBeforeLast(".")
        val ext = if (originalName.contains(".")) ".${originalName.substringAfterLast(".")}" else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parentDir, "$nameWithoutExt ($counter)$ext")
            counter++
        }
        return candidate
    }

    suspend fun deleteEmptyFolder(item: FileItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!item.isDirectory) return@withContext Result.failure(IllegalArgumentException("Item is not a folder"))
            val files = item.file.listFiles()
            if (files != null && files.isNotEmpty()) {
                return@withContext Result.failure(IllegalStateException("Folder is not empty"))
            }
            val success = item.file.delete()
            invalidateAllFileCaches()
            invalidateCategoryCache()
            invalidateFolderSize(item.file.parentFile)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copy(sources: List<FileItem>, destinationDir: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!destinationDir.exists()) destinationDir.mkdirs()
            var count = 0
            val destCanonical = destinationDir.canonicalFile
            for (source in sources) {
                val srcCanonical = source.file.canonicalFile
                if (source.isDirectory) {
                    if (destCanonical.path == srcCanonical.path || destCanonical.path.startsWith(srcCanonical.path + File.separator)) {
                        return@withContext Result.failure(IllegalArgumentException("Cannot copy folder '${source.name}' into itself or its child directory"))
                    }
                }
                val dest = if (srcCanonical.parentFile?.canonicalPath == destCanonical.path || File(destinationDir, source.name).exists()) {
                    generateNonConflictingFile(destinationDir, source.name)
                } else {
                    File(destinationDir, source.name)
                }
                if (source.isDirectory) {
                    val copied = source.file.copyRecursively(dest, overwrite = true)
                    if (!copied) throw java.io.IOException("Failed to copy directory: ${source.name}")
                } else {
                    source.file.copyTo(dest, overwrite = true)
                }
                count++
            }
            invalidateAllFileCaches()
            invalidateCategoryCache()
            invalidateFolderSize(destinationDir)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun move(sources: List<FileItem>, destinationDir: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!destinationDir.exists()) destinationDir.mkdirs()
            var count = 0
            val destCanonical = destinationDir.canonicalFile
            for (source in sources) {
                val srcCanonical = source.file.canonicalFile
                if (source.isDirectory) {
                    if (destCanonical.path == srcCanonical.path || destCanonical.path.startsWith(srcCanonical.path + File.separator)) {
                        return@withContext Result.failure(IllegalArgumentException("Cannot move folder '${source.name}' into itself or its child directory"))
                    }
                }
                val dest = if (File(destinationDir, source.name).exists() && srcCanonical.parentFile?.canonicalPath != destCanonical.path) {
                    generateNonConflictingFile(destinationDir, source.name)
                } else {
                    File(destinationDir, source.name)
                }
                val destCanonicalFile = dest.canonicalFile
                if (srcCanonical.path == destCanonicalFile.path) {
                    // Already in destination directory; nothing to move
                    count++
                    continue
                }
                val moved = source.file.renameTo(dest)
                if (!moved) {
                    if (source.isDirectory) {
                        val copied = source.file.copyRecursively(dest, overwrite = true)
                        if (copied) {
                            source.file.deleteRecursively()
                        } else {
                            throw java.io.IOException("Failed to copy folder '${source.name}' across storage volumes")
                        }
                    } else {
                        val copied = try {
                            source.file.copyTo(dest, overwrite = true)
                            true
                        } catch (e: Exception) {
                            false
                        }
                        if (copied) {
                            source.file.delete()
                        } else {
                            throw java.io.IOException("Failed to copy file '${source.name}' across storage volumes")
                        }
                    }
                }
                invalidateFolderSize(source.file.parentFile)
                count++
            }
            invalidateAllFileCaches()
            invalidateCategoryCache()
            invalidateFolderSize(destinationDir)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readText(file: File, maxChars: Int = 300_000): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(java.io.FileNotFoundException("File not found or unreadable"))
            }
            val charArray = CharArray(maxChars)
            val readCount = file.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.read(charArray, 0, maxChars)
            }
            val text = if (readCount > 0) String(charArray, 0, readCount) else ""
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeText(file: File, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            file.writeText(content)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun zip(items: List<FileItem>, zipOutputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            ZipOutputStream(FileOutputStream(zipOutputFile)).use { zos ->
                for (item in items) {
                    addToZip("", item.file, zos)
                }
            }
            Result.success(zipOutputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addToZip(basePath: String, file: File, zos: ZipOutputStream) {
        val entryName = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryName/"))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addToZip(entryName, child, zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    suspend fun unzip(zipFile: File, outputDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destCanonical = outputDir.canonicalFile
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val destFile = File(outputDir, entry.name)
                    val outCanonical = destFile.canonicalFile
                    if (!outCanonical.path.startsWith(destCanonical.path + File.separator) && outCanonical != destCanonical) {
                        throw SecurityException("Zip Slip detected: Entry attempts to escape output directory (${entry.name})")
                    }
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            invalidateAllFileCaches()
            invalidateCategoryCache()
            invalidateFolderSize(outputDir)
            Result.success(outputDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSocialAppGroups(context: Context): List<SocialAppGroup> = withContext(Dispatchers.IO) {
        val groups = mutableListOf<SocialAppGroup>()
        val packageManager = context.packageManager

        fun isInstalled(pkg: String): Boolean {
            return try {
                packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }

        val root = rootStorageDirectory
        val androidMedia = File(root, "Android/media")
        val pictures = File(root, "Pictures")
        val movies = File(root, "Movies")
        val download = downloadsDirectory
        val sampleBase = File(context.getExternalFilesDir(null) ?: context.filesDir, "CentExplorer")

        fun createEntry(dir: File, hint: String): SocialFolderEntry? {
            if (!dir.exists() || !dir.isDirectory) return null
            val files = dir.listFiles() ?: return null
            val count = files.size
            var size = 0L
            for (f in files) {
                size += if (f.isDirectory) calculateDirectorySize(f, maxDepth = 2) else f.length()
            }
            return SocialFolderEntry(
                name = dir.name,
                folder = dir,
                fileCount = count,
                sizeBytes = size,
                lastModified = dir.lastModified(),
                categoryHint = hint
            )
        }

        // 1. WhatsApp
        val waFolders = mutableListOf<SocialFolderEntry>()
        val waCandidates = listOf(
            Pair(File(androidMedia, "com.whatsapp/WhatsApp/Media/WhatsApp Images"), "Images"),
            Pair(File(androidMedia, "com.whatsapp/WhatsApp/Media/WhatsApp Video"), "Videos"),
            Pair(File(androidMedia, "com.whatsapp/WhatsApp/Media/WhatsApp Documents"), "Documents"),
            Pair(File(androidMedia, "com.whatsapp/WhatsApp/Media/WhatsApp Audio"), "Audio"),
            Pair(File(androidMedia, "com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"), "Voice Notes"),
            Pair(File(androidMedia, "com.whatsapp/WhatsApp/Media/WhatsApp Animated Gifs"), "GIFs"),
            Pair(File(androidMedia, "com.whatsapp/WhatsApp/Media/WhatsApp Stickers"), "Stickers"),
            Pair(File(root, "WhatsApp/Media/WhatsApp Images"), "Images"),
            Pair(File(root, "WhatsApp/Media/WhatsApp Video"), "Videos"),
            Pair(File(root, "WhatsApp/Media/WhatsApp Documents"), "Documents"),
            Pair(File(root, "WhatsApp/Media/WhatsApp Audio"), "Audio"),
            Pair(File(sampleBase, "WhatsApp/WhatsApp Images"), "Images"),
            Pair(File(sampleBase, "WhatsApp/WhatsApp Video"), "Videos"),
            Pair(File(sampleBase, "WhatsApp"), "Media")
        )
        for ((cand, hint) in waCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (waFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    waFolders.add(entry)
                }
            }
        }
        val isWaInstalled = isInstalled("com.whatsapp") || isInstalled("com.whatsapp.w4b")
        if (isWaInstalled || waFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.WHATSAPP,
                    packageName = "com.whatsapp",
                    isAppInstalled = isWaInstalled,
                    folders = waFolders
                )
            )
        }

        // 2. Telegram
        val tgFolders = mutableListOf<SocialFolderEntry>()
        val tgCandidates = listOf(
            Pair(File(root, "Telegram/Telegram Images"), "Images"),
            Pair(File(root, "Telegram/Telegram Video"), "Videos"),
            Pair(File(root, "Telegram/Telegram Documents"), "Documents"),
            Pair(File(root, "Telegram/Telegram Audio"), "Audio"),
            Pair(File(root, "Telegram"), "Media"),
            Pair(File(androidMedia, "org.telegram.messenger/Telegram/Telegram Images"), "Images"),
            Pair(File(androidMedia, "org.telegram.messenger/Telegram/Telegram Video"), "Videos"),
            Pair(File(sampleBase, "Telegram"), "Media")
        )
        for ((cand, hint) in tgCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (tgFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    tgFolders.add(entry)
                }
            }
        }
        val isTgInstalled = isInstalled("org.telegram.messenger") || isInstalled("org.telegram.messenger.web")
        if (isTgInstalled || tgFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.TELEGRAM,
                    packageName = "org.telegram.messenger",
                    isAppInstalled = isTgInstalled,
                    folders = tgFolders
                )
            )
        }

        // 3. Instagram
        val igFolders = mutableListOf<SocialFolderEntry>()
        val igCandidates = listOf(
            Pair(File(pictures, "Instagram"), "Images"),
            Pair(File(movies, "Instagram"), "Videos"),
            Pair(File(root, "Instagram"), "Media"),
            Pair(File(sampleBase, "Instagram"), "Media")
        )
        for ((cand, hint) in igCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (igFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    igFolders.add(entry)
                }
            }
        }
        val isIgInstalled = isInstalled("com.instagram.android")
        if (isIgInstalled || igFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.INSTAGRAM,
                    packageName = "com.instagram.android",
                    isAppInstalled = isIgInstalled,
                    folders = igFolders
                )
            )
        }

        // 4. Facebook & Messenger
        val fbFolders = mutableListOf<SocialFolderEntry>()
        val fbCandidates = listOf(
            Pair(File(pictures, "Facebook"), "Images"),
            Pair(File(movies, "Facebook"), "Videos"),
            Pair(File(pictures, "Messenger"), "Images"),
            Pair(File(movies, "Messenger"), "Videos"),
            Pair(File(root, "Facebook"), "Media")
        )
        for ((cand, hint) in fbCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (fbFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    fbFolders.add(entry)
                }
            }
        }
        val isFbInstalled = isInstalled("com.facebook.katana") || isInstalled("com.facebook.orca")
        if (isFbInstalled || fbFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.FACEBOOK,
                    packageName = "com.facebook.katana",
                    isAppInstalled = isFbInstalled,
                    folders = fbFolders
                )
            )
        }

        // 5. TikTok
        val ttFolders = mutableListOf<SocialFolderEntry>()
        val ttCandidates = listOf(
            Pair(File(movies, "TikTok"), "Videos"),
            Pair(File(pictures, "TikTok"), "Images"),
            Pair(File(root, "TikTok"), "Videos")
        )
        for ((cand, hint) in ttCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (ttFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    ttFolders.add(entry)
                }
            }
        }
        val isTtInstalled = isInstalled("com.zhiliaoapp.musically") || isInstalled("com.ss.android.ugc.trill")
        if (isTtInstalled || ttFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.TIKTOK,
                    packageName = "com.zhiliaoapp.musically",
                    isAppInstalled = isTtInstalled,
                    folders = ttFolders
                )
            )
        }

        // 6. Snapchat
        val snapFolders = mutableListOf<SocialFolderEntry>()
        val snapCandidates = listOf(
            Pair(File(pictures, "Snapchat"), "Images"),
            Pair(File(movies, "Snapchat"), "Videos"),
            Pair(File(root, "Snapchat"), "Media")
        )
        for ((cand, hint) in snapCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (snapFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    snapFolders.add(entry)
                }
            }
        }
        val isSnapInstalled = isInstalled("com.snapchat.android")
        if (isSnapInstalled || snapFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.SNAPCHAT,
                    packageName = "com.snapchat.android",
                    isAppInstalled = isSnapInstalled,
                    folders = snapFolders
                )
            )
        }

        // 7. Twitter / X
        val twFolders = mutableListOf<SocialFolderEntry>()
        val twCandidates = listOf(
            Pair(File(pictures, "Twitter"), "Images"),
            Pair(File(movies, "Twitter"), "Videos"),
            Pair(File(download, "Twitter"), "Media")
        )
        for ((cand, hint) in twCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (twFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    twFolders.add(entry)
                }
            }
        }
        val isTwInstalled = isInstalled("com.twitter.android")
        if (isTwInstalled || twFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.TWITTER,
                    packageName = "com.twitter.android",
                    isAppInstalled = isTwInstalled,
                    folders = twFolders
                )
            )
        }

        // 8. YouTube
        val ytFolders = mutableListOf<SocialFolderEntry>()
        val ytCandidates = listOf(
            Pair(File(movies, "YouTube"), "Videos"),
            Pair(File(download, "YouTube"), "Videos")
        )
        for ((cand, hint) in ytCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (ytFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    ytFolders.add(entry)
                }
            }
        }
        val isYtInstalled = isInstalled("com.google.android.youtube")
        if (isYtInstalled || ytFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.YOUTUBE,
                    packageName = "com.google.android.youtube",
                    isAppInstalled = isYtInstalled,
                    folders = ytFolders
                )
            )
        }

        // 9. Reddit
        val rdFolders = mutableListOf<SocialFolderEntry>()
        val rdCandidates = listOf(
            Pair(File(pictures, "Reddit"), "Images"),
            Pair(File(movies, "Reddit"), "Videos")
        )
        for ((cand, hint) in rdCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (rdFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    rdFolders.add(entry)
                }
            }
        }
        val isRdInstalled = isInstalled("com.reddit.frontpage")
        if (isRdInstalled || rdFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.REDDIT,
                    packageName = "com.reddit.frontpage",
                    isAppInstalled = isRdInstalled,
                    folders = rdFolders
                )
            )
        }

        // 10. Discord
        val dcFolders = mutableListOf<SocialFolderEntry>()
        val dcCandidates = listOf(
            Pair(File(pictures, "Discord"), "Images"),
            Pair(File(movies, "Discord"), "Videos")
        )
        for ((cand, hint) in dcCandidates) {
            createEntry(cand, hint)?.let { entry ->
                if (dcFolders.none { it.folder.absolutePath == entry.folder.absolutePath }) {
                    dcFolders.add(entry)
                }
            }
        }
        val isDcInstalled = isInstalled("com.discord")
        if (isDcInstalled || dcFolders.isNotEmpty()) {
            groups.add(
                SocialAppGroup(
                    socialType = SocialFolderType.DISCORD,
                    packageName = "com.discord",
                    isAppInstalled = isDcInstalled,
                    folders = dcFolders
                )
            )
        }

        // Fallback for emulator / fresh storage
        if (groups.isEmpty()) {
            val defaultWa = File(sampleBase, "WhatsApp").apply { mkdirs() }
            val defaultWaImg = File(defaultWa, "WhatsApp Images").apply { mkdirs() }
            val defaultWaVid = File(defaultWa, "WhatsApp Video").apply { mkdirs() }
            val defaultTg = File(sampleBase, "Telegram").apply { mkdirs() }
            val defaultIg = File(sampleBase, "Instagram").apply { mkdirs() }

            createEntry(defaultWaImg, "Images")?.let {
                groups.add(
                    SocialAppGroup(
                        socialType = SocialFolderType.WHATSAPP,
                        packageName = "com.whatsapp",
                        isAppInstalled = false,
                        folders = listOf(it, createEntry(defaultWaVid, "Videos") ?: it)
                    )
                )
            }
            createEntry(defaultTg, "Media")?.let {
                groups.add(
                    SocialAppGroup(
                        socialType = SocialFolderType.TELEGRAM,
                        packageName = "org.telegram.messenger",
                        isAppInstalled = false,
                        folders = listOf(it)
                    )
                )
            }
            createEntry(defaultIg, "Media")?.let {
                groups.add(
                    SocialAppGroup(
                        socialType = SocialFolderType.INSTAGRAM,
                        packageName = "com.instagram.android",
                        isAppInstalled = false,
                        folders = listOf(it)
                    )
                )
            }
        }

        groups
    }
}
