package io.github.mpolci.bthomeclient.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mpolci.bthomeclient.domain.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test
    fun savesAndRestoresValidatedSettings() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val scope = CoroutineScope(SupervisorJob())
            val file = context.cacheDir.resolve("settings-${UUID.randomUUID()}.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val repository = SettingsRepository(dataStore)
            val expected = AppSettings(retentionDurationMillis = 604_800_000, themeMode = io.github.mpolci.bthomeclient.domain.model.ThemeMode.System)

            try {
                val legacyKey = longPreferencesKey("minimum_persistence_interval_millis")
                dataStore.edit { it[legacyKey] = 60_000 }
                repository.save(expected)
                assertEquals(expected, repository.settings.first())
                assertEquals(null, dataStore.data.first()[legacyKey])
            } finally {
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                file.delete()
            }
        }
    }
}
