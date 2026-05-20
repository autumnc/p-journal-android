package com.pjournal.app.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pjournal.app.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsState(
    val deepseekApiKey: String = "",
    val flomoEmail: String = "",
    val flomoPassword: String = "",
    val webdavUrl: String = "",
    val webdavUsername: String = "",
    val webdavPassword: String = "",
    val personalExperience: String = "",
    val personalHobbies: String = "",
    val recentStatus: String = "",
    val focusMode: Boolean = false,
    val einkMode: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val encryptionPassword: String = "",
    val editorFont: String = "default",
    val editorFontSize: String = "16",
    val syncMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = SettingsState(
                deepseekApiKey = prefs.getStringFlow("deepseek_api_key").first(),
                flomoEmail = prefs.getStringFlow("flomo_email").first(),
                flomoPassword = prefs.getStringFlow("flomo_password").first(),
                webdavUrl = prefs.getStringFlow("webdav_url").first(),
                webdavUsername = prefs.getStringFlow("webdav_username").first(),
                webdavPassword = prefs.getStringFlow("webdav_password").first(),
                personalExperience = prefs.getStringFlow("personal_experience").first(),
                personalHobbies = prefs.getStringFlow("personal_hobbies").first(),
                recentStatus = prefs.getStringFlow("recent_status").first(),
                focusMode = prefs.focusMode.first(),
                einkMode = prefs.einkMode.first(),
                encryptionEnabled = prefs.encryptionEnabled.first(),
                encryptionPassword = prefs.encryptionPassword.first(),
                editorFont = prefs.editorFont.first(),
                editorFontSize = prefs.editorFontSize.first()
            )
        }
    }

    fun saveString(key: String, value: String) {
        viewModelScope.launch {
            prefs.setString(key, value)
            // Clear flomo token when credentials change
            if (key == "flomo_email" || key == "flomo_password") {
                prefs.setString("flomo_token", "")
            }
            refreshState()
        }
    }

    fun clearString(key: String) {
        saveString(key, "")
    }

    fun setFocusMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setFocusMode(enabled)
            refreshState()
        }
    }

    fun setEinkMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setEinkMode(enabled)
            refreshState()
        }
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setEncryptionEnabled(enabled)
            if (!enabled) prefs.setEncryptionPassword("")
            refreshState()
        }
    }

    fun setEncryptionPassword(password: String) {
        viewModelScope.launch {
            prefs.setEncryptionPassword(password)
            refreshState()
        }
    }

    fun syncWebDav() {
        viewModelScope.launch {
            _state.value = _state.value.copy(syncMessage = "同步中...")
            try {
                val result = com.pjournal.app.PJournalApp.instance.syncManager.performSync()
                _state.value = _state.value.copy(syncMessage = result.toMessage())
            } catch (e: Exception) {
                _state.value = _state.value.copy(syncMessage = "同步失败: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(syncMessage = null)
    }

    private suspend fun refreshState() {
        _state.value = SettingsState(
            deepseekApiKey = prefs.getStringFlow("deepseek_api_key").first(),
            flomoEmail = prefs.getStringFlow("flomo_email").first(),
            flomoPassword = prefs.getStringFlow("flomo_password").first(),
            webdavUrl = prefs.getStringFlow("webdav_url").first(),
            webdavUsername = prefs.getStringFlow("webdav_username").first(),
            webdavPassword = prefs.getStringFlow("webdav_password").first(),
            personalExperience = prefs.getStringFlow("personal_experience").first(),
            personalHobbies = prefs.getStringFlow("personal_hobbies").first(),
            recentStatus = prefs.getStringFlow("recent_status").first(),
            focusMode = prefs.focusMode.first(),
            einkMode = prefs.einkMode.first(),
            encryptionEnabled = prefs.encryptionEnabled.first(),
            encryptionPassword = prefs.encryptionPassword.first(),
            editorFont = prefs.editorFont.first(),
            editorFontSize = prefs.editorFontSize.first()
        )
    }
}
