package com.ct.explorer.core.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

sealed interface AppEvent {
    data class ShowMessage(val message: String) : AppEvent
    data class FileChanged(val action: FileAction, val file: File, val targetFile: File? = null) : AppEvent
}

enum class FileAction {
    CREATED,
    DELETED,
    RENAMED,
    MOVED,
    RESTORED,
    TRASHED,
    SHREDDED
}

/**
 * Universal Event Bus for decoupling communication across modules
 * (e.g. Vault notifying file refresh, Cleaner notifying space freed, etc.)
 */
object AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    fun showMessage(message: String) {
        _events.tryEmit(AppEvent.ShowMessage(message))
    }

    fun notifyFileChanged(action: FileAction, file: File, targetFile: File? = null) {
        _events.tryEmit(AppEvent.FileChanged(action, file, targetFile))
    }
}
