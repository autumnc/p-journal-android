package com.pjournal.app.ui.screens.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pjournal.app.data.BuiltInPrompts
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.repository.JournalRepository
import com.pjournal.app.network.DeepseekApi
import com.pjournal.app.network.FlomoApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditorState(
    val prompt: String? = null,
    val isGeneratingPrompt: Boolean = false,
    val isSendingFlomo: Boolean = false,
    val message: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JournalRepository(
        com.pjournal.app.PJournalApp.instance.database.journalEntryDao()
    )
    private val prefs = PreferencesManager(application)
    private val deepseekApi = DeepseekApi()
    private val flomoApi = FlomoApi()

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun setInitialPrompt(prompt: String?) {
        _state.value = _state.value.copy(prompt = prompt)
    }

    fun generateAiPrompt() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGeneratingPrompt = true)
            try {
                val apiKey = prefs.getStringFlow("deepseek_api_key").first()
                val experience = prefs.getStringFlow("personal_experience").first()
                val hobbies = prefs.getStringFlow("personal_hobbies").first()
                val recentStatus = prefs.getStringFlow("recent_status").first()

                val seedPrompt = BuiltInPrompts.random()
                when (val result = deepseekApi.generatePrompt(apiKey, experience, hobbies, recentStatus, seedPrompt)) {
                    is com.pjournal.app.network.ApiResult.Success -> {
                        _state.value = _state.value.copy(
                            prompt = result.content,
                            isGeneratingPrompt = false,
                            message = "AI 提示词已生成"
                        )
                    }
                    is com.pjournal.app.network.ApiResult.Failure -> {
                        _state.value = _state.value.copy(
                            isGeneratingPrompt = false,
                            message = "生成失败: ${result.error}"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isGeneratingPrompt = false,
                    message = "生成失败: ${e.message}"
                )
            }
        }
    }

    suspend fun saveEntry(text: String): String {
        val prompt = _state.value.prompt
        val filename = repository.saveEntry(text, prompt)
        _state.value = _state.value.copy(prompt = null)
        triggerSync()
        return filename
    }

    suspend fun updateEntry(filename: String, text: String) {
        val prompt = _state.value.prompt
        repository.updateEntry(filename, text, prompt)
        _state.value = _state.value.copy(prompt = null)
        triggerSync()
    }

    suspend fun loadEntryForEdit(filename: String): String? {
        val entry = repository.getEntry(filename) ?: return null
        _state.value = _state.value.copy(prompt = entry.prompt)
        return repository.extractBody(entry.content)
    }

    private fun triggerSync() {
        viewModelScope.launch {
            try {
                com.pjournal.app.PJournalApp.instance.syncManager.triggerSyncIfReady()
            } catch (_: Exception) {}
        }
    }

    suspend fun sendToFlomo(text: String): Boolean {
        _state.value = _state.value.copy(isSendingFlomo = true)
        return try {
            val email = prefs.getStringFlow("flomo_email").first()
            val password = prefs.getStringFlow("flomo_password").first()

            if (email.isBlank() || password.isBlank()) {
                _state.value = _state.value.copy(
                    isSendingFlomo = false,
                    message = "请先配置 Flomo 账号"
                )
                return false
            }

            var token = prefs.getStringFlow("flomo_token").first()
            var success = false

            if (token.isNotBlank()) {
                success = flomoApi.createMemo(token, text)
            }

            if (!success) {
                token = flomoApi.login(email, password) ?: ""
                if (token.isNotBlank()) {
                    prefs.setString("flomo_token", token)
                    success = flomoApi.createMemo(token, text)
                }
            }

            _state.value = _state.value.copy(
                isSendingFlomo = false,
                message = if (success) "已发送到 Flomo" else "发送失败"
            )
            success
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isSendingFlomo = false,
                message = "发送失败: ${e.message}"
            )
            false
        }
    }
}
