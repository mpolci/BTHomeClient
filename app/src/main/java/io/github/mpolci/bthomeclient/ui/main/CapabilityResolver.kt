package io.github.mpolci.bthomeclient.ui.main

data class CapabilityFacts(val bleSupported: Boolean, val missingPermissionRationales: List<Boolean>, val requestAttempted: Boolean, val bluetoothEnabled: Boolean) {
    val permissionsGranted get() = missingPermissionRationales.isEmpty()
}
fun resolveCapability(facts: CapabilityFacts): ScanCapability = when {
    !facts.bleSupported -> ScanCapability.BleUnsupported
    !facts.permissionsGranted && facts.requestAttempted && facts.missingPermissionRationales.any { !it } -> ScanCapability.PermissionPermanentlyDenied
    !facts.permissionsGranted -> ScanCapability.PermissionMissing
    !facts.bluetoothEnabled -> ScanCapability.BluetoothDisabled
    else -> ScanCapability.Ready
}
