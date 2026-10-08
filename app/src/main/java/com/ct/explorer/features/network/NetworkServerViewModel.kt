package com.ct.explorer.features.network

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.base.BaseFeatureViewModel
import com.ct.explorer.core.navigation.NavigationManager
import com.ct.explorer.core.navigation.Screen
import com.ct.explorer.data.repository.FastShareRepository
import com.ct.explorer.data.repository.FileRepository
import com.ct.explorer.utils.FtpServer
import com.ct.explorer.utils.webshare.WebShareServer
import com.ct.explorer.utils.webshare.WebShareState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class FtpServerState(
    val isRunning: Boolean = false,
    val port: Int = 2121,
    val ipAddress: String = "192.168.1.108"
) {
    val url: String get() = "ftp://$ipAddress:$port"
}

class NetworkServerViewModel(application: Application) : BaseFeatureViewModel(application) {

    private val fastShareRepository = FastShareRepository(application)
    private val fileRepository = FileRepository(application)

    // FTP Server
    private var activeFtpServer: FtpServer? = null
    private val _ftpServerState = MutableStateFlow(FtpServerState())
    val ftpServerState: StateFlow<FtpServerState> = _ftpServerState.asStateFlow()

    // WebShare Server
    private val _webShareServer = lazy {
        WebShareServer(application).apply {
            onStateChanged = { state ->
                _webShareState.value = state
            }
        }
    }
    private val webShareServer get() = _webShareServer.value
    private val _webShareState = MutableStateFlow(WebShareState(ipAddress = ""))
    val webShareState: StateFlow<WebShareState> = _webShareState.asStateFlow()

    fun openFtpServer() {
        if (!_ftpServerState.value.isRunning) {
            val ip = fastShareRepository.getLocalIpAddress()
            _ftpServerState.update { it.copy(ipAddress = ip) }
        }
        NavigationManager.navigateTo(Screen.FTP_SERVER)
    }

    fun toggleFtpServer() {
        if (_ftpServerState.value.isRunning) {
            activeFtpServer?.stop()
            activeFtpServer = null
            _ftpServerState.update { it.copy(isRunning = false) }
            showMessage("FTP Service stopped")
        } else {
            val ip = fastShareRepository.getLocalIpAddress()
            val server = FtpServer(
                rootDir = fileRepository.rootStorageDirectory,
                port = 2121,
                onClientConnected = { clientIp ->
                    viewModelScope.launch(Dispatchers.Main) {
                        showMessage("PC connected from $clientIp")
                    }
                }
            )
            if (server.start()) {
                activeFtpServer = server
                _ftpServerState.value = FtpServerState(isRunning = true, port = 2121, ipAddress = ip)
                showMessage("FTP Service started on ftp://$ip:2121")
            } else {
                showMessage("Failed to bind FTP Server on port 2121")
            }
        }
    }

    fun openWebShare() {
        if (!_webShareState.value.isRunning) {
            launchSafe {
                val ip = webShareServer.getLocalIpAddress()
                _webShareState.update { it.copy(ipAddress = ip) }
            }
        }
        NavigationManager.navigateTo(Screen.WEB_SHARE)
    }

    fun toggleWebShare() {
        if (_webShareState.value.isRunning) {
            webShareServer.stop()
            showMessage("Web Share stopped")
        } else {
            if (webShareServer.start()) {
                showMessage("Web Share started! Open in any browser: ${_webShareState.value.serverUrl}")
            } else {
                showMessage("Failed to start Web Share server")
            }
        }
    }

    fun handleBackPress(): Boolean {
        return NavigationManager.popBackStack()
    }

    fun stopAllServers() {
        runCatching { activeFtpServer?.stop() }
        activeFtpServer = null
        if (_webShareServer.isInitialized()) {
            runCatching { _webShareServer.value.stop() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllServers()
    }
}
