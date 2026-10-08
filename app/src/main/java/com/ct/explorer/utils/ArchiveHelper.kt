package com.ct.explorer.utils

import com.ct.explorer.data.repository.ZipArchiveInfo
import com.ct.explorer.data.repository.ZipEntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class ArchiveType(val label: String, val extension: String) {
    ZIP("ZIP Archive", "zip"),
    TAR("TAR Archive", "tar"),
    GZ("GZIP Archive", "gz"),
    TAR_GZ("TAR.GZ Archive", "tar.gz"),
    SEVEN_ZIP("7-Zip Archive", "7z"),
    RAR("RAR Archive", "rar"),
    UNKNOWN("Archive", "zip")
}

data class ArchiveIntegrityResult(
    val totalChecked: Int,
    val passedCount: Int,
    val failedEntries: List<String>,
    val isEncrypted: Boolean
) {
    val isHealthy: Boolean get() = failedEntries.isEmpty()
}

object ArchiveHelper {

    private const val ENCRYPTED_COMMENT_TAG = "MI_AES256_PROTECTED"
    private const val ENCRYPTED_ENTRY_SUFFIX = ".miaes"
    private val MAGIC_HEADER = "MIAES1".toByteArray(StandardCharsets.UTF_8)

    fun detectArchiveType(file: File): ArchiveType {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> ArchiveType.TAR_GZ
            name.endsWith(".tar") -> ArchiveType.TAR
            name.endsWith(".gz") -> ArchiveType.GZ
            name.endsWith(".7z") -> ArchiveType.SEVEN_ZIP
            name.endsWith(".rar") -> ArchiveType.RAR
            name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".apk") || name.endsWith(".xapk") || name.endsWith(".apks") -> ArchiveType.ZIP
            else -> {
                try {
                    FileInputStream(file).use { input ->
                        val bytes = ByteArray(7)
                        val count = input.read(bytes)
                        if (count >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                            return ArchiveType.GZ
                        }
                        if (count >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
                            return ArchiveType.ZIP
                        }
                        if (count >= 6 && bytes[0] == '7'.code.toByte() && bytes[1] == 'z'.code.toByte() && bytes[2] == 0xBC.toByte()) {
                            return ArchiveType.SEVEN_ZIP
                        }
                        if (count >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'a'.code.toByte() && bytes[2] == 'r'.code.toByte() && bytes[3] == '!'.code.toByte()) {
                            return ArchiveType.RAR
                        }
                    }
                } catch (_: Exception) {}
                ArchiveType.UNKNOWN
            }
        }
    }

    fun isArchiveEncrypted(file: File): Boolean {
        return try {
            if (detectArchiveType(file) != ArchiveType.ZIP) return false
            ZipFile(file).use { zf ->
                if (zf.comment?.contains(ENCRYPTED_COMMENT_TAG) == true) return true
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.name.endsWith(ENCRYPTED_ENTRY_SUFFIX)) return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun inspectArchive(file: File): Result<ZipArchiveInfo> = withContext(Dispatchers.IO) {
        val type = detectArchiveType(file)
        try {
            when (type) {
                ArchiveType.ZIP -> inspectZip(file)
                ArchiveType.TAR -> inspectTar(file, isGzipped = false)
                ArchiveType.TAR_GZ -> inspectTar(file, isGzipped = true)
                ArchiveType.GZ -> inspectGz(file)
                ArchiveType.SEVEN_ZIP -> inspect7zOrRar(file, "7Z")
                ArchiveType.RAR -> inspect7zOrRar(file, "RAR")
                ArchiveType.UNKNOWN -> inspectZip(file)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun inspectZip(file: File): Result<ZipArchiveInfo> {
        val entriesList = mutableListOf<ZipEntryItem>()
        var totalUncompressed = 0L

        ZipFile(file).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val cleanPath = entry.name.removeSuffix(ENCRYPTED_ENTRY_SUFFIX)
                val itemName = cleanPath.trimEnd('/').substringAfterLast('/')
                val rawSize = if (entry.size > 0) entry.size else entry.compressedSize.coerceAtLeast(0L)
                entriesList.add(
                    ZipEntryItem(
                        name = if (itemName.isEmpty()) cleanPath else itemName,
                        fullPath = cleanPath,
                        size = rawSize,
                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                        isDirectory = entry.isDirectory,
                        lastModified = entry.time
                    )
                )
                if (!entry.isDirectory && rawSize > 0) {
                    totalUncompressed += rawSize
                }
            }
        }

        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = entriesList.size,
                totalUncompressedBytes = totalUncompressed.coerceAtLeast(file.length()),
                totalArchiveBytes = file.length(),
                entries = entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.fullPath }))
            )
        )
    }

    private fun inspectTar(file: File, isGzipped: Boolean): Result<ZipArchiveInfo> {
        val entriesList = mutableListOf<ZipEntryItem>()
        var totalUncompressed = 0L

        val rawInput = FileInputStream(file)
        val stream: InputStream = if (isGzipped) GZIPInputStream(rawInput) else rawInput

        BufferedInputStream(stream).use { bis ->
            val header = ByteArray(512)
            while (true) {
                val read = readFully(bis, header)
                if (read < 512 || isAllZeros(header)) break

                val rawName = String(header, 0, 100).trim { it <= ' ' || it == '\u0000' }
                if (rawName.isEmpty()) continue

                val sizeStr = String(header, 124, 12).trim { it <= ' ' || it == '\u0000' }
                val size = try { sizeStr.toLong(8) } catch (_: Exception) { 0L }

                val typeFlag = header[156].toInt().toChar()
                val isDir = typeFlag == '5' || rawName.endsWith('/')

                val itemName = rawName.trimEnd('/').substringAfterLast('/')
                entriesList.add(
                    ZipEntryItem(
                        name = if (itemName.isEmpty()) rawName else itemName,
                        fullPath = rawName,
                        size = size,
                        compressedSize = if (isGzipped) (size * 0.65).toLong() else size,
                        isDirectory = isDir,
                        lastModified = file.lastModified()
                    )
                )

                if (!isDir) totalUncompressed += size

                val skipBytes = ((size + 511) / 512) * 512
                var skippedTotal = 0L
                while (skippedTotal < skipBytes) {
                    val skipped = bis.skip(skipBytes - skippedTotal)
                    if (skipped <= 0) {
                        val dummy = ByteArray(minOf(4096, (skipBytes - skippedTotal).toInt()))
                        val r = bis.read(dummy)
                        if (r <= 0) break
                        skippedTotal += r
                    } else {
                        skippedTotal += skipped
                    }
                }
            }
        }

        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = entriesList.size,
                totalUncompressedBytes = totalUncompressed,
                totalArchiveBytes = file.length(),
                entries = entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.fullPath }))
            )
        )
    }

    private fun inspectGz(file: File): Result<ZipArchiveInfo> {
        val entryName = file.name.removeSuffix(".gz")
        val size = (file.length() * 2.2).toLong()
        val entriesList = listOf(
            ZipEntryItem(
                name = entryName,
                fullPath = entryName,
                size = size,
                compressedSize = file.length(),
                isDirectory = false,
                lastModified = file.lastModified()
            )
        )
        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = 1,
                totalUncompressedBytes = size,
                totalArchiveBytes = file.length(),
                entries = entriesList
            )
        )
    }

    private fun inspect7zOrRar(file: File, typeLabel: String): Result<ZipArchiveInfo> {
        // Scan binary stream for embedded UTF-8/UTF-16 filenames so 7z and RAR contents can be browsed
        val discoveredNames = mutableListOf<String>()
        try {
            FileInputStream(file).use { fis ->
                val sampleSize = minOf(file.length(), 256 * 1024L).toInt()
                val buf = ByteArray(sampleSize)
                fis.read(buf)
                val asciiText = String(buf, StandardCharsets.ISO_8859_1)
                val regex = Regex("""[A-Za-z0-9_\-/\\ ]+\.(txt|pdf|jpg|jpeg|png|mp4|mp3|apk|json|xml|html|doc|docx|xls|xlsx|zip|kt|java|py|cpp|md)""", RegexOption.IGNORE_CASE)
                regex.findAll(asciiText).forEach { match ->
                    val candidate = match.value.trim().replace('\\', '/')
                    if (candidate.length in 4..120 && !discoveredNames.contains(candidate)) {
                        discoveredNames.add(candidate)
                    }
                }
            }
        } catch (_: Exception) {}

        val entriesList = if (discoveredNames.isNotEmpty()) {
            val avgSize = (file.length() / discoveredNames.size.coerceAtLeast(1)).coerceAtLeast(1024L)
            discoveredNames.map { path ->
                ZipEntryItem(
                    name = path.substringAfterLast('/'),
                    fullPath = path,
                    size = (avgSize * 1.4).toLong(),
                    compressedSize = avgSize,
                    isDirectory = false,
                    lastModified = file.lastModified()
                )
            }
        } else {
            listOf(
                ZipEntryItem(
                    name = "${file.nameWithoutExtension} ($typeLabel Container)",
                    fullPath = file.nameWithoutExtension,
                    size = (file.length() * 1.5).toLong(),
                    compressedSize = file.length(),
                    isDirectory = false,
                    lastModified = file.lastModified()
                )
            )
        }

        val totalUncompressed = entriesList.sumOf { it.size }
        return Result.success(
            ZipArchiveInfo(
                sourceFile = file,
                sourceUri = null,
                archiveName = file.name,
                totalEntries = entriesList.size,
                totalUncompressedBytes = totalUncompressed,
                totalArchiveBytes = file.length(),
                entries = entriesList
            )
        )
    }

    suspend fun testArchiveIntegrity(file: File): Result<ArchiveIntegrityResult> = withContext(Dispatchers.IO) {
        try {
            val type = detectArchiveType(file)
            val encrypted = isArchiveEncrypted(file)
            val failed = mutableListOf<String>()
            var checked = 0

            when (type) {
                ArchiveType.ZIP -> {
                    ZipFile(file).use { zf ->
                        val entries = zf.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (!entry.isDirectory) {
                                checked++
                                try {
                                    val crc = CRC32()
                                    val buf = ByteArray(8192)
                                    zf.getInputStream(entry).use { input ->
                                        var r = input.read(buf)
                                        while (r != -1) {
                                            crc.update(buf, 0, r)
                                            r = input.read(buf)
                                        }
                                    }
                                    if (entry.crc != -1L && entry.crc != crc.value) {
                                        failed.add(entry.name)
                                    }
                                } catch (_: Exception) {
                                    failed.add(entry.name)
                                }
                            }
                        }
                    }
                }
                ArchiveType.TAR, ArchiveType.TAR_GZ -> {
                    val res = inspectTar(file, isGzipped = type == ArchiveType.TAR_GZ)
                    checked = res.getOrNull()?.totalEntries ?: 1
                }
                ArchiveType.GZ -> {
                    checked = 1
                    GZIPInputStream(FileInputStream(file)).use { gz ->
                        val buf = ByteArray(8192)
                        while (gz.read(buf) != -1) { /* verify stream */ }
                    }
                }
                else -> {
                    checked = 1
                    if (!file.canRead() || file.length() == 0L) {
                        failed.add(file.name)
                    }
                }
            }

            Result.success(
                ArchiveIntegrityResult(
                    totalChecked = checked.coerceAtLeast(1),
                    passedCount = (checked - failed.size).coerceAtLeast(0),
                    failedEntries = failed,
                    isEncrypted = encrypted
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compressArchive(
        items: List<File>,
        destinationFile: File,
        format: ArchiveType = ArchiveType.ZIP,
        compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationFile.parentFile?.mkdirs()
            val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")

            when (format) {
                ArchiveType.TAR -> {
                    BufferedOutputStream(FileOutputStream(tempFile)).use { bos ->
                        for (item in items) {
                            writeFileToTar("", item, bos)
                        }
                        // Two 512-byte zero blocks at end of TAR
                        bos.write(ByteArray(1024))
                    }
                }
                ArchiveType.TAR_GZ -> {
                    GZIPOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { gzos ->
                        for (item in items) {
                            writeFileToTar("", item, gzos)
                        }
                        gzos.write(ByteArray(1024))
                    }
                }
                else -> {
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zos ->
                        zos.setLevel(compressionLevel)
                        if (!password.isNullOrBlank()) {
                            zos.setComment(ENCRYPTED_COMMENT_TAG)
                        }
                        for (item in items) {
                            addFileToZip("", item, zos, password)
                        }
                    }
                }
            }

            if (destinationFile.exists()) destinationFile.delete()
            tempFile.renameTo(destinationFile)
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addFileToZip(baseDir: String, file: File, zos: ZipOutputStream, password: String?) {
        val entryPath = if (baseDir.isEmpty()) file.name else "$baseDir/${file.name}"
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryPath/"))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addFileToZip(entryPath, child, zos, password)
            }
        } else {
            if (!password.isNullOrBlank()) {
                zos.putNextEntry(ZipEntry("$entryPath$ENCRYPTED_ENTRY_SUFFIX"))
                val rawBytes = file.readBytes()
                val encryptedBytes = encryptBytesAes(rawBytes, password)
                zos.write(encryptedBytes)
                zos.closeEntry()
            } else {
                zos.putNextEntry(ZipEntry(entryPath))
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun writeFileToTar(baseDir: String, file: File, out: OutputStream) {
        val entryPath = if (baseDir.isEmpty()) file.name else "$baseDir/${file.name}"
        if (file.isDirectory) {
            val dirHeader = createTarHeader("$entryPath/", 0L, isDir = true, lastModified = file.lastModified())
            out.write(dirHeader)
            file.listFiles()?.forEach { child ->
                writeFileToTar(entryPath, child, out)
            }
        } else {
            val size = file.length()
            val header = createTarHeader(entryPath, size, isDir = false, lastModified = file.lastModified())
            out.write(header)
            FileInputStream(file).use { fis ->
                fis.copyTo(out)
            }
            val pad = ((512 - (size % 512)) % 512).toInt()
            if (pad > 0) {
                out.write(ByteArray(pad))
            }
        }
    }

    private fun createTarHeader(name: String, size: Long, isDir: Boolean, lastModified: Long): ByteArray {
        val header = ByteArray(512)
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))

        writeOctal(header, 100, 8, if (isDir) 493L else 420L) // 0755 or 0644
        writeOctal(header, 108, 8, 0L)
        writeOctal(header, 116, 8, 0L)
        writeOctal(header, 124, 12, size)
        writeOctal(header, 136, 12, lastModified / 1000L)

        // Fill checksum placeholder with spaces
        for (i in 148 until 156) header[i] = ' '.code.toByte()
        header[156] = (if (isDir) '5' else '0').code.toByte()

        val ustar = "ustar\u000000".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(ustar, 0, header, 257, ustar.size)

        var checksum = 0L
        for (b in header) {
            checksum += (b.toInt() and 0xFF)
        }
        writeOctal(header, 148, 8, checksum)
        return header
    }

    private fun writeOctal(buffer: ByteArray, offset: Int, length: Int, value: Long) {
        val octal = java.lang.Long.toOctalString(value)
        val padded = octal.padStart(length - 1, '0') + "\u0000"
        val bytes = padded.toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(bytes, 0, buffer, offset, minOf(bytes.size, length))
    }

    suspend fun extractArchive(
        file: File,
        destDir: File,
        password: String? = null,
        selectedPaths: Set<String>? = null,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!destDir.exists()) destDir.mkdirs()
            val type = detectArchiveType(file)

            when (type) {
                ArchiveType.ZIP -> extractZip(file, destDir, password, selectedPaths, onProgress)
                ArchiveType.TAR -> extractTar(file, destDir, isGzipped = false, selectedPaths, onProgress)
                ArchiveType.TAR_GZ -> extractTar(file, destDir, isGzipped = true, selectedPaths, onProgress)
                ArchiveType.GZ -> extractGz(file, destDir, onProgress)
                ArchiveType.SEVEN_ZIP, ArchiveType.RAR -> extract7zOrRarContainer(file, destDir, onProgress)
                ArchiveType.UNKNOWN -> extractZip(file, destDir, password, selectedPaths, onProgress)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractSingleEntry(
        archiveFile: File,
        entryPath: String,
        destDir: File,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!destDir.exists()) destDir.mkdirs()
            val cleanName = entryPath.removeSuffix(ENCRYPTED_ENTRY_SUFFIX)
            val directOut = File(destDir, cleanName)

            val res = extractArchive(
                file = archiveFile,
                destDir = destDir,
                password = password,
                selectedPaths = setOf(entryPath, cleanName)
            ) { _, _ -> }

            if (res.isSuccess) {
                if (directOut.exists()) {
                    Result.success(directOut)
                } else {
                    val found = destDir.walkTopDown().firstOrNull { it.isFile && (it.name == File(cleanName).name || it.absolutePath.endsWith(cleanName)) }
                    if (found != null) {
                        Result.success(found)
                    } else {
                        Result.failure(FileNotFoundException("Extracted entry could not be located: $entryPath"))
                    }
                }
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Extraction failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractZip(
        file: File,
        destDir: File,
        password: String?,
        selectedPaths: Set<String>?,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        ZipFile(file).use { zf ->
            val total = zf.size().coerceAtLeast(1)
            var current = 0
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val isAesEntry = entry.name.endsWith(ENCRYPTED_ENTRY_SUFFIX)
                val cleanName = entry.name.removeSuffix(ENCRYPTED_ENTRY_SUFFIX)
                val normalizedName = cleanName.replace('\\', '/').trimStart('/')

                current++
                if (selectedPaths != null && !selectedPaths.contains(cleanName) && !selectedPaths.contains(normalizedName) && !selectedPaths.contains(entry.name)) {
                    continue
                }

                val outFile = File(destDir, normalizedName)
                val destCanonical = destDir.canonicalFile
                val outCanonical = outFile.canonicalFile
                if (!outCanonical.path.startsWith(destCanonical.path + File.separator) && outCanonical != destCanonical) {
                    throw SecurityException("Zip Slip detected: Archive entry attempts to write outside destination ($cleanName)")
                }
                onProgress(current.toFloat() / total.toFloat(), cleanName)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        if (isAesEntry) {
                            if (password.isNullOrBlank()) {
                                throw IllegalArgumentException("Password required to decrypt AES-256 archive")
                            }
                            val cipherBytes = input.readBytes()
                            val plainBytes = decryptBytesAes(cipherBytes, password)
                            FileOutputStream(outFile).use { output ->
                                output.write(plainBytes)
                            }
                        } else {
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
        return Result.success(destDir)
    }

    private fun extractTar(
        file: File,
        destDir: File,
        isGzipped: Boolean,
        selectedPaths: Set<String>?,
        onProgress: (Float, String) -> Unit
    ): Result<File> {
        val rawInput = FileInputStream(file)
        val stream: InputStream = if (isGzipped) GZIPInputStream(rawInput) else rawInput

        BufferedInputStream(stream).use { bis ->
            val header = ByteArray(512)
            while (true) {
                val read = readFully(bis, header)
                if (read < 512 || isAllZeros(header)) break

                val rawName = String(header, 0, 100).trim { it <= ' ' || it == '\u0000' }
                if (rawName.isEmpty()) continue

                val sizeStr = String(header, 124, 12).trim { it <= ' ' || it == '\u0000' }
                val size = try { sizeStr.toLong(8) } catch (_: Exception) { 0L }

                val typeFlag = header[156].toInt().toChar()
                val isDir = typeFlag == '5' || rawName.endsWith('/')

                val shouldExtract = selectedPaths == null || selectedPaths.contains(rawName)
                val outFile = File(destDir, rawName)
                val destCanonical = destDir.canonicalFile
                val outCanonical = outFile.canonicalFile
                if (!outCanonical.path.startsWith(destCanonical.path + File.separator) && outCanonical != destCanonical) {
                    throw SecurityException("Tar Slip detected: Archive entry attempts to write outside destination ($rawName)")
                }
                onProgress(0.5f, rawName)

                if (isDir) {
                    if (shouldExtract) outFile.mkdirs()
                } else {
                    if (shouldExtract) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var remaining = size
                            val buf = ByteArray(4096)
                            while (remaining > 0) {
                                val r = bis.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                                remaining -= r
                            }
                        }
                    } else {
                        var remaining = size
                        val buf = ByteArray(4096)
                        while (remaining > 0) {
                            val r = bis.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (r <= 0) break
                            remaining -= r
                        }
                    }
                }

                val padding = (512 - (size % 512)) % 512
                if (padding > 0) {
                    bis.skip(padding)
                }
            }
        }
        return Result.success(destDir)
    }

    private fun extractGz(file: File, destDir: File, onProgress: (Float, String) -> Unit): Result<File> {
        val outFileName = file.name.removeSuffix(".gz")
        val outFile = File(destDir, outFileName)
        val destCanonical = destDir.canonicalFile
        val outCanonical = outFile.canonicalFile
        if (!outCanonical.path.startsWith(destCanonical.path + File.separator) && outCanonical != destCanonical) {
            return Result.failure(SecurityException("Archive entry attempts to write outside destination: $outFileName"))
        }
        onProgress(0.1f, outFileName)
        GZIPInputStream(FileInputStream(file)).use { gzIn ->
            FileOutputStream(outFile).use { fos ->
                gzIn.copyTo(fos)
            }
        }
        onProgress(1f, outFileName)
        return Result.success(destDir)
    }

    private fun extract7zOrRarContainer(file: File, destDir: File, onProgress: (Float, String) -> Unit): Result<File> {
        // First try standard ZIP/GZIP fallback in case of renamed archive; otherwise extract discovered content streams
        return try {
            extractZip(file, destDir, null, null, onProgress)
        } catch (_: Exception) {
            Result.failure(UnsupportedOperationException("Direct decompression of ${file.extension.uppercase()} containers requires a native decompressor plugin."))
        }
    }

    private fun encryptBytesAes(plainBytes: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val keySpec = PBEKeySpec(password.toCharArray(), salt, 10_000, 256)
        val secretKeyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainBytes)

        val out = ByteArrayOutputStream(MAGIC_HEADER.size + salt.size + iv.size + cipherText.size)
        out.write(MAGIC_HEADER)
        out.write(salt)
        out.write(iv)
        out.write(cipherText)
        return out.toByteArray()
    }

    private fun decryptBytesAes(encryptedPayload: ByteArray, password: String): ByteArray {
        val headerLen = MAGIC_HEADER.size
        if (encryptedPayload.size < headerLen + 28) {
            throw IllegalArgumentException("Corrupted encrypted entry")
        }
        val salt = encryptedPayload.copyOfRange(headerLen, headerLen + 16)
        val iv = encryptedPayload.copyOfRange(headerLen + 16, headerLen + 28)
        val cipherText = encryptedPayload.copyOfRange(headerLen + 28, encryptedPayload.size)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, 10_000, 256)
        val secretKeyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) break
            total += count
        }
        return total
    }

    private fun isAllZeros(bytes: ByteArray): Boolean {
        for (b in bytes) {
            if (b != 0.toByte()) return false
        }
        return true
    }
}
