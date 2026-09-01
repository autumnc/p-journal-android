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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.db.JournalHistoryEntity
import com.pjournal.app.data.font.FontManager
import com.pjournal.app.ui.util.isCtrl
import com.pjournal.app.util.renderMarkdownForEditor
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
    einkMode: Boolean = false,
    onDone: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var hasLoadedEntry by rememberSaveable(editFilename) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showShortcutHelp by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var historyVersions by remember { mutableStateOf<List<JournalHistoryEntity>>(emptyList()) }
    var historyPreview by remember { mutableStateOf<Pair<JournalHistoryEntity, String>?>(null) }
    var historyRestoreTarget by remember { mutableStateOf<JournalHistoryEntity?>(null) }
    var historyDeleteTarget by remember { mutableStateOf<JournalHistoryEntity?>(null) }
    var selAnchor by remember { mutableStateOf<Int?>(null) }
    var shiftDown by remember { mutableStateOf(false) }
    var wordCount by remember { mutableStateOf(0) }
    var showPrompt by remember { mutableStateOf(true) }
    val undoStack = remember(editFilename) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(editFilename) { mutableStateListOf<TextFieldValue>() }

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
    val editorBoldFont by prefs.editorBoldFont.collectAsStateWithLifecycle(initialValue = "default")
    val editorItalicFont by prefs.editorItalicFont.collectAsStateWithLifecycle(initialValue = "default")
    val editorFontSize by prefs.editorFontSize.collectAsStateWithLifecycle(initialValue = "16")
    val firstLineIndent by prefs.firstLineIndent.collectAsStateWithLifecycle(initialValue = false)
    val fontFamily = remember(editorFont, importedFonts) {
        if (editorFont == "default") FontFamily.Default
        else fontManager.getFontFamily(editorFont) ?: FontFamily.Default
    }
    val boldFontFamily = remember(editorBoldFont, importedFonts) {
        if (editorBoldFont == "default") null
        else fontManager.getFontFamily(editorBoldFont)
    }
    val italicFontFamily = remember(editorItalicFont, importedFonts) {
        if (editorItalicFont == "default") null
        else fontManager.getFontFamily(editorItalicFont)
    }
    val fontSize = editorFontSize.toIntOrNull()?.sp ?: 16.sp

    // Markdown syntax highlight colors (must be read in composable context)
    val mdTextColor = MaterialTheme.colorScheme.onBackground
    val mdMutedColor = MaterialTheme.colorScheme.outline
    val mdPrimaryColor = MaterialTheme.colorScheme.primary
    val mdAccentColor = MaterialTheme.colorScheme.tertiary
    val mdHighlightBg = if (einkMode) Color(0xFFE8E8E8) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    val mdCursorOffset = textState.selection.end.coerceIn(0, textState.text.length)
    val mdHighlightTransform = remember(editorFont, boldFontFamily, italicFontFamily, mdTextColor, mdMutedColor, mdPrimaryColor, mdAccentColor, mdHighlightBg, einkMode, fontSize, firstLineIndent, mdCursorOffset) {
        VisualTransformation { annotatedString ->
            renderMarkdownForEditor(
                text = annotatedString.text,
                textColor = mdTextColor,
                mutedColor = mdMutedColor,
                primaryColor = mdPrimaryColor,
                accentColor = mdAccentColor,
                highlightBg = mdHighlightBg,
                einkMode = einkMode,
                baseFontSize = fontSize,
                customFontBoldBoost = editorFont != "default",
                boldFontFamily = boldFontFamily,
                italicFontFamily = italicFontFamily,
                firstLineIndent = firstLineIndent,
                cursorOffset = mdCursorOffset
            )
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
        if (editFilename != null && !hasLoadedEntry) {
            val body = viewModel.loadEntryForEdit(editFilename)
            if (body != null) {
                textState = TextFieldValue(body)
                selAnchor = null
            }
            hasLoadedEntry = true
        }
    }

    LaunchedEffect(textState.text) {
        wordCount = textState.text.count { it in '一'..'鿿' || it in '　'..'〿' || it in '＀'..'￯' } +
            Regex("[a-zA-Z]+").findAll(textState.text).count()
    }

    fun recordUndoSnapshot() {
        if (undoStack.lastOrNull() == textState) return
        undoStack.add(textState)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun applyEdit(value: TextFieldValue, recordUndo: Boolean = true) {
        if (recordUndo && value != textState) recordUndoSnapshot()
        textState = value
        selAnchor = null
    }

    fun undoEdit(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.add(textState)
        textState = previous
        selAnchor = null
        return true
    }

    fun redoEdit(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.add(textState)
        textState = next
        selAnchor = null
        return true
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
            ctrl && key == Key.Z && shift -> {
                if (!redoEdit()) viewModel.setMessage("没有可重做内容")
                true
            }
            ctrl && key == Key.Z -> {
                if (!undoEdit()) viewModel.setMessage("没有可撤销内容")
                true
            }
            ctrl && key == Key.Y -> {
                if (editFilename != null) {
                    scope.launch {
                        historyVersions = viewModel.getHistory(editFilename)
                        showHistory = true
                    }
                } else {
                    viewModel.setMessage("新日记保存后才有历史版本")
                }
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
            ctrl && key == Key.B -> { applyEdit(textState.insertMarkers("**")); true }
            ctrl && key == Key.T -> { applyEdit(textState.insertMarkers("*")); true }
            ctrl && key == Key.D -> { applyEdit(textState.insertMarkers("~~")); true }
            ctrl && key == Key.H -> { applyEdit(textState.insertMarkers("==")); true }
            ctrl && key == Key.U -> { applyEdit(textState.insertMarkers("<u>", "</u>")); true }
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
                            applyEdit(textState.copy(
                                text = textState.text.replaceRange(s, e, polished),
                                selection = TextRange(s, s + polished.length)
                            ))
                        }
                    }
                }
                true
            }
            key == Key.F1 -> { applyEdit(textState.applyHeading(1)); true }
            key == Key.F2 -> { applyEdit(textState.applyHeading(2)); true }
            key == Key.F3 -> { applyEdit(textState.applyHeading(3)); true }
            key == Key.F4 -> { applyEdit(textState.applyHeading(4)); true }
            key == Key.F5 -> { applyEdit(textState.applyHeading(5)); true }
            key == Key.F6 -> { applyEdit(textState.applyHeading(6)); true }
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
                    Text("^Z 撤销 · ^Shift+Z 重做 · ^Y 历史版本")
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
                applyEdit(if (selection != null) {
                    TextFieldValue(newText, selection = selection)
                } else {
                    TextFieldValue(newText)
                })
            },
            onDismiss = { showFindReplace = false }
        )
    }

    if (showHistory && editFilename != null) {
        HistoryDialog(
            versions = historyVersions,
            onPreview = { version ->
                scope.launch {
                    val body = viewModel.loadHistoryBody(version.id)
                    if (body != null) historyPreview = version to body
                }
            },
            onRestore = { historyRestoreTarget = it },
            onDelete = { historyDeleteTarget = it },
            onDismiss = { showHistory = false }
        )
    }

    historyPreview?.let { (version, body) ->
        HistoryPreviewDialog(
            version = version,
            body = body,
            onDismiss = { historyPreview = null },
            onRestore = {
                historyPreview = null
                historyRestoreTarget = version
            }
        )
    }

    historyRestoreTarget?.let { version ->
        AlertDialog(
            onDismissRequest = { historyRestoreTarget = null },
            title = { Text("恢复历史版本？") },
            text = { Text("当前内容会先保存为一个历史版本，然后恢复所选版本。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val restored = viewModel.restoreHistoryVersion(editFilename!!, version.id)
                        if (restored != null) {
                            applyEdit(TextFieldValue(restored), recordUndo = false)
                            undoStack.clear()
                            redoStack.clear()
                            historyVersions = viewModel.getHistory(editFilename)
                            viewModel.setMessage("已恢复历史版本")
                        } else {
                            viewModel.setMessage("恢复失败")
                        }
                        historyRestoreTarget = null
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { historyRestoreTarget = null }) { Text("取消") }
            }
        )
    }

    historyDeleteTarget?.let { version ->
        AlertDialog(
            onDismissRequest = { historyDeleteTarget = null },
            title = { Text("删除历史版本？") },
            text = { Text("此操作只删除所选历史版本，不影响当前日记。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.deleteHistoryVersion(version.id)
                        historyVersions = viewModel.getHistory(editFilename!!)
                        historyDeleteTarget = null
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { historyDeleteTarget = null }) { Text("取消") }
            }
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
                    if (editFilename != null) {
                        IconButton(onClick = {
                            scope.launch {
                                historyVersions = viewModel.getHistory(editFilename)
                                showHistory = true
                            }
                        }) {
                            Icon(Icons.Outlined.History, "历史版本")
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
                    onValueChange = { value ->
                        applyEdit(value)
                    },
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
private fun HistoryDialog(
    versions: List<JournalHistoryEntity>,
    onPreview: (JournalHistoryEntity) -> Unit,
    onRestore: (JournalHistoryEntity) -> Unit,
    onDelete: (JournalHistoryEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("历史版本") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (versions.isEmpty()) {
                    Text(
                        text = "暂无历史版本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    versions.forEach { version ->
                        HistoryVersionRow(
                            version = version,
                            onPreview = { onPreview(version) },
                            onRestore = { onRestore(version) },
                            onDelete = { onDelete(version) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun HistoryVersionRow(
    version: JournalHistoryEntity,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val label = remember(version.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(version.createdAt))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${version.content.toByteArray(Charsets.UTF_8).size} B",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPreview) { Text("预览") }
            TextButton(onClick = onRestore) { Text("恢复") }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HistoryPreviewDialog(
    version: JournalHistoryEntity,
    body: String,
    onDismiss: () -> Unit,
    onRestore: () -> Unit
) {
    val label = remember(version.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(version.createdAt))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Text(
                text = body.ifBlank { "内容为空" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onRestore) { Text("恢复") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
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
