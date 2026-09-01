package io.github.mpolci.bthomeclient.ble

import io.github.mpolci.bthomeclient.bthome.BTHomeV2Parser
import io.github.mpolci.bthomeclient.domain.model.ScanObservation
import io.github.mpolci.bthomeclient.domain.model.SensorAddress

class BleAdvertisementMapper(private val parser: BTHomeV2Parser) {
    fun map(address: String, name: String?, rssi: Int, receptionEpochMillis: Long, serviceUuid: Int, serviceData: ByteArray): ScanEvent? {
        val normalizedAddress = SensorAddress.from(address) ?: return null
        return ScanEvent.Advertisement(ScanObservation(normalizedAddress, name, rssi, receptionEpochMillis, parser.parse(serviceUuid, serviceData)))
    }
}
