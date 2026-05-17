package com.pjournal.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("pjournal_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val KEY_FLOMO_EMAIL = stringPreferencesKey("flomo_email")
        private val KEY_FLOMO_PASSWORD = stringPreferencesKey("flomo_password")
        private val KEY_FLOMO_TOKEN = stringPreferencesKey("flomo_token")
        private val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        private val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        private val KEY_WEBDAV_SYNC_STATE = stringPreferencesKey("webdav_sync_state")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_PERSONAL_EXPERIENCE = stringPreferencesKey("personal_experience")
        private val KEY_PERSONAL_HOBBIES = stringPreferencesKey("personal_hobbies")
        private val KEY_FOCUS_MODE = booleanPreferencesKey("focus_mode")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_EINK_MODE = booleanPreferencesKey("eink_mode")
        private val KEY_ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        private val KEY_ENCRYPTION_PASSWORD = stringPreferencesKey("encryption_password")
        private val KEY_EDITOR_FONT = stringPreferencesKey("editor_font")
        private val KEY_EDITOR_FONT_SIZE = stringPreferencesKey("editor_font_size")
        private val KEY_CONFIG_VERSION = longPreferencesKey("config_version")
    }

    // Keys excluded from sync: device_id, webdav_sync_state, flomo_token
    suspend fun exportToJson(): String {
        val prefs = context.dataStore.data.first()
        val json = JSONObject()
        json.put("deepseek_api_key", prefs[KEY_DEEPSEEK_API_KEY] ?: "")
        json.put("flomo_email", prefs[KEY_FLOMO_EMAIL] ?: "")
        json.put("flomo_password", prefs[KEY_FLOMO_PASSWORD] ?: "")
        json.put("webdav_url", prefs[KEY_WEBDAV_URL] ?: "")
        json.put("webdav_username", prefs[KEY_WEBDAV_USERNAME] ?: "")
        json.put("webdav_password", prefs[KEY_WEBDAV_PASSWORD] ?: "")
        json.put("personal_experience", prefs[KEY_PERSONAL_EXPERIENCE] ?: "")
        json.put("personal_hobbies", prefs[KEY_PERSONAL_HOBBIES] ?: "")
        json.put("focus_mode", prefs[KEY_FOCUS_MODE] ?: false)
        json.put("dark_theme", prefs[KEY_DARK_THEME] ?: false)
        json.put("eink_mode", prefs[KEY_EINK_MODE] ?: false)
        json.put("encryption_enabled", prefs[KEY_ENCRYPTION_ENABLED] ?: false)
        json.put("encryption_password", prefs[KEY_ENCRYPTION_PASSWORD] ?: "")
        json.put("editor_font", prefs[KEY_EDITOR_FONT] ?: "default")
        json.put("editor_font_size", prefs[KEY_EDITOR_FONT_SIZE] ?: "16")
        json.put("config_version", prefs[KEY_CONFIG_VERSION] ?: 0L)
        return json.toString()
    }

    suspend fun importFromJson(jsonStr: String) {
        val json = JSONObject(jsonStr)
        context.dataStore.edit { prefs ->
            json.optString("deepseek_api_key").takeIf { it.isNotEmpty() }?.let { prefs[KEY_DEEPSEEK_API_KEY] = it }
            json.optString("flomo_email").takeIf { it.isNotEmpty() }?.let { prefs[KEY_FLOMO_EMAIL] = it }
            json.optString("flomo_password").takeIf { it.isNotEmpty() }?.let { prefs[KEY_FLOMO_PASSWORD] = it }
            json.optString("webdav_url").takeIf { it.isNotEmpty() }?.let { prefs[KEY_WEBDAV_URL] = it }
            json.optString("webdav_username").takeIf { it.isNotEmpty() }?.let { prefs[KEY_WEBDAV_USERNAME] = it }
            json.optString("webdav_password").takeIf { it.isNotEmpty() }?.let { prefs[KEY_WEBDAV_PASSWORD] = it }
            json.optString("personal_experience").takeIf { it.isNotEmpty() }?.let { prefs[KEY_PERSONAL_EXPERIENCE] = it }
            json.optString("personal_hobbies").takeIf { it.isNotEmpty() }?.let { prefs[KEY_PERSONAL_HOBBIES] = it }
            if (json.has("focus_mode")) prefs[KEY_FOCUS_MODE] = json.getBoolean("focus_mode")
            if (json.has("dark_theme")) prefs[KEY_DARK_THEME] = json.getBoolean("dark_theme")
            if (json.has("eink_mode")) prefs[KEY_EINK_MODE] = json.getBoolean("eink_mode")
            if (json.has("encryption_enabled")) prefs[KEY_ENCRYPTION_ENABLED] = json.getBoolean("encryption_enabled")
            if (json.has("encryption_password")) prefs[KEY_ENCRYPTION_PASSWORD] = json.getString("encryption_password")
            if (json.has("editor_font")) prefs[KEY_EDITOR_FONT] = json.getString("editor_font")
            if (json.has("editor_font_size")) prefs[KEY_EDITOR_FONT_SIZE] = json.getString("editor_font_size")
            if (json.has("config_version")) prefs[KEY_CONFIG_VERSION] = json.getLong("config_version")
        }
    }

    suspend fun getConfigVersion(): Long {
        return context.dataStore.data.first()[KEY_CONFIG_VERSION] ?: 0L
    }

    val focusMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FOCUS_MODE] ?: false
    }

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_THEME] ?: false
    }

    val einkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EINK_MODE] ?: false
    }

    val encryptionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENCRYPTION_ENABLED] ?: false
    }

    val encryptionPassword: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENCRYPTION_PASSWORD] ?: ""
    }

    val editorFont: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_EDITOR_FONT] ?: "default"
    }

    val editorFontSize: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_EDITOR_FONT_SIZE] ?: "16"
    }

    suspend fun setFocusMode(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_FOCUS_MODE] = enabled
            it[KEY_CONFIG_VERSION] = (it[KEY_CONFIG_VERSION] ?: 0L) + 1
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_DARK_THEME] = enabled
            it[KEY_CONFIG_VERSION] = (it[KEY_CONFIG_VERSION] ?: 0L) + 1
        }
    }

    suspend fun setEinkMode(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_EINK_MODE] = enabled
            it[KEY_CONFIG_VERSION] = (it[KEY_CONFIG_VERSION] ?: 0L) + 1
        }
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_ENCRYPTION_ENABLED] = enabled
            it[KEY_CONFIG_VERSION] = (it[KEY_CONFIG_VERSION] ?: 0L) + 1
        }
    }

    suspend fun setEncryptionPassword(password: String) {
        context.dataStore.edit {
            it[KEY_ENCRYPTION_PASSWORD] = password
            it[KEY_CONFIG_VERSION] = (it[KEY_CONFIG_VERSION] ?: 0L) + 1
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_DEVICE_ID] = id }
        return id
    }

    suspend fun getString(key: String): String {
        return when (key) {
            "deepseek_api_key" -> context.dataStore.data.map { it[KEY_DEEPSEEK_API_KEY] ?: "" }
            "flomo_email" -> context.dataStore.data.map { it[KEY_FLOMO_EMAIL] ?: "" }
            "flomo_password" -> context.dataStore.data.map { it[KEY_FLOMO_PASSWORD] ?: "" }
            "flomo_token" -> context.dataStore.data.map { it[KEY_FLOMO_TOKEN] ?: "" }
            "webdav_url" -> context.dataStore.data.map { it[KEY_WEBDAV_URL] ?: "" }
            "webdav_username" -> context.dataStore.data.map { it[KEY_WEBDAV_USERNAME] ?: "" }
            "webdav_password" -> context.dataStore.data.map { it[KEY_WEBDAV_PASSWORD] ?: "" }
            "personal_experience" -> context.dataStore.data.map { it[KEY_PERSONAL_EXPERIENCE] ?: "" }
            "personal_hobbies" -> context.dataStore.data.map { it[KEY_PERSONAL_HOBBIES] ?: "" }
            else -> throw IllegalArgumentException("Unknown key: $key")
        }.let { flow ->
            // Since this is suspend, we need a different approach
            ""
        }
    }

    suspend fun setString(key: String, value: String) {
        context.dataStore.edit { prefs ->
            when (key) {
                "deepseek_api_key" -> prefs[KEY_DEEPSEEK_API_KEY] = value
                "flomo_email" -> prefs[KEY_FLOMO_EMAIL] = value
                "flomo_password" -> prefs[KEY_FLOMO_PASSWORD] = value
                "flomo_token" -> prefs[KEY_FLOMO_TOKEN] = value
                "webdav_url" -> prefs[KEY_WEBDAV_URL] = value
                "webdav_username" -> prefs[KEY_WEBDAV_USERNAME] = value
                "webdav_password" -> prefs[KEY_WEBDAV_PASSWORD] = value
                "personal_experience" -> prefs[KEY_PERSONAL_EXPERIENCE] = value
                "personal_hobbies" -> prefs[KEY_PERSONAL_HOBBIES] = value
                "webdav_sync_state" -> prefs[KEY_WEBDAV_SYNC_STATE] = value
                "encryption_password" -> prefs[KEY_ENCRYPTION_PASSWORD] = value
                "editor_font" -> prefs[KEY_EDITOR_FONT] = value
                "editor_font_size" -> prefs[KEY_EDITOR_FONT_SIZE] = value
            }
            if (key != "flomo_token" && key != "webdav_sync_state") {
                prefs[KEY_CONFIG_VERSION] = (prefs[KEY_CONFIG_VERSION] ?: 0L) + 1
            }
        }
    }

    fun getStringFlow(key: String): Flow<String> {
        return context.dataStore.data.map { prefs ->
            when (key) {
                "deepseek_api_key" -> prefs[KEY_DEEPSEEK_API_KEY] ?: ""
                "flomo_email" -> prefs[KEY_FLOMO_EMAIL] ?: ""
                "flomo_password" -> prefs[KEY_FLOMO_PASSWORD] ?: ""
                "flomo_token" -> prefs[KEY_FLOMO_TOKEN] ?: ""
                "webdav_url" -> prefs[KEY_WEBDAV_URL] ?: ""
                "webdav_username" -> prefs[KEY_WEBDAV_USERNAME] ?: ""
                "webdav_password" -> prefs[KEY_WEBDAV_PASSWORD] ?: ""
                "webdav_sync_state" -> prefs[KEY_WEBDAV_SYNC_STATE] ?: "{}"
                "personal_experience" -> prefs[KEY_PERSONAL_EXPERIENCE] ?: ""
                "personal_hobbies" -> prefs[KEY_PERSONAL_HOBBIES] ?: ""
                "encryption_password" -> prefs[KEY_ENCRYPTION_PASSWORD] ?: ""
                "editor_font" -> prefs[KEY_EDITOR_FONT] ?: "default"
                "editor_font_size" -> prefs[KEY_EDITOR_FONT_SIZE] ?: "16"
                else -> ""
            }
        }
    }

    fun getBooleanFlow(key: String): Flow<Boolean> {
        return when (key) {
            "focus_mode" -> focusMode
            "dark_theme" -> darkTheme
            "eink_mode" -> einkMode
            "encryption_enabled" -> encryptionEnabled
            else -> throw IllegalArgumentException("Unknown boolean key: $key")
        }
    }

    suspend fun setBoolean(key: String, value: Boolean) {
        when (key) {
            "focus_mode" -> setFocusMode(value)
            "dark_theme" -> setDarkTheme(value)
            "eink_mode" -> setEinkMode(value)
            "encryption_enabled" -> setEncryptionEnabled(value)
        }
    }
}
