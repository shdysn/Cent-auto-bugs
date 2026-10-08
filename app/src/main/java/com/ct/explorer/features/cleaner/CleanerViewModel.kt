package com.ct.explorer.features.cleaner

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.base.BaseFeatureViewModel
import com.ct.explorer.core.navigation.NavigationManager
import com.ct.explorer.core.navigation.Screen
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.repository.CleanScanResult
import com.ct.explorer.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CleanerViewModel(application: Application) : BaseFeatureViewModel(application) {

    private val fileRepository = FileRepository(application)

    private val _cleanScan = MutableStateFlow<CleanScanResult?>(null)
    val cleanScan: StateFlow<CleanScanResult?> = _cleanScan.asStateFlow()

    val isCleaning = MutableStateFlow(false)
    val isCleanScanning = MutableStateFlow(false)
    val cleanedBytes = MutableStateFlow<Long?>(null)

    fun openCleaner() {
        NavigationManager.navigateTo(Screen.CLEANER)
        startCleanScan()
    }

    fun startCleanScan() {
        viewModelScope.launch {
            isCleanScanning.value = true
            cleanedBytes.value = null
            val result = fileRepository.scanForClean()
            _cleanScan.value = result
            isCleanScanning.value = false
        }
    }

    fun cleanSelectedJunk() {
        val scan = _cleanScan.value ?: return
        viewModelScope.launch {
            isCleaning.value = true
            var bytesCleaned = 0L
            for (item in scan.junkFiles) {
                val size = item.size
                if (fileRepository.delete(item).getOrDefault(false)) {
                    bytesCleaned += size
                }
            }
            cleanedBytes.value = bytesCleaned
            _cleanScan.value = scan.copy(junkFiles = emptyList())
            isCleaning.value = false
            showMessage("Cleaned ${FileItem.formatBytes(bytesCleaned)} of junk files")
        }
    }

    fun cleanEmptyFolders(folders: List<FileItem>? = null) {
        val targetFolders = folders ?: _cleanScan.value?.emptyFolders ?: emptyList()
        if (targetFolders.isEmpty()) return
        deleteEmptyFolders(targetFolders)
    }

    fun performClean() = cleanSelectedJunk()

    fun deleteEmptyFolders(folders: List<FileItem>) {
        if (folders.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            for (f in folders) {
                if (fileRepository.deleteEmptyFolder(f).getOrDefault(false)) count++
            }
            showMessage("Removed $count empty folder(s)")
            startCleanScan()
        }
    }

    fun deleteLargeFiles(items: List<FileItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            var bytesFreed = 0L
            for (item in items) {
                val size = item.size
                if (fileRepository.delete(item).getOrDefault(false)) {
                    count++
                    bytesFreed += size
                }
            }
            showMessage("Deleted $count large file(s) (Freed ${FileItem.formatBytes(bytesFreed)})")
            startCleanScan()
        }
    }

    fun deleteApkPackages(items: List<FileItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            for (item in items) {
                if (fileRepository.delete(item).getOrDefault(false)) count++
            }
            showMessage("Deleted $count APK package(s)")
            startCleanScan()
        }
    }
}
