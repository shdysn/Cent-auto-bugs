package com.ct.explorer.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.ct.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference

sealed class InstallSessionEvent {
    data class PendingUserAction(
        val sessionId: Int,
        val packageName: String?,
        val confirmIntent: Intent
    ) : InstallSessionEvent()

    data class Success(
        val sessionId: Int,
        val packageName: String?
    ) : InstallSessionEvent()

    data class Cancelled(
        val sessionId: Int,
        val packageName: String?
    ) : InstallSessionEvent()

    data class Failed(
        val sessionId: Int,
        val packageName: String?,
        val reason: String
    ) : InstallSessionEvent()
}

object InstallerStatusBus {
    private val _events = MutableSharedFlow<InstallSessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<InstallSessionEvent> = _events.asSharedFlow()

    @Volatile
    private var foregroundActivityRef: WeakReference<Activity>? = null

    fun attachActivity(activity: Activity) {
        foregroundActivityRef = WeakReference(activity)
    }

    fun detachActivity(activity: Activity) {
        if (foregroundActivityRef?.get() === activity) {
            foregroundActivityRef = null
        }
    }

    fun emitEvent(event: InstallSessionEvent) {
        _events.tryEmit(event)
    }

    fun launchConfirmationIntent(context: Context, confirmIntent: Intent): Boolean {
        val fgActivity = foregroundActivityRef?.get()
        return try {
            if (fgActivity != null && !fgActivity.isFinishing && !fgActivity.isDestroyed) {
                fgActivity.startActivity(confirmIntent)
                true
            } else {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmIntent)
                true
            }
        } catch (e: Exception) {
            try {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun formatFailureReason(status: Int, rawMessage: String?): String {
        val baseReason = when (status) {
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                "Installation blocked by device security policy or Play Protect."
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Conflicting package signature or version downgrade. Uninstall the existing app first."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "This APK is incompatible with your device's Android OS version or CPU architecture."
            PackageInstaller.STATUS_FAILURE_INVALID ->
                "The APK package is invalid, corrupted, or missing required split APKs."
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                "Insufficient free storage space on device to install this application."
            else -> "Installation failed"
        }
        return if (!rawMessage.isNullOrBlank() && !baseReason.contains(rawMessage, ignoreCase = true)) {
            "$baseReason ($rawMessage)"
        } else {
            baseReason
        }
    }
}

class PackageInstallerStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != InAppPackageInstallerHelper.ACTION_INSTALL_COMMIT) return

        val sessionId = intent.getIntExtra(
            PackageInstaller.EXTRA_SESSION_ID,
            intent.getIntExtra("session_id", -1)
        )
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra("package_name")
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    InstallerStatusBus.emitEvent(
                        InstallSessionEvent.PendingUserAction(
                            sessionId = sessionId,
                            packageName = packageName,
                            confirmIntent = confirmIntent
                        )
                    )
                    InstallerStatusBus.launchConfirmationIntent(context, confirmIntent)
                } else {
                    InstallerStatusBus.emitEvent(
                        InstallSessionEvent.Failed(
                            sessionId = sessionId,
                            packageName = packageName,
                            reason = "System installer confirmation prompt unavailable"
                        )
                    )
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                InstallerStatusBus.emitEvent(
                    InstallSessionEvent.Success(
                        sessionId = sessionId,
                        packageName = packageName
                    )
                )
                Toast.makeText(context, "App installed successfully!", Toast.LENGTH_SHORT).show()
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                InstallerStatusBus.emitEvent(
                    InstallSessionEvent.Cancelled(
                        sessionId = sessionId,
                        packageName = packageName
                    )
                )
            }
            else -> {
                val readable = InstallerStatusBus.formatFailureReason(status, statusMessage)
                InstallerStatusBus.emitEvent(
                    InstallSessionEvent.Failed(
                        sessionId = sessionId,
                        packageName = packageName,
                        reason = readable
                    )
                )
            }
        }
    }
}

object InAppPackageInstallerHelper {

    const val ACTION_INSTALL_COMMIT = "com.ct.explorer.ACTION_INSTALL_COMMIT"

    fun isValidPackageName(pkg: String?): Boolean {
        if (pkg.isNullOrBlank() || pkg.equals("Unknown", ignoreCase = true)) return false
        return pkg.contains(".") && pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$"))
    }

    fun createCommitPendingIntent(
        context: Context,
        sessionId: Int,
        packageName: String?
    ): PendingIntent {
        val intent = Intent(context, PackageInstallerStatusReceiver::class.java).apply {
            action = ACTION_INSTALL_COMMIT
            setPackage(context.packageName)
            if (!packageName.isNullOrBlank()) {
                putExtra("package_name", packageName)
            }
            putExtra("session_id", sessionId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    /**
     * Installs an APK (or XAPK/APKS bundle) using Android's native PackageInstaller Session API.
     * Streams APK bytes directly in-app, providing live progress feedback and firing the
     * system confirmation dialog via PackageInstallerStatusReceiver.
     */
    suspend fun installApkSession(
        context: Context,
        apkFile: File,
        packageName: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || !apkFile.canRead() || apkFile.length() == 0L) {
            return@withContext Result.failure(Exception("APK file is missing or empty (0 bytes)"))
        }

        if (!FileOpener.canInstallUnknownApps(context)) {
            withContext(Dispatchers.Main) {
                FileOpener.requestInstallUnknownAppsPermission(context)
            }
            return@withContext Result.failure(
                SecurityException("Please allow 'Install unknown apps' permission for Cent File Manager in Settings, then try again.")
            )
        }

        val ext = apkFile.extension.lowercase()
        if (ext == "xapk" || ext == "apks") {
            val parseRes = XapkInstaller.parseXapk(apkFile, context)
            val info = parseRes.getOrElse { err ->
                return@withContext Result.failure(err)
            }
            return@withContext XapkInstaller.installXapk(context, info, onProgress)
        }

        var session: PackageInstaller.Session? = null
        try {
            onProgress(0.05f, "Initializing package installer session...")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (isValidPackageName(packageName)) {
                try {
                    params.setAppPackageName(packageName)
                } catch (_: Exception) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                } catch (_: Exception) {}
            }

            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            val totalSize = apkFile.length().coerceAtLeast(1L)
            onProgress(0.12f, "Staging package (${FileItem.formatBytes(totalSize)})...")

            session.openWrite("base.apk", 0, totalSize).use { sessionOut ->
                FileInputStream(apkFile).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        sessionOut.write(buffer, 0, count)
                        bytesCopied += count
                        val progress = (0.12f + (0.78f * bytesCopied / totalSize)).coerceIn(0.12f, 0.90f)
                        onProgress(progress, "Staging: ${(progress * 100).toInt()}%")
                    }
                }
                session.fsync(sessionOut)
            }

            onProgress(0.95f, "Launching system confirmation prompt...")

            val pendingIntent = createCommitPendingIntent(context, sessionId, packageName)
            session.commit(pendingIntent.intentSender)
            session.close()
            session = null

            onProgress(1.0f, "Waiting for user confirmation...")
            Result.success(true)
        } catch (e: Exception) {
            try {
                session?.abandon()
            } catch (_: Exception) {}

            // Fallback to direct system package installer if session API fails
            try {
                val fallbackLaunched = withContext(Dispatchers.Main) {
                    FileOpener.installApk(context, apkFile)
                }
                if (fallbackLaunched) {
                    Result.success(true)
                } else {
                    Result.failure(e)
                }
            } catch (_: Exception) {
                Result.failure(e)
            }
        }
    }
}
