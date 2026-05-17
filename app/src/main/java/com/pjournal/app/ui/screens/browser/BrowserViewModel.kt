package com.pjournal.app.ui.screens.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pjournal.app.PJournalApp
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.db.JournalEntryEntity
import com.pjournal.app.data.repository.JournalRepository
import com.pjournal.app.network.FlomoApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

data class BrowserEntry(
    val filename: String,
    val displayDate: String,
    val preview: String,
    val tags: List<String> = emptyList()
)

data class BrowserState(
    val entries: List<BrowserEntry> = emptyList(),
    val message: String? = null,
    val isLoading: Boolean = false,
    val sendingSet: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList()
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JournalRepository(
        PJournalApp.instance.database.journalEntryDao()
    )
    private val prefs = PreferencesManager(application)
    private val flomoApi = FlomoApi()

    private val _state = MutableStateFlow(BrowserState(isLoading = true))
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private var allEntities: List<JournalEntryEntity> = emptyList()

    init {
        loadEntries()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            repository.getAllEntries().collect { entities ->
                allEntities = entities
                val tags = entities
                    .flatMap { extractTags(it.content) }
                    .distinct()
                    .sorted()
                _state.value = _state.value.copy(availableTags = tags)
                applyFilters()
            }
        }
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        applyFilters()
    }

    fun selectTag(tag: String?) {
        val current = _state.value.selectedTag
        _state.value = _state.value.copy(selectedTag = if (current == tag) null else tag)
        applyFilters()
    }

    fun clearFilters() {
        _state.value = _state.value.copy(searchQuery = "", selectedTag = null)
        applyFilters()
    }

    private fun applyFilters() {
        val state = _state.value
        var filtered = allEntities

        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery
            filtered = filtered.filter {
                it.content.contains(query, ignoreCase = true)
            }
        }

        if (state.selectedTag != null) {
            val tag = state.selectedTag
            filtered = filtered.filter { entity ->
                extractTags(entity.content).any { it == tag }
            }
        }

        val browserEntries = filtered.map { entity ->
            mapToBrowserEntry(entity)
        }
        _state.value = _state.value.copy(entries = browserEntries, isLoading = false)
    }

    private fun mapToBrowserEntry(entity: JournalEntryEntity): BrowserEntry {
        val displayDate = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
            val cleanName = entity.filename.removeSuffix(".txt")
            val dt = sdf.parse(cleanName)
            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(dt!!)
        } catch (e: Exception) {
            entity.filename
        }

        val body = repository.extractBody(entity.content)
        val preview = body.take(50).replace('\n', ' ').trim()
        val tags = extractTags(entity.content)

        return BrowserEntry(
            filename = entity.filename,
            displayDate = displayDate,
            preview = preview,
            tags = tags
        )
    }

    fun deleteEntry(filename: String) {
        viewModelScope.launch {
            repository.deleteEntry(filename)
            _state.value = _state.value.copy(message = "已删除")
        }
    }

    fun sendToFlomo(filename: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(sendingSet = _state.value.sendingSet + filename)

            val entry = repository.getEntry(filename)
            if (entry == null) {
                _state.value = _state.value.copy(
                    sendingSet = _state.value.sendingSet - filename,
                    message = "条目不存在"
                )
                return@launch
            }

            val body = repository.extractBody(entry.content)
            if (body.isBlank()) {
                _state.value = _state.value.copy(
                    sendingSet = _state.value.sendingSet - filename,
                    message = "内容为空"
                )
                return@launch
            }

            val email = prefs.getStringFlow("flomo_email").first()
            val password = prefs.getStringFlow("flomo_password").first()

            if (email.isBlank() || password.isBlank()) {
                _state.value = _state.value.copy(
                    sendingSet = _state.value.sendingSet - filename,
                    message = "请先配置 Flomo 账号"
                )
                return@launch
            }

            var token = prefs.getStringFlow("flomo_token").first()
            var success = false

            if (token.isNotBlank()) {
                success = flomoApi.createMemo(token, body)
            }

            if (!success) {
                token = flomoApi.login(email, password) ?: ""
                if (token.isNotBlank()) {
                    prefs.setString("flomo_token", token)
                    success = flomoApi.createMemo(token, body)
                }
            }

            _state.value = _state.value.copy(
                sendingSet = _state.value.sendingSet - filename,
                message = if (success) "已发送到 Flomo" else "发送失败"
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    companion object {
        private val TAG_REGEX = Regex("#([\\w\\u4e00-\\u9fff]+)")

        fun extractTags(content: String): List<String> {
            return TAG_REGEX.findAll(content)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
        }
    }
}

class BrowserViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BrowserViewModel(application) as T
    }
}
