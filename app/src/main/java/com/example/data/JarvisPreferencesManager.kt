package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GeminiConnectionStatus {
    NOT_CONFIGURED,
    CONNECTED,
    INVALID_KEY,
    NETWORK_ERROR,
    QUOTA_EXCEEDED
}

class JarvisPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis_ai_settings", Context.MODE_PRIVATE)

    private val keyCustomApiKey = "gemini_api_key_custom"
    private val keySelectedModel = "gemini_selected_model"
    private val keyConnectionStatus = "gemini_conn_status"
    private val keyConnectionError = "gemini_conn_error"
    private val keyLastTestedTime = "gemini_last_tested_time"

    private val _apiKeyFlow = MutableStateFlow(getSavedApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private val _selectedModelFlow = MutableStateFlow(getSelectedModel())
    val selectedModelFlow: StateFlow<String> = _selectedModelFlow.asStateFlow()

    private val _connectionStatusFlow = MutableStateFlow(getConnectionStatus())
    val connectionStatusFlow: StateFlow<GeminiConnectionStatus> = _connectionStatusFlow.asStateFlow()

    private val _lastErrorMessageFlow = MutableStateFlow(getLastErrorMessage())
    val lastErrorMessageFlow: StateFlow<String> = _lastErrorMessageFlow.asStateFlow()

    fun getSavedApiKey(): String {
        return prefs.getString(keyCustomApiKey, "") ?: ""
    }

    /**
     * Returns the active API key: User custom key if present, otherwise BuildConfig.GEMINI_API_KEY.
     */
    fun getActiveApiKey(): String {
        val userKey = getSavedApiKey().trim()
        if (userKey.isNotEmpty()) {
            return userKey
        }
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
        return if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
    }

    fun saveApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(keyCustomApiKey, clean).apply()
        _apiKeyFlow.value = clean
        if (clean.isEmpty()) {
            setConnectionStatus(GeminiConnectionStatus.NOT_CONFIGURED, "")
        }
    }

    fun clearApiKey() {
        prefs.edit().remove(keyCustomApiKey).apply()
        _apiKeyFlow.value = ""
        setConnectionStatus(GeminiConnectionStatus.NOT_CONFIGURED, "API Key cleared.")
    }

    fun getSelectedModel(): String {
        return prefs.getString(keySelectedModel, "gemini-3.5-flash") ?: "gemini-3.5-flash"
    }

    fun saveSelectedModel(model: String) {
        prefs.edit().putString(keySelectedModel, model).apply()
        _selectedModelFlow.value = model
    }

    fun getConnectionStatus(): GeminiConnectionStatus {
        val name = prefs.getString(keyConnectionStatus, GeminiConnectionStatus.NOT_CONFIGURED.name)
        return try {
            GeminiConnectionStatus.valueOf(name ?: GeminiConnectionStatus.NOT_CONFIGURED.name)
        } catch (e: Exception) {
            GeminiConnectionStatus.NOT_CONFIGURED
        }
    }

    fun getLastErrorMessage(): String {
        return prefs.getString(keyConnectionError, "") ?: ""
    }

    private val keyVoiceProfile = "jarvis_voice_profile"
    private val keyWakeScreenEnabled = "jarvis_wake_screen_enabled"
    private val keyForegroundServiceEnabled = "jarvis_foreground_service_enabled"

    private val _voiceProfileFlow = MutableStateFlow(getVoiceProfile())
    val voiceProfileFlow: StateFlow<String> = _voiceProfileFlow.asStateFlow()

    private val _wakeScreenEnabledFlow = MutableStateFlow(isWakeScreenEnabled())
    val wakeScreenEnabledFlow: StateFlow<Boolean> = _wakeScreenEnabledFlow.asStateFlow()

    private val _foregroundServiceEnabledFlow = MutableStateFlow(isForegroundServiceEnabled())
    val foregroundServiceEnabledFlow: StateFlow<Boolean> = _foregroundServiceEnabledFlow.asStateFlow()

    fun getVoiceProfile(): String {
        return prefs.getString(keyVoiceProfile, "FEMALE") ?: "FEMALE"
    }

    fun saveVoiceProfile(profile: String) {
        prefs.edit().putString(keyVoiceProfile, profile).apply()
        _voiceProfileFlow.value = profile
    }

    fun isWakeScreenEnabled(): Boolean {
        return prefs.getBoolean(keyWakeScreenEnabled, true)
    }

    fun setWakeScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(keyWakeScreenEnabled, enabled).apply()
        _wakeScreenEnabledFlow.value = enabled
    }

    fun isForegroundServiceEnabled(): Boolean {
        return prefs.getBoolean(keyForegroundServiceEnabled, false)
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(keyForegroundServiceEnabled, enabled).apply()
        _foregroundServiceEnabledFlow.value = enabled
    }

    fun setConnectionStatus(status: GeminiConnectionStatus, errorMessage: String = "") {
        prefs.edit()
            .putString(keyConnectionStatus, status.name)
            .putString(keyConnectionError, errorMessage)
            .putLong(keyLastTestedTime, System.currentTimeMillis())
            .apply()
        _connectionStatusFlow.value = status
        _lastErrorMessageFlow.value = errorMessage
    }
}
