package com.ct.explorer.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ct.explorer.data.model.FileItem
import java.io.File

object FileOpener {

    const val EXTRA_FROM_INTERNAL_INSTALLER = "from_mi_explorer_internal"

    fun getSafeContentUri(context: Context, file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            android.util.Log.e("FileOpener", "FileProvider failed for ${file.absolutePath}", e)
            null
        }
    }

    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (e: Exception) {
                true
            }
        } else {
            true
        }
    }

    fun requestInstallUnknownAppsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(fallback)
                } catch (_: Exception) {}
            }
        }
    }

    fun uninstallApp(context: Context, packageName: String) {
        if (packageName.isBlank() || packageName == "Unknown") {
            Toast.makeText(context, "Invalid package name", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                val fallback = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:$packageName")
                    putExtra(Intent.EXTRA_RETURN_RESULT, false)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallback)
            } catch (err: Exception) {
                Toast.makeText(context, "Cannot uninstall app: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun installApk(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "APK file is missing or empty", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!canInstallUnknownApps(context)) {
            Toast.makeText(
                context,
                "Please allow 'Install unknown apps' permission for Cent File Manager",
                Toast.LENGTH_LONG
            ).show()
            requestInstallUnknownAppsPermission(context)
            return false
        }

        return try {
            val uri = getSafeContentUri(context, file) ?: run {
                Toast.makeText(context, "Cannot prepare file URI for installation", Toast.LENGTH_SHORT).show()
                return false
            }

            val pm = context.packageManager
            val preferredInstallers = listOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.samsung.android.packageinstaller",
                "com.coloros.phonemanager"
            )

            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(EXTRA_FROM_INTERNAL_INSTALLER, true)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(EXTRA_FROM_INTERNAL_INSTALLER, true)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            // Query activities that handle ACTION_INSTALL_PACKAGE or ACTION_VIEW, excluding our own app
            val installCandidates = pm.queryIntentActivities(installIntent, 0)
                .filter { it.activityInfo.packageName != context.packageName }

            if (installCandidates.isNotEmpty()) {
                val chosen = installCandidates.firstOrNull {
                    it.activityInfo.packageName in preferredInstallers
                } ?: installCandidates.first()

                installIntent.setClassName(chosen.activityInfo.packageName, chosen.activityInfo.name)
                context.startActivity(installIntent)
                return true
            }

            val viewCandidates = pm.queryIntentActivities(viewIntent, 0)
                .filter { it.activityInfo.packageName != context.packageName }

            if (viewCandidates.isNotEmpty()) {
                val chosen = viewCandidates.firstOrNull {
                    it.activityInfo.packageName in preferredInstallers
                } ?: viewCandidates.first()

                viewIntent.setClassName(chosen.activityInfo.packageName, chosen.activityInfo.name)
                context.startActivity(viewIntent)
                return true
            }

            // Final fallback if PackageManager filtered everything out
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot launch installer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openWithChooser(context: Context, item: FileItem) {
        val file = item.file
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = getSafeContentUri(context, file) ?: run {
                Toast.makeText(context, "Unable to access file securely", Toast.LENGTH_SHORT).show()
                return
            }

            val mimeType = item.mimeType
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val chooser = Intent.createChooser(intent, "Open \"${item.name}\" with")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: android.content.ActivityNotFoundException) {
            // Try generic fallback if specific mime type failed
            tryGenericFallback(context, item)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryGenericFallback(context: Context, item: FileItem) {
        try {
            val uri = getSafeContentUri(context, item.file) ?: run {
                Toast.makeText(context, "Cannot generate secure URI for file", Toast.LENGTH_SHORT).show()
                return
            }
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(fallbackIntent, "Open \"${item.name}\" with")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWithSpecificApp(context: Context, item: FileItem, packageName: String, activityName: String) {
        try {
            val uri = getSafeContentUri(context, item.file) ?: run {
                openWithChooser(context, item)
                return
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                setClassName(packageName, activityName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to chooser
            openWithChooser(context, item)
        }
    }

    fun queryIntentApps(context: Context, item: FileItem): List<ResolveInfo> {
        return try {
            val uri = getSafeContentUri(context, item.file) ?: return emptyList()

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun shareFile(context: Context, item: FileItem) {
        val file = item.file
        if (!file.exists()) return
        try {
            val uri = getSafeContentUri(context, file) ?: run {
                Toast.makeText(context, "Cannot prepare file for sharing", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(intent, "Share \"${item.name}\"")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMultipleFiles(context: Context, items: List<FileItem>) {
        val existingFiles = items.map { it.file }.filter { it.exists() }
        if (existingFiles.isEmpty()) return
        try {
            val uris = ArrayList<Uri>()
            for (f in existingFiles) {
                getSafeContentUri(context, f)?.let { uris.add(it) }
            }
            if (uris.isEmpty()) {
                Toast.makeText(context, "Cannot prepare files for sharing", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(intent, "Share ${existingFiles.size} files")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share files: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

