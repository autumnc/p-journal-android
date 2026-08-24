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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.font.FontManager
import com.pjournal.app.ui.util.isCtrl
import com.pjournal.app.util.parseMarkdownHighlight
import com.pjournal.app.PJournalApp
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

    var textState by remember { mutableStateOf(TextFieldValue("")) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showShortcutHelp by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var selAnchor by remember { mutableStateOf<Int?>(null) }
    var shiftDown by remember { mutableStateOf(false) }
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
    val fontManager = remember { FontManager.getInstance(PJournalApp.instance) }
    val importedFonts by fontManager.importedFonts.collectAsStateWithLifecycle(emptyList())
    val editorFont by prefs.editorFont.collectAsStateWithLifecycle(initialValue = "default")
    val editorFontSize by prefs.editorFontSize.collectAsStateWithLifecycle(initialValue = "16")
    val fontFamily = remember(editorFont, importedFonts) {
        if (editorFont == "default") FontFamily.Default
        else fontManager.getFontFamily(editorFont) ?: FontFamily.Default
    }
    val fontSize = editorFontSize.toIntOrNull()?.sp ?: 16.sp

    // Detect if current entry is Markdown format
    val fileFormat by prefs.fileFormat.collectAsStateWithLifecycle(initialValue = "txt")
    val isMarkdown = editFilename?.endsWith(".md") == true || (editFilename == null && fileFormat == "md")

    // Markdown syntax highlight colors (must be read in composable context)
    val mdTextColor = MaterialTheme.colorScheme.onBackground
    val mdMutedColor = MaterialTheme.colorScheme.outline
    val mdPrimaryColor = MaterialTheme.colorScheme.primary
    val mdAccentColor = MaterialTheme.colorScheme.tertiary

    val mdHighlightTransform = remember(isMarkdown, mdTextColor, mdMutedColor, mdPrimaryColor, mdAccentColor, fontSize) {
        if (isMarkdown) {
            VisualTransformation { annotatedString ->
                val highlighted = parseMarkdownHighlight(
                    text = annotatedString.text,
                    textColor = mdTextColor,
                    mutedColor = mdMutedColor,
                    primaryColor = mdPrimaryColor,
                    accentColor = mdAccentColor,
                    baseFontSize = fontSize
                )
                TransformedText(highlighted, OffsetMapping.Identity)
            }
        } else {
            VisualTransformation.None
        }
    }
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
            if (body != null) {
                textState = TextFieldValue(body)
                selAnchor = null
            }
        }
    }

    LaunchedEffect(textState.text) {
        wordCount = textState.text.count { it in '一'..'鿿' || it in '　'..'〿' || it in '＀'..'￯' } +
            Regex("[a-zA-Z]+").findAll(textState.text).count()
    }

    fun shiftSelect(step: Int) {
        val len = textState.text.length
        val sel = textState.selection
        val anchor = selAnchor ?: sel.end
        selAnchor = anchor
        val cursor = (sel.end + step).coerceIn(0, len)
        textState = textState.copy(selection = TextRange(anchor, cursor))
    }

    fun shiftSelectLine(down: Int) {
        val len = textState.text.length
        val sel = textState.selection
        val anchor = selAnchor ?: sel.end
        selAnchor = anchor
        val cursor = sel.end
        val text = textState.text
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val newCursor = if (down > 0) {
            val nextLineStart = text.indexOf('\n', cursor) + 1
            if (nextLineStart == 0) cursor
            else {
                val col = cursor - lineStart
                val nextLineEnd = text.indexOf('\n', nextLineStart)
                val lineEnd = if (nextLineEnd == -1) len else nextLineEnd
                (nextLineStart + col).coerceAtMost(lineEnd)
            }
        } else {
            if (lineStart == 0) cursor
            else {
                val prevLineEnd = lineStart - 1
                val prevLineStart = text.lastIndexOf('\n', prevLineEnd - 1) + 1
                val col = cursor - lineStart
                (prevLineStart + col).coerceAtMost(prevLineEnd)
            }
        }
        textState = textState.copy(selection = TextRange(anchor, newCursor))
    }

    val handleEditorKey: (KeyEvent) -> Boolean = { event ->
        // Track Shift via its own key events; some keyboards don't report the
        // shift modifier on the arrow-key event itself, so fall back to this.
        if (event.key == Key.ShiftLeft || event.key == Key.ShiftRight) {
            if (event.type == KeyEventType.KeyDown) shiftDown = true
            else if (event.type == KeyEventType.KeyUp) shiftDown = false
            false
        } else if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            val key = event.key
            val ctrl = event.isCtrl
            // Some keyboards don't set the shift modifier on the arrow event itself,
            // so fall back to the Shift key tracked above.
            val shift = event.isShiftPressed || shiftDown
            when {
            ctrl && key == Key.S -> {
                if (textState.text.isNotBlank()) {
                    scope.launch {
                        if (editFilename != null) viewModel.updateEntry(editFilename, textState.text)
                        else viewModel.saveEntry(textState.text)
                        onDone()
                    }
                } else {
                    onNavigateBack()
                }
                true
            }
            ctrl && key == Key.Q -> {
                if (textState.text.isNotBlank()) showDiscardDialog = true else onNavigateBack()
                true
            }
            ctrl && key == Key.P -> {
                viewModel.generateAiPrompt()
                true
            }
            ctrl && key == Key.F -> {
                scope.launch { if (textState.text.isNotBlank()) viewModel.sendToFlomo(textState.text) }
                true
            }
            ctrl && key == Key.K -> {
                showShortcutHelp = true
                true
            }
            ctrl && key == Key.R -> {
                showFindReplace = true
                true
            }
            ctrl && key == Key.B -> { textState = textState.insertMarkers("**"); selAnchor = null; true }
            ctrl && key == Key.T -> { textState = textState.insertMarkers("*"); selAnchor = null; true }
            ctrl && key == Key.D -> { textState = textState.insertMarkers("~~"); selAnchor = null; true }
            ctrl && key == Key.H -> { textState = textState.insertMarkers("=="); selAnchor = null; true }
            ctrl && key == Key.U -> { textState = textState.insertMarkers("<u>", "</u>"); selAnchor = null; true }
            ctrl && key == Key.O -> {
                val sel = textState.selection
                val s = minOf(sel.start, sel.end)
                val e = maxOf(sel.start, sel.end)
                val selected = textState.text.substring(s, e)
                if (selected.isBlank()) {
                    viewModel.setMessage("请先用 Shift+方向键选中要润色的文本")
                } else {
                    scope.launch {
                        val polished = viewModel.polishSelected(selected)
                        if (polished != null && polished != selected) {
                            textState = textState.copy(
                                text = textState.text.replaceRange(s, e, polished),
                                selection = TextRange(s, s + polished.length)
                            )
                            selAnchor = null
                        }
                    }
                }
                true
            }
            key == Key.F1 -> { textState = textState.applyHeading(1); selAnchor = null; true }
            key == Key.F2 -> { textState = textState.applyHeading(2); selAnchor = null; true }
            key == Key.F3 -> { textState = textState.applyHeading(3); selAnchor = null; true }
            key == Key.F4 -> { textState = textState.applyHeading(4); selAnchor = null; true }
            key == Key.F5 -> { textState = textState.applyHeading(5); selAnchor = null; true }
            key == Key.F6 -> { textState = textState.applyHeading(6); selAnchor = null; true }
            key == Key.Escape -> {
                if (textState.text.isNotBlank()) showDiscardDialog = true else onNavigateBack()
                true
            }
            !ctrl && shift && event.key == Key.DirectionRight -> { shiftSelect(1); true }
            !ctrl && shift && event.key == Key.DirectionLeft -> { shiftSelect(-1); true }
            !ctrl && shift && event.key == Key.DirectionDown -> { shiftSelectLine(1); true }
            !ctrl && shift && event.key == Key.DirectionUp -> { shiftSelectLine(-1); true }
            else -> false
            }
        }
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

    if (showShortcutHelp) {
        AlertDialog(
            onDismissRequest = { showShortcutHelp = false },
            title = { Text("编辑器快捷键") },
            text = {
                Column {
                    Text("^S 保存 · ^Q/Esc 放弃 · ^K 帮助")
                    Text("^B 加粗 ** · ^T 斜体 * · ^D 删除线 ~~")
                    Text("^U 下划线 <u> · ^H 高亮 ==")
                    Text("F1-F6 标题 1-6")
                    Text("^P AI提示 · ^O 润色 · ^R 查找替换")
                    Text("^F 发送Flomo · Shift+方向键 选字")
                }
            },
            confirmButton = {
                TextButton(onClick = { showShortcutHelp = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showFindReplace) {
        FindReplaceDialog(
            text = textState.text,
            onApply = { newText, selection ->
                textState = if (selection != null) {
                    TextFieldValue(newText, selection = selection)
                } else {
                    TextFieldValue(newText)
                }
                selAnchor = null
            },
            onDismiss = { showFindReplace = false }
        )
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .imePadding()
        .onPreviewKeyEvent { handleEditorKey(it) }) {
        // Top bar
        if (effectiveFocus) {
            FocusModeEditorTopBar(
                visible = !showPrompt && textState.text.isNotBlank(),
                wordCount = wordCount,
                onBack = {
                    if (textState.text.isNotBlank()) showDiscardDialog = true
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
                                val cleanName = editFilename.removeSuffix(".txt").removeSuffix(".md")
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
                        if (textState.text.isNotBlank()) showDiscardDialog = true
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
                                    if (textState.text.isNotBlank()) viewModel.sendToFlomo(textState.text)
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
                        if (textState.text.isNotBlank()) {
                            scope.launch {
                                if (editFilename != null) {
                                    viewModel.updateEntry(editFilename, textState.text)
                                } else {
                                    viewModel.saveEntry(textState.text)
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
                            tint = if (textState.text.isNotBlank())
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
                    value = textState,
                    onValueChange = { textState = it; selAnchor = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { handleEditorKey(it) }
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.6f).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    visualTransformation = mdHighlightTransform,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (textState.text.isEmpty()) {
                                Text(
                                    text = "写下你的想法...",
                                    fontSize = fontSize,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Bottom bar for focus mode (both free and prompted writing)
            val showBottomBar = effectiveFocus && textState.text.isNotBlank()
                || (useFullscreen && !isFreeWrite && currentPrompt != null)
            if (showBottomBar) {
                FocusModeBottomBar(
                    wordCount = wordCount,
                    showPromptToggle = !isFreeWrite && currentPrompt != null,
                    promptVisible = showPrompt,
                    canDone = textState.text.isNotBlank(),
                    onTogglePrompt = { showPrompt = !showPrompt },
                    onDone = {
                        scope.launch {
                            if (editFilename != null) {
                                viewModel.updateEntry(editFilename, textState.text)
                            } else {
                                viewModel.saveEntry(textState.text)
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
    val scrollState = rememberScrollState()
    val maxHeight = if (focusMode) 108.dp else 96.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(scrollState)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onDismiss() })
                }
                .padding(if (focusMode) 12.dp else 16.dp)
        ) {
            Text(
                text = prompt,
                fontSize = if (focusMode) 16.sp else 15.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
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

private fun findMatches(text: String, needle: String): List<IntRange> {
    if (needle.isEmpty()) return emptyList()
    val result = mutableListOf<IntRange>()
    var index = 0
    while (true) {
        val found = text.indexOf(needle, index, ignoreCase = true)
        if (found == -1) break
        result.add(found until found + needle.length)
        index = found + needle.length
    }
    return result
}

private fun TextFieldValue.insertMarkers(open: String, close: String = open): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    return if (start == end) {
        copy(text = text.replaceRange(start, end, open + close), selection = TextRange(start + open.length))
    } else {
        val wrapped = text.replaceRange(start, end, open + text.substring(start, end) + close)
        copy(text = wrapped, selection = TextRange(end + open.length + close.length))
    }
}

private fun TextFieldValue.applyHeading(level: Int): TextFieldValue {
    val cursor = minOf(selection.start, selection.end)
    val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
    var lineEnd = text.indexOf('\n', lineStart)
    if (lineEnd == -1) lineEnd = text.length
    val line = text.substring(lineStart, lineEnd)
    val content = line.trimStart().trimStart('#').removePrefix(" ")
    val newLine = "#".repeat(level) + " " + content
    val newText = text.replaceRange(lineStart, lineEnd, newLine)
    return copy(text = newText, selection = TextRange(lineStart + newLine.length))
}

@Composable
private fun FindReplaceDialog(
    text: String,
    onApply: (String, TextRange?) -> Unit,
    onDismiss: () -> Unit
) {
    var workingText by remember { mutableStateOf(text) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var currentIndex by remember { mutableStateOf(0) }
    val findFocusRequester = remember { FocusRequester() }

    LaunchedEffect(text) { workingText = text }
    LaunchedEffect(Unit) { findFocusRequester.requestFocus() }

    val matches = remember(findText, workingText) { findMatches(workingText, findText) }
    val currentMatch = if (matches.isEmpty()) null else matches[currentIndex.coerceIn(0, matches.size - 1)]

    fun highlight(index: Int) {
        if (matches.isEmpty()) return
        val size = matches.size
        val idx = ((index % size) + size) % size
        currentIndex = idx
        val m = matches[idx]
        onApply(workingText, TextRange(m.first, m.last))
    }

    fun replaceCurrent() {
        val m = currentMatch ?: return
        val newText = workingText.replaceRange(m.first, m.last, replaceText)
        workingText = newText
        currentIndex = 0
        onApply(newText, TextRange(m.first, m.first + replaceText.length))
    }

    fun replaceAll() {
        if (matches.isEmpty()) return
        var result = workingText
        var offset = 0
        for (m in matches) {
            val s = m.first + offset
            val e = m.last + offset
            result = result.replaceRange(s, e, replaceText)
            offset += replaceText.length - (e - s)
        }
        workingText = result
        currentIndex = 0
        onApply(result, null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查找替换") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when {
                                event.isCtrl && event.key == Key.A -> { replaceAll(); true }
                                event.key == Key.Enter -> { highlight(currentIndex + 1); true }
                                else -> false
                            }
                        } else false
                    }
            ) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it; currentIndex = 0 },
                    label = { Text("查找") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(findFocusRequester)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("替换") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (matches.isEmpty()) "无匹配" else "${currentIndex + 1}/${matches.size} 匹配",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { highlight(currentIndex - 1) }, enabled = matches.isNotEmpty()) {
                        Text("上一个")
                    }
                    TextButton(onClick = { highlight(currentIndex + 1) }, enabled = matches.isNotEmpty()) {
                        Text("下一个")
                    }
                    TextButton(onClick = { replaceCurrent() }, enabled = currentMatch != null) {
                        Text("替换")
                    }
                    TextButton(onClick = { replaceAll() }, enabled = matches.isNotEmpty()) {
                        Text("全部替换")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

