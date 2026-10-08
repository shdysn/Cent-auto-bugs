package com.ct.explorer.utils

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

data class XapkInfo(
    val file: File,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val iconBitmap: Bitmap?,
    val splitApkNames: List<String>,
    val obbFileNames: List<String>,
    val totalSizeBytes: Long
)

object XapkInstaller {

    suspend fun parseXapk(file: File, context: Context? = null): Result<XapkInfo> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(Exception("Bundle file is missing or empty"))
            }

            var packageName = ""
            var appName = file.nameWithoutExtension
            var versionName = "1.0"
            var versionCode = 1L
            var iconBitmap: Bitmap? = null
            val apkNames = mutableListOf<String>()
            val obbNames = mutableListOf<String>()

            ZipFile(file).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name.lowercase()
                    if (name.endsWith(".apk")) {
                        apkNames.add(entry.name)
                    } else if (name.endsWith(".obb")) {
                        obbNames.add(entry.name)
                    } else if (name == "manifest.json" || name.endsWith("/manifest.json")) {
                        try {
                            zf.getInputStream(entry).use { stream ->
                                val jsonStr = stream.bufferedReader().readText()
                                val obj = JSONObject(jsonStr)
                                packageName = obj.optString("package_name", packageName)
                                appName = obj.optString("name", appName)
                                versionName = obj.optString("version_name", versionName)
                                versionCode = obj.optLong("version_code", versionCode)
                            }
                        } catch (_: Exception) {}
                    } else if (name == "icon.png" || name.endsWith("/icon.png")) {
                        try {
                            zf.getInputStream(entry).use { stream ->
                                iconBitmap = BitmapFactory.decodeStream(stream)
                            }
                        } catch (_: Exception) {}
                    }
                }

                // If manifest.json or icon was not present (e.g. standard .apks bundle), inspect base.apk via PackageManager
                if (context != null && apkNames.isNotEmpty() && (packageName.isBlank() || iconBitmap == null)) {
                    val baseEntryName = apkNames.firstOrNull {
                        val simple = it.substringAfterLast('/').lowercase()
                        simple == "base.apk" || simple == "base-master.apk" || !simple.startsWith("split_config")
                    } ?: apkNames.first()

                    val entry = zf.getEntry(baseEntryName)
                    if (entry != null) {
                        val tempApk = File(context.cacheDir, "parse_split_${System.nanoTime()}.apk")
                        try {
                            zf.getInputStream(entry).use { input ->
                                FileOutputStream(tempApk).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val pm = context.packageManager
                            val pkgInfo = pm.getPackageArchiveInfo(tempApk.absolutePath, PackageManager.GET_META_DATA)
                            val appInfo = pkgInfo?.applicationInfo
                            if (pkgInfo != null && appInfo != null) {
                                appInfo.sourceDir = tempApk.absolutePath
                                appInfo.publicSourceDir = tempApk.absolutePath
                                if (packageName.isBlank() && !pkgInfo.packageName.isNullOrBlank()) {
                                    packageName = pkgInfo.packageName
                                }
                                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { "" }
                                if (label.isNotBlank() && (appName == file.nameWithoutExtension || appName.isBlank())) {
                                    appName = label
                                }
                                if (!pkgInfo.versionName.isNullOrBlank()) {
                                    versionName = pkgInfo.versionName ?: versionName
                                }
                                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    pkgInfo.longVersionCode
                                } else {
                                    @Suppress("DEPRECATION")
                                    pkgInfo.versionCode.toLong()
                                }
                                if (iconBitmap == null) {
                                    iconBitmap = try {
                                        pm.getApplicationIcon(appInfo).toBitmap(width = 144, height = 144)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            try { tempApk.delete() } catch (_: Exception) {}
                        }
                    }
                }
            }

            if (apkNames.isEmpty()) {
                return@withContext Result.failure(Exception("No APK files found inside ${file.name}"))
            }

            Result.success(
                XapkInfo(
                    file = file,
                    packageName = packageName.ifBlank { "Unknown" },
                    appName = appName,
                    versionName = versionName,
                    versionCode = versionCode,
                    iconBitmap = iconBitmap,
                    splitApkNames = apkNames,
                    obbFileNames = obbNames,
                    totalSizeBytes = file.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installXapk(
        context: Context,
        xapkInfo: XapkInfo,
        onProgress: (Float, String) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!FileOpener.canInstallUnknownApps(context)) {
            withContext(Dispatchers.Main) {
                FileOpener.requestInstallUnknownAppsPermission(context)
            }
            return@withContext Result.failure(
                SecurityException("Please allow 'Install unknown apps' permission for Cent File Manager in Settings, then try again.")
            )
        }

        try {
            // Step 1: Extract OBB files if any
            if (xapkInfo.obbFileNames.isNotEmpty() && InAppPackageInstallerHelper.isValidPackageName(xapkInfo.packageName)) {
                try {
                    val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb/${xapkInfo.packageName}")
                    obbDir.mkdirs()
                    ZipFile(xapkInfo.file).use { zf ->
                        for (obbEntryName in xapkInfo.obbFileNames) {
                            val entry = zf.getEntry(obbEntryName) ?: continue
                            val safeName = obbEntryName.substringAfterLast('/').replace("..", "").replace("/", "")
                            if (safeName.isBlank()) continue
                            val obbFile = File(obbDir, safeName)
                            val obbCanonical = obbFile.canonicalFile
                            val obbDirCanonical = obbDir.canonicalFile
                            if (!obbCanonical.path.startsWith(obbDirCanonical.path + File.separator)) {
                                continue
                            }
                            onProgress(0.1f, "Copying OBB: ${obbFile.name}")
                            zf.getInputStream(entry).use { input ->
                                FileOutputStream(obbFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Step 2: Session-based PackageInstaller for Split APKs
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (InAppPackageInstallerHelper.isValidPackageName(xapkInfo.packageName)) {
                try {
                    params.setAppPackageName(xapkInfo.packageName)
                } catch (_: Exception) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                } catch (_: Exception) {}
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            try {
                ZipFile(xapkInfo.file).use { zf ->
                    val totalApks = xapkInfo.splitApkNames.size.coerceAtLeast(1)
                    for ((index, apkEntryName) in xapkInfo.splitApkNames.withIndex()) {
                        val entry = zf.getEntry(apkEntryName) ?: continue
                        val cleanName = apkEntryName
                            .substringAfterLast('/')
                            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

                        onProgress(0.15f + (0.75f * index / totalApks), "Staging split (${index + 1}/$totalApks): $cleanName")

                        val declaredSize = if (entry.size > 0L) entry.size else -1L
                        session.openWrite(cleanName, 0, declaredSize).use { sessionOut ->
                            zf.getInputStream(entry).use { apkIn ->
                                apkIn.copyTo(sessionOut)
                            }
                            session.fsync(sessionOut)
                        }
                    }
                }

                // Step 3: Commit session via PackageInstallerStatusReceiver
                onProgress(0.95f, "Launching system confirmation prompt...")
                val pendingIntent = InAppPackageInstallerHelper.createCommitPendingIntent(
                    context = context,
                    sessionId = sessionId,
                    packageName = xapkInfo.packageName.takeIf { InAppPackageInstallerHelper.isValidPackageName(it) }
                )

                session.commit(pendingIntent.intentSender)
                session.close()
                onProgress(1.0f, "Waiting for user confirmation...")
                Result.success(true)
            } catch (e: Exception) {
                try { session.abandon() } catch (_: Exception) {}
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
