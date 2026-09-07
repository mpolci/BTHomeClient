package io.github.mpolci.bthomeclient.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import io.github.mpolci.bthomeclient.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun save(settings: AppSettings)
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(retentionDurationMillis = preferences[RETENTION_DURATION], themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[THEME_MODE] } ?: ThemeMode.Dark)
    }

    override suspend fun save(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences.remove(LEGACY_MINIMUM_PERSISTENCE_INTERVAL)
            preferences[THEME_MODE] = settings.themeMode.name
            if (settings.retentionDurationMillis == null) {
                preferences.remove(RETENTION_DURATION)
            } else {
                preferences[RETENTION_DURATION] = settings.retentionDurationMillis
            }
        }
    }

    private companion object {
        val LEGACY_MINIMUM_PERSISTENCE_INTERVAL = androidx.datastore.preferences.core.longPreferencesKey("minimum_persistence_interval_millis")
        val RETENTION_DURATION = androidx.datastore.preferences.core.longPreferencesKey("retention_duration_millis")
        val THEME_MODE = androidx.datastore.preferences.core.stringPreferencesKey("theme_mode")
    }
}
