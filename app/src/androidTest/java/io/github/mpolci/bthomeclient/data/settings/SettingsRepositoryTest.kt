package io.github.mpolci.bthomeclient.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
            val repository = SettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )
            val expected = AppSettings(
                minimumPersistenceIntervalMillis = 60_000,
                retentionDurationMillis = 604_800_000,
            )

            try {
                repository.save(expected)
                assertEquals(expected, repository.settings.first())
            } finally {
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                file.delete()
            }
        }
    }
}
