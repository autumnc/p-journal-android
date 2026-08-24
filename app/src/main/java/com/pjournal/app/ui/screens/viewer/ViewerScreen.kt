package com.pjournal.app.ui.screens.viewer

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pjournal.app.PJournalApp
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.font.FontManager
import com.pjournal.app.data.repository.JournalRepository
import com.pjournal.app.network.FlomoApi
import com.pjournal.app.ui.util.isCtrl
import com.pjournal.app.util.parseMarkdown
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    filename: String,
    onBack: () -> Unit
) {
    val repository = remember {
        JournalRepository(PJournalApp.instance.database.journalEntryDao())
    }
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<String?>(null) }
    var body by remember { mutableStateOf("") }
    var isFreeWrite by remember { mutableStateOf(false) }
    var isMarkdown by remember { mutableStateOf(false) }

    val viewerFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 60.dp.roundToPx() }

    // Font preferences
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val fontManager = remember { FontManager.getInstance(PJournalApp.instance) }
    val importedFonts by fontManager.importedFonts.collectAsStateWithLifecycle(emptyList())
    val editorFont by prefs.editorFont.collectAsStateWithLifecycle(initialValue = "default")
    val fontFamily = remember(editorFont, importedFonts) {
        if (editorFont == "default") FontFamily.Default
        else fontManager.getFontFamily(editorFont) ?: FontFamily.Default
    }

    LaunchedEffect(filename) {
        val entry = repository.getEntry(filename)
        if (entry != null) {
            isMarkdown = entry.filename.endsWith(".md")
            val dateFormatted = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
                val cleanName = entry.filename.removeSuffix(".txt").removeSuffix(".md")
                val dt = sdf.parse(cleanName)
                SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(dt!!)
            } catch (e: Exception) {
                entry.filename
            }
            title = dateFormatted
            prompt = entry.prompt
            isFreeWrite = entry.prompt == null

            // Parse content to extract body (skip metadata)
            val content = entry.content
            val lines = content.split('\n')
            var inMetadata = true
            val bodyLines = mutableListOf<String>()
            for (line in lines) {
                val stripped = line.trim()
                if (inMetadata && (
                    stripped.startsWith("日期:") ||
                    stripped.startsWith("字数:") ||
                    stripped.startsWith("提示词:") ||
                    stripped == "自由写作" ||
                    stripped.isEmpty()
                )) {
                    inMetadata = stripped != "自由写作" || line.trim() == "自由写作"
                    if (stripped == "自由写作") inMetadata = false
                    continue
                } else {
                    inMetadata = false
                    if (stripped.isEmpty()) {
                        bodyLines.add("")
                    } else {
                        bodyLines.add(line)
                    }
                }
            }
            body = bodyLines.joinToString("\n").trim()
        }
    }

    LaunchedEffect(Unit) {
        viewerFocusRequester.requestFocus()
    }

    val handleViewerKey: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when {
                event.key == Key.Escape || event.key == Key.Q -> {
                    onBack()
                    true
                }
                event.key == Key.DirectionDown || event.key == Key.J -> {
                    scrollScope.launch {
                        scrollState.scrollTo((scrollState.value + scrollStep).coerceAtMost(scrollState.maxValue))
                    }
                    true
                }
                event.key == Key.DirectionUp || event.key == Key.K -> {
                    scrollScope.launch {
                        scrollState.scrollTo((scrollState.value - scrollStep).coerceAtLeast(0))
                    }
                    true
                }
                event.key == Key.Spacebar || event.key == Key.PageUp -> {
                    scrollScope.launch {
                        scrollState.scrollTo((scrollState.value - scrollState.viewportSize).coerceAtLeast(0))
                    }
                    true
                }
                event.key == Key.PageDown -> {
                    scrollScope.launch {
                        scrollState.scrollTo((scrollState.value + scrollState.viewportSize).coerceAtMost(scrollState.maxValue))
                    }
                    true
                }
                event.key == Key.G -> {
                    scrollScope.launch {
                        if (event.isShiftPressed) scrollState.scrollTo(scrollState.maxValue)
                        else scrollState.scrollTo(0)
                    }
                    true
                }
                event.isCtrl && event.key == Key.F -> {
                    scrollScope.launch {
                        val msg = sendBodyToFlomo(context, body)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .onPreviewKeyEvent { handleViewerKey(it) }
                .focusRequester(viewerFocusRequester)
                .focusable()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Prompt if present
            prompt?.let { p ->
                Text(
                    text = p,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isFreeWrite) {
                Text(
                    text = "自由写作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Body text
            if (isMarkdown) {
                val markdownAnnotated = parseMarkdown(
                    text = body,
                    textColor = MaterialTheme.colorScheme.onBackground,
                    mutedColor = MaterialTheme.colorScheme.outline,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    accentYellow = MaterialTheme.colorScheme.tertiary,
                    highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    baseFontSize = MaterialTheme.typography.bodyLarge.fontSize
                )
                Text(
                    text = markdownAnnotated,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily)
                )
            } else {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private suspend fun sendBodyToFlomo(context: Context, body: String): String {
    if (body.isBlank()) return "日记内容为空"
    val prefs = PreferencesManager(context)
    val email = prefs.getStringFlow("flomo_email").first()
    val password = prefs.getStringFlow("flomo_password").first()
    if (email.isBlank() || password.isBlank()) return "请先配置 Flomo 账号"
    val flomoApi = FlomoApi()
    var token = prefs.getStringFlow("flomo_token").first()
    var success = flomoApi.createMemo(token, body)
    if (!success) {
        token = flomoApi.login(email, password) ?: ""
        if (token.isNotBlank()) {
            prefs.setString("flomo_token", token)
            success = flomoApi.createMemo(token, body)
        }
    }
    return if (success) "已发送到 Flomo" else "发送失败"
}
