package com.ct.explorer.core.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.events.AppEventBus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Robust base ViewModel for all isolated feature modules.
 * Provides safe coroutine launching with automatic exception handling and message dispatch.
 */
abstract class BaseFeatureViewModel(application: Application) : AndroidViewModel(application) {

    protected val appContext: Application get() = getApplication()

    protected fun launchSafe(
        scope: CoroutineScope = viewModelScope,
        errorMessage: String? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val handler = CoroutineExceptionHandler { _, throwable ->
            val displayError = errorMessage ?: throwable.localizedMessage ?: "Unexpected error occurred"
            AppEventBus.showMessage(displayError)
        }
        return scope.launch(Dispatchers.IO + handler) {
            block()
        }
    }

    fun showMessage(msg: String) {
        AppEventBus.showMessage(msg)
    }
}
