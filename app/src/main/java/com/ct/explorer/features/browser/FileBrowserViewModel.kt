package com.ct.explorer.features.browser

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.base.BaseFeatureViewModel
import com.ct.explorer.data.model.ClipboardState
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.SortType
import com.ct.explorer.data.model.ViewMode
import com.ct.explorer.data.model.sortFileList
import com.ct.explorer.data.repository.FileRepository
import com.ct.explorer.ui.viewmodel.StorageTabState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class FileBrowserViewModel(application: Application) : BaseFeatureViewModel(application) {

    private val fileRepository = FileRepository(application)
    private val initialDir: File = fileRepository.rootStorageDirectory

    private val _storageState = MutableStateFlow(StorageTabState(currentDir = initialDir))
    val storageState: StateFlow<StorageTabState> = _storageState.asStateFlow()

    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    init {
        loadDirectory(initialDir)
    }

    fun navigateToFolder(dir: File, addToHistory: Boolean = true) {
        loadDirectory(dir, addToHistory = addToHistory)
    }

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

    fun goBackInDirectory() {
        val current = _storageState.value
        if (current.backStack.isNotEmpty()) {
            val prev = current.backStack.last()
            val newBackStack = current.backStack.dropLast(1)
            val newForwardStack = current.forwardStack + current.currentDir

            viewModelScope.launch(Dispatchers.IO) {
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

    fun loadDirectory(dir: File, addToHistory: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _storageState.value
            val isSameDir = current.currentDir.absolutePath == dir.absolutePath

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

    fun refreshCurrentDirectory() {
        FileRepository.invalidateAllFileCaches()
        loadDirectory(_storageState.value.currentDir, addToHistory = false)
    }

    // Clipboard Operations
    fun copyItems(items: List<FileItem>) {
        if (items.isNotEmpty()) {
            _clipboard.value = ClipboardState(items = items, isCut = false)
            clearSelection()
            showMessage("${items.size} item(s) copied")
        }
    }

    fun cutItems(items: List<FileItem>) {
        if (items.isNotEmpty()) {
            _clipboard.value = ClipboardState(items = items, isCut = true)
            clearSelection()
            showMessage("${items.size} item(s) cut to clipboard")
        }
    }

    fun copySelected() {
        copyItems(_storageState.value.selectedItems.toList())
    }

    fun cutSelected() {
        cutItems(_storageState.value.selectedItems.toList())
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

    fun pasteItems(destDir: File = _storageState.value.currentDir) {
        val clip = _clipboard.value ?: return

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

    fun pasteToCurrentDirectory() = pasteItems(_storageState.value.currentDir)

    // Selection & View utilities
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

    fun setSearchQuery(query: String) {
        _storageState.update { it.copy(searchQuery = query) }
        loadDirectory(_storageState.value.currentDir)
    }
}
