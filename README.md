# BTHome Client

Local Android monitoring for unencrypted BTHome v2 environmental BLE advertisements on Android 12/API 31 and newer.

## Features

- Petrol Material dashboard with registered sensors first and nearby unregistered sensors after them.
- All available environmental readings shown directly, with optional image/placement and a compact battery indicator.
- Latest valid readings and their reception timestamps retained for registered sensors across restarts.
- Same-page registration, optional placement/photo, per-field editing, and removal with confirmation.
- Sensor identity, exact reception age/battery, protocol diagnostics and discovered history capability.
- Automatic sensor-stored history updates, with a shared queue and readable cached archives during failures.
- Portrait historical overview with a shared measurement/time window, plus landscape exploration using a fixed cursor, pan/pinch and sensor comparison.
- Global downloaded-history retention and Dark Petrol, Light or System appearance settings.

## Setup

Use JDK 17, Android SDK `android-36.1`, and the checked-in wrapper:

```text
./gradlew assembleDebug
./gradlew installDebug
```

Android Studio can open the project directly; the app has `minSdk 31` and compiles against SDK 36.1.

## Privacy and scanning

Only `BLUETOOTH_SCAN` (`neverForLocation`) and `BLUETOOTH_CONNECT` are declared. No location, storage, or media permission is requested. Scanning is foreground-only on Live and sensor pages; history pauses scanning and owns its GATT transfer session. Android backup is disabled; registered data remains local.

Nearby Devices is requested only after the user chooses Allow. Bluetooth enable and app-settings actions are also user initiated.

Registered sensors use normalized BLE addresses as identity; address randomization can make a device appear new. Room stores registered metadata and downloaded-history state; unregistered BTHome advertisements remain memory-only; the latest valid measurement packet is stored with its reception timestamp for each registered sensor, without creating a live-derived historical series. DataStore stores the downloaded-history retention preference, which is unlimited by default and offers 7/30/90/365-day choices for dated samples.

The app supports BTHome service UUID `0xFCD2`, v2, unencrypted packets. Temperature, humidity, pressure, CO2, battery, packet ID, timestamp, device type, and firmware are decoded with typed malformed/unsupported diagnostics. See [architecture](docs/architecture.md), [protocol support](docs/bthome-support.md), and [testing](docs/testing.md).

## Verification

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

Physical validation uses a Google Pixel 4a (5G), Android 14/API 34. The native redesign was exercised with a real SENE-A, including live readings, registration, full and incremental history download, and persisted data after process restart. Automated suites also cover database migrations, concurrency, chart math, native navigation, editing, failure states and accessibility. See [testing](docs/testing.md) and [the implementation record](docs/ux/implementation-plan.md) for exact results.

## Limitations

BTHome v1, encrypted advertisements, background scanning, cloud/accounts, additional BTHome objects and computed mold/dew/heat metrics are out of scope. GATT history currently supports the existing SENE-A protocol only. Device reset/renaming and security enrollment are candidate features, not implemented controls. History comes from sensor storage; the app does not build history from live advertisements. Undated samples are preserved but cannot be plotted on the timeline.
