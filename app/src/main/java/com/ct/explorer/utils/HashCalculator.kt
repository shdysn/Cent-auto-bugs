package com.ct.explorer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class FileHashes(
    val md5: String,
    val sha1: String,
    val sha256: String,
    val fileSize: Long
)

object HashCalculator {

    suspend fun calculateHashes(
        file: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<FileHashes> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist or is a directory"))
            }

            val md5Digest = MessageDigest.getInstance("MD5")
            val sha1Digest = MessageDigest.getInstance("SHA-1")
            val sha256Digest = MessageDigest.getInstance("SHA-256")

            val totalBytes = file.length().coerceAtLeast(1L)
            var bytesReadTotal = 0L

            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024) // 64 KB buffer
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md5Digest.update(buffer, 0, bytesRead)
                    sha1Digest.update(buffer, 0, bytesRead)
                    sha256Digest.update(buffer, 0, bytesRead)

                    bytesReadTotal += bytesRead
                    onProgress?.invoke(bytesReadTotal.toFloat() / totalBytes.toFloat())
                }
            }

            val md5Hex = bytesToHex(md5Digest.digest())
            val sha1Hex = bytesToHex(sha1Digest.digest())
            val sha256Hex = bytesToHex(sha256Digest.digest())

            Result.success(
                FileHashes(
                    md5 = md5Hex,
                    sha1 = sha1Hex,
                    sha256 = sha256Hex,
                    fileSize = file.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }
}
