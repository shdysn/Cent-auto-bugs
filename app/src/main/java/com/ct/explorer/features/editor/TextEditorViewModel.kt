package com.ct.explorer.features.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.ct.explorer.core.base.BaseFeatureViewModel
import com.ct.explorer.core.navigation.NavigationManager
import com.ct.explorer.core.navigation.Screen
import com.ct.explorer.data.repository.FileRepository
import com.ct.explorer.ui.viewmodel.TextEditorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class TextEditorViewModel(application: Application) : BaseFeatureViewModel(application) {

    private val fileRepository = FileRepository(application)

    private val _textEditorState = MutableStateFlow(TextEditorState())
    val textEditorState: StateFlow<TextEditorState> = _textEditorState.asStateFlow()

    fun openTextFile(file: File) {
        viewModelScope.launch {
            val directRead = fileRepository.readText(file).getOrNull()
            val rawContent = directRead ?: ""
            val maxSafeChars = 150_000
            val isOversized = rawContent.length > maxSafeChars
            val content = if (isOversized) {
                rawContent.take(maxSafeChars) + "\n\n... [File exceeds 150 KB — opened in Read-Only Mode to protect original data] ..."
            } else {
                rawContent
            }
            val isHtml = file.extension.lowercase() in listOf("html", "htm")
            _textEditorState.value = TextEditorState(
                file = file,
                title = file.name,
                content = content,
                originalContent = content,
                isHtmlMode = isHtml,
                showHtmlPreview = isHtml,
                isReadOnly = isOversized
            )
            if (isOversized) {
                showMessage("Opened in Read-Only Mode to protect original file from truncation")
            }
            NavigationManager.navigateTo(Screen.TEXT_EDITOR)
        }
    }

    fun openTextEditor(file: File) = openTextFile(file)

    fun openTextFromUri(uri: Uri, displayName: String) {
        viewModelScope.launch {
            val content = try {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: ""
            } catch (e: Exception) {
                ""
            }

            val isHtml = displayName.endsWith(".html", ignoreCase = true) || displayName.endsWith(".htm", ignoreCase = true)

            _textEditorState.value = TextEditorState(
                file = null,
                sourceUri = uri,
                title = displayName,
                content = content,
                originalContent = content,
                isHtmlMode = isHtml,
                showHtmlPreview = isHtml
            )
            NavigationManager.navigateTo(Screen.TEXT_EDITOR)
        }
    }

    fun updateTextContent(newContent: String) {
        _textEditorState.update { it.copy(content = newContent) }
    }

    fun updateEditorContent(newContent: String) = updateTextContent(newContent)

    fun toggleHtmlPreview() {
        _textEditorState.update { it.copy(showHtmlPreview = !it.showHtmlPreview) }
    }

    fun toggleEditorWordWrap() {
        _textEditorState.update { it.copy(wordWrap = !it.wordWrap) }
    }

    fun saveTextFile() {
        val state = _textEditorState.value
        if (state.isReadOnly) {
            showMessage("File is in Read-Only Mode to prevent data truncation.")
            return
        }
        viewModelScope.launch {
            _textEditorState.update { it.copy(isSaving = true) }
            val success = if (state.file != null) {
                fileRepository.writeText(state.file, state.content).isSuccess
            } else if (state.sourceUri != null) {
                try {
                    appContext.contentResolver.openOutputStream(state.sourceUri, "wt")?.use { out ->
                        out.bufferedWriter(Charsets.UTF_8).use { it.write(state.content) }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            } else false

            if (success) {
                _textEditorState.update { it.copy(originalContent = it.content, isSaving = false) }
                showMessage("Saved successfully")
            } else {
                _textEditorState.update { it.copy(isSaving = false) }
                showMessage("Failed to save file")
            }
        }
    }

    fun saveEditorFile() = saveTextFile()
}
