# Testing

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew compileDebugAndroidTestKotlin
./gradlew packageDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

JVM tests cover parser, coordinator, ViewModels, routes, and formatters. Compose/Room/DataStore tests compile/package as instrumentation tests; `connectedDebugAndroidTest` executes them on a device.

```text
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -i bthome
```

For a real-sensor smoke test, authorize a phone with ADB, install the debug build, grant Nearby Devices through the app action, enable Bluetooth, and verify an unencrypted BTHome v2 FCD2 sensor appears with plausible units. Navigate away/back, register/edit it, try Photo Picker where practical, change settings, restart the process, and confirm it becomes offline after five minutes of silence while unregistered devices disappear.

If no sensor appears, check Nearby Devices permission, Bluetooth, FCD2 v2 unencrypted advertising, range, and sensor sleep interval. `neverForLocation` can filter some Bluetooth results on some Android implementations; address randomization can create a new identity. For ADB, check `adb devices`, unlock and authorize the phone.

## Physical validation result

On 2026-09-01, a Google Pixel 4a (5G) running Android 14/API 34 completed 11/11 connected tests. A SENE-A unencrypted BTHome v2 sensor was detected with plausible temperature, humidity, pressure, and battery values. Registration and persistence across force-stop/restart were verified; detail and settings screens were exercised. Editor/settings saving is covered by automated tests. Final logcat contained no AndroidRuntime, BluetoothLeScanner, or Room errors.

Photo Picker selection and a real five-minute silence/offline wait were not manually exercised during this validation. Automated coverage remains available where applicable.
