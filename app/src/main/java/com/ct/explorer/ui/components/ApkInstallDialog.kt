package com.ct.explorer.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ct.explorer.data.model.ApkFileItem
import com.ct.explorer.ui.theme.MiGreen
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.InAppPackageInstallerHelper
import com.ct.explorer.utils.InstallSessionEvent
import com.ct.explorer.utils.InstallerStatusBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class InAppInstallProgress {
    IDLE,
    STAGING,
    PROMPTING,
    COMPLETED,
    FAILED
}

private fun queryInstalledPackage(context: Context, packageName: String): PackageInfo? {
    if (packageName.isBlank() || packageName == "Unknown") return null
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (_: Exception) {
        null
    }
}

private fun getPkgVersionCode(pkg: PackageInfo?): Long {
    if (pkg == null) return 0L
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkg.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pkg.versionCode.toLong()
    }
}

/**
 * Clean, lightweight Popup Installer that ONLY installs the APK without redundant technical info.
 */
@Composable
fun ApkInstallDialog(
    apk: ApkFileItem,
    onDismiss: () -> Unit,
    onInstall: () -> Unit = {},
    onShare: () -> Unit = {},
    onFastShare: () -> Unit = {},
    onChecksum: () -> Unit = {},
    autoStartInstall: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var installProgress by remember(apk.path) { mutableStateOf(InAppInstallProgress.IDLE) }
    var errorMessage by remember(apk.path) { mutableStateOf<String?>(null) }

    var canInstallUnknown by remember {
        mutableStateOf(FileOpener.canInstallUnknownApps(context))
    }

    var installedPkgInfo by remember(apk.packageName) {
        mutableStateOf(queryInstalledPackage(context, apk.packageName))
    }

    val isAppInstalledOnDevice = installedPkgInfo != null

    fun startInstallation() {
        if (!FileOpener.canInstallUnknownApps(context)) {
            canInstallUnknown = false
            FileOpener.requestInstallUnknownAppsPermission(context)
            return
        }
        errorMessage = null
        installProgress = InAppInstallProgress.STAGING

        scope.launch {
            val res = InAppPackageInstallerHelper.installApkSession(
                context = context,
                apkFile = apk.file,
                packageName = apk.packageName
            ) { _, _ -> }

            res.fold(
                onSuccess = {
                    if (installProgress == InAppInstallProgress.STAGING) {
                        installProgress = InAppInstallProgress.PROMPTING
                    }
                },
                onFailure = { err ->
                    try {
                        val fallbackSuccess = FileOpener.installApk(context, apk.file)
                        if (fallbackSuccess) {
                            installProgress = InAppInstallProgress.PROMPTING
                        } else {
                            installProgress = InAppInstallProgress.FAILED
                            errorMessage = err.localizedMessage ?: "Installation failed"
                        }
                    } catch (e: Exception) {
                        installProgress = InAppInstallProgress.FAILED
                        errorMessage = err.localizedMessage ?: "Installation failed"
                    }
                }
            )
        }
    }

    LaunchedEffect(apk.path) {
        if (autoStartInstall) {
            startInstallation()
        }
    }

    LaunchedEffect(apk.path) {
        InstallerStatusBus.events.collectLatest { event ->
            when (event) {
                is InstallSessionEvent.PendingUserAction -> {
                    installProgress = InAppInstallProgress.PROMPTING
                }
                is InstallSessionEvent.Success -> {
                    installedPkgInfo = queryInstalledPackage(context, apk.packageName)
                    installProgress = InAppInstallProgress.COMPLETED
                    errorMessage = null
                }
                is InstallSessionEvent.Cancelled -> {
                    installProgress = InAppInstallProgress.IDLE
                    errorMessage = "Installation cancelled."
                }
                is InstallSessionEvent.Failed -> {
                    installProgress = InAppInstallProgress.FAILED
                    errorMessage = event.reason
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, apk.packageName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstallUnknown = FileOpener.canInstallUnknownApps(context)
                val updatedPkg = queryInstalledPackage(context, apk.packageName)
                val updatedVerCode = getPkgVersionCode(updatedPkg)
                installedPkgInfo = updatedPkg

                if (installProgress == InAppInstallProgress.PROMPTING && updatedPkg != null) {
                    if (apk.versionCode <= 0L || updatedVerCode == apk.versionCode) {
                        installProgress = InAppInstallProgress.COMPLETED
                        errorMessage = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = installProgress != InAppInstallProgress.STAGING)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (apk.icon != null) {
                        Image(
                            bitmap = apk.icon.toBitmap(96, 96).asImageBitmap(),
                            contentDescription = apk.name,
                            modifier = Modifier.size(56.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MiGreen,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Name
                Text(
                    text = apk.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Core prompt / Status
                when (installProgress) {
                    InAppInstallProgress.STAGING, InAppInstallProgress.PROMPTING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = CtOrange,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Installing application...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    InAppInstallProgress.COMPLETED -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "App installed successfully",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    InAppInstallProgress.FAILED -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMessage ?: "Installation failed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFEF4444),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    InAppInstallProgress.IDLE -> {
                        Text(
                            text = if (isAppInstalledOnDevice) {
                                "Do you want to install an update to this existing application?"
                            } else {
                                "Do you want to install this application?"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        if (!canInstallUnknown) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Permission required to install apps from storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CtOrange,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (installProgress) {
                        InAppInstallProgress.COMPLETED -> {
                            TextButton(onClick = onDismiss) {
                                Text("Done", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(apk.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MiGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Open")
                            }
                        }

                        InAppInstallProgress.STAGING, InAppInstallProgress.PROMPTING -> {
                            TextButton(onClick = onDismiss) {
                                Text("Dismiss")
                            }
                        }

                        InAppInstallProgress.FAILED -> {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { startInstallation() },
                                colors = ButtonDefaults.buttonColors(containerColor = CtOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retry")
                            }
                        }

                        InAppInstallProgress.IDLE -> {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (!canInstallUnknown) {
                                Button(
                                    onClick = { FileOpener.requestInstallUnknownAppsPermission(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Allow Permission")
                                }
                            } else {
                                Button(
                                    onClick = { startInstallation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (isAppInstalledOnDevice) "Update" else "Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
