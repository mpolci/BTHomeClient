package io.github.mpolci.bthomeclient.domain.model

import org.junit.Test

class AppSettingsTest {
    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero retention duration`() {
        AppSettings(retentionDurationMillis = 0)
    }
}
