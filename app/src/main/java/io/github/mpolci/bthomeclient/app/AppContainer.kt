package io.github.mpolci.bthomeclient.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import io.github.mpolci.bthomeclient.data.local.BTHomeDatabase
import io.github.mpolci.bthomeclient.data.repository.SensorCoordinator
import io.github.mpolci.bthomeclient.data.settings.SettingsRepository
import io.github.mpolci.bthomeclient.util.SystemClock
import io.github.mpolci.bthomeclient.ble.AndroidBleScanner
import io.github.mpolci.bthomeclient.ble.AndroidHistoryDownloader
import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.ui.main.MainScanViewModel
import io.github.mpolci.bthomeclient.ui.registration.RegistrationViewModel
import io.github.mpolci.bthomeclient.ui.detail.DetailViewModel
import io.github.mpolci.bthomeclient.ui.settings.SettingsViewModel
import io.github.mpolci.bthomeclient.ui.history.HistoryViewModel
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.data.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.databaseBuilder(context, BTHomeDatabase::class.java, "bthome-client.db")
        .addMigrations(BTHomeDatabase.MIGRATION_1_3, BTHomeDatabase.MIGRATION_2_3)
        .build()
    private val sensorMutationMutex = kotlinx.coroutines.sync.Mutex()
    private val settingsRepository = SettingsRepository(context.settingsDataStore)

    val sensorCoordinator = SensorCoordinator(
        registeredSensorDao = database.registeredSensorDao(),
        downloadedHistoryDao = database.downloadedHistoryDao(),
        settingsRepository = settingsRepository,
        clock = SystemClock,
        applicationScope = applicationScope,
        mutationMutex = sensorMutationMutex,
    )

    val settings = sensorCoordinator.settings
    val bleScanner = AndroidBleScanner(context, SystemClock, BTHomeV2Parser())
    private val historyRepository = HistoryRepository(AndroidHistoryDownloader(context), database.downloadedHistoryDao(), SystemClock, sensorMutationMutex) { settingsRepository.settings.first().retentionDurationMillis }
    val historySession = io.github.mpolci.bthomeclient.data.repository.HistorySessionCoordinator(historyRepository, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    fun createMainScanViewModel() = MainScanViewModel(bleScanner, sensorCoordinator)
    fun createRegistrationViewModel(address: SensorAddress) = RegistrationViewModel(address, sensorCoordinator)
    fun createDetailViewModel(address: SensorAddress) = DetailViewModel(address, sensorCoordinator)
    fun createSettingsViewModel() = SettingsViewModel(sensorCoordinator)
    fun historyRepository() = historyRepository
    fun createHistoryViewModel(address: SensorAddress?, selection: io.github.mpolci.bthomeclient.ui.history.HistorySelection? = null) = HistoryViewModel(address, sensorCoordinator, historyRepository, historySession, selection)
}
