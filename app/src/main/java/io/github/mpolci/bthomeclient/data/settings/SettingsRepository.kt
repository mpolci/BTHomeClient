package io.github.mpolci.bthomeclient.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun save(settings: AppSettings)
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            minimumPersistenceIntervalMillis = preferences[MINIMUM_PERSISTENCE_INTERVAL]
                ?: AppSettings.DEFAULT_MINIMUM_PERSISTENCE_INTERVAL_MILLIS,
            retentionDurationMillis = preferences[RETENTION_DURATION],
        )
    }

    override suspend fun save(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[MINIMUM_PERSISTENCE_INTERVAL] = settings.minimumPersistenceIntervalMillis
            if (settings.retentionDurationMillis == null) {
                preferences.remove(RETENTION_DURATION)
            } else {
                preferences[RETENTION_DURATION] = settings.retentionDurationMillis
            }
        }
    }

    private companion object {
        val MINIMUM_PERSISTENCE_INTERVAL = longPreferencesKey("minimum_persistence_interval_millis")
        val RETENTION_DURATION = longPreferencesKey("retention_duration_millis")
    }
}
