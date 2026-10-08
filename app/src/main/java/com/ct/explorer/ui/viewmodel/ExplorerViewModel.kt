package com.ct.explorer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ct.explorer.data.model.*
import com.ct.explorer.data.repository.*
import com.ct.explorer.ui.components.CtTab
import com.ct.explorer.utils.FileOpener
import com.ct.explorer.utils.ArchiveHelper
import com.ct.explorer.utils.XapkInstaller
import com.ct.explorer.utils.XapkInfo
import com.ct.explorer.utils.BiometricHelper
import com.ct.explorer.utils.webshare.WebShareServer
import com.ct.explorer.utils.webshare.WebShareState
import com.ct.explorer.utils.shredder.FileShredderHelper
import com.ct.explorer.utils.shredder.ShredMethod
import com.ct.explorer.utils.shredder.ShredProgress
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import com.ct.explorer.core.navigation.NavigationManager
import com.ct.explorer.core.events.AppEventBus
import com.ct.explorer.features.vault.VaultViewModel
import com.ct.explorer.features.network.NetworkServerViewModel
import com.ct.explorer.features.cleaner.CleanerViewModel
import com.ct.explorer.features.media.AudioPlayerViewModel
import com.ct.explorer.features.editor.TextEditorViewModel
import com.ct.explorer.features.analyzer.StorageAnalyzerViewModel
import com.ct.explorer.features.browser.FileBrowserViewModel

typealias Screen = com.ct.explorer.core.navigation.Screen
typealias FtpServerState = com.ct.explorer.features.network.FtpServerState

data class PdfViewerState(
    val file: File? = null,
    val title: String = ""
)

data class VideoPlayerState(
    val file: File? = null,
    val title: String = "",
    val playlist: List<FileItem> = emptyList(),
    val currentIndex: Int = 0
)

data class AudioPlayerState(
    val currentFile: File? = null,
    val title: String = "",
    val artist: String = "Unknown Artist",
    val durationMs: Int = 0,
    val currentPositionMs: Int = 0,
    val isPlaying: Boolean = false,
    val isVisible: Boolean = false,
    val isExpanded: Boolean = false,
    val playlist: List<FileItem> = emptyList(),
    val currentIndex: Int = 0,
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false
)

data class ZipViewerState(
    val archiveInfo: ZipArchiveInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class StorageTabState(
    val currentDir: File,
    val backStack: List<File> = emptyList(),
    val forwardStack: List<File> = emptyList(),
    val items: List<FileItem> = emptyList(),
    val selectedItems: Set<FileItem> = emptySet(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortType: SortType = SortType.NAME_ASC,
    val foldersOnTop: Boolean = true,
    val searchQuery: String = "",
    val showHidden: Boolean = false,
    val filterOnlyBigFiles: Boolean = false,
    val isLoading: Boolean = false
) {
    val isSelectionMode: Boolean get() = selectedItems.isNotEmpty()

    val displayItems: List<FileItem>
        get() = if (filterOnlyBigFiles) {
            items.filter { it.effectiveSize >= 10L * 1024 * 1024 }
        } else {
            items
        }

    val bigItemsCount: Int
        get() = items.count { it.effectiveSize >= 10L * 1024 * 1024 }
}

data class TextEditorState(
    val file: File? = null,
    val sourceUri: android.net.Uri? = null,
    val title: String = "",
    val content: String = "",
    val originalContent: String = "",
    val wordWrap: Boolean = false,
    val isSaving: Boolean = false,
    val isHtmlMode: Boolean = false,
    val showHtmlPreview: Boolean = false,
    val isReadOnly: Boolean = false
) {
    val isModified: Boolean get() = content != originalContent
    val lineCount: Int get() = if (content.isEmpty()) 1 else content.lines().size
    val charCount: Int get() = content.length
}

data class ImageViewerState(
    val currentFile: File? = null,
    val imageList: List<FileItem> = emptyList(),
    val currentIndex: Int = 0
)

data class CategoryViewState(
    val category: FileCategory = FileCategory.IMAGE,
    val title: String = "",
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    val fileRepository = FileRepository(application.applicationContext)
    private val _appsRepository = lazy { AppsRepository(application.applicationContext) }
    val appsRepository get() = _appsRepository.value

    // Current Screen (Delegated to core NavigationManager)
    val currentScreen: StateFlow<Screen> get() = NavigationManager.currentScreen

    // Current Main Tab (Recent vs Storage)
    private val _selectedTab = MutableStateFlow(CtTab.STORAGE)
    val selectedTab: StateFlow<CtTab> = _selectedTab.asStateFlow()

    // Recent Files State
    private val _recentFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val recentFiles: StateFlow<List<FileItem>> = _recentFiles.asStateFlow()
    val isRecentLoading = MutableStateFlow(false)

    // Storage Tab State
    private val initialDir = fileRepository.rootStorageDirectory
    private val initialItems = fileRepository.getFastInitialRootItems()
    private val _storageState = MutableStateFlow(
        StorageTabState(
            currentDir = initialDir,
            items = initialItems,
            isLoading = false
        )
    )
    val storageState: StateFlow<StorageTabState> = _storageState.asStateFlow()

    // Storage capacity overview
    private val _storageSpace = MutableStateFlow(
        StorageSpace(totalBytes = 64L * 1024 * 1024 * 1024, freeBytes = 38L * 1024 * 1024 * 1024, usedBytes = 26L * 1024 * 1024 * 1024)
    )
    val storageSpace: StateFlow<StorageSpace> = _storageSpace.asStateFlow()

    // Clipboard
    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    // Feature ViewModels (Isolated Domain Delegation)
    val vaultViewModel by lazy { VaultViewModel(application) }
    val networkServerViewModel by lazy { NetworkServerViewModel(application) }
    val cleanerViewModel by lazy { CleanerViewModel(application) }
    val audioPlayerViewModel by lazy { AudioPlayerViewModel(application) }
    val textEditorViewModel by lazy { TextEditorViewModel(application) }
    val storageAnalyzerViewModel by lazy { StorageAnalyzerViewModel(application) }
    val fileBrowserViewModel by lazy { FileBrowserViewModel(application) }

    // Cleaner State (Delegated to CleanerViewModel)
    val cleanScan: StateFlow<CleanScanResult?> get() = cleanerViewModel.cleanScan
    val isCleaning: MutableStateFlow<Boolean> get() = cleanerViewModel.isCleaning
    val isCleanScanning: MutableStateFlow<Boolean> get() = cleanerViewModel.isCleanScanning
    val cleanedBytes: MutableStateFlow<Long?> get() = cleanerViewModel.cleanedBytes

    // FTP Server State (Delegated to NetworkServerViewModel)
    val ftpServerState: StateFlow<FtpServerState> get() = networkServerViewModel.ftpServerState

    // Text Editor State (Delegated to TextEditorViewModel)
    val textEditorState: StateFlow<TextEditorState> get() = textEditorViewModel.textEditorState

    // Image Viewer State
    private val _imageViewerState = MutableStateFlow(ImageViewerState())
    val imageViewerState: StateFlow<ImageViewerState> = _imageViewerState.asStateFlow()

    // Category View State
    private val _categoryViewState = MutableStateFlow(CategoryViewState())
    val categoryViewState: StateFlow<CategoryViewState> = _categoryViewState.asStateFlow()

    // App Manager State
    private val _installedApps = MutableStateFlow<List<AppInfoItem>>(emptyList())
    val installedApps: StateFlow<List<AppInfoItem>> = _installedApps.asStateFlow()
    val isAppsLoading = MutableStateFlow(false)
    val includeSystemApps = MutableStateFlow(false)
    val appsSearchQuery = MutableStateFlow("")

    private val _storageApks = MutableStateFlow<List<ApkFileItem>>(emptyList())
    val storageApks: StateFlow<List<ApkFileItem>> = _storageApks.asStateFlow()
    val isStorageApksLoading = MutableStateFlow(false)
    val apkScreenTab = MutableStateFlow(ApkTab.INSTALLED_APPS)
    val appBackupGroups = MutableStateFlow<List<AppBackupGroup>>(emptyList())
    val isBackupsLoading = MutableStateFlow(false)
    val selectedAppsForBatch = MutableStateFlow<Set<String>>(emptySet())
    val isBatchBackingUp = MutableStateFlow(false)
    val batchBackupProgress = MutableStateFlow("")
    private val _apkInstallTarget = MutableStateFlow<ApkFileItem?>(null)
    val apkInstallTarget: StateFlow<ApkFileItem?> = _apkInstallTarget.asStateFlow()

    // Debounce tracking for directory loading
    private var lastLoadedDirPath: String? = null
    private var lastLoadTimestamp: Long = 0L

    // Vault State (Delegated to VaultViewModel)
    val vaultRepository get() = vaultViewModel.vaultRepository
    val isVaultPinSet: MutableStateFlow<Boolean> get() = vaultViewModel.isVaultPinSet
    val isVaultUnlocked: MutableStateFlow<Boolean> get() = vaultViewModel.isVaultUnlocked
    val vaultFiles: StateFlow<List<FileItem>> get() = vaultViewModel.vaultFiles
    val isVaultLoading: MutableStateFlow<Boolean> get() = vaultViewModel.isVaultLoading

    // Duplicate Repository & State (Lazy)
    private val _duplicateRepository = lazy { DuplicateRepository(application) }
    val duplicateRepository get() = _duplicateRepository.value
    private val _duplicateScanResult = MutableStateFlow<DuplicateScanResult?>(null)
    val duplicateScanResult: StateFlow<DuplicateScanResult?> = _duplicateScanResult.asStateFlow()
    val isDuplicateScanning = MutableStateFlow(false)
    val selectedDuplicateFiles = MutableStateFlow<Set<FileItem>>(emptySet())

    // Storage Analyzer Repository & State (Delegated to StorageAnalyzerViewModel)
    private val _storageAnalyzerRepository = lazy { StorageAnalyzerRepository(application) }
    val storageAnalyzerRepository get() = _storageAnalyzerRepository.value
    val storageAnalysisResult: StateFlow<StorageAnalysisResult?> get() = storageAnalyzerViewModel.storageAnalysisResult
    val isStorageAnalyzing: MutableStateFlow<Boolean> get() = storageAnalyzerViewModel.isStorageAnalyzing

    // Zip Viewer & Compressor State (Lazy)
    private val _zipRepository = lazy { ZipRepository(application) }
    val zipRepository get() = _zipRepository.value
    private val _zipViewerState = MutableStateFlow(ZipViewerState())
    val zipViewerState: StateFlow<ZipViewerState> = _zipViewerState.asStateFlow()
    val isZipExtracting = MutableStateFlow(false)

    // AMOLED Pitch Black Mode State
    val isAmoledMode = MutableStateFlow(false)

    // App Health & Self-Healing Sentinel
    val appHealthManager = com.ct.explorer.core.guardian.AppHealthManager(application)
    val healthState = appHealthManager.healthState

    fun openDiagnosticsScreen() {
        com.ct.explorer.core.navigation.NavigationManager.navigateTo(com.ct.explorer.core.navigation.Screen.DIAGNOSTICS)
    }

    fun runSelfAuditAndRepair() {
        viewModelScope.launch {
            val report = appHealthManager.runFullDiagnosticsAndAutoRepair()
            showMessage("Self-Audit complete: Health Score ${report.healthScore}%")
        }
    }

    fun clearCrashHistory() {
        appHealthManager.clearCrashHistory()
        showMessage("Crash logs reset")
    }

    // Recycle Bin (Trash) (Lazy)
    private val _trashRepository = lazy { TrashRepository(application) }
    val trashRepository get() = _trashRepository.value
    private val _trashItems = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItems: StateFlow<List<TrashItem>> = _trashItems.asStateFlow()
    val isTrashLoading = MutableStateFlow(false)

    // Favorites / Pinned Folders (Lazy)
    private val _favoritesRepository = lazy { FavoritesRepository(application) }
    val favoritesRepository get() = _favoritesRepository.value
    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites.asStateFlow()

    // PDF Viewer State
    private val _pdfViewerState = MutableStateFlow(PdfViewerState())
    val pdfViewerState: StateFlow<PdfViewerState> = _pdfViewerState.asStateFlow()

    // Built-in Audio Player (Delegated to AudioPlayerViewModel)
    val audioPlayerState: StateFlow<AudioPlayerState> get() = audioPlayerViewModel.audioPlayerState

    // Built-in Video Player
    private val _videoPlayerState = MutableStateFlow(VideoPlayerState())
    val videoPlayerState: StateFlow<VideoPlayerState> = _videoPlayerState.asStateFlow()

    // Snackbar message
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // 1. Tags Repository & State (Lazy)
    private val _tagsRepository = lazy { TagsRepository(application) }
    val tagsRepository get() = _tagsRepository.value
    private val _fileTagsMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val fileTagsMap: StateFlow<Map<String, List<String>>> = _fileTagsMap.asStateFlow()
    val selectedTagFilter = MutableStateFlow<String?>(null)

    // 2. Network Drives (Cloud / SMB / WebDAV) (Lazy)
    private val _networkStorageRepository = lazy { NetworkStorageRepository(application) }
    val networkStorageRepository get() = _networkStorageRepository.value
    private val _networkDrives = MutableStateFlow<List<NetworkDrive>>(emptyList())
    val networkDrives: StateFlow<List<NetworkDrive>> = _networkDrives.asStateFlow()
    private val _activeNetworkDrive = MutableStateFlow<NetworkDrive?>(null)
    val activeNetworkDrive: StateFlow<NetworkDrive?> = _activeNetworkDrive.asStateFlow()
    private val _remoteFiles = MutableStateFlow<List<RemoteFileItem>>(emptyList())
    val remoteFiles: StateFlow<List<RemoteFileItem>> = _remoteFiles.asStateFlow()
    val currentRemotePath = MutableStateFlow("/")
    val isTestingNetworkDrive = MutableStateFlow(false)
    val isScanningLan = MutableStateFlow(false)

    // 3. Fast Share (Direct P2P Offline Wi-Fi Transfer) (Lazy)
    private val _fastShareRepository = lazy { FastShareRepository(application) }
    val fastShareRepository get() = _fastShareRepository.value
    private val _fastShareState = MutableStateFlow(FastShareState())
    val fastShareState: StateFlow<FastShareState> = _fastShareState.asStateFlow()

    // 4. Multi-Storage Volumes (Internal, SD Card, USB OTG)
    private val _storageVolumes = MutableStateFlow(
        listOf(
            StorageVolumeItem(
                id = "internal_storage",
                name = "Internal Storage",
                file = initialDir,
                type = VolumeType.INTERNAL,
                totalBytes = 64L * 1024 * 1024 * 1024,
                freeBytes = 38L * 1024 * 1024 * 1024,
                isPrimary = true
            )
        )
    )
    val storageVolumes: StateFlow<List<StorageVolumeItem>> = _storageVolumes.asStateFlow()
    val selectedVolume = MutableStateFlow<StorageVolumeItem?>(null)

    // 5. Dual-Pane Split Screen Mode
    val isDualPaneActive = MutableStateFlow(false)
    private val _paneBState = MutableStateFlow(StorageTabState(currentDir = initialDir))
    val paneBState: StateFlow<StorageTabState> = _paneBState.asStateFlow()
    val activePaneIndex = MutableStateFlow(0) // 0: Pane A, 1: Pane B

    // 6. Split APKs / XAPK Installer
    val xapkInstallTarget = MutableStateFlow<XapkInfo?>(null)
    val isInstallingXapk = MutableStateFlow(false)
    val xapkInstallProgress = MutableStateFlow("")

    // 7. Biometric Vault Unlock (Delegated to VaultViewModel)
    val isBiometricVaultEnabled: MutableStateFlow<Boolean> get() = vaultViewModel.isBiometricVaultEnabled

    // 8. Wireless Web Share (HTTP Server) (Delegated to NetworkServerViewModel)
    val webShareState: StateFlow<WebShareState> get() = networkServerViewModel.webShareState

    // 9. Military-Grade File Shredder
    val shredTargets = MutableStateFlow<List<File>>(emptyList())
    val isShredding = MutableStateFlow(false)
    val shredProgress = MutableStateFlow(ShredProgress())

    // 11. Smart Collections / Virtual Folders (Lazy)
    private val _smartCollectionsRepository = lazy { SmartCollectionsRepository(application) }
    val smartCollectionsRepository get() = _smartCollectionsRepository.value
    val smartCollections = MutableStateFlow<List<SmartCollection>>(emptyList())
    val activeCollectionFiles = MutableStateFlow<CollectionWithFiles?>(null)
    val isCollectionLoading = MutableStateFlow(false)

    // 12. Storage Time Machine / On This Day (Lazy)
    private val _timeMachineRepository = lazy { TimeMachineRepository() }
    val timeMachineRepository get() = _timeMachineRepository.value
    val timeMachineData = MutableStateFlow<TimeMachineData?>(null)
    val isTimeMachineLoading = MutableStateFlow(false)

    init {
        // 1. Instantly start non-blocking directory and storage queries
        refreshStorage()
        loadDirectory(initialDir)

        // 2. Defer non-critical tags, favorites, and volume scans slightly so first frame draws with 0 lag
        viewModelScope.launch(Dispatchers.IO) {
            delay(150)
            loadFavorites()
            loadTags()
            loadStorageVolumes()
        }
    }

    fun selectTab(tab: CtTab) {
        _selectedTab.value = tab
        if (tab == CtTab.RECENT && _recentFiles.value.isEmpty()) {
            loadRecentFiles()
        }
    }

    fun navigateToScreen(screen: Screen) {
        NavigationManager.navigateTo(screen)
    }

    fun handleBackPress(): Boolean {
        if (NavigationManager.getCurrentScreen() == Screen.MAIN) {
            if (isDualPaneActive.value && activePaneIndex.value == 1) {
                val paneB = _paneBState.value
                if (paneB.selectedItems.isNotEmpty()) {
                    clearSelectionPaneB()
                    return true
                }
                if (paneB.backStack.isNotEmpty()) {
                    backPaneB()
                    return true
                }
            }
            val state = _storageState.value
            if (state.isSelectionMode) {
                clearSelection()
                return true
            }
            if (_selectedTab.value == CtTab.STORAGE && state.backStack.isNotEmpty()) {
                goBackInDirectory()
                return true
            }
            if (_selectedTab.value == CtTab.RECENT) {
                _selectedTab.value = CtTab.STORAGE
                return true
            }
            return false
        }

        if (NavigationManager.getCurrentScreen() == Screen.SMART_COLLECTIONS) {
            if (activeCollectionFiles.value != null) {
                closeActiveCollection()
                return true
            }
            closeActiveCollection()
            return NavigationManager.popBackStack()
        }

        if (NavigationManager.getCurrentScreen() == Screen.NETWORK_DRIVES && _activeNetworkDrive.value != null) {
            if (!navigateUpRemoteFolder()) {
                disconnectNetworkDrive()
            }
            return true
        }

        return NavigationManager.popBackStack()
    }

    fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageSpace.value = fileRepository.getStorageSpace()
        }
    }

    fun onStoragePermissionGranted() {
        val currentDir = _storageState.value.currentDir
        val targetDir = if (currentDir.exists() && currentDir.canRead()) {
            currentDir
        } else {
            fileRepository.rootStorageDirectory
        }

        val now = System.currentTimeMillis()
        if (lastLoadedDirPath == targetDir.absolutePath && (now - lastLoadTimestamp) < 1200L) {
            // Avoid redundant duplicate disk sweep on cold boot
            return
        }

        refreshStorage()
        loadDirectory(targetDir)
        if (_selectedTab.value == CtTab.RECENT) {
            loadRecentFiles()
        }
    }

    fun loadRecentFiles(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            isRecentLoading.value = true
            if (forceRefresh) {
                FileRepository.invalidateRecentCache()
            }
            val files = fileRepository.getRecentFiles()
            _recentFiles.value = files
            isRecentLoading.value = false
        }
    }

    fun navigateToFolder(dir: File, addToHistory: Boolean = true) =
        loadDirectory(dir, addToHistory = addToHistory)

    fun navigateUp(): Boolean {
        val current = _storageState.value
        val parent = current.currentDir.parentFile
        val rootPath = fileRepository.rootStorageDirectory.parentFile?.absolutePath ?: ""
        return if (parent != null && parent.canRead() && (rootPath.isEmpty() || parent.absolutePath.startsWith(rootPath))) {
            loadDirectory(parent, addToHistory = true)
            true
        } else if (current.backStack.isNotEmpty()) {
            goBackInDirectory()
            true
        } else {
            false
        }
    }

    fun loadDirectory(dir: File, addToHistory: Boolean = false) {
        lastLoadedDirPath = dir.absolutePath
        lastLoadTimestamp = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val current = _storageState.value
            val isSameDir = current.currentDir.absolutePath == dir.absolutePath
            val hasItems = isSameDir && current.items.isNotEmpty()

            val newBackStack = if (addToHistory && !isSameDir) {
                current.backStack + current.currentDir
            } else {
                current.backStack
            }

            if (current.items.isEmpty()) {
                _storageState.update {
                    it.copy(
                        currentDir = dir,
                        backStack = newBackStack,
                        forwardStack = emptyList(),
                        selectedItems = emptySet(),
                        isLoading = true
                    )
                }
            }

            val items = fileRepository.listFiles(
                directory = dir,
                showHidden = current.showHidden,
                sortType = current.sortType,
                foldersOnTop = current.foldersOnTop,
                searchQuery = current.searchQuery
            )

            _storageState.update {
                it.copy(
                    currentDir = dir,
                    backStack = newBackStack,
                    forwardStack = if (isSameDir) it.forwardStack else emptyList(),
                    items = items,
                    isLoading = false
                )
            }
        }
    }

    fun goBackInDirectory() {
        val current = _storageState.value
        if (current.backStack.isNotEmpty()) {
            val prev = current.backStack.last()
            val newBackStack = current.backStack.dropLast(1)
            val newForwardStack = current.forwardStack + current.currentDir

            viewModelScope.launch {
                val items = fileRepository.listFiles(
                    directory = prev,
                    showHidden = current.showHidden,
                    sortType = current.sortType,
                    foldersOnTop = current.foldersOnTop,
                    searchQuery = current.searchQuery
                )
                _storageState.update {
                    it.copy(
                        currentDir = prev,
                        backStack = newBackStack,
                        forwardStack = newForwardStack,
                        items = items,
                        selectedItems = emptySet()
                    )
                }
            }
        }
    }

    fun refreshCurrentDirectory() {
        FileRepository.invalidateAllFileCaches()
        loadDirectory(_storageState.value.currentDir, addToHistory = false)
        refreshStorage()
        loadRecentFiles(forceRefresh = true)
    }

    fun toggleViewMode() {
        _storageState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    fun setSortType(sortType: SortType) {
        _storageState.update { current ->
            val sorted = sortFileList(current.items, sortType, current.foldersOnTop)
            current.copy(sortType = sortType, items = sorted)
        }
    }

    fun toggleFoldersOnTop() {
        _storageState.update { current ->
            val newFoldersOnTop = !current.foldersOnTop
            val sorted = sortFileList(current.items, current.sortType, newFoldersOnTop)
            current.copy(foldersOnTop = newFoldersOnTop, items = sorted)
        }
    }

    fun toggleShowHidden() {
        val newShow = !_storageState.value.showHidden
        _storageState.update { it.copy(showHidden = newShow) }
        loadDirectory(_storageState.value.currentDir)
    }

    fun toggleBigFilesFilter() {
        _storageState.update { it.copy(filterOnlyBigFiles = !it.filterOnlyBigFiles) }
    }

    fun setSearchQuery(query: String) {
        _storageState.update { it.copy(searchQuery = query) }
        loadDirectory(_storageState.value.currentDir)
    }

    fun toggleSelectItem(item: FileItem) {
        _storageState.update { state ->
            val set = state.selectedItems.toMutableSet()
            if (set.contains(item)) set.remove(item) else set.add(item)
            state.copy(selectedItems = set)
        }
    }

    fun selectAll(items: List<FileItem>? = null) {
        _storageState.update { state ->
            val toSelect = items ?: state.displayItems
            state.copy(selectedItems = toSelect.toSet())
        }
    }

    fun clearSelection() {
        _storageState.update { state ->
            state.copy(selectedItems = emptySet())
        }
    }

    fun copySelected() {
        val items = _storageState.value.selectedItems.toList()
        if (items.isNotEmpty()) {
            _clipboard.value = ClipboardState(items = items, isCut = false)
            clearSelection()
            showMessage("${items.size} item(s) copied")
        }
    }

    fun cutSelected() {
        val items = _storageState.value.selectedItems.toList()
        if (items.isNotEmpty()) {
            _clipboard.value = ClipboardState(items = items, isCut = true)
            clearSelection()
            showMessage("${items.size} item(s) cut to clipboard")
        }
    }

    fun copySingle(item: FileItem) {
        _clipboard.value = ClipboardState(items = listOf(item), isCut = false)
        showMessage("\"${item.name}\" copied")
    }

    fun cutSingle(item: FileItem) {
        _clipboard.value = ClipboardState(items = listOf(item), isCut = true)
        showMessage("\"${item.name}\" ready to move")
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun pasteToCurrentDirectory() {
        val clip = _clipboard.value ?: return
        val destDir = _storageState.value.currentDir

        viewModelScope.launch {
            if (clip.isCut) {
                val res = fileRepository.move(clip.items, destDir)
                if (res.isSuccess) {
                    showMessage("Moved ${res.getOrNull()} items successfully")
                    _clipboard.value = null
                } else {
                    showMessage("Move failed: ${res.exceptionOrNull()?.message}")
                }
            } else {
                val res = fileRepository.copy(clip.items, destDir)
                if (res.isSuccess) {
                    showMessage("Copied ${res.getOrNull()} items successfully")
                } else {
                    showMessage("Copy failed: ${res.exceptionOrNull()?.message}")
                }
            }
            refreshCurrentDirectory()
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val res = fileRepository.createFolder(_storageState.value.currentDir, name)
            if (res.isSuccess) {
                showMessage("Folder \"$name\" created")
                refreshCurrentDirectory()
            } else {
                showMessage("Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun createTextFile(name: String) {
        viewModelScope.launch {
            val res = fileRepository.createTextFile(_storageState.value.currentDir, name)
            if (res.isSuccess) {
                showMessage("File \"$name\" created")
                refreshCurrentDirectory()
                openTextEditor(res.getOrThrow())
            } else {
                showMessage("Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteItems(items: List<FileItem>) {
        viewModelScope.launch {
            var count = 0
            for (item in items) {
                if (fileRepository.delete(item).getOrDefault(false)) count++
            }
            showMessage("Deleted $count item(s)")
            clearSelection()
            refreshCurrentDirectory()
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        viewModelScope.launch {
            val res = fileRepository.rename(item, newName)
            if (res.isSuccess) {
                showMessage("Renamed to \"$newName\"")
                refreshCurrentDirectory()
            } else {
                showMessage("Rename failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun zipItems(items: List<FileItem>, zipName: String) {
        val sanitized = if (zipName.endsWith(".zip")) zipName else "$zipName.zip"
        val zipFile = File(_storageState.value.currentDir, sanitized)

        viewModelScope.launch {
            val res = fileRepository.zip(items, zipFile)
            if (res.isSuccess) {
                showMessage("Archive \"$sanitized\" created")
                refreshCurrentDirectory()
            } else {
                showMessage("Failed to compress: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun unzipItem(item: FileItem) {
        val outputName = item.name.removeSuffix(".zip").removeSuffix(".ZIP")
        val outputDir = File(_storageState.value.currentDir, outputName)

        viewModelScope.launch {
            val res = fileRepository.unzip(item.file, outputDir)
            if (res.isSuccess) {
                showMessage("Extracted to \"$outputName\"")
                refreshCurrentDirectory()
            } else {
                showMessage("Extraction failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    // Cleaner Tools (Delegated to CleanerViewModel)
    fun openCleaner() = cleanerViewModel.openCleaner()
    fun startCleanScan() = cleanerViewModel.startCleanScan()
    fun cleanSelectedJunk() {
        cleanerViewModel.cleanSelectedJunk()
        refreshStorage()
    }
    fun cleanEmptyFolders() = cleanerViewModel.cleanEmptyFolders()
    fun performClean() = cleanSelectedJunk()

    // FTP Server Tool (Delegated to NetworkServerViewModel)
    fun openFtpServer() = networkServerViewModel.openFtpServer()
    fun toggleFtpServer() = networkServerViewModel.toggleFtpServer()

    // Cleaner Detailed Actions (Delegated to CleanerViewModel)
    fun deleteLargeFiles(items: List<FileItem>) {
        cleanerViewModel.deleteLargeFiles(items)
        refreshStorage()
        refreshCurrentDirectory()
    }

    fun deleteApkPackages(items: List<FileItem>) {
        cleanerViewModel.deleteApkPackages(items)
        refreshStorage()
        refreshCurrentDirectory()
    }

    fun deleteEmptyFolders(folders: List<FileItem>) {
        cleanerViewModel.deleteEmptyFolders(folders)
        refreshCurrentDirectory()
    }

    // Category Screen
    fun openCategory(category: FileCategory, title: String) {
        if (category == FileCategory.APK) {
            openAppInstaller()
            return
        }

        if (title == "Downloads") {
            _selectedTab.value = CtTab.STORAGE
            loadDirectory(fileRepository.downloadsDirectory, addToHistory = true)
            NavigationManager.navigateTo(Screen.MAIN)
            return
        }

        val cached = fileRepository.getCachedCategory(category)
        if (!cached.isNullOrEmpty()) {
            _categoryViewState.value = CategoryViewState(
                category = category,
                title = title,
                items = cached,
                isLoading = false
            )
        } else {
            _categoryViewState.value = CategoryViewState(
                category = category,
                title = title,
                isLoading = true
            )
        }
        navigateToScreen(Screen.CATEGORY_VIEW)

        viewModelScope.launch {
            val items = fileRepository.getCategoryFiles(category)
            _categoryViewState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun refreshCategory() {
        val category = _categoryViewState.value.category
        if (NavigationManager.getCurrentScreen() == Screen.CATEGORY_VIEW) {
            viewModelScope.launch {
                val items = fileRepository.getCategoryFiles(category)
                _categoryViewState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    // Social Media Hub
    private val _socialHubState = MutableStateFlow(SocialHubState())
    val socialHubState: StateFlow<SocialHubState> = _socialHubState.asStateFlow()

    fun openSocialHub() {
        navigateToScreen(Screen.SOCIAL_HUB)
        loadSocialHub()
    }

    fun loadSocialHub() {
        viewModelScope.launch {
            _socialHubState.update { it.copy(isLoading = true) }
            val apps = fileRepository.getSocialAppGroups(getApplication())
            _socialHubState.update { it.copy(apps = apps, isLoading = false) }
        }
    }

    fun setSocialFilter(filter: String) {
        _socialHubState.update { it.copy(selectedFilter = filter) }
    }

    fun setSocialSearchQuery(query: String) {
        _socialHubState.update { it.copy(searchQuery = query) }
    }

    fun navigateToSocialFolder(folder: File) {
        _selectedTab.value = CtTab.STORAGE
        loadDirectory(folder, addToHistory = true)
        NavigationManager.navigateTo(Screen.MAIN)
    }

    // Text & HTML Editor/Viewer (Delegated to TextEditorViewModel)
    fun openTextFile(file: File) = textEditorViewModel.openTextFile(file)
    fun openTextEditor(file: File) = textEditorViewModel.openTextEditor(file)
    fun openTextFromUri(uri: android.net.Uri, displayName: String) = textEditorViewModel.openTextFromUri(uri, displayName)
    fun updateTextContent(newContent: String) = textEditorViewModel.updateTextContent(newContent)
    fun updateEditorContent(newContent: String) = textEditorViewModel.updateEditorContent(newContent)
    fun toggleHtmlPreview() = textEditorViewModel.toggleHtmlPreview()
    fun toggleEditorWordWrap() = textEditorViewModel.toggleEditorWordWrap()
    fun saveTextFile() = textEditorViewModel.saveTextFile()
    fun saveEditorFile() = textEditorViewModel.saveEditorFile()

    // Image Viewer
    fun openImageViewer(file: File, siblingItems: List<FileItem>) {
        val candidateImages = siblingItems.filter { it.category == FileCategory.IMAGE }
        val imageFiles = if (candidateImages.size > 1) {
            candidateImages
        } else {
            val categoryImages = _categoryViewState.value.items.filter { it.category == FileCategory.IMAGE }
            if (categoryImages.size > 1 && categoryImages.any { it.file.absolutePath == file.absolutePath }) {
                categoryImages
            } else {
                val parent = file.parentFile
                val diskImages = parent?.listFiles()?.filter {
                    !it.isDirectory && FileItem(it).category == FileCategory.IMAGE
                }?.map { FileItem(it) }?.sortedBy { it.name } ?: emptyList()

                if (diskImages.size > 1) {
                    diskImages
                } else if (candidateImages.isNotEmpty()) {
                    candidateImages
                } else {
                    listOf(FileItem(file))
                }
            }
        }
        val index = imageFiles.indexOfFirst { it.file.absolutePath == file.absolutePath }.coerceAtLeast(0)
        _imageViewerState.value = ImageViewerState(
            currentFile = file,
            imageList = imageFiles,
            currentIndex = index
        )
        navigateToScreen(Screen.IMAGE_VIEWER)
    }

    fun setImageIndex(index: Int) {
        val state = _imageViewerState.value
        if (state.imageList.isNotEmpty() && index in state.imageList.indices) {
            _imageViewerState.value = state.copy(
                currentIndex = index,
                currentFile = state.imageList[index].file
            )
        }
    }

    fun nextImage() {
        val state = _imageViewerState.value
        if (state.imageList.isNotEmpty() && state.currentIndex < state.imageList.size - 1) {
            val nextIdx = state.currentIndex + 1
            _imageViewerState.value = state.copy(
                currentIndex = nextIdx,
                currentFile = state.imageList[nextIdx].file
            )
        }
    }

    fun prevImage() {
        val state = _imageViewerState.value
        if (state.imageList.isNotEmpty() && state.currentIndex > 0) {
            val prevIdx = state.currentIndex - 1
            _imageViewerState.value = state.copy(
                currentIndex = prevIdx,
                currentFile = state.imageList[prevIdx].file
            )
        }
    }

    // App & APK Manager (Cloner / Extractor & Downgrade Hub)
    fun openAppInstaller() {
        val cached = appsRepository.getCachedStorageApks()
        if (!cached.isNullOrEmpty()) {
            _storageApks.value = cached
            isStorageApksLoading.value = false
        }
        navigateToScreen(Screen.APP_INSTALLER)
        loadStorageApks()
    }

    fun openAppManager(tab: ApkTab = ApkTab.INSTALLED_APPS) {
        apkScreenTab.value = tab
        navigateToScreen(Screen.APP_MANAGER)
        loadApps()
        loadAppBackups()
        loadStorageApks()
    }

    fun setApkTab(tab: ApkTab) {
        apkScreenTab.value = tab
        when (tab) {
            ApkTab.INSTALLED_APPS -> if (_installedApps.value.isEmpty()) loadApps()
            ApkTab.DOWNGRADE_HUB -> loadAppBackups()
            ApkTab.APK_FILES -> if (_storageApks.value.isEmpty()) loadStorageApks()
        }
    }

    fun loadStorageApks() {
        viewModelScope.launch {
            if (_storageApks.value.isEmpty()) {
                isStorageApksLoading.value = true
            }
            val apks = appsRepository.getStorageApkFiles()
            _storageApks.value = apks
            isStorageApksLoading.value = false
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            isAppsLoading.value = true
            val apps = appsRepository.getInstalledApps(includeSystemApps = includeSystemApps.value)
            _installedApps.value = apps
            isAppsLoading.value = false
        }
    }

    fun loadAppBackups() {
        viewModelScope.launch {
            isBackupsLoading.value = true
            val backups = appsRepository.getAppBackups()
            appBackupGroups.value = backups
            isBackupsLoading.value = false
        }
    }

    fun toggleAppBatchSelection(packageName: String) {
        selectedAppsForBatch.update { current ->
            if (current.contains(packageName)) current - packageName else current + packageName
        }
    }

    fun selectAllAppsForBatch(apps: List<AppInfoItem>) {
        selectedAppsForBatch.value = apps.map { it.packageName }.toSet()
    }

    fun clearAppBatchSelection() {
        selectedAppsForBatch.value = emptySet()
    }

    fun batchBackupSelectedApps() {
        val selectedPkgNames = selectedAppsForBatch.value
        if (selectedPkgNames.isEmpty()) {
            showMessage("No apps selected")
            return
        }
        val targets = _installedApps.value.filter { it.packageName in selectedPkgNames }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            isBatchBackingUp.value = true
            var successCount = 0
            for ((index, app) in targets.withIndex()) {
                batchBackupProgress.value = "Backing up ${index + 1}/${targets.size}: ${app.appName}..."
                val res = appsRepository.backupAppApk(app)
                if (res.isSuccess) successCount++
            }
            isBatchBackingUp.value = false
            batchBackupProgress.value = ""
            clearAppBatchSelection()
            showMessage("Backed up $successCount apps to Backup Hub!")
            loadAppBackups()
            loadApps()
            loadStorageApks()
        }
    }

    fun backupInstalledApp(app: AppInfoItem) {
        viewModelScope.launch {
            showMessage("Backing up ${app.appName}...")
            val res = appsRepository.backupAppApk(app)
            res.fold(
                onSuccess = { dest ->
                    showMessage("Saved ${app.appName} to Downloads/CentExplorer/Backup")
                    loadAppBackups()
                    loadApps()
                    loadStorageApks()
                },
                onFailure = { err ->
                    showMessage("Backup failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun extractAndShareApp(context: android.content.Context, app: AppInfoItem) {
        viewModelScope.launch {
            showMessage("Extracting APK for ${app.appName}...")
            val res = appsRepository.backupAppApk(app)
            res.fold(
                onSuccess = { destFile ->
                    FileOpener.shareFile(context, FileItem(destFile))
                    loadAppBackups()
                },
                onFailure = { err ->
                    showMessage("Failed to extract APK: ${err.localizedMessage}")
                }
            )
        }
    }

    fun sendAppViaFastShare(app: AppInfoItem) {
        viewModelScope.launch {
            showMessage("Extracting APK for ${app.appName}...")
            val res = appsRepository.backupAppApk(app)
            res.fold(
                onSuccess = { destFile ->
                    openFastShare(listOf(FileItem(destFile)))
                },
                onFailure = { err ->
                    showMessage("Failed to extract APK: ${err.localizedMessage}")
                }
            )
        }
    }

    fun sendBackupViaFastShare(backup: ApkFileItem) {
        openFastShare(listOf(FileItem(backup.file)))
    }

    fun deleteBackup(backup: ApkFileItem) {
        viewModelScope.launch {
            if (backup.file.delete()) {
                showMessage("Deleted backup ${backup.name}")
                loadAppBackups()
                loadStorageApks()
            } else {
                showMessage("Failed to delete backup")
            }
        }
    }

    fun toggleSystemApps() {
        includeSystemApps.update { !it }
        loadApps()
    }

    fun setAppsSearchQuery(q: String) {
        appsSearchQuery.value = q
    }

    fun deleteStorageApk(item: ApkFileItem) {
        viewModelScope.launch {
            if (item.file.delete()) {
                showMessage("Deleted \"${item.name}\"")
                loadStorageApks()
                loadAppBackups()
            } else {
                showMessage("Failed to delete file")
            }
        }
    }

    fun openApkInstallDialog(file: File) {
        val ext = file.extension.lowercase()
        if (ext == "xapk" || ext == "apks") {
            openXapkFile(file)
            return
        }
        viewModelScope.launch {
            val item = appsRepository.parseApkFile(file)
            _apkInstallTarget.value = item
        }
    }

    fun closeApkInstallDialog() {
        _apkInstallTarget.value = null
    }

    // AMOLED Mode
    fun toggleAmoledMode() {
        val next = !isAmoledMode.value
        isAmoledMode.value = next
        showMessage(if (next) "AMOLED Pure Black ON" else "AMOLED Pure Black OFF")
    }

    // Vault Functions (Delegated to VaultViewModel)
    fun openVault() = vaultViewModel.openVault()
    fun setupVaultPin(pin: String, answer: String) = vaultViewModel.setupVaultPin(pin, answer)
    fun unlockVault(pin: String): Boolean = vaultViewModel.unlockVault(pin)
    fun resetVaultPinWithAnswer(answer: String, newPin: String): Boolean = vaultViewModel.resetVaultPinWithAnswer(answer, newPin)
    fun openVaultFilePreview(item: FileItem, onReady: (FileItem) -> Unit) = vaultViewModel.openVaultFilePreview(item, onReady)
    fun lockVault() = vaultViewModel.lockVault()
    fun loadVaultFiles() = vaultViewModel.loadVaultFiles()
    fun addFileToVault(item: FileItem) = vaultViewModel.addFileToVault(item) { refreshCurrentDirectory() }
    fun addFilesToVault(items: List<FileItem>) {
        vaultViewModel.addFilesToVault(items) {
            clearSelection()
            refreshCurrentDirectory()
        }
    }
    fun restoreFileFromVault(item: FileItem) = vaultViewModel.restoreFileFromVault(item) { refreshCurrentDirectory() }
    fun deleteFileFromVault(item: FileItem) = vaultViewModel.deleteFileFromVault(item)

    // Duplicate Finder Functions
    fun openDuplicateFinder() {
        navigateToScreen(Screen.DUPLICATES)
    }

    fun scanForDuplicates() {
        viewModelScope.launch {
            isDuplicateScanning.value = true
            val result = duplicateRepository.findDuplicates()
            _duplicateScanResult.value = result
            selectedDuplicateFiles.value = result.groups.flatMap { it.duplicates }.toSet()
            isDuplicateScanning.value = false
        }
    }

    fun toggleSelectDuplicate(file: FileItem) {
        val current = selectedDuplicateFiles.value.toMutableSet()
        if (current.contains(file)) current.remove(file) else current.add(file)
        selectedDuplicateFiles.value = current
    }

    fun selectAllDuplicateCopies() {
        val allCopies = _duplicateScanResult.value?.groups?.flatMap { it.duplicates }?.toSet() ?: emptySet()
        selectedDuplicateFiles.value = allCopies
    }

    fun clearSelectedDuplicates() {
        selectedDuplicateFiles.value = emptySet()
    }

    fun deleteSelectedDuplicates() {
        val toDelete = selectedDuplicateFiles.value.toList()
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            val deletedCount = duplicateRepository.deleteFiles(toDelete)
            val freedBytes = toDelete.sumOf { it.size }
            showMessage("Deleted $deletedCount duplicates (Freed ${FileItem.formatBytes(freedBytes)})")
            selectedDuplicateFiles.value = emptySet()
            scanForDuplicates()
            refreshStorage()
            loadDirectory(_storageState.value.currentDir)
        }
    }

    // Storage Analyzer Functions (Delegated to StorageAnalyzerViewModel)
    fun openStorageAnalyzer() = storageAnalyzerViewModel.openStorageAnalyzer()
    fun analyzeStorage() = storageAnalyzerViewModel.analyzeStorage()
    fun refreshStorageSpace() {
        refreshStorage()
        storageAnalyzerViewModel.refreshStorageSpace()
    }
    fun analyzeStorageCategories() = storageAnalyzerViewModel.analyzeStorageCategories()
    fun loadLargeFiles() = storageAnalyzerViewModel.loadLargeFiles()

    fun openDirectoryFromAnalyzer(dir: File) {
        loadDirectory(dir, addToHistory = true)
        _selectedTab.value = CtTab.STORAGE
        navigateToScreen(Screen.MAIN)
    }

    // Batch Rename Function
    fun batchRename(pairs: List<Pair<FileItem, String>>) {
        viewModelScope.launch {
            var successCount = 0
            var skippedCount = 0
            for ((item, newName) in pairs) {
                val cleanName = newName.trim().replace("/", "").replace("\\", "").replace("\u0000", "")
                if (cleanName.isNotBlank() && item.name != cleanName) {
                    val parent = item.file.parentFile ?: continue
                    val target = File(parent, cleanName)
                    if (target.exists()) {
                        skippedCount++
                        continue
                    }
                    if (item.file.renameTo(target)) {
                        successCount++
                    }
                }
            }
            FileRepository.invalidateAllFileCaches()
            refreshCurrentDirectory()
            refreshStorage()
            clearSelection()
            loadDirectory(_storageState.value.currentDir)
            if (skippedCount > 0) {
                showMessage("Renamed $successCount files ($skippedCount skipped due to conflict)")
            } else {
                showMessage("Renamed $successCount files successfully")
            }
        }
    }

    // Zip / Tar / Gz / 7z / Rar Archive Functions
    fun openZipViewer(file: File) {
        viewModelScope.launch {
            _zipViewerState.value = ZipViewerState(isLoading = true)
            navigateToScreen(Screen.ZIP_VIEWER)
            val res = ArchiveHelper.inspectArchive(file)
            res.fold(
                onSuccess = { info ->
                    _zipViewerState.value = ZipViewerState(archiveInfo = info, isLoading = false)
                },
                onFailure = { err ->
                    _zipViewerState.value = ZipViewerState(isLoading = false, errorMessage = err.localizedMessage)
                }
            )
        }
    }

    fun openZipFromUri(uri: android.net.Uri, name: String) {
        viewModelScope.launch {
            _zipViewerState.value = ZipViewerState(isLoading = true)
            navigateToScreen(Screen.ZIP_VIEWER)
            val res = zipRepository.inspectZipUri(uri, name)
            res.fold(
                onSuccess = { info ->
                    _zipViewerState.value = ZipViewerState(archiveInfo = info, isLoading = false)
                },
                onFailure = { err ->
                    _zipViewerState.value = ZipViewerState(isLoading = false, errorMessage = err.localizedMessage)
                }
            )
        }
    }

    var cachedArchivePassword: String? = null

    fun openZipEntry(
        archiveFile: File,
        entry: ZipEntryItem,
        password: String? = null,
        onRequiresPassword: (() -> Unit)? = null
    ) {
        if (entry.isDirectory) return

        val pass = password ?: cachedArchivePassword
        val previewDir = File(getApplication<android.app.Application>().cacheDir, "zip_previews/${archiveFile.nameWithoutExtension}")
        val cleanName = entry.fullPath.removeSuffix(".miaes")
        val cachedFile = File(previewDir, cleanName)

        // Fast path: if already extracted in cache, open immediately without waiting
        if (cachedFile.exists() && cachedFile.length() > 0L && (entry.size == 0L || cachedFile.length() == entry.size)) {
            val item = FileItem(cachedFile)
            if (!openFileSmart(item)) {
                com.ct.explorer.utils.FileOpener.openWithChooser(getApplication(), item)
            }
            return
        }

        viewModelScope.launch {
            showMessage("Opening ${entry.name}...")
            val res = ArchiveHelper.extractSingleEntry(
                archiveFile = archiveFile,
                entryPath = entry.fullPath,
                destDir = previewDir,
                password = pass
            )

            res.fold(
                onSuccess = { extractedFile ->
                    if (pass != null) cachedArchivePassword = pass
                    val item = FileItem(extractedFile)
                    if (!openFileSmart(item)) {
                        com.ct.explorer.utils.FileOpener.openWithChooser(getApplication(), item)
                    }
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: ""
                    if (msg.contains("Password", ignoreCase = true) || msg.contains("decrypt", ignoreCase = true)) {
                        if (onRequiresPassword != null) {
                            onRequiresPassword()
                        } else {
                            showMessage("Password required for encrypted archive file")
                        }
                    } else {
                        showMessage("Cannot open file: $msg")
                    }
                }
            )
        }
    }

    fun extractSingleZipEntry(
        archiveFile: File,
        entry: ZipEntryItem,
        targetDir: File,
        password: String? = null
    ) {
        val pass = password ?: cachedArchivePassword
        viewModelScope.launch {
            isZipExtracting.value = true
            val res = ArchiveHelper.extractSingleEntry(
                archiveFile = archiveFile,
                entryPath = entry.fullPath,
                destDir = targetDir,
                password = pass
            )
            isZipExtracting.value = false
            res.fold(
                onSuccess = { file ->
                    showMessage("Extracted ${file.name} to ${targetDir.name}")
                    loadDirectory(_storageState.value.currentDir)
                    refreshStorage()
                },
                onFailure = { err ->
                    showMessage("Failed to extract ${entry.name}: ${err.localizedMessage}")
                }
            )
        }
    }

    fun openZipEntryWithChooser(
        archiveFile: File,
        entry: ZipEntryItem,
        context: android.content.Context,
        password: String? = null,
        onRequiresPassword: (() -> Unit)? = null
    ) {
        if (entry.isDirectory) return
        val pass = password ?: cachedArchivePassword
        val previewDir = File(getApplication<android.app.Application>().cacheDir, "zip_previews/${archiveFile.nameWithoutExtension}")
        val cleanName = entry.fullPath.removeSuffix(".miaes")
        val cachedFile = File(previewDir, cleanName)

        if (cachedFile.exists() && cachedFile.length() > 0L && (entry.size == 0L || cachedFile.length() == entry.size)) {
            com.ct.explorer.utils.FileOpener.openWithChooser(context, FileItem(cachedFile))
            return
        }

        viewModelScope.launch {
            showMessage("Extracting ${entry.name} for app chooser...")
            val res = ArchiveHelper.extractSingleEntry(
                archiveFile = archiveFile,
                entryPath = entry.fullPath,
                destDir = previewDir,
                password = pass
            )
            res.fold(
                onSuccess = { extractedFile ->
                    if (pass != null) cachedArchivePassword = pass
                    com.ct.explorer.utils.FileOpener.openWithChooser(context, FileItem(extractedFile))
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: ""
                    if (msg.contains("Password", ignoreCase = true) || msg.contains("decrypt", ignoreCase = true)) {
                        if (onRequiresPassword != null) onRequiresPassword()
                        else showMessage("Password required for encrypted archive")
                    } else {
                        showMessage("Cannot open file: $msg")
                    }
                }
            )
        }
    }

    fun extractZipArchive(
        zipFile: File,
        targetDir: File,
        selectedEntries: Set<String>? = null,
        password: String? = null
    ) {
        viewModelScope.launch {
            isZipExtracting.value = true
            val res = ArchiveHelper.extractArchive(
                file = zipFile,
                destDir = targetDir,
                password = password,
                selectedPaths = selectedEntries,
                onProgress = { _, _ -> }
            )
            isZipExtracting.value = false
            res.fold(
                onSuccess = {
                    showMessage("Extracted archive to ${targetDir.name}")
                    loadDirectory(_storageState.value.currentDir)
                    refreshStorage()
                },
                onFailure = { err ->
                    showMessage("Extraction failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun compressFilesToZip(
        items: List<File>,
        destinationZip: File,
        compressionLevel: Int,
        format: com.ct.explorer.utils.ArchiveType = com.ct.explorer.utils.ArchiveType.ZIP,
        password: String? = null
    ) {
        viewModelScope.launch {
            showMessage("Compressing ${items.size} items...")
            val res = ArchiveHelper.compressArchive(
                items = items,
                destinationFile = destinationZip,
                format = format,
                compressionLevel = compressionLevel,
                password = password
            )
            res.fold(
                onSuccess = { createdFile ->
                    showMessage("Created ${createdFile.name} (${FileItem.formatBytes(createdFile.length())})")
                    clearSelection()
                    loadDirectory(_storageState.value.currentDir)
                    refreshStorage()
                },
                onFailure = { err ->
                    showMessage("Compression failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // ==========================================
    // RECYCLE BIN (TRASH) METHODS
    // ==========================================

    fun openTrash() {
        navigateToScreen(Screen.TRASH)
        loadTrashItems()
    }

    fun loadTrashItems() {
        viewModelScope.launch {
            isTrashLoading.value = true
            _trashItems.value = trashRepository.getTrashItems()
            isTrashLoading.value = false
        }
    }

    fun moveToTrash(items: List<FileItem>) {
        viewModelScope.launch {
            var successCount = 0
            for (item in items) {
                val res = trashRepository.moveToTrash(item)
                if (res.isSuccess) successCount++
            }
            showMessage("Moved $successCount item(s) to Recycle Bin")
            clearSelection()
            loadTrashItems()
            refreshCurrentDirectory()
            refreshStorage()
        }
    }

    fun restoreTrashItems(items: List<TrashItem>) {
        viewModelScope.launch {
            var restoredCount = 0
            for (item in items) {
                val res = trashRepository.restoreItem(item)
                if (res.isSuccess) restoredCount++
            }
            showMessage("Restored $restoredCount item(s)")
            loadTrashItems()
            refreshCurrentDirectory()
            refreshStorage()
        }
    }

    fun deletePermanentlyMultiple(items: List<TrashItem>) {
        viewModelScope.launch {
            var deletedCount = 0
            for (item in items) {
                if (trashRepository.deletePermanently(item)) deletedCount++
            }
            showMessage("Permanently deleted $deletedCount item(s)")
            loadTrashItems()
            refreshStorage()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            if (trashRepository.emptyTrash()) {
                showMessage("Recycle Bin emptied")
                loadTrashItems()
                refreshStorage()
            } else {
                showMessage("Failed to empty Recycle Bin")
            }
        }
    }

    // ==========================================
    // FAVORITES / PINNED FOLDERS METHODS
    // ==========================================

    fun loadFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            val favs = favoritesRepository.getFavorites()
            _favorites.value = favs
        }
    }

    fun toggleFavorite(file: File, customName: String? = null) {
        viewModelScope.launch {
            val isNowFav = favoritesRepository.toggleFavorite(file, customName)
            loadFavorites()
            showMessage(if (isNowFav) "Added to Favorites" else "Removed from Favorites")
        }
    }

    fun toggleFavorites(items: List<FileItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            for (item in items) {
                favoritesRepository.toggleFavorite(item.file, item.name)
            }
            loadFavorites()
            clearSelection()
            showMessage("Updated favorites for ${items.size} item(s)")
        }
    }

    suspend fun isFavorite(path: String): Boolean {
        return favoritesRepository.isFavorite(path)
    }

    // ==========================================
    // PDF VIEWER METHODS
    // ==========================================

    fun openPdfFile(file: File) {
        _pdfViewerState.value = PdfViewerState(
            file = file,
            title = file.name
        )
        navigateToScreen(Screen.PDF_VIEWER)
    }

    // ==========================================
    // BUILT-IN AUDIO PLAYER METHODS (Delegated to AudioPlayerViewModel)
    // ==========================================

    fun playAudio(item: FileItem, playlist: List<FileItem> = emptyList()) =
        audioPlayerViewModel.playAudio(item, playlist)
    fun pauseAudio() = audioPlayerViewModel.pauseAudio()
    fun toggleAudioPlayPause() = audioPlayerViewModel.toggleAudioPlayPause()
    fun seekAudioTo(positionMs: Int) = audioPlayerViewModel.seekAudioTo(positionMs)
    fun playNextAudio() = audioPlayerViewModel.playNextAudio()
    fun playPreviousAudio() = audioPlayerViewModel.playPreviousAudio()
    fun toggleAudioExpanded() = audioPlayerViewModel.toggleAudioExpanded()
    fun toggleAudioShuffle() = audioPlayerViewModel.toggleAudioShuffle()
    fun toggleAudioRepeat() = audioPlayerViewModel.toggleAudioRepeat()
    fun closeAudioPlayer() = audioPlayerViewModel.closeAudioPlayer()

    // ==========================================
    // BUILT-IN VIDEO PLAYER METHODS
    // ==========================================

    fun playVideo(item: FileItem, playlist: List<FileItem> = emptyList()) {
        // Pause audio if currently running
        audioPlayerViewModel.pauseAudio()

        val fullList = if (playlist.isNotEmpty()) playlist else listOf(item)
        val idx = fullList.indexOfFirst { it.path == item.path }.coerceAtLeast(0)

        _videoPlayerState.value = VideoPlayerState(
            file = item.file,
            title = item.name,
            playlist = fullList,
            currentIndex = idx
        )
        navigateToScreen(Screen.VIDEO_PLAYER)
    }

    fun openVideoPlayer(file: File) {
        val item = FileItem(file)
        val fullList = file.parentFile?.listFiles()?.filter {
            it.isFile && it.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "3gp", "flv", "m4v", "wmv", "rmvb", "ts", "mpeg", "webm")
        }?.map { FileItem(it) } ?: listOf(item)
        playVideo(item, fullList)
    }

    fun playNextVideo() {
        val state = _videoPlayerState.value
        if (state.playlist.isEmpty()) return
        val nextIdx = (state.currentIndex + 1) % state.playlist.size
        val nextItem = state.playlist[nextIdx]
        playVideo(nextItem, state.playlist)
    }

    fun playPreviousVideo() {
        val state = _videoPlayerState.value
        if (state.playlist.isEmpty()) return
        val prevIdx = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.lastIndex
        val prevItem = state.playlist[prevIdx]
        playVideo(prevItem, state.playlist)
    }

    // ==========================================
    // SMART DIRECT FILE OPENER
    // (Bypasses "Open With" dialog for known extensions)
    // ==========================================

    fun openFileSmart(item: FileItem, siblingItems: List<FileItem> = emptyList()): Boolean {
        if (item.isDirectory) {
            loadDirectory(item.file, addToHistory = true)
            return true
        }

        val ext = item.extension.lowercase()

        // 1. PDF Documents
        if (ext == "pdf") {
            openPdfFile(item.file)
            return true
        }

        // 2. Audio Files
        if (item.category == FileCategory.AUDIO || ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "opus", "amr", "m4b", "mid", "midi")) {
            val audioSiblings = siblingItems.filter { it.category == FileCategory.AUDIO || it.extension.lowercase() in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "opus", "amr") }
            playAudio(item, if (audioSiblings.isNotEmpty()) audioSiblings else listOf(item))
            return true
        }

        // 3. Video Files
        if (item.category == FileCategory.VIDEO || ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "mpg", "mpeg")) {
            val videoSiblings = siblingItems.filter { it.category == FileCategory.VIDEO || it.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "ts", "mpg", "mpeg") }
            playVideo(item, if (videoSiblings.isNotEmpty()) videoSiblings else listOf(item))
            return true
        }

        // 4. Image Files
        if (item.category == FileCategory.IMAGE || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")) {
            val imageSiblings = siblingItems.filter { it.category == FileCategory.IMAGE }
            openImageViewer(item.file, if (imageSiblings.isNotEmpty()) imageSiblings else listOf(item))
            return true
        }

        // 5. Archive (ZIP, TAR, GZ, TGZ)
        if (ext in listOf("zip", "tar", "gz", "tgz", "jar") || item.name.lowercase().endsWith(".tar.gz")) {
            openZipViewer(item.file)
            return true
        }

        // 6. Text / Code / HTML / Markdown / Logs / JSON / XML / CSV
        if (item.category == FileCategory.CODE || ext in listOf(
                "txt", "log", "json", "xml", "html", "htm", "md", "csv",
                "kt", "java", "py", "js", "ts", "css", "c", "cpp", "h",
                "sh", "properties", "yaml", "yml", "sql", "conf", "ini",
                "gradle", "kts", "env", "bat"
            )
        ) {
            openTextEditor(item.file)
            return true
        }

        // 7. Split APKs Bundle (XAPK / APKS)
        if (ext in listOf("xapk", "apks")) {
            openXapkFile(item.file)
            return true
        }

        // 8. Regular APK Installation files
        if (item.category == FileCategory.APK || ext == "apk") {
            openApkInstallDialog(item.file)
            return true
        }

        // Non-previewable (documents like docx, unknown binary) -> return false so popup/chooser can be used
        return false
    }

    // ==========================================
    // 1. Color Tags & Labels
    // ==========================================
    fun loadTags() {
        viewModelScope.launch(Dispatchers.IO) {
            val map = tagsRepository.getFileTagsMap()
            _fileTagsMap.value = map
        }
    }

    fun toggleTagForFile(file: File, tagId: String) {
        viewModelScope.launch {
            tagsRepository.toggleTagForFile(file.absolutePath, tagId)
            loadTags()
        }
    }

    fun filterByTag(tagId: String?) {
        selectedTagFilter.value = tagId
        refreshCurrentDirectory()
    }

    // ==========================================
    // 2. Cloud & Network Drives (SMB / WebDAV / FTP)
    // ==========================================
    fun openNetworkDrives() {
        navigateToScreen(Screen.NETWORK_DRIVES)
        loadNetworkDrives()
    }

    fun loadNetworkDrives() {
        viewModelScope.launch {
            _networkDrives.value = networkStorageRepository.getSavedDrives()
        }
    }

    fun saveNetworkDrive(drive: NetworkDrive) {
        viewModelScope.launch {
            networkStorageRepository.saveDrive(drive)
            loadNetworkDrives()
            showMessage("Saved ${drive.name}")
        }
    }

    fun deleteNetworkDrive(id: String) {
        viewModelScope.launch {
            networkStorageRepository.deleteDrive(id)
            loadNetworkDrives()
            showMessage("Drive removed")
        }
    }

    fun connectNetworkDrive(drive: NetworkDrive) {
        viewModelScope.launch {
            _activeNetworkDrive.value = drive
            val initialPath = drive.remotePath.ifBlank { "/" }
            currentRemotePath.value = initialPath
            _remoteFiles.value = networkStorageRepository.listRemoteFiles(drive, initialPath)
        }
    }

    fun disconnectNetworkDrive() {
        _activeNetworkDrive.value = null
        currentRemotePath.value = "/"
        _remoteFiles.value = emptyList()
    }

    fun refreshRemoteFiles() {
        val drive = _activeNetworkDrive.value ?: return
        viewModelScope.launch {
            _remoteFiles.value = networkStorageRepository.listRemoteFiles(drive, currentRemotePath.value)
        }
    }

    fun navigateRemoteFolder(folder: RemoteFileItem) {
        val drive = _activeNetworkDrive.value ?: return
        val normalized = if (folder.path.startsWith("/")) folder.path else "/${folder.path}"
        currentRemotePath.value = normalized
        viewModelScope.launch {
            _remoteFiles.value = networkStorageRepository.listRemoteFiles(drive, normalized)
        }
    }

    fun navigateUpRemoteFolder(): Boolean {
        val drive = _activeNetworkDrive.value ?: return false
        val curr = currentRemotePath.value.trimEnd('/')
        val rootPath = drive.remotePath.trimEnd('/')
        if (curr.isEmpty() || curr == "/" || curr == rootPath) {
            return false
        }
        val parent = curr.substringBeforeLast('/', "").ifEmpty { "/" }
        currentRemotePath.value = parent
        viewModelScope.launch {
            _remoteFiles.value = networkStorageRepository.listRemoteFiles(drive, parent)
        }
        return true
    }

    fun scanLanForNetworkDrives() {
        if (isScanningLan.value) return
        viewModelScope.launch {
            isScanningLan.value = true
            showMessage("Scanning local Wi-Fi for SMB / FTP / WebDAV servers...")
            val localIp = fastShareRepository.getLocalIpAddress()
            val discovered = networkStorageRepository.scanLanServers(localIp)
            isScanningLan.value = false
            if (discovered.isNotEmpty()) {
                for (d in discovered) {
                    networkStorageRepository.saveDrive(d)
                }
                loadNetworkDrives()
                showMessage("Found and added ${discovered.size} server(s) on LAN!")
            } else {
                showMessage("No active SMB/FTP/WebDAV servers found on local subnet ($localIp)")
            }
        }
    }

    fun testNetworkDriveConnection(drive: NetworkDrive) {
        viewModelScope.launch {
            isTestingNetworkDrive.value = true
            val res = networkStorageRepository.testConnection(drive)
            isTestingNetworkDrive.value = false
            res.fold(
                onSuccess = { msg -> showMessage("✓ $msg") },
                onFailure = { err -> showMessage("✗ Connection failed: ${err.localizedMessage}") }
            )
        }
    }

    // ==========================================
    // 3. Fast Share (Direct Offline Wi-Fi P2P)
    // ==========================================
    fun openFastShare(files: List<FileItem> = emptyList()) {
        _fastShareState.update { it.copy(sharingFiles = files) }
        navigateToScreen(Screen.FAST_SHARE)
        if (files.isNotEmpty()) {
            startFastShare(files)
        }
    }

    fun addFilesToFastShare(files: List<FileItem>) {
        val current = _fastShareState.value.sharingFiles
        val combined = (current + files).distinctBy { it.path }
        _fastShareState.update { it.copy(sharingFiles = combined) }
        if (_fastShareState.value.isServerRunning) {
            // Restart with updated list
            startFastShare(combined)
        }
    }

    fun removeFileFromFastShare(file: FileItem) {
        val updated = _fastShareState.value.sharingFiles.filter { it.path != file.path }
        _fastShareState.update { it.copy(sharingFiles = updated) }
        if (_fastShareState.value.isServerRunning) {
            if (updated.isEmpty()) {
                stopFastShare()
            } else {
                startFastShare(updated)
            }
        }
    }

    fun startFastShare(files: List<FileItem>) {
        if (files.isEmpty()) {
            showMessage("Please select files to share")
            return
        }
        viewModelScope.launch {
            val ip = fastShareRepository.getLocalIpAddress()
            val res = fastShareRepository.startShareServer(files, port = 7777) { downloadedFile ->
                showMessage("Device downloaded: $downloadedFile")
            }
            res.fold(
                onSuccess = { (url, actualPort) ->
                    _fastShareState.value = FastShareState(
                        isServerRunning = true,
                        hostIp = ip,
                        port = actualPort,
                        sharingFiles = files
                    )
                    showMessage("Sharing active on $url")
                },
                onFailure = { err ->
                    showMessage("Failed to start transfer server: ${err.localizedMessage}")
                }
            )
        }
    }

    fun stopFastShare() {
        fastShareRepository.stopShareServer()
        _fastShareState.update { it.copy(isServerRunning = false) }
        showMessage("Transfer server stopped")
    }


    // ==========================================
    // 4. Multi-Storage Volumes (Internal, SD Card, USB OTG)
    // ==========================================
    fun loadStorageVolumes() {
        viewModelScope.launch(Dispatchers.IO) {
            val vols = fileRepository.getStorageVolumes()
            _storageVolumes.value = vols
            if (selectedVolume.value == null && vols.isNotEmpty()) {
                selectedVolume.value = vols.first()
            }
        }
    }

    fun switchStorageVolume(volume: StorageVolumeItem) {
        selectedVolume.value = volume
        loadDirectory(volume.file, addToHistory = true)
        showMessage("Switched to ${volume.name}")
    }

    // ==========================================
    // 5. Dual-Pane Split Screen Mode
    // ==========================================
    fun toggleDualPane() {
        val active = !isDualPaneActive.value
        isDualPaneActive.value = active
        if (active) {
            // Initialize Pane B with current folder or root
            _paneBState.value = StorageTabState(currentDir = _storageState.value.currentDir)
            loadDirectoryPaneB(_storageState.value.currentDir)
        }
    }

    fun setActivePane(index: Int) {
        activePaneIndex.value = index
    }

    fun navigatePaneB(dir: File) {
        val current = _paneBState.value
        val newStack = current.backStack + current.currentDir
        _paneBState.update { it.copy(currentDir = dir, backStack = newStack) }
        loadDirectoryPaneB(dir)
    }

    fun backPaneB() {
        val current = _paneBState.value
        if (current.backStack.isNotEmpty()) {
            val prev = current.backStack.last()
            val newStack = current.backStack.dropLast(1)
            _paneBState.update { it.copy(currentDir = prev, backStack = newStack) }
            loadDirectoryPaneB(prev)
        }
    }

    private fun loadDirectoryPaneB(dir: File) {
        viewModelScope.launch {
            val items = fileRepository.listFiles(dir, showHidden = false, sortType = SortType.NAME_ASC)
            _paneBState.update { it.copy(items = items, selectedItems = emptySet()) }
        }
    }

    fun toggleSelectPaneB(item: FileItem) {
        _paneBState.update { state ->
            val updated = if (state.selectedItems.contains(item)) {
                state.selectedItems - item
            } else {
                state.selectedItems + item
            }
            state.copy(selectedItems = updated)
        }
    }

    fun clearSelectionPaneB() {
        _paneBState.update { it.copy(selectedItems = emptySet()) }
    }

    fun copyPaneAtoB() {
        val sourceItems = _storageState.value.selectedItems.toList()
        if (sourceItems.isEmpty()) return
        val targetDir = _paneBState.value.currentDir
        viewModelScope.launch {
            fileRepository.copy(sourceItems, targetDir)
            showMessage("Copied ${sourceItems.size} items to Pane B (${targetDir.name})")
            clearSelection()
            loadDirectoryPaneB(targetDir)
            refreshCurrentDirectory()
        }
    }

    fun movePaneAtoB() {
        val sourceItems = _storageState.value.selectedItems.toList()
        if (sourceItems.isEmpty()) return
        val targetDir = _paneBState.value.currentDir
        viewModelScope.launch {
            fileRepository.move(sourceItems, targetDir)
            showMessage("Moved ${sourceItems.size} items to Pane B (${targetDir.name})")
            clearSelection()
            refreshCurrentDirectory()
            loadDirectoryPaneB(targetDir)
        }
    }

    fun copyPaneBtoA() {
        val sourceItems = _paneBState.value.selectedItems.toList()
        if (sourceItems.isEmpty()) return
        val targetDir = _storageState.value.currentDir
        viewModelScope.launch {
            fileRepository.copy(sourceItems, targetDir)
            showMessage("Copied ${sourceItems.size} items to Pane A (${targetDir.name})")
            _paneBState.update { it.copy(selectedItems = emptySet()) }
            refreshCurrentDirectory()
            loadDirectoryPaneB(_paneBState.value.currentDir)
        }
    }

    fun movePaneBtoA() {
        val sourceItems = _paneBState.value.selectedItems.toList()
        if (sourceItems.isEmpty()) return
        val targetDir = _storageState.value.currentDir
        viewModelScope.launch {
            fileRepository.move(sourceItems, targetDir)
            showMessage("Moved ${sourceItems.size} items to Pane A (${targetDir.name})")
            _paneBState.update { it.copy(selectedItems = emptySet()) }
            loadDirectoryPaneB(_paneBState.value.currentDir)
            refreshCurrentDirectory()
        }
    }

    // ==========================================
    // 6. Split APKs / XAPK Installer
    // ==========================================
    fun openXapkFile(file: File) {
        viewModelScope.launch {
            val res = XapkInstaller.parseXapk(file, getApplication())
            res.fold(
                onSuccess = { info ->
                    xapkInstallTarget.value = info
                },
                onFailure = { err ->
                    showMessage("Cannot parse XAPK/APKS: ${err.localizedMessage}")
                }
            )
        }
    }

    fun closeXapkDialog() {
        xapkInstallTarget.value = null
        isInstallingXapk.value = false
        xapkInstallProgress.value = ""
    }

    fun installActiveXapk() {
        val info = xapkInstallTarget.value ?: return
        viewModelScope.launch {
            isInstallingXapk.value = true
            xapkInstallProgress.value = "Preparing split installation..."
            val res = XapkInstaller.installXapk(
                context = getApplication(),
                xapkInfo = info,
                onProgress = { p, step ->
                    xapkInstallProgress.value = step
                }
            )
            isInstallingXapk.value = false
            res.fold(
                onSuccess = {
                    showMessage("Split APK installation started! Follow system prompt.")
                    closeXapkDialog()
                },
                onFailure = { err ->
                    showMessage("Split APK installation failed: ${err.localizedMessage}")
                }
            )
        }
    }

    // ==========================================
    // 7. Biometric Vault Unlock (Delegated to VaultViewModel)
    // ==========================================
    fun toggleBiometricVault(enabled: Boolean) = vaultViewModel.toggleBiometricVault(enabled)
    fun unlockVaultWithBiometrics() = vaultViewModel.unlockVaultWithBiometrics()

    // ==========================================
    // 8. Recycle Bin Retention Auto-Purge
    // ==========================================
    fun downloadNetworkFile(drive: NetworkDrive, remoteFile: RemoteFileItem) {
        viewModelScope.launch {
            showMessage("Downloading ${remoteFile.name}...")
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "CentExplorer/Network").apply { mkdirs() }
            val targetFile = File(targetDir, remoteFile.name)
            val res = networkStorageRepository.downloadRemoteFile(drive, remoteFile, targetFile)
            res.fold(
                onSuccess = { file ->
                    showMessage("Saved to Downloads/CentExplorer/Network/${file.name}")
                    refreshCurrentDirectory()
                },
                onFailure = { err ->
                    showMessage("Download failed: ${err.localizedMessage}")
                }
            )
        }
    }

    fun purgeTrashExpired(retentionDays: Int = 30) {
        viewModelScope.launch {
            val purgedCount = trashRepository.purgeExpiredItems(retentionDays)
            if (purgedCount > 0) {
                showMessage("Auto-purged $purgedCount items older than $retentionDays days")
                loadTrashItems()
            }
        }
    }

    // ==========================================
    // 8. WIRELESS WEB SHARE METHODS (Delegated to NetworkServerViewModel)
    // ==========================================

    fun openWebShare() = networkServerViewModel.openWebShare()
    fun toggleWebShare() = networkServerViewModel.toggleWebShare()

    // ==========================================
    // 9. FILE SHREDDER METHODS
    // ==========================================

    fun openFileShredder(files: List<File> = emptyList()) {
        shredTargets.value = files
        navigateToScreen(Screen.FILE_SHREDDER)
    }

    fun openFileSelectorForShredder() {
        val items = _storageState.value.selectedItems.map { it.file }
        if (items.isNotEmpty()) {
            shredTargets.value = (shredTargets.value + items).distinctBy { it.absolutePath }
        } else {
            val nonDirs = _storageState.value.items.filter { !it.isDirectory }.take(3).map { it.file }
            shredTargets.value = (shredTargets.value + nonDirs).distinctBy { it.absolutePath }
            showMessage("Added files from current folder. You can add more!")
        }
    }

    fun removeShredTarget(file: File) {
        shredTargets.value = shredTargets.value.filter { it.absolutePath != file.absolutePath }
    }

    fun startShredding(method: ShredMethod) {
        val targets = shredTargets.value
        if (targets.isEmpty()) return

        viewModelScope.launch {
            isShredding.value = true
            shredProgress.value = ShredProgress(totalFiles = targets.size)
            val result = FileShredderHelper.shredFiles(targets, method) { p ->
                shredProgress.value = p
            }
            isShredding.value = false
            if (result.isSuccess) {
                showMessage("Successfully shredded ${result.getOrNull()} file(s) permanently")
                shredTargets.value = emptyList()
                refreshCurrentDirectory()
                refreshStorage()
            } else {
                showMessage("Shredding error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    // ==========================================
    // 11. SMART COLLECTIONS METHODS
    // ==========================================

    fun openSmartCollections() {
        closeActiveCollection()
        navigateToScreen(Screen.SMART_COLLECTIONS)
        loadSmartCollections()
    }

    fun loadSmartCollections() {
        viewModelScope.launch {
            smartCollections.value = smartCollectionsRepository.getCollections()
        }
    }

    fun openCollection(coll: SmartCollection) {
        viewModelScope.launch {
            isCollectionLoading.value = true
            activeCollectionFiles.value = smartCollectionsRepository.loadCollectionFiles(coll)
            isCollectionLoading.value = false
        }
    }

    fun closeActiveCollection() {
        activeCollectionFiles.value = null
    }

    fun createCustomCollection(title: String, description: String) {
        viewModelScope.launch {
            val created = smartCollectionsRepository.createCustomCollection(
                title = title,
                description = description,
                colorHex = "#FF6700",
                iconTag = "folder"
            )
            loadSmartCollections()
            showMessage("Created collection \"${created.title}\"")
        }
    }

    fun addFileToCollection(collectionId: String, filePath: String) {
        viewModelScope.launch {
            if (smartCollectionsRepository.addFileToCollection(collectionId, filePath)) {
                showMessage("Added file to collection!")
                loadSmartCollections()
            } else {
                showMessage("File is already in this collection")
            }
        }
    }

    fun removeFileFromCollection(collectionId: String, filePath: String) {
        viewModelScope.launch {
            if (smartCollectionsRepository.removeFileFromCollection(collectionId, filePath)) {
                showMessage("Removed file from collection")
                activeCollectionFiles.value?.let { curr ->
                    activeCollectionFiles.value = smartCollectionsRepository.loadCollectionFiles(curr.collection)
                }
                loadSmartCollections()
            }
        }
    }

    // ==========================================
    // 12. STORAGE TIME MACHINE METHODS
    // ==========================================

    fun openTimeMachine() {
        navigateToScreen(Screen.TIME_MACHINE)
        refreshTimeMachine()
    }

    fun refreshTimeMachine() {
        viewModelScope.launch {
            isTimeMachineLoading.value = true
            timeMachineData.value = timeMachineRepository.analyzeTimeMachine()
            isTimeMachineLoading.value = false
        }
    }

    fun deleteFilesPermanently(files: List<File>) {
        viewModelScope.launch {
            var count = 0
            for (f in files) {
                if (f.delete()) count++
            }
            showMessage("Deleted $count file(s)")
            refreshCurrentDirectory()
            refreshStorage()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerViewModel.closeAudioPlayer()
        vaultViewModel.lockVault()
        networkServerViewModel.stopAllServers()
        if (_fastShareRepository.isInitialized()) {
            runCatching { _fastShareRepository.value.stopShareServer() }
        }
    }

    fun showMessage(msg: String) {
        _message.value = msg
        AppEventBus.showMessage(msg)
    }

    fun clearMessage() {
        _message.value = null
    }
}
