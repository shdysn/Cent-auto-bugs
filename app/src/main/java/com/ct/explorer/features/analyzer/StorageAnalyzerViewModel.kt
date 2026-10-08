package com.ct.explorer.features.analyzer

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.base.BaseFeatureViewModel
import com.ct.explorer.core.navigation.NavigationManager
import com.ct.explorer.core.navigation.Screen
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.model.StorageSpace
import com.ct.explorer.data.repository.CategoryUsage
import com.ct.explorer.data.repository.FileRepository
import com.ct.explorer.data.repository.StorageAnalysisResult
import com.ct.explorer.data.repository.StorageAnalyzerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StorageAnalyzerViewModel(application: Application) : BaseFeatureViewModel(application) {

    private val storageAnalyzerRepository = StorageAnalyzerRepository(application)
    private val fileRepository = FileRepository(application)

    private val _storageAnalysisResult = MutableStateFlow<StorageAnalysisResult?>(null)
    val storageAnalysisResult: StateFlow<StorageAnalysisResult?> = _storageAnalysisResult.asStateFlow()

    val isStorageAnalyzing = MutableStateFlow(false)

    private val _storageSpace = MutableStateFlow(StorageSpace(0L, 0L, 0L))
    val storageSpace: StateFlow<StorageSpace> = _storageSpace.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryUsage>>(emptyList())
    val categories: StateFlow<List<CategoryUsage>> = _categories.asStateFlow()

    private val _largeFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val largeFiles: StateFlow<List<FileItem>> = _largeFiles.asStateFlow()

    fun openStorageAnalyzer() {
        NavigationManager.navigateTo(Screen.STORAGE_ANALYZER)
        analyzeStorage()
    }

    fun refreshStorageSpace() {
        viewModelScope.launch(Dispatchers.IO) {
            val space = fileRepository.getStorageSpace()
            _storageSpace.value = space
        }
    }

    fun analyzeStorageCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = storageAnalyzerRepository.analyzeStorage()
            _categories.value = result.categories
        }
    }

    fun loadLargeFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = storageAnalyzerRepository.analyzeStorage()
            _largeFiles.value = result.largestFiles
        }
    }

    fun analyzeStorage() {
        viewModelScope.launch {
            isStorageAnalyzing.value = true
            val result = storageAnalyzerRepository.analyzeStorage()
            _storageAnalysisResult.value = result
            _categories.value = result.categories
            _largeFiles.value = result.largestFiles
            _storageSpace.value = StorageSpace(
                totalBytes = result.totalBytes,
                usedBytes = result.usedBytes,
                freeBytes = result.freeBytes
            )
            isStorageAnalyzing.value = false
        }
    }
}
