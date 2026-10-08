package com.ct.explorer.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ct.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cent_vault_prefs", Context.MODE_PRIVATE)

    private val vaultDir: File = File(context.filesDir, "CentVault_Secure").apply {
        if (!exists()) mkdirs()
    }

    private val filesDir: File = File(vaultDir, "vault_files").apply {
        if (!exists()) mkdirs()
    }

    init {
        clearTempPreviewCache()
    }

    private val vaultKey: SecretKey by lazy {
        getOrCreateVaultKey()
    }

    private fun getOrCreateKeyStoreMaster(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
            keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    private fun encryptWithKeyStore(rawBytes: ByteArray): Pair<String, String>? {
        return try {
            val masterKey = getOrCreateKeyStoreMaster() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val enc = cipher.doFinal(rawBytes)
            Pair(
                Base64.encodeToString(enc, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptWithKeyStore(encBase64: String, ivBase64: String): ByteArray? {
        return try {
            val masterKey = getOrCreateKeyStoreMaster() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val enc = Base64.decode(encBase64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
            cipher.doFinal(enc)
        } catch (e: Exception) {
            null
        }
    }

    private fun getOrCreateVaultKey(): SecretKey {
        // 1. Try to load from KeyStore-encrypted preferences
        val encBase64 = prefs.getString(KEY_CIPHER_SECRET_ENC, null)
        val ivBase64 = prefs.getString(KEY_CIPHER_SECRET_IV, null)
        if (encBase64 != null && ivBase64 != null) {
            val rawBytes = decryptWithKeyStore(encBase64, ivBase64)
            if (rawBytes != null && rawBytes.size == 32) {
                return SecretKeySpec(rawBytes, "AES")
            }
        }

        // 2. Check for legacy unencrypted key to migrate seamlessly
        val legacyHex = prefs.getString(KEY_CIPHER_SECRET_LEGACY, null)
        if (legacyHex != null) {
            try {
                val legacyBytes = legacyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                if (legacyBytes.size == 32) {
                    val encrypted = encryptWithKeyStore(legacyBytes)
                    if (encrypted != null) {
                        prefs.edit()
                            .putString(KEY_CIPHER_SECRET_ENC, encrypted.first)
                            .putString(KEY_CIPHER_SECRET_IV, encrypted.second)
                            .remove(KEY_CIPHER_SECRET_LEGACY)
                            .apply()
                    }
                    return SecretKeySpec(legacyBytes, "AES")
                }
            } catch (_: Exception) {}
        }

        // 3. Generate a brand new 256-bit AES vault key
        val newKeyBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val encrypted = encryptWithKeyStore(newKeyBytes)
        if (encrypted != null) {
            prefs.edit()
                .putString(KEY_CIPHER_SECRET_ENC, encrypted.first)
                .putString(KEY_CIPHER_SECRET_IV, encrypted.second)
                .remove(KEY_CIPHER_SECRET_LEGACY)
                .apply()
        } else {
            // Fallback for rare systems lacking AndroidKeyStore
            val fallbackHex = newKeyBytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_CIPHER_SECRET_LEGACY, fallbackHex).apply()
        }
        val secretKey = SecretKeySpec(newKeyBytes, "AES")
        java.util.Arrays.fill(newKeyBytes, 0.toByte())
        return secretKey
    }

    fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hash(pin) == storedHash
    }

    fun setPin(pin: String, securityAnswer: String) {
        prefs.edit()
            .putString(KEY_PIN_HASH, hash(pin))
            .putString(KEY_SECURITY_ANSWER, hash(securityAnswer.trim().lowercase()))
            .apply()
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        val storedHash = prefs.getString(KEY_SECURITY_ANSWER, null) ?: return false
        return hash(answer.trim().lowercase()) == storedHash
    }

    fun resetPinWithSecurityAnswer(answer: String, newPin: String): Boolean {
        if (!verifySecurityAnswer(answer)) return false
        prefs.edit().putString(KEY_PIN_HASH, hash(newPin)).apply()
        return true
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Decrypts a vault file to a temporary preview cache file so in-app viewers
     * (ImageViewer, VideoPlayer, PdfViewer, AudioPlayer, TextEditor) or external choosers
     * can read valid decrypted bytes instead of the encrypted MIV1 stream.
     */
    suspend fun decryptToTempCacheFile(vaultFile: File): File? = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) return@withContext null
        var tempOut: File? = null
        try {
            val previewDir = File(context.cacheDir, "vault_preview").apply {
                if (!exists()) mkdirs()
            }
            val cleanName = vaultFile.name.replace(Regex("^\\d{13}_"), "")
            val targetTemp = File(previewDir, cleanName)
            tempOut = targetTemp

            var success = false
            FileInputStream(vaultFile).use { fis ->
                val header = ByteArray(MAGIC_HEADER.size)
                val readHeader = fis.read(header)
                if (readHeader == MAGIC_HEADER.size && header.contentEquals(MAGIC_HEADER)) {
                    val iv = ByteArray(16)
                    val readIv = fis.read(iv)
                    if (readIv == 16) {
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(Cipher.DECRYPT_MODE, vaultKey, IvParameterSpec(iv))
                        CipherInputStream(fis, cipher).use { cis ->
                            FileOutputStream(targetTemp).use { fos ->
                                cis.copyTo(fos)
                            }
                        }
                        success = true
                    } else {
                        return@withContext null
                    }
                } else {
                    FileOutputStream(targetTemp).use { fos ->
                        if (readHeader > 0) fos.write(header, 0, readHeader)
                        fis.copyTo(fos)
                    }
                    success = true
                }
            }
            if (success) targetTemp else { targetTemp.delete(); null }
        } catch (e: Exception) {
            e.printStackTrace()
            tempOut?.delete()
            null
        }
    }

    fun clearTempPreviewCache() {
        try {
            val previewDir = File(context.cacheDir, "vault_preview")
            if (previewDir.exists()) {
                previewDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    suspend fun getVaultFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val files = filesDir.listFiles() ?: return@withContext emptyList()
        files.map { FileItem(it) }.sortedByDescending { it.lastModified }
    }

    /**
     * Encrypts and moves source file into encrypted vault storage using AES-256-CBC.
     */
    suspend fun addToVault(source: File): Boolean = withContext(Dispatchers.IO) {
        if (!source.exists() || source.isDirectory) return@withContext false
        val dest = File(filesDir, source.name)
        val finalDest = if (dest.exists()) {
            File(filesDir, "${System.currentTimeMillis()}_${source.name}")
        } else dest

        try {
            val iv = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, vaultKey, IvParameterSpec(iv))

            FileOutputStream(finalDest).use { fos ->
                fos.write(MAGIC_HEADER)
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(source).use { fis ->
                        fis.copyTo(cos)
                    }
                }
            }

            if (finalDest.exists() && finalDest.length() > 0) {
                val deleted = source.delete()
                if (!deleted) {
                    // Privacy guard: Rollback encrypted file if source could not be deleted
                    finalDest.delete()
                    false
                } else {
                    true
                }
            } else {
                finalDest.delete()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            finalDest.delete()
            false
        }
    }

    /**
     * Decrypts and restores vault file to target directory using AES-256-CBC.
     */
    suspend fun restoreFromVault(vaultFile: File, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) return@withContext false
        var dest: File? = null
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val cleanName = vaultFile.name.replace(Regex("^\\d{13}_"), "")
            var targetDest = File(targetDir, cleanName)
            if (targetDest.exists()) {
                val nameWithoutExt = cleanName.substringBeforeLast(".")
                val ext = if (cleanName.contains(".")) ".${cleanName.substringAfterLast(".")}" else ""
                targetDest = File(targetDir, "${nameWithoutExt}_restored$ext")
            }
            dest = targetDest

            var restoreSuccess = false
            FileInputStream(vaultFile).use { fis ->
                val header = ByteArray(MAGIC_HEADER.size)
                val readHeader = fis.read(header)
                if (readHeader == MAGIC_HEADER.size && header.contentEquals(MAGIC_HEADER)) {
                    val iv = ByteArray(16)
                    val readIv = fis.read(iv)
                    if (readIv == 16) {
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(Cipher.DECRYPT_MODE, vaultKey, IvParameterSpec(iv))
                        CipherInputStream(fis, cipher).use { cis ->
                            FileOutputStream(targetDest).use { fos ->
                                cis.copyTo(fos)
                            }
                        }
                        restoreSuccess = true
                    }
                } else {
                    // Plaintext legacy fallback
                    FileOutputStream(targetDest).use { fos ->
                        if (readHeader > 0) {
                            fos.write(header, 0, readHeader)
                        }
                        fis.copyTo(fos)
                    }
                    restoreSuccess = true
                }
            }

            if (restoreSuccess && targetDest.exists()) {
                vaultFile.delete()
                true
            } else {
                targetDest.delete()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            dest?.delete()
            false
        }
    }

    suspend fun deleteFromVault(vaultFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            vaultFile.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val MAGIC_HEADER = byteArrayOf(0x4D, 0x49, 0x56, 0x31) // "MIV1"
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_SECURITY_ANSWER = "vault_security_answer"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "CentVaultMasterKey_v1"
        private const val KEY_CIPHER_SECRET_ENC = "vault_cipher_secret_enc"
        private const val KEY_CIPHER_SECRET_IV = "vault_cipher_secret_iv"
        private const val KEY_CIPHER_SECRET_LEGACY = "vault_cipher_secret_key"
    }
}
