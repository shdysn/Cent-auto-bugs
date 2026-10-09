package com.ct.explorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.components.ApkInstallDialog
import com.ct.explorer.ui.components.ChecksumDialog
import com.ct.explorer.ui.components.CtFullAudioPlayerSheet
import com.ct.explorer.ui.components.CtMiniAudioBar
import com.ct.explorer.ui.screens.*
import com.ct.explorer.ui.theme.CentExplorerTheme
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.ui.viewmodel.Screen
import com.ct.explorer.utils.FileOpener

class MainActivity : ComponentActivity() {

    private val viewModel: ExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.ct.explorer.utils.InstallerStatusBus.attachActivity(this)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        // Self-Healing Sentinel: Run initial background audit and clean orphan temp files
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.appHealthManager.runFullDiagnosticsAndAutoRepair()
        }

        setContent {
            val isAmoled by viewModel.isAmoledMode.collectAsStateWithLifecycle()
            CentExplorerTheme(amoledMode = isAmoled) {
                CtMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.ct.explorer.utils.InstallerStatusBus.attachActivity(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.lockVault()
    }

    override fun onDestroy() {
        com.ct.explorer.utils.InstallerStatusBus.detachActivity(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.getBooleanExtra(FileOpener.EXTRA_FROM_INTERNAL_INSTALLER, false)) {
            return
        }

        // 0. Check In-App PackageInstaller Session Commit Callback (APK / Split XAPK / APKS)
        if (intent.action == "com.ct.explorer.ACTION_INSTALL_COMMIT") {
            val status = intent.getIntExtra(
                android.content.pm.PackageInstaller.EXTRA_STATUS,
                android.content.pm.PackageInstaller.STATUS_FAILURE
            )
            val statusMessage = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)
            when (status) {
                android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    if (confirmIntent != null) {
                        try {
                            startActivity(confirmIntent)
                        } catch (e: Exception) {
                            viewModel.showMessage("Cannot launch installer prompt: ${e.localizedMessage}")
                        }
                    }
                }
                android.content.pm.PackageInstaller.STATUS_SUCCESS -> {
                    viewModel.showMessage("Package installed successfully!")
                    viewModel.loadApps()
                    viewModel.loadStorageApks()
                    viewModel.loadAppBackups()
                }
                android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    viewModel.showMessage("Installation cancelled by user")
                }
                else -> {
                    val readable = com.ct.explorer.utils.InstallerStatusBus.formatFailureReason(status, statusMessage)
                    viewModel.showMessage(readable)
                }
            }
            return
        }

        // 0.5. Check Cloud OAuth 2.0 Web Redirect (com.pkstudio.ctexplorer.app:/... or ctexplorer://oauth-callback)
        val dataUri = intent.data
        if (dataUri != null && (dataUri.scheme == "com.pkstudio.ctexplorer.app" || dataUri.scheme == "ctexplorer")) {
            val token = com.ct.explorer.utils.CloudOAuthHelper.extractToken(dataUri)
            val code = com.ct.explorer.utils.CloudOAuthHelper.extractCode(dataUri)
            if (!token.isNullOrBlank()) {
                viewModel.connectGoogleDriveWithOAuthToken(token)
            } else if (!code.isNullOrBlank()) {
                viewModel.showMessage("Exchanging authorization code with Google...")
                lifecycleScope.launch {
                    val exchangeRes = com.ct.explorer.utils.CloudOAuthHelper.exchangeCodeForToken(code)
                    exchangeRes.fold(
                        onSuccess = { exchangedToken ->
                            viewModel.connectGoogleDriveWithOAuthToken(exchangedToken)
                        },
                        onFailure = { err ->
                            viewModel.showMessage("Google Token Exchange error: ${err.localizedMessage}")
                        }
                    )
                }
            } else {
                val err = com.ct.explorer.utils.CloudOAuthHelper.extractError(dataUri)
                viewModel.showMessage("OAuth Response: ${err ?: "No authorization token received"}")
            }
            return
        }

        // 1. Check Home Screen Storage Widget Actions
        val widgetTarget = intent.getStringExtra(com.ct.explorer.widget.CtStorageWidgetProvider.EXTRA_WIDGET_TARGET)
        if (!widgetTarget.isNullOrBlank()) {
            when (widgetTarget) {
                com.ct.explorer.widget.CtStorageWidgetProvider.TARGET_CLEANER -> viewModel.openCleaner()
                com.ct.explorer.widget.CtStorageWidgetProvider.TARGET_FAST_SHARE -> viewModel.openFastShare()
                com.ct.explorer.widget.CtStorageWidgetProvider.TARGET_STORAGE -> {
                    viewModel.selectTab(com.ct.explorer.ui.components.CtTab.STORAGE)
                    viewModel.navigateToScreen(Screen.MAIN)
                }
            }
            return
        }

        // 2. Check Home Screen Pinned Folder/File Shortcut
        val shortcutPath = intent.getStringExtra(com.ct.explorer.utils.ShortcutHelper.EXTRA_SHORTCUT_PATH)
        if (!shortcutPath.isNullOrBlank()) {
            val targetFile = java.io.File(shortcutPath)
            if (targetFile.exists()) {
                if (targetFile.isDirectory) {
                    viewModel.selectTab(com.ct.explorer.ui.components.CtTab.STORAGE)
                    viewModel.navigateToScreen(Screen.MAIN)
                    viewModel.loadDirectory(targetFile, addToHistory = true)
                } else {
                    viewModel.openFileSmart(FileItem(targetFile), listOf(FileItem(targetFile)))
                }
            } else {
                viewModel.showMessage("Pinned item no longer exists")
            }
            return
        }

        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val mimeType = intent.type ?: contentResolver.getType(uri) ?: ""
            val displayName = getFileNameFromUri(uri)

            val lowerName = displayName.lowercase()
            val lowerMime = mimeType.lowercase()

            when {
                // Video Files (When opened from any other file manager, gallery, WhatsApp, browser)
                lowerMime.startsWith("video/") ||
                lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") ||
                lowerName.endsWith(".mov") || lowerName.endsWith(".3gp") || lowerName.endsWith(".flv") ||
                lowerName.endsWith(".webm") || lowerName.endsWith(".ts") || lowerName.endsWith(".m4v") ||
                lowerName.endsWith(".wmv") || lowerName.endsWith(".rmvb") || lowerName.endsWith(".mpeg") -> {
                    if (uri.scheme == "file" && uri.path != null && java.io.File(uri.path!!).exists()) {
                        viewModel.openVideoPlayer(java.io.File(uri.path!!))
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val safeName = java.io.File(displayName).name.ifEmpty { "video_${System.currentTimeMillis()}.mp4" }
                                val videoFile = java.io.File(cacheDir, safeName).apply {
                                    parentFile?.mkdirs()
                                    contentResolver.openInputStream(uri)?.use { input ->
                                        outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (videoFile.exists() && videoFile.length() > 0L) {
                                        viewModel.openVideoPlayer(videoFile)
                                    } else {
                                        viewModel.showMessage("Unable to load video")
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    viewModel.showMessage("Error opening video: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                }
                // APK, XAPK, APKS Installation Packages (In-App Installer)
                lowerName.endsWith(".apk") || lowerName.endsWith(".xapk") || lowerName.endsWith(".apks") || lowerMime.contains("android.package-archive") -> {
                    if (uri.scheme == "file" && uri.path != null && java.io.File(uri.path!!).canRead()) {
                        val localFile = java.io.File(uri.path!!)
                        if (lowerName.endsWith(".xapk") || lowerName.endsWith(".apks")) {
                            viewModel.openXapkFile(localFile)
                        } else {
                            viewModel.openApkInstallDialog(localFile)
                        }
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val safeName = java.io.File(displayName).name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "pkg_${System.currentTimeMillis()}.apk" }
                                val cacheFile = java.io.File(cacheDir, "view_${System.currentTimeMillis()}_$safeName").apply {
                                    parentFile?.mkdirs()
                                    contentResolver.openInputStream(uri)?.use { input ->
                                        outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (!cacheFile.exists() || cacheFile.length() == 0L) {
                                        viewModel.showMessage("Package file is empty or unreadable")
                                    } else if (lowerName.endsWith(".xapk") || lowerName.endsWith(".apks")) {
                                        viewModel.openXapkFile(cacheFile)
                                    } else {
                                        viewModel.openApkInstallDialog(cacheFile)
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    viewModel.showMessage("Failed to open package: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                }
                // PDF Documents
                lowerName.endsWith(".pdf") || lowerMime.contains("pdf") -> {
                    if (uri.scheme == "file" && uri.path != null && java.io.File(uri.path!!).exists()) {
                        viewModel.openPdfFile(java.io.File(uri.path!!))
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val safeName = java.io.File(displayName).name.ifEmpty { "document_${System.currentTimeMillis()}.pdf" }
                                val cacheFile = java.io.File(cacheDir, safeName).apply {
                                    parentFile?.mkdirs()
                                    contentResolver.openInputStream(uri)?.use { input ->
                                        outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (cacheFile.exists()) {
                                        viewModel.openPdfFile(cacheFile)
                                    } else {
                                        viewModel.showMessage("Unable to load document")
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    viewModel.showMessage("Failed to open PDF: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                }
                // Word Documents (.docx, .doc)
                lowerName.endsWith(".docx") || lowerName.endsWith(".doc") || lowerMime.contains("wordprocessingml") || lowerMime.contains("msword") -> {
                    if (uri.scheme == "file" && uri.path != null && java.io.File(uri.path!!).exists()) {
                        viewModel.openWordFile(java.io.File(uri.path!!))
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val safeName = java.io.File(displayName).name.ifEmpty { "doc_${System.currentTimeMillis()}.docx" }
                                val cacheFile = java.io.File(cacheDir, safeName).apply {
                                    parentFile?.mkdirs()
                                    contentResolver.openInputStream(uri)?.use { input ->
                                        outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (cacheFile.exists()) {
                                        viewModel.openWordFile(cacheFile)
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    viewModel.showMessage("Failed to open Word doc: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                }
                // Excel Spreadsheets (.xlsx, .xls, .csv)
                lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".csv") || lowerMime.contains("spreadsheetml") || lowerMime.contains("ms-excel") || lowerMime.contains("csv") -> {
                    if (uri.scheme == "file" && uri.path != null && java.io.File(uri.path!!).exists()) {
                        viewModel.openExcelFile(java.io.File(uri.path!!))
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val safeName = java.io.File(displayName).name.ifEmpty { "sheet_${System.currentTimeMillis()}.xlsx" }
                                val cacheFile = java.io.File(cacheDir, safeName).apply {
                                    parentFile?.mkdirs()
                                    contentResolver.openInputStream(uri)?.use { input ->
                                        outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (cacheFile.exists()) {
                                        viewModel.openExcelFile(cacheFile)
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    viewModel.showMessage("Failed to open Spreadsheet: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                }
                // Multi-Format Archives (ZIP, 7Z, RAR, TAR, GZ, TGZ)
                lowerName.endsWith(".zip") || lowerName.endsWith(".7z") || lowerName.endsWith(".rar") ||
                lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerName.endsWith(".tgz") ||
                lowerMime.contains("zip") || lowerMime.contains("tar") || lowerMime.contains("rar") -> {
                    if (uri.scheme == "file" && uri.path != null && java.io.File(uri.path!!).exists()) {
                        viewModel.openZipViewer(java.io.File(uri.path!!))
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val safeName = java.io.File(displayName).name.ifEmpty { "archive_${System.currentTimeMillis()}.zip" }
                                val cacheFile = java.io.File(cacheDir, safeName).apply {
                                    parentFile?.mkdirs()
                                    contentResolver.openInputStream(uri)?.use { input ->
                                        outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (cacheFile.exists()) {
                                        viewModel.openZipViewer(cacheFile)
                                    } else {
                                        viewModel.openZipFromUri(uri, displayName)
                                    }
                                }
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main) {
                                    viewModel.openZipFromUri(uri, displayName)
                                }
                            }
                        }
                    }
                }
                // HTML Files
                lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerMime.contains("html") -> {
                    viewModel.openTextFromUri(uri, displayName)
                }
                // Text, JSON, XML, Logs, Markdown, CSV, Code, etc.
                else -> {
                    viewModel.openTextFromUri(uri, displayName)
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            name = cursor.getString(idx)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query error
            }
        }
        return name ?: uri.lastPathSegment ?: "file.txt"
    }
}

@Composable
fun MiMainApp(viewModel: ExplorerViewModel) = CtMainApp(viewModel)

@Composable
fun CtMainApp(viewModel: ExplorerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Build permissions list depending on Android version
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    // Permission launcher for standard runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.onStoragePermissionGranted()
        }
    }

    // Request permissions on launch if not granted (performed off Main Thread to avoid binder IPC stall)
    LaunchedEffect(Unit) {
        val hasMissing = withContext(Dispatchers.IO) {
            permissionsToRequest.any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        }
        if (hasMissing) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Track if storage permission was already granted to avoid resetting directory on every ON_RESUME
    var hadStorageAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Lifecycle observer: when returning from system Settings, only refresh if permission was freshly granted
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
                if (hasAllFilesAccess && !hadStorageAccess) {
                    hadStorageAccess = true
                    viewModel.onStoragePermissionGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        com.ct.explorer.core.events.AppEventBus.events.collect { event ->
            when (event) {
                is com.ct.explorer.core.events.AppEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        com.ct.explorer.utils.InstallerStatusBus.events.collect { event ->
            when (event) {
                is com.ct.explorer.utils.InstallSessionEvent.Success -> {
                    viewModel.loadApps()
                    viewModel.loadStorageApks()
                    viewModel.loadAppBackups()
                    viewModel.showMessage("Package installed successfully!")
                }
                is com.ct.explorer.utils.InstallSessionEvent.Failed -> {
                    viewModel.showMessage(event.reason)
                }
                else -> {}
            }
        }
    }

    var apkChecksumTarget by remember { mutableStateOf<FileItem?>(null) }

    BackHandler(enabled = true) {
        val handled = viewModel.handleBackPress()
        if (!handled) {
            (context as? android.app.Activity)?.finish()
        }
    }

    val audioPlayerState by viewModel.audioPlayerState.collectAsStateWithLifecycle()
    val apkInstallTarget by viewModel.apkInstallTarget.collectAsStateWithLifecycle()
    val isVideoScreen = currentScreen == Screen.VIDEO_PLAYER
    val isFullscreenDocumentScreen = currentScreen == Screen.PDF_VIEWER ||
        currentScreen == Screen.WORD_VIEWER ||
        currentScreen == Screen.EXCEL_VIEWER
    val isFullscreenScreen = isVideoScreen || isFullscreenDocumentScreen

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (isVideoScreen) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
        contentWindowInsets = if (isFullscreenScreen) androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (audioPlayerState.isVisible && !isFullscreenScreen) {
                CtMiniAudioBar(
                    state = audioPlayerState,
                    onExpand = { viewModel.toggleAudioExpanded() },
                    onPlayPause = { viewModel.toggleAudioPlayPause() },
                    onNext = { viewModel.playNextAudio() },
                    onClose = { viewModel.closeAudioPlayer() }
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentScreen,
            modifier = Modifier
                .fillMaxSize()
                .then(if (isFullscreenScreen) Modifier else Modifier.padding(innerPadding)),
            label = "CentScreenTransition"
        ) { screen ->
            when (screen) {
                Screen.MAIN -> MainScreen(viewModel = viewModel)
                Screen.CLEANER -> CleanerScreen(viewModel = viewModel)
                Screen.FTP_SERVER -> FtpServerScreen(viewModel = viewModel)
                Screen.CATEGORY_VIEW -> CategoryViewScreen(viewModel = viewModel)
                Screen.TEXT_EDITOR -> TextEditorScreen(viewModel = viewModel)
                Screen.IMAGE_VIEWER -> ImageViewerScreen(viewModel = viewModel)
                Screen.APP_MANAGER -> AppManagerScreen(viewModel = viewModel)
                Screen.VAULT -> VaultScreen(viewModel = viewModel)
                Screen.DUPLICATES -> DuplicateFinderScreen(viewModel = viewModel)
                Screen.STORAGE_ANALYZER -> StorageAnalyzerScreen(viewModel = viewModel)
                Screen.ZIP_VIEWER -> ZipViewerScreen(viewModel = viewModel)
                Screen.TRASH -> TrashScreen(viewModel = viewModel)
                Screen.PDF_VIEWER -> PdfViewerScreen(viewModel = viewModel)
                Screen.VIDEO_PLAYER -> VideoPlayerScreen(viewModel = viewModel)
                Screen.NETWORK_DRIVES -> NetworkDrivesScreen(viewModel = viewModel)
                Screen.FAST_SHARE -> FastShareScreen(viewModel = viewModel)
                Screen.SOCIAL_HUB -> SocialHubScreen(viewModel = viewModel)
                Screen.WEB_SHARE -> WebShareScreen(viewModel = viewModel)
                Screen.FILE_SHREDDER -> FileShredderScreen(viewModel = viewModel)
                Screen.SMART_COLLECTIONS -> SmartCollectionsScreen(viewModel = viewModel)
                Screen.TIME_MACHINE -> TimeMachineScreen(viewModel = viewModel)
                Screen.APP_INSTALLER -> AppInstallerScreen(viewModel = viewModel)
                Screen.DIAGNOSTICS -> DiagnosticScreen(viewModel = viewModel)
                Screen.WORD_VIEWER -> WordViewerScreen(viewModel = viewModel)
                Screen.EXCEL_VIEWER -> ExcelViewerScreen(viewModel = viewModel)
            }
        }

        if (audioPlayerState.isExpanded) {
            CtFullAudioPlayerSheet(
                state = audioPlayerState,
                viewModel = viewModel,
                onDismiss = { viewModel.toggleAudioExpanded() }
            )
        }

        apkInstallTarget?.let { apk ->
            ApkInstallDialog(
                apk = apk,
                onDismiss = { viewModel.closeApkInstallDialog() },
                onInstall = {
                    FileOpener.installApk(context, apk.file)
                },
                onShare = {
                    FileOpener.shareFile(context, FileItem(apk.file))
                },
                onFastShare = {
                    viewModel.sendBackupViaFastShare(apk)
                },
                onChecksum = {
                    apkChecksumTarget = FileItem(apk.file)
                }
            )
        }

        apkChecksumTarget?.let { item ->
            ChecksumDialog(
                item = item,
                onDismiss = { apkChecksumTarget = null }
            )
        }

        val xapkTarget by viewModel.xapkInstallTarget.collectAsStateWithLifecycle()
        val isInstallingXapk by viewModel.isInstallingXapk.collectAsStateWithLifecycle()
        val xapkProgress by viewModel.xapkInstallProgress.collectAsStateWithLifecycle()

        xapkTarget?.let { xapk ->
            com.ct.explorer.ui.components.XapkInstallDialog(
                xapkInfo = xapk,
                isInstalling = isInstallingXapk,
                progressText = xapkProgress,
                onDismiss = { viewModel.closeXapkDialog() },
                onInstall = { viewModel.installActiveXapk() }
            )
        }
    }
}
