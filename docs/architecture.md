# Architecture

The single Android module uses one activity, Compose Material 3, manual `AppContainer` wiring, ViewModels, Flow/StateFlow, Room, Preferences DataStore, and Android BLE APIs.

`AndroidBleScanner` filters FCD2 and emits typed callbackFlow events. The Android-free mapper/parser decodes protocol data. `SensorCoordinator` merges live observations with Room registrations, prunes stale live devices, preserves registered offline devices, and exposes state to ViewModels. Navigation owns scanning lifecycle: the main and sensor routes share foreground scanning; history and settings stop it.

Room stores `registered_sensors`, `downloaded_history_samples`, and `history_sync_states`. BTHome advertisements update live state, registered technical metadata, and one latest valid measurement snapshot per registered sensor; DataStore stores the downloaded-history retention preference.

Removing a registration also deletes downloaded history and its sync state through Room foreign-key cascades. A sensor that is still advertising remains in memory and immediately returns to the unregistered section.

## Responsibilities

- `ble/`: Android scanner, raw framework extraction, and Android-free mapper.
- `bthome/`: protocol model/parser.
- `domain/model/`: immutable UI-independent values.
- `data/local/`: Room entities, indexes, DAOs, database, and mappers.
- `data/settings/`: Preferences DataStore.
- `data/repository/`: merge, staleness, and downloaded-history retention.
- `ui/`: main, registration, detail, settings, history, and shared image presentation.

The live flow is scan → mapper/parser → coordinator → UI. The coordinator emits registered items in registration order, then fresh unregistered items. Freshness is strictly newer than five minutes. Stale observations are removed from the in-memory feed; registered sensors retain their latest valid measurement snapshot and original reception time. Live cards show the age of that snapshot after five minutes, including after an app restart. A malformed or unsupported packet can update diagnostics without changing the saved measurement time. New valid packets replace the complete snapshot, preserving the existing per-packet semantics rather than mixing fields received at different times. Downloaded-history retention removes only dated samples when the user saves a shorter preference. Photo Picker URIs are local metadata, rendered defensively, and backup is disabled.

Database version 3 adds latest-reading, history-capability and registration-identity metadata. Explicit migrations support the historical v1/v2 schema variants, including versions that reused a version number. Registrations and existing downloaded archives are preserved; the legacy live-history table is removed rather than imported into downloaded history. Latest snapshots are deleted with registration. Each new registration receives a fresh identity so late imports cannot attach to a re-registered address.
