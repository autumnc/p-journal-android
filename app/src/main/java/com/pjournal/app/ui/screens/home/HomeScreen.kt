package com.pjournal.app.ui.screens.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    focusMode: Boolean,
    isDarkTheme: Boolean,
    einkMode: Boolean = false,
    encryptionEnabled: Boolean = false,
    encryptionPassword: String = "",
    onToggleTheme: () -> Unit,
    onPromptWrite: (String?) -> Unit,
    onFreeWrite: () -> Unit,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory())
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showPasswordGate by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var selectedLandscapeAction by remember { mutableStateOf("提示写作") }

    val openBrowser: () -> Unit = {
        if (encryptionEnabled && encryptionPassword.isNotBlank()) {
            passwordInput = ""
            passwordError = false
            showPasswordGate = true
        } else {
            onBrowse()
        }
    }

    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(state.syncMessage) {
        if (state.syncMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSyncMessage()
        }
    }

    // Password gate dialog
    if (showPasswordGate) {
        AlertDialog(
            onDismissRequest = {
                showPasswordGate = false
                passwordInput = ""
                passwordError = false
            },
            title = { Text("需要密码") },
            text = {
                Column {
                    Text(
                        "查看过往日记需要输入密码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = false
                        },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError,
                        supportingText = if (passwordError) {
                            { Text("密码错误") }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (passwordInput == encryptionPassword) {
                                    showPasswordGate = false
                                    passwordInput = ""
                                    passwordError = false
                                    onBrowse()
                                } else {
                                    passwordError = true
                                }
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (passwordInput == encryptionPassword) {
                        showPasswordGate = false
                        passwordInput = ""
                        passwordError = false
                        onBrowse()
                    } else {
                        passwordError = true
                    }
                }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordGate = false
                    passwordInput = ""
                    passwordError = false
                }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正经人日记") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDarkTheme) "日间模式" else "夜间模式"
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.P -> {
                                selectedLandscapeAction = "提示写作"
                                onPromptWrite(null)
                                true
                            }
                            Key.F -> {
                                selectedLandscapeAction = "自由写作"
                                onFreeWrite()
                                true
                            }
                            Key.V -> if (state.totalEntries > 0) {
                                selectedLandscapeAction = "查看过往日记"
                                openBrowser()
                                true
                            } else false
                            Key.W -> {
                                selectedLandscapeAction = "同步到WebDAV"
                                viewModel.syncWebDav()
                                true
                            }
                            Key.S -> { onSettings(); true }
                            Key.Escape, Key.Q -> { onExit(); true }
                            else -> false
                        }
                    } else false
                }
                .focusRequester(rootFocusRequester)
                .focusable()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 24.dp))

            // Week tracker
            WeekTracker(weekDays = state.weekDates, einkMode = einkMode)

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 32.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                StatItem("连续", "${state.streak}天")
                Spacer(modifier = Modifier.width(32.dp))
                StatItem("总计", "${state.totalEntries}篇")
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 24.dp))

            // Today status
            Text(
                text = if (state.todayCount > 0) {
                    if (state.todayCount == 1) "今日已写 1 篇"
                    else "今日已写 ${state.todayCount} 篇"
                } else {
                    "今日尚未写日记"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.todayCount > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )

            // Sync message
            state.syncMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 48.dp))

            // Menu buttons
            if (isLandscape) {
                LandscapeMenu(
                    selectedAction = selectedLandscapeAction,
                    einkMode = einkMode,
                    items = buildList {
                        add(
                            LandscapeMenuItem(
                                label = "提示写作",
                                shortcut = "p",
                                icon = Icons.Outlined.AutoAwesome,
                                onClick = {
                                    selectedLandscapeAction = "提示写作"
                                    @Suppress("DEPRECATION")
                                    onPromptWrite(null) // null means pick from prompts
                                }
                            )
                        )
                        add(
                            LandscapeMenuItem(
                                label = "自由写作",
                                shortcut = "f",
                                icon = Icons.Outlined.EditNote,
                                onClick = {
                                    selectedLandscapeAction = "自由写作"
                                    onFreeWrite()
                                }
                            )
                        )
                        if (state.totalEntries > 0) {
                            add(
                                LandscapeMenuItem(
                                    label = "查看过往日记",
                                    shortcut = "v",
                                    icon = Icons.Outlined.History,
                                    onClick = {
                                        selectedLandscapeAction = "查看过往日记"
                                        openBrowser()
                                    }
                                )
                            )
                        }
                        add(
                            LandscapeMenuItem(
                                label = "同步到WebDAV",
                                shortcut = "w",
                                icon = Icons.Outlined.Sync,
                                onClick = {
                                    selectedLandscapeAction = "同步到WebDAV"
                                    viewModel.syncWebDav()
                                }
                            )
                        )
                    }
                )
            } else {
                MenuButton("提示写作", einkMode = einkMode, onClick = {
                    @Suppress("DEPRECATION")
                    onPromptWrite(null) // null means pick from prompts
                })

                Spacer(modifier = Modifier.height(12.dp))

                MenuButton("自由写作", einkMode = einkMode, onClick = onFreeWrite)

                if (state.totalEntries > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MenuButton("查看过往日记", einkMode = einkMode, onClick = openBrowser)
                }

                Spacer(modifier = Modifier.height(12.dp))
                MenuButton("同步到WebDAV", einkMode = einkMode, onClick = { viewModel.syncWebDav() })
            }
        }
    }
}

private data class LandscapeMenuItem(
    val label: String,
    val shortcut: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun LandscapeMenu(
    selectedAction: String,
    einkMode: Boolean,
    items: List<LandscapeMenuItem>
) {
    val selectedItem = items.firstOrNull { it.label == selectedAction } ?: items.first()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                LandscapeMenuIconButton(
                    item = item,
                    selected = item.label == selectedItem.label,
                    einkMode = einkMode
                )
                if (index != items.lastIndex) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "[${selectedItem.shortcut}]${selectedItem.label}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LandscapeMenuIconButton(
    item: LandscapeMenuItem,
    selected: Boolean,
    einkMode: Boolean
) {
    val shape = RoundedCornerShape(10.dp)
    val backgroundColor = when {
        einkMode && selected -> MaterialTheme.colorScheme.onSurface
        einkMode -> MaterialTheme.colorScheme.surface
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = when {
        einkMode && selected -> MaterialTheme.colorScheme.surface
        einkMode -> MaterialTheme.colorScheme.onSurface
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .then(
                if (einkMode) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape)
                } else {
                    Modifier
                }
            )
            .background(backgroundColor)
            .clickable(onClick = item.onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(28.dp),
            tint = contentColor
        )
    }
}

@Composable
private fun WeekTracker(weekDays: List<WeekDay>, einkMode: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        weekDays.forEachIndexed { _, day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(44.dp)
            ) {
                // Day label
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (day.isToday && !einkMode)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else if (day.isToday && einkMode)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else if (einkMode)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (day.isToday && einkMode)
                            MaterialTheme.colorScheme.onSurface
                        else if (day.isToday)
                            MaterialTheme.colorScheme.primary
                        else if (einkMode)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Check mark
                if (day.hasEntry) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (einkMode) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.tertiary
                    )
                } else if (!day.isFuture) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (einkMode) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun MenuButton(
    text: String,
    einkMode: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (einkMode) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp))
                else Modifier
            )
            .background(
                if (einkMode) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (einkMode) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}
