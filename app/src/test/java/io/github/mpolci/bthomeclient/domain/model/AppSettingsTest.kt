package io.github.mpolci.bthomeclient.domain.model

import org.junit.Test

class AppSettingsTest {
    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero persistence interval`() {
        AppSettings(minimumPersistenceIntervalMillis = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero retention duration`() {
        AppSettings(retentionDurationMillis = 0)
    }
}
