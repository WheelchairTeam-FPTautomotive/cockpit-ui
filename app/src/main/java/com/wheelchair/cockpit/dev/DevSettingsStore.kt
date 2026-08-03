package com.wheelchair.cockpit.dev

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicReference

// --- START MODIFICATION ---
private val Context.devSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cockpit_dev_settings"
)

class DevSettingsStore(context: Context) {

    private val dataStore = context.applicationContext.devSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshot = AtomicReference(DevSettings())

    // MODIFIED: Eager StateFlow + AtomicReference for OkHttp sync reads
    val settings: StateFlow<DevSettings> = dataStore.data
        .map { prefs -> prefs.toDevSettings() }
        .onEach { snapshot.set(it) }
        .stateIn(scope, SharingStarted.Eagerly, DevSettings())

    fun current(): DevSettings = snapshot.get()

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DEVELOPER_MODE] = enabled }
    }

    suspend fun setBaseUrl(url: String) {
        dataStore.edit { it[KEY_BASE_URL] = DevSettings.normalizeBaseUrl(url) }
    }

    suspend fun setMockRagEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MOCK_RAG] = enabled }
    }

    suspend fun setBypassDrivingLock(enabled: Boolean) {
        dataStore.edit { it[KEY_BYPASS_DRIVING] = enabled }
    }

    suspend fun setHttpLogLevel(level: HttpLogLevel) {
        dataStore.edit { it[KEY_HTTP_LOG_LEVEL] = level.name }
    }

    private fun Preferences.toDevSettings(): DevSettings = DevSettings(
        developerModeEnabled = this[KEY_DEVELOPER_MODE] ?: false,
        baseUrl = this[KEY_BASE_URL] ?: DevSettings.DEFAULT_BASE_URL,
        mockRagEnabled = this[KEY_MOCK_RAG] ?: false,
        bypassDrivingLock = this[KEY_BYPASS_DRIVING] ?: false,
        httpLogLevel = HttpLogLevel.fromName(this[KEY_HTTP_LOG_LEVEL])
    )

    companion object {
        private val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode_enabled")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MOCK_RAG = booleanPreferencesKey("mock_rag_enabled")
        private val KEY_BYPASS_DRIVING = booleanPreferencesKey("bypass_driving_lock")
        private val KEY_HTTP_LOG_LEVEL = stringPreferencesKey("http_log_level")
    }
}
// --- END MODIFICATION ---
