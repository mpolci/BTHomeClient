# BTHome Client

Local Android monitoring for unencrypted BTHome v2 environmental BLE advertisements on Android 12/API 31 and newer.

## Features

- Petrol Material dashboard with registered sensors first and nearby unregistered sensors after them.
- All available environmental readings shown directly, with optional image/placement and a compact battery indicator.
- Latest valid readings and their reception timestamps retained for registered sensors across restarts.
- Registration with optional Android Photo Picker image.
- Detail editing/removal with live protocol diagnostics and a history entry point.
- Global downloaded-history retention setting.
- Accessible history placeholder while chart visualization is deferred.

## Setup

Use JDK 17, Android SDK `android-36.1`, and the checked-in wrapper:

```text
./gradlew assembleDebug
./gradlew installDebug
```

Android Studio can open the project directly; the app has `minSdk 31` and compiles against SDK 36.1.

## Privacy and scanning

Only `BLUETOOTH_SCAN` (`neverForLocation`) and `BLUETOOTH_CONNECT` are declared. No location, storage, or media permission is requested. Scanning is foreground-only and runs only while the main dashboard route is started. Android backup is disabled; registered data remains local.

Nearby Devices is requested only after the user chooses Allow. Bluetooth enable and app-settings actions are also user initiated.

Registered sensors use normalized BLE addresses as identity; address randomization can make a device appear new. Room stores registered metadata and downloaded-history state; unregistered BTHome advertisements remain memory-only; the latest valid measurement packet is stored with its reception timestamp for each registered sensor, without creating a live-derived historical series. DataStore stores the downloaded-history retention preference, which is unlimited by default and offers 7/30/90/365-day choices for dated samples.

The app supports BTHome service UUID `0xFCD2`, v2, unencrypted packets. Temperature, humidity, pressure, CO2, battery, packet ID, timestamp, device type, and firmware are decoded with typed malformed/unsupported diagnostics. See [architecture](docs/architecture.md), [protocol support](docs/bthome-support.md), and [testing](docs/testing.md).

## Verification

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

Physical validation completed on 2026-09-01 with a Google Pixel 4a (5G), Android 14/API 34: 11/11 connected tests passed and an unencrypted BTHome v2 SENE-A sensor was detected with plausible temperature, humidity, pressure, and battery values. Registration and persistence across force-stop/restart were verified; detail and settings screens were exercised. Editor/settings saving is covered by automated tests.

## Limitations

BTHome v1, encrypted packets, GATT, background scanning, charts, cloud/accounts, all BTHome objects, and computed mold/dew/heat metrics are out of scope.
