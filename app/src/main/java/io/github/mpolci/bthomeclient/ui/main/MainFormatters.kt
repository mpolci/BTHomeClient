package io.github.mpolci.bthomeclient.ui.main

import io.github.mpolci.bthomeclient.bthome.BTHomeParseResult
import io.github.mpolci.bthomeclient.domain.model.MeasurementType
import io.github.mpolci.bthomeclient.bthome.BTHomeMeasurements
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun mergedMeasurements(live: BTHomeMeasurements?, stored: BTHomeMeasurements?) = BTHomeMeasurements(
    live?.batteryPercent ?: stored?.batteryPercent, live?.temperatureCelsius ?: stored?.temperatureCelsius,
    live?.humidityPercent ?: stored?.humidityPercent, live?.pressureHectopascals ?: stored?.pressureHectopascals,
    live?.carbonDioxidePpm ?: stored?.carbonDioxidePpm,
)
fun formatLastSeen(epochMillis: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String = epochMillis?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zoneId).format(Instant.ofEpochMilli(it)) } ?: "—"
fun diagnosticLabel(result: BTHomeParseResult?): String = when (result) {
    is BTHomeParseResult.Decoded -> result.advertisement.diagnostics.joinToString { it.toString() }.ifBlank { "None" }
    is BTHomeParseResult.Unsupported -> result.reason.name + result.diagnostics.joinToString(prefix = " · ") { it.toString() }
    is BTHomeParseResult.Malformed -> result.reason.name
    null -> "—"
}
fun measurementTypes(measurements: BTHomeMeasurements): Set<MeasurementType> = buildSet {
    if (measurements.batteryPercent != null) add(MeasurementType.Battery); if (measurements.temperatureCelsius != null) add(MeasurementType.Temperature); if (measurements.humidityPercent != null) add(MeasurementType.Humidity); if (measurements.pressureHectopascals != null) add(MeasurementType.Pressure); if (measurements.carbonDioxidePpm != null) add(MeasurementType.CarbonDioxide)
}
