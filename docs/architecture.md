# Architecture

The single Android module uses one activity, Compose Material 3, manual `AppContainer` wiring, ViewModels, Flow/StateFlow, Room, Preferences DataStore, and Android BLE APIs.

`AndroidBleScanner` filters FCD2 and emits typed callbackFlow events. The Android-free mapper/parser decodes protocol data. `SensorCoordinator` merges live observations with Room registrations, prunes stale live devices, preserves registered offline devices, throttles persistence, and exposes state to ViewModels. Navigation owns scanning lifecycle: only the main route starts scanning.

Room v1 stores `registered_sensors` and nullable composite `sensor_readings`; DataStore stores sampling and retention preferences.

## Responsibilities

- `ble/`: Android scanner, raw framework extraction, and Android-free mapper.
- `bthome/`: protocol model/parser.
- `domain/model/`: immutable UI-independent values.
- `data/local/`: Room entities, indexes, DAOs, database, and mappers.
- `data/settings/`: Preferences DataStore.
- `data/repository/`: merge, staleness, throttling, and retention.
- `ui/`: main, registration, detail, settings, history, and shared image presentation.

The flow is scan → mapper/parser → coordinator → Room/UI. The coordinator emits registered items in registration order, then fresh unregistered items. Freshness is strictly newer than five minutes; registered records remain offline while stale observations are removed. Per-sensor persistence defaults to 15 minutes and retention runs after inserts or a shorter setting. History APIs are chronological/range based. Photo Picker URIs are local metadata, rendered defensively, and backup is disabled.
