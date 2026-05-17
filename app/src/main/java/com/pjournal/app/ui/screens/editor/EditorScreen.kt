package com.pjournal.app.ui.screens.editor

import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pjournal.app.data.PreferencesManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    promptText: String?,
    editFilename: String? = null,
    isPromptMode: Boolean = false,
    focusMode: Boolean,
    onDone: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var text by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var wordCount by remember { mutableStateOf(0) }
    var showPrompt by remember { mutableStateOf(true) }

    val currentPrompt = state.prompt ?: promptText
    val prefs = remember { PreferencesManager(context) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Fullscreen: both free and prompted writing; full focus mode: only free writing
    val isFreeWrite = editFilename == null && !isPromptMode
    val useFullscreen = focusMode
    val effectiveFocus = focusMode && isFreeWrite

    val horizontalPadding = when {
        effectiveFocus -> 16.dp
        isLandscape -> 80.dp
        else -> 20.dp
    }

    // Editor font preferences
    val editorFont by prefs.editorFont.collectAsStateWithLifecycle(initialValue = "default")
    val editorFontSize by prefs.editorFontSize.collectAsStateWithLifecycle(initialValue = "16")
    val fontFamily = when (editorFont) {
        "serif" -> FontFamily.Serif
        "sans" -> FontFamily.SansSerif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    val fontSize = editorFontSize.toIntOrNull()?.sp ?: 16.sp

    LaunchedEffect(useFullscreen) {
        if (useFullscreen) {
            val activity = context as? ComponentActivity
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (useFullscreen) {
                val activity = context as? ComponentActivity
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setInitialPrompt(promptText)
        focusRequester.requestFocus()
    }

    // Auto-select built-in prompt for prompted writing
    LaunchedEffect(isPromptMode) {
        if (isPromptMode && state.prompt == null && promptText == null) {
            viewModel.setInitialPrompt(com.pjournal.app.data.BuiltInPrompts.random())
        }
    }

    // Load existing entry for editing
    LaunchedEffect(editFilename) {
        if (editFilename != null) {
            val body = viewModel.loadEntryForEdit(editFilename)
            if (body != null) text = body
        }
    }

    LaunchedEffect(text) {
        wordCount = text.count { it in '一'..'鿿' || it in '　'..'〿' || it in '＀'..'￯' } +
            Regex("[a-zA-Z]+").findAll(text).count()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃这篇日记？") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onNavigateBack() }) {
                    Text("放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续写")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        if (effectiveFocus) {
            FocusModeEditorTopBar(
                visible = !showPrompt && text.isNotBlank(),
                wordCount = wordCount,
                onBack = {
                    if (text.isNotBlank()) showDiscardDialog = true
                    else onNavigateBack()
                }
            )
        } else {
            val hasDeepseek by prefs.getStringFlow("deepseek_api_key")
                .collectAsStateWithLifecycle(initialValue = "")
            val hasFlomo by prefs.getStringFlow("flomo_email")
                .collectAsStateWithLifecycle(initialValue = "")

            TopAppBar(
                title = {
                    Text(
                        if (editFilename != null) {
                            try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
                                val cleanName = editFilename.removeSuffix(".txt")
                                val dt = sdf.parse(cleanName)
                                "编辑 · " + SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(dt!!)
                            } catch (_: Exception) { "编辑" }
                        } else {
                            SimpleDateFormat("M月d日", Locale.getDefault()).format(Date())
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (text.isNotBlank()) showDiscardDialog = true
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (hasDeepseek.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.generateAiPrompt() },
                            enabled = !state.isGeneratingPrompt
                        ) {
                            if (state.isGeneratingPrompt) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, "AI提示")
                            }
                        }
                    }
                    if (hasFlomo.isNotBlank()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (text.isNotBlank()) viewModel.sendToFlomo(text)
                                }
                            },
                            enabled = !state.isSendingFlomo
                        ) {
                            if (state.isSendingFlomo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Outlined.Send, "发送Flomo")
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (text.isNotBlank()) {
                            scope.launch {
                                if (editFilename != null) {
                                    viewModel.updateEntry(editFilename, text)
                                } else {
                                    viewModel.saveEntry(text)
                                }
                                onDone()
                            }
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Check,
                            "完成",
                            tint = if (text.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }

        // Content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            if (showPrompt && currentPrompt != null) {
                PromptBanner(
                    prompt = currentPrompt,
                    focusMode = effectiveFocus,
                    onDismiss = { showPrompt = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            state.message?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2000)
                    viewModel.clearMessage()
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = if (effectiveFocus) 18.sp else fontSize,
                        lineHeight = if (effectiveFocus) 32.sp else (fontSize.value * 1.6f).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = "写下你的想法...",
                                    fontSize = if (effectiveFocus) 18.sp else fontSize,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Bottom bar for focus mode (both free and prompted writing)
            val showBottomBar = effectiveFocus && text.isNotBlank()
                || (useFullscreen && !isFreeWrite && currentPrompt != null)
            if (showBottomBar) {
                FocusModeBottomBar(
                    wordCount = wordCount,
                    showPromptToggle = !isFreeWrite && currentPrompt != null,
                    promptVisible = showPrompt,
                    canDone = text.isNotBlank(),
                    onTogglePrompt = { showPrompt = !showPrompt },
                    onDone = {
                        scope.launch {
                            if (editFilename != null) {
                                viewModel.updateEntry(editFilename, text)
                            } else {
                                viewModel.saveEntry(text)
                            }
                            onDone()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FocusModeEditorTopBar(
    visible: Boolean,
    wordCount: Int,
    onBack: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$wordCount 字",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PromptBanner(
    prompt: String,
    focusMode: Boolean,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(if (focusMode) 12.dp else 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = prompt,
                fontSize = if (focusMode) 16.sp else 15.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // In focus mode, prompt stays visible (no dismiss button)
        }
    }
}

@Composable
private fun FocusModeBottomBar(
    wordCount: Int,
    showPromptToggle: Boolean = false,
    promptVisible: Boolean = true,
    canDone: Boolean = true,
    onTogglePrompt: () -> Unit = {},
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$wordCount 字",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showPromptToggle) {
            IconButton(onClick = onTogglePrompt) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = "提示词",
                    modifier = Modifier.size(22.dp),
                    tint = if (promptVisible)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            }
        }
        FilledIconButton(
            onClick = onDone,
            enabled = canDone,
            modifier = Modifier.size(40.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Outlined.Check, "完成", modifier = Modifier.size(20.dp))
        }
    }
}
