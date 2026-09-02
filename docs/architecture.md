# Architecture

The single Android module uses one activity, Compose Material 3, manual `AppContainer` wiring, ViewModels, Flow/StateFlow, Room, Preferences DataStore, and Android BLE APIs.

`AndroidBleScanner` filters FCD2 and emits typed callbackFlow events. The Android-free mapper/parser decodes protocol data. `SensorCoordinator` merges live observations with Room registrations, prunes stale live devices, preserves registered offline devices, and exposes state to ViewModels. Navigation owns scanning lifecycle: only the main route starts scanning.

Room v2 stores `registered_sensors`, `downloaded_history_samples`, and `history_sync_states`. BTHome advertisements update only live state and registered technical metadata; DataStore stores the downloaded-history retention preference.

Removing a registration also deletes downloaded history and its sync state through Room foreign-key cascades. A sensor that is still advertising remains in memory and immediately returns to the unregistered section.

## Responsibilities

- `ble/`: Android scanner, raw framework extraction, and Android-free mapper.
- `bthome/`: protocol model/parser.
- `domain/model/`: immutable UI-independent values.
- `data/local/`: Room entities, indexes, DAOs, database, and mappers.
- `data/settings/`: Preferences DataStore.
- `data/repository/`: merge, staleness, and downloaded-history retention.
- `ui/`: main, registration, detail, settings, history, and shared image presentation.

The live flow is scan → mapper/parser → coordinator → UI. The coordinator emits registered items in registration order, then fresh unregistered items. Freshness is strictly newer than five minutes; registered records remain offline while stale observations are removed and render an explicit no-live-reading state. Downloaded-history retention removes only dated samples when the user saves a shorter preference. Photo Picker URIs are local metadata, rendered defensively, and backup is disabled.
