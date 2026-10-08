package com.ct.explorer.features.vault

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.base.BaseFeatureViewModel
import com.ct.explorer.core.events.AppEventBus
import com.ct.explorer.core.events.FileAction
import com.ct.explorer.core.navigation.NavigationManager
import com.ct.explorer.core.navigation.Screen
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.data.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class VaultViewModel(application: Application) : BaseFeatureViewModel(application) {

    val vaultRepository = VaultRepository(application)

    val isVaultPinSet = MutableStateFlow(false)
    val isVaultUnlocked = MutableStateFlow(false)
    private val _vaultFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val vaultFiles: StateFlow<List<FileItem>> = _vaultFiles.asStateFlow()
    val isVaultLoading = MutableStateFlow(false)
    val isBiometricVaultEnabled = MutableStateFlow(false)

    init {
        isVaultPinSet.value = vaultRepository.isPinSet()
        isBiometricVaultEnabled.value = vaultRepository.isBiometricEnabled()
    }

    fun openVault() {
        isVaultPinSet.value = vaultRepository.isPinSet()
        isBiometricVaultEnabled.value = vaultRepository.isBiometricEnabled()
        NavigationManager.navigateTo(Screen.VAULT)
    }

    fun setupVaultPin(pin: String, answer: String) {
        vaultRepository.setPin(pin, answer)
        isVaultPinSet.value = true
        isVaultUnlocked.value = true
        loadVaultFiles()
        showMessage("Vault PIN set successfully")
    }

    fun unlockVault(pin: String): Boolean {
        val valid = vaultRepository.verifyPin(pin)
        if (valid) {
            isVaultUnlocked.value = true
            loadVaultFiles()
        }
        return valid
    }

    fun resetVaultPinWithAnswer(answer: String, newPin: String): Boolean {
        val success = vaultRepository.resetPinWithSecurityAnswer(answer, newPin)
        if (success) {
            isVaultPinSet.value = true
            isVaultUnlocked.value = true
            loadVaultFiles()
        }
        return success
    }

    fun openVaultFilePreview(item: FileItem, onReady: (FileItem) -> Unit) {
        launchSafe(errorMessage = "Could not decrypt vault file for preview") {
            val decrypted = vaultRepository.decryptToTempCacheFile(item.file)
            if (decrypted != null && decrypted.exists()) {
                onReady(FileItem(decrypted))
            } else {
                showMessage("Could not decrypt vault file for preview")
            }
        }
    }

    fun lockVault() {
        isVaultUnlocked.value = false
        _vaultFiles.value = emptyList()
        vaultRepository.clearTempPreviewCache()
        showMessage("Vault locked")
    }

    fun loadVaultFiles() {
        launchSafe {
            isVaultLoading.value = true
            _vaultFiles.value = vaultRepository.getVaultFiles()
            isVaultLoading.value = false
        }
    }

    fun addFileToVault(item: FileItem, onFileMoved: (() -> Unit)? = null) {
        launchSafe {
            val success = vaultRepository.addToVault(item.file)
            if (success) {
                showMessage("Moved \"${item.name}\" to Private Vault")
                AppEventBus.notifyFileChanged(FileAction.MOVED, item.file)
                onFileMoved?.invoke()
                if (isVaultUnlocked.value) loadVaultFiles()
            } else {
                showMessage("Failed to move file to Vault")
            }
        }
    }

    fun addFilesToVault(items: List<FileItem>, onFilesMoved: (() -> Unit)? = null) {
        if (items.isEmpty()) return
        launchSafe {
            var count = 0
            for (item in items) {
                if (vaultRepository.addToVault(item.file)) {
                    count++
                    AppEventBus.notifyFileChanged(FileAction.MOVED, item.file)
                }
            }
            showMessage("Moved $count item(s) to Private Vault")
            onFilesMoved?.invoke()
            if (isVaultUnlocked.value) loadVaultFiles()
        }
    }

    fun restoreFileFromVault(item: FileItem, onRestored: (() -> Unit)? = null) {
        launchSafe {
            val target = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).let {
                File(it, "Restored")
            }
            val success = vaultRepository.restoreFromVault(item.file, target)
            if (success) {
                showMessage("Restored to Downloads/Restored")
                AppEventBus.notifyFileChanged(FileAction.RESTORED, item.file, File(target, item.name))
                loadVaultFiles()
                onRestored?.invoke()
            } else {
                showMessage("Failed to restore file")
            }
        }
    }

    fun deleteFileFromVault(item: FileItem) {
        launchSafe {
            val success = vaultRepository.deleteFromVault(item.file)
            if (success) {
                showMessage("Deleted from Vault")
                AppEventBus.notifyFileChanged(FileAction.DELETED, item.file)
                loadVaultFiles()
            }
        }
    }

    fun toggleBiometricVault(enabled: Boolean) {
        vaultRepository.setBiometricEnabled(enabled)
        isBiometricVaultEnabled.value = enabled
        showMessage(if (enabled) "Biometric fingerprint unlock enabled" else "Biometric unlock disabled")
    }

    fun unlockVaultWithBiometrics() {
        launchSafe {
            _vaultFiles.value = vaultRepository.getVaultFiles()
            isVaultUnlocked.value = true
            showMessage("Vault unlocked with fingerprint")
        }
    }

    fun handleBackPress(): Boolean {
        lockVault()
        return NavigationManager.popBackStack()
    }

    override fun onCleared() {
        super.onCleared()
        lockVault()
    }
}
