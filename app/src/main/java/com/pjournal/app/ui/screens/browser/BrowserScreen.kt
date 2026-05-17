package com.pjournal.app.ui.screens.browser

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onViewEntry: (String) -> Unit,
    onEditEntry: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: BrowserViewModel = viewModel(
        factory = BrowserViewModelFactory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (isLandscape) 48.dp else 12.dp

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2000)
            viewModel.clearMessage()
        }
    }

    deleteTarget?.let { filename ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除日记？") },
            text = { Text("确定永久删除这篇日记？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(filename)
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("过往日记") },
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
        ) {
            // Message banner
            state.message?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = horizontalPadding, vertical = 6.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Search bar + tag chips + result count in a compact block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = horizontalPadding, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "搜索",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.searchQuery.isNotEmpty() || state.selectedTag != null) {
                                TextButton(
                                    onClick = { viewModel.clearFilters() },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("清除", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.search("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Clear, "清除搜索",
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                )

                // Tag chips + result count on same row
                if (state.availableTags.isNotEmpty() || state.searchQuery.isNotBlank() || state.selectedTag != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.availableTags.forEach { tag ->
                                InputChip(
                                    selected = state.selectedTag == tag,
                                    onClick = { viewModel.selectTag(tag) },
                                    label = {
                                        Text(
                                            "#$tag",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(vertical = 0.dp)
                                        )
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        val hasFilter = state.searchQuery.isNotBlank() || state.selectedTag != null
                        if (hasFilter) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${state.entries.size} 篇",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Divider
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            if (state.entries.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.searchQuery.isNotBlank() || state.selectedTag != null) {
                            "无匹配日记"
                        } else {
                            "暂无日记"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = state.entries,
                        key = { _, entry -> entry.filename }
                    ) { index, entry ->
                        EntryRow(
                            entry = entry,
                            isSendingFlomo = entry.filename in state.sendingSet,
                            onClick = { onViewEntry(entry.filename) },
                            onEdit = { onEditEntry(entry.filename) },
                            onDelete = { deleteTarget = entry.filename },
                            onSendFlomo = { viewModel.sendToFlomo(entry.filename) }
                        )
                        if (index < state.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = horizontalPadding),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: BrowserEntry,
    isSendingFlomo: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSendFlomo: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (isLandscape) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.displayDate,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (entry.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    entry.tags.take(2).forEachIndexed { i, tag ->
                        if (i > 0) Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (entry.tags.size > 2) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+${entry.tags.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            if (entry.preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = entry.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isSendingFlomo) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(2.dp)
                    .size(18.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            IconButton(
                onClick = onSendFlomo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    "发送Flomo",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Outlined.Edit,
                "编辑",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Outlined.Delete,
                "删除",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}
