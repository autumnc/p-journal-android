package com.pjournal.app.ui.screens.viewer

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pjournal.app.PJournalApp
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.font.FontManager
import com.pjournal.app.data.repository.JournalRepository
import java.text.SimpleDateFormat
import java.util.Locale

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
            val dateFormatted = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
                val cleanName = entry.filename.removeSuffix(".txt")
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
                .verticalScroll(rememberScrollState())
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
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
