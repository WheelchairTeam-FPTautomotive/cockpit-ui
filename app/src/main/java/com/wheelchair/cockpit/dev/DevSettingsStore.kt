package com.wheelchair.cockpit.dev

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.launch
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
// ReplaceFileCorruptionHandler: corrupt cockpit_dev_settings resets to defaults instead of crashing boot
private val Context.devSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cockpit_dev_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.w(TAG_DEV_SETTINGS, "Dev settings corrupt; resetting to defaults", ex)
        emptyPreferences()
    }
)

private const val TAG_DEV_SETTINGS = "CockpitUI"

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

    // --- START MODIFICATION ---
    // Keep AtomicReference in sync immediately so OkHttp interceptor sees Apply
    // before DataStore flow re-emits (fixes stale 10.0.2.2 host).
    private fun publish(update: (DevSettings) -> DevSettings): DevSettings {
        val next = snapshot.updateAndGet(update)
        return next
    }

    /** Immediate host override for OkHttp; persists to DataStore asynchronously. */
    fun applyBaseUrlNow(url: String): String {
        val normalized = DevSettings.normalizeBaseUrl(url)
        publish { it.copy(baseUrl = normalized) }
        scope.launch {
            dataStore.edit { it[KEY_BASE_URL] = normalized }
        }
        Log.i(TAG_DEV_SETTINGS, "Dev baseUrl applied now → $normalized")
        return normalized
    }

    /** Immediate developer-mode flag for OkHttp; persists asynchronously. */
    fun applyDeveloperModeNow(enabled: Boolean) {
        publish { it.copy(developerModeEnabled = enabled) }
        scope.launch {
            dataStore.edit { it[KEY_DEVELOPER_MODE] = enabled }
        }
    }

    fun applyShowCitationCardsNow(enabled: Boolean) {
        publish { it.copy(showCitationCards = enabled) }
        scope.launch {
            dataStore.edit { it[KEY_SHOW_CITATIONS] = enabled }
        }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        applyDeveloperModeNow(enabled)
        dataStore.edit { it[KEY_DEVELOPER_MODE] = enabled }
    }

    suspend fun setBaseUrl(url: String) {
        applyBaseUrlNow(url)
        // ensure persist completed for callers that await
        dataStore.edit { it[KEY_BASE_URL] = DevSettings.normalizeBaseUrl(url) }
    }

    suspend fun setMockRagEnabled(enabled: Boolean) {
        publish { it.copy(mockRagEnabled = enabled) }
        dataStore.edit { it[KEY_MOCK_RAG] = enabled }
    }

    suspend fun setBypassDrivingLock(enabled: Boolean) {
        publish { it.copy(bypassDrivingLock = enabled) }
        dataStore.edit { it[KEY_BYPASS_DRIVING] = enabled }
    }

    suspend fun setHttpLogLevel(level: HttpLogLevel) {
        publish { it.copy(httpLogLevel = level) }
        dataStore.edit { it[KEY_HTTP_LOG_LEVEL] = level.name }
    }

    suspend fun setShowCitationCards(enabled: Boolean) {
        publish { it.copy(showCitationCards = enabled) }
        dataStore.edit { it[KEY_SHOW_CITATIONS] = enabled }
    }

    private fun Preferences.toDevSettings(): DevSettings = DevSettings(
        developerModeEnabled = this[KEY_DEVELOPER_MODE] ?: false,
        baseUrl = this[KEY_BASE_URL] ?: DevSettings.DEFAULT_BASE_URL,
        mockRagEnabled = this[KEY_MOCK_RAG] ?: false,
        bypassDrivingLock = this[KEY_BYPASS_DRIVING] ?: false,
        httpLogLevel = HttpLogLevel.fromName(this[KEY_HTTP_LOG_LEVEL]),
        showCitationCards = this[KEY_SHOW_CITATIONS] ?: true,
    )

    companion object {
        private val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode_enabled")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_MOCK_RAG = booleanPreferencesKey("mock_rag_enabled")
        private val KEY_BYPASS_DRIVING = booleanPreferencesKey("bypass_driving_lock")
        private val KEY_HTTP_LOG_LEVEL = stringPreferencesKey("http_log_level")
        private val KEY_SHOW_CITATIONS = booleanPreferencesKey("show_citation_cards")
    }
    // --- END MODIFICATION ---
}
// --- END MODIFICATION ---
