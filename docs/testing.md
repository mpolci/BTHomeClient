# Testing

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew compileDebugAndroidTestKotlin
./gradlew packageDebugAndroidTest
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

JVM tests cover parser, coordinator, ViewModels, routes, and formatters. Compose/Room/DataStore tests compile/package as instrumentation tests; `connectedDebugAndroidTest` executes them on a device.

```text
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat --pid=<app-process-id>
```

For a real-sensor smoke test, authorize a phone with ADB, install the debug build, grant Nearby Devices through the app action, enable Bluetooth, and verify an unencrypted BTHome v2 FCD2 sensor appears with plausible units. Navigate away/back, register/edit it, try Photo Picker where practical, change settings, restart the process, and confirm it becomes offline after five minutes of silence while unregistered devices disappear.

If no sensor appears, check Nearby Devices permission, Bluetooth, FCD2 v2 unencrypted advertising, range, and sensor sleep interval. `neverForLocation` can filter some Bluetooth results on some Android implementations; address randomization can create a new identity. For ADB, check `adb devices`, unlock and authorize the phone.

## Physical validation result

On 2026-09-01, a Google Pixel 4a (5G) running Android 14/API 34 completed 11/11 connected tests. A SENE-A unencrypted BTHome v2 sensor was detected with plausible temperature, humidity, pressure, and battery values. Registration and persistence across force-stop/restart were verified; detail and settings screens were exercised. Editor/settings saving is covered by automated tests. Final logcat contained no AndroidRuntime, BluetoothLeScanner, or Room errors.

Photo Picker selection and a real five-minute silence/offline wait were not manually exercised during this validation. Automated coverage remains available where applicable.

## Native live-screen iteration — 2026-09-07

The petrol Material live screen and latest-reading persistence passed JVM tests, lint, debug assembly, and 14/14 connected Android tests on the Pixel 4a (5G), Android 14.

Coverage includes registered-first sections, all environmental values without expansion, optional battery display, stale values remaining available with Bluetooth off, the five-minute boundary, restoration by a new coordinator, rejected older/invalid updates, and a version-1 database opened through the version-2 migration and reopened with a saved snapshot.

The migration fixture is created from the checked-in Room JSON schema using Android SQLite/JSON APIs, avoiding the serialization incompatibility encountered in MigrationTestHelper. Room validates the migrated schema on opening. The initial connected run also had transient Compose activity failures; subsequent targeted and full runs succeeded.

These results describe the initial live-only milestone. The subsequent implementation replaces the older routes and adds an explicit appearance preference with Dark Petrol as the default.

## Native sensor-page milestone — 2026-09-07

The live screen and new sensor page passed 21/21 connected tests, including same-page registration, per-field save/cancel, photo actions, history capability states, deletion confirmation, and explicit v3 migrations from all known historical v1/v2 schema shapes. JVM tests and native test compilation also passed. The device is confirmed by the owner as a development phone; reinstall the final app after Gradle instrumentation cleanup.

## Completed native history and UX implementation — 2026-09-07

- All 113 JVM tests passed, including archive import/retention, cursor boundaries, registration replacement, cancellation/queue ownership, retained ViewModel state, chart segmentation and extrema reduction.
- The full native suite passed 57/57 tests on the Pixel 4a (5G), Android 14. It covers Room migrations, preferences, Live and sensor pages, registration/edit/removal, real application navigation, orientation and Activity recreation, chart gestures, cache/error states and screenshot fixtures.
- A final empty-overview regression case increased the native suite to 58 cases. All 17 affected `HistoryScreenTest` cases subsequently passed; the final large-font history screenshot test also passed. Unaffected native cases were not repeated after these small presentation changes.
- `lintDebug`, `assembleDebug` and instrumentation compilation passed on the final sources.
- An independent native visual review inspected twelve synthetic scenarios at ordinary and 1.6× font scales, plus a real downloaded archive. Findings were corrected and affected screenshots recaptured. See [the review and screenshots](ux/native-review.md).

The real SENE-A was discovered, registered from its sensor page and automatically downloaded through the production GATT/repository/navigation path. A 130,306-position transfer produced 1,010 dated and four undated samples. Registration and archive survived force-stop/restart, and a subsequent automatic incremental download grew the dated archive to 1,011 samples. Known historical truncation now remains visible across later successful incremental updates. The final installed app was provisioned with the real sensor again after instrumentation finished; that full download contained 1,013 dated samples, and a final process restart retained the registration and current readings.

The test runner may reinstall the app before a connected run, resetting its development data and permissions even when `leaveApksInstalledAfterRun=true` retains the APK afterward. Complete all instrumentation first, then restore the desired final on-device state. Avoid running destructive test databases against the production database; migration fixtures use separate named databases, and navigation tests clean up only their isolated synthetic registration.

Screenshot fixtures live in `UxVisualReviewTest`, using a debug-only host with the production composables. The phone writes PNG files to `/sdcard/Android/data/io.github.mpolci.bthomeclient/files/ux-review/`; the approved copies are in `docs/ux/review/native/`. When capturing, wait for both Compose and Android window transitions rather than treating Compose idle as proof that the system screenshot is ready.

Physical coverage is Android 14 on this development phone and the existing SENE-A history protocol. Additional Android versions, other sensor firmware, prolonged multi-device radio contention and selecting a personal image through the system Photo Picker were not manually certified in this pass. Photo actions, save/cancel behavior and safe persistence error handling are covered by UI tests and code review; fixture images are synthetic.
