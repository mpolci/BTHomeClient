package io.github.mpolci.bthomeclient.ui.history

import io.github.mpolci.bthomeclient.data.local.DownloadedHistorySampleEntity
import io.github.mpolci.bthomeclient.data.local.HistorySyncStateEntity
import io.github.mpolci.bthomeclient.data.repository.HistoryUpdateState
import io.github.mpolci.bthomeclient.domain.model.RegisteredSensor
import io.github.mpolci.bthomeclient.domain.model.SensorAddress
import io.github.mpolci.bthomeclient.history.chart.HistoryChartPoint
import io.github.mpolci.bthomeclient.history.chart.HistoryTimeRange
import io.github.mpolci.bthomeclient.history.chart.HistoryViewport

enum class HistoryMeasurement(val label: String, val unit: String) {
    Temperature("Temperature", "°C"), Humidity("Humidity", "%"), Pressure("Pressure", "hPa"), Battery("Battery", "%");

    fun value(sample: DownloadedHistorySampleEntity): Double = when (this) {
        Temperature -> sample.temperatureCelsius
        Humidity -> sample.humidityPercent.toDouble()
        Pressure -> sample.pressureHectopascals
        Battery -> sample.batteryPercent.toDouble()
    }
}

data class HistorySelection(
    val measurement: HistoryMeasurement = HistoryMeasurement.Temperature,
    val viewport: HistoryViewport? = null,
)

data class HistorySensorData(
    val sensor: RegisteredSensor,
    val samples: List<DownloadedHistorySampleEntity>,
    val points: List<HistoryChartPoint>,
    val sync: HistorySyncStateEntity?,
) {
    val address: SensorAddress get() = sensor.address
    val undatedCount: Int get() = samples.count { it.recordedEpochMillis == null }
}

data class HistoryPageState(
    val sensors: List<HistorySensorData> = emptyList(),
    val updates: Map<SensorAddress, HistoryUpdateState> = emptyMap(),
    val measurement: HistoryMeasurement = HistoryMeasurement.Temperature,
    val viewport: HistoryViewport = HistoryViewport(0, 60_000),
    val coverage: HistoryTimeRange? = null,
    val selectedAddress: SensorAddress? = null,
    val comparisonAddress: SensorAddress? = null,
    val loading: Boolean = true,
    val sensorMissing: Boolean = false,
) {
    val selected: HistorySensorData? get() = sensors.firstOrNull { it.address == selectedAddress }
    val comparison: HistorySensorData? get() = sensors.firstOrNull { it.address == comparisonAddress }
}
