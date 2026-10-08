package com.ct.explorer.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.textEditorState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    BackHandler {
        viewModel.handleBackPress()
    }

    val displayTitle = state.title.ifEmpty { state.file?.name ?: "Text Viewer" }

    Scaffold(
        modifier = modifier.testTag("text_editor_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                            if (state.isModified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Edited",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CtOrange
                                )
                            }
                        }
                        Text(
                            text = if (state.showHtmlPreview) "HTML Web Preview" else "${state.lineCount} lines, ${state.charCount} characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("text_editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // HTML Preview vs Code Toggle button
                    if (state.isHtmlMode) {
                        IconButton(onClick = { viewModel.toggleHtmlPreview() }) {
                            Icon(
                                imageVector = if (state.showHtmlPreview) Icons.Default.Code else Icons.Default.Language,
                                contentDescription = if (state.showHtmlPreview) "Show Code" else "Show HTML Preview",
                                tint = CtOrange
                            )
                        }
                    }

                    if (!state.showHtmlPreview) {
                        IconButton(
                            onClick = { viewModel.toggleEditorWordWrap() },
                            modifier = Modifier.testTag("text_editor_wrap_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.WrapText,
                                contentDescription = "Toggle Wrap",
                                tint = if (state.wordWrap) CtOrange else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = { viewModel.saveEditorFile() },
                            enabled = !state.isSaving && !state.isReadOnly,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isReadOnly) MaterialTheme.colorScheme.surfaceVariant else CtOrange,
                                contentColor = if (state.isReadOnly) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(end = 8.dp).testTag("text_editor_save_button")
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            } else if (state.isReadOnly) {
                                Text("Read Only")
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.showHtmlPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, state.content, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val lineCount = state.lineCount.coerceIn(1, 3000)
                val lineNumbersText = remember(lineCount) {
                    (1..lineCount).joinToString("\n")
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Line numbers gutter
                    Text(
                        text = lineNumbersText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )

                    val editorModifier = if (state.wordWrap) {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScrollState)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    }

                    BasicTextField(
                        value = state.content,
                        onValueChange = { viewModel.updateEditorContent(it) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(CtOrange),
                        modifier = editorModifier.testTag("text_editor_field")
                    )
                }
            }
        }
    }
}
