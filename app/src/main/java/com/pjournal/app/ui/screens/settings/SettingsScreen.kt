package com.pjournal.app.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape) 60.dp else 20.dp

    var editingField by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editValue by remember { mutableStateOf("") }

    // Edit dialog
    editingField?.let { (key, label) ->
        val isPassword = label.contains("密码")
        AlertDialog(
            onDismissRequest = { editingField = null },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = { Text(label) },
                    singleLine = true,
                    visualTransformation = if (isPassword)
                        PasswordVisualTransformation()
                    else
                        androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            viewModel.saveString(key, editValue)
                            editingField = null
                            focusManager.clearFocus()
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveString(key, editValue)
                    editingField = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Display ──
            SectionHeader("显示")
            Spacer(modifier = Modifier.height(8.dp))
            FocusModeCard(
                enabled = state.focusMode,
                onToggle = { viewModel.setFocusMode(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            EinkModeCard(
                enabled = state.einkMode,
                onToggle = { viewModel.setEinkMode(it) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            EncryptionCard(
                enabled = state.encryptionEnabled,
                password = state.encryptionPassword,
                onToggle = { viewModel.setEncryptionEnabled(it) },
                onSetPassword = { viewModel.setEncryptionPassword(it) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ── Editor Font ──
            SectionHeader("编辑器样式")
            Spacer(modifier = Modifier.height(8.dp))
            FontSelector(
                label = "字体",
                current = state.editorFont,
                options = listOf(
                    "default" to "默认",
                    "serif" to "衬线",
                    "sans" to "无衬线",
                    "mono" to "等宽"
                ),
                onSelect = { viewModel.saveString("editor_font", it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            FontSizeSlider(
                label = "字号",
                current = state.editorFontSize.toIntOrNull() ?: 16,
                onSet = { viewModel.saveString("editor_font_size", it.toString()) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ── AI ──
            SectionHeader("AI 提示")
            SettingsField(
                label = "Deepseek API Key",
                value = state.deepseekApiKey,
                masked = true,
                onClick = {
                    editValue = state.deepseekApiKey
                    editingField = "deepseek_api_key" to "Deepseek API Key"
                },
                onClear = { viewModel.clearString("deepseek_api_key") }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ── Flomo ──
            SectionHeader("Flomo 同步")
            SettingsField(
                label = "邮箱",
                value = state.flomoEmail,
                onClick = {
                    editValue = state.flomoEmail
                    editingField = "flomo_email" to "邮箱"
                },
                onClear = { viewModel.clearString("flomo_email") }
            )
            SettingsField(
                label = "密码",
                value = state.flomoPassword,
                masked = true,
                onClick = {
                    editValue = state.flomoPassword
                    editingField = "flomo_password" to "密码"
                },
                onClear = { viewModel.clearString("flomo_password") }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ── WebDAV ──
            SectionHeader("WebDAV 同步")
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "同步日记到 WebDAV",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.syncWebDav() }) {
                    Icon(Icons.Outlined.CloudSync, "立即同步")
                }
            }
            state.syncMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SettingsField(
                label = "服务器地址",
                value = state.webdavUrl,
                onClick = {
                    editValue = state.webdavUrl
                    editingField = "webdav_url" to "服务器地址"
                },
                onClear = { viewModel.clearString("webdav_url") }
            )
            SettingsField(
                label = "用户名",
                value = state.webdavUsername,
                onClick = {
                    editValue = state.webdavUsername
                    editingField = "webdav_username" to "用户名"
                },
                onClear = { viewModel.clearString("webdav_username") }
            )
            SettingsField(
                label = "密码",
                value = state.webdavPassword,
                masked = true,
                onClick = {
                    editValue = state.webdavPassword
                    editingField = "webdav_password" to "密码"
                },
                onClear = { viewModel.clearString("webdav_password") }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ── Personal Info ──
            SectionHeader("个人信息（用于 AI 提示生成）")
            SettingsField(
                label = "经历",
                value = state.personalExperience,
                onClick = {
                    editValue = state.personalExperience
                    editingField = "personal_experience" to "经历"
                },
                onClear = { viewModel.clearString("personal_experience") }
            )
            SettingsField(
                label = "爱好",
                value = state.personalHobbies,
                onClick = {
                    editValue = state.personalHobbies
                    editingField = "personal_hobbies" to "爱好"
                },
                onClear = { viewModel.clearString("personal_hobbies") }
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FocusModeCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.LightMode,
            contentDescription = null,
            tint = if (enabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "专注模式",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (enabled) "编辑器全屏，无干扰写作" else "标准编辑器显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun EinkModeCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.AutoStories,
            contentDescription = null,
            tint = if (enabled)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "墨水屏模式",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (enabled) "黑白高对比度，适合电子墨水屏" else "正常色彩显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun EncryptionCard(
    enabled: Boolean,
    password: String,
    onToggle: (Boolean) -> Unit,
    onSetPassword: (String) -> Unit
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var newPassword by remember(password) { mutableStateOf(password) }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(if (password.isBlank()) "设置加密密码" else "修改加密密码") },
            text = {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newPassword.isNotBlank()) {
                                onSetPassword(newPassword)
                                showPasswordDialog = false
                            }
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPassword.isNotBlank()) {
                            onSetPassword(newPassword)
                            showPasswordDialog = false
                        }
                    },
                    enabled = newPassword.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (enabled)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "加密保护",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (enabled) "查看过往日记需要密码" else "关闭时无需密码即可查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (password.isBlank()) "密码未设置" else "密码: ${"*".repeat(minOf(password.length, 12))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showPasswordDialog = true }) {
                    Text(if (password.isBlank()) "设置" else "修改")
                }
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    masked: Boolean = false,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 4.dp,
                end = 4.dp,
                top = 12.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when {
                    value.isBlank() -> "(未设置)"
                    masked -> "*".repeat(minOf(value.length, 20))
                    value.length > 40 -> value.take(40) + "..."
                    else -> value
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (value.isBlank())
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        TextButton(onClick = onClick) {
            Text("编辑")
        }
        if (value.isNotBlank()) {
            TextButton(onClick = onClear) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun FontSelector(
    label: String,
    current: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(48.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        options.forEach { (key, name) ->
            val selected = current == key
            TextButton(
                onClick = { onSelect(key) }
            ) {
                Text(
                    text = name,
                    color = if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun FontSizeSlider(
    label: String,
    current: Int,
    onSet: (Int) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$label ($current)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(80.dp)
            )
            Text(
                text = "预览",
                fontSize = current.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = current.toFloat(),
            onValueChange = { onSet(it.toInt()) },
            valueRange = 12f..36f,
            steps = 23,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text("12sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text("36sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
