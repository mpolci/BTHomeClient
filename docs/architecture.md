# Architecture

The single Android module uses one activity, Compose Material 3, manual `AppContainer` wiring, ViewModels, Flow/StateFlow, Room, Preferences DataStore, and Android BLE APIs.

`AndroidBleScanner` filters FCD2 and emits typed callbackFlow events. The Android-free mapper/parser decodes protocol data. `SensorCoordinator` merges live observations with Room registrations, prunes stale live devices, preserves registered offline devices, and exposes state to ViewModels. Navigation owns scanning lifecycle: the main and sensor routes share foreground scanning; history and settings stop it.

Room stores `registered_sensors`, `downloaded_history_samples`, and `history_sync_states`. BTHome advertisements update live state, registered technical metadata, and one latest valid measurement snapshot per registered sensor; DataStore stores downloaded-history retention and appearance preferences. Dark Petrol is the default, with explicit Light and System options.

Removing a registration also deletes downloaded history and its sync state through Room foreign-key cascades. A sensor that is still advertising remains in memory and immediately returns to the unregistered section.

## Responsibilities

- `ble/`: Android scanner, raw framework extraction, and Android-free mapper.
- `bthome/`: protocol model/parser.
- `domain/model/`: immutable UI-independent values.
- `data/local/`: Room entities, indexes, DAOs, database, and mappers.
- `data/settings/`: Preferences DataStore.
- `data/repository/`: merge, staleness, downloaded-history import/retention, and shared download-session ownership.
- `history/chart/`: Android-independent viewport, segmentation, nearest-sample and extrema-preserving reduction.
- `ui/`: live, sensor detail and inline editing, settings, historical overview/detail, and shared image presentation. The older standalone registration screen is no longer reachable.

The live flow is scan → mapper/parser → coordinator → UI. The coordinator emits registered items in registration order, then fresh unregistered items. Freshness is strictly newer than five minutes. Stale observations are removed from the in-memory feed; registered sensors retain their latest valid measurement snapshot and original reception time. Live cards show the age of that snapshot after five minutes, including after an app restart. A malformed or unsupported packet can update diagnostics without changing the saved measurement time. New valid packets replace the complete snapshot, preserving the existing per-packet semantics rather than mixing fields received at different times. Downloaded-history retention removes only dated samples when the user saves a shorter preference. Photo Picker URIs are local metadata, rendered defensively, and backup is disabled.

Database version 3 adds latest-reading, history-capability and registration-identity metadata. Explicit migrations support the historical v1/v2 schema variants, including versions that reused a version number. Registrations and existing downloaded archives are preserved; the legacy live-history table is removed rather than imported into downloaded history. Latest snapshots are deleted with registration. Each new registration receives a fresh identity so late imports cannot attach to a re-registered address.

## Historical exploration

History capability starts Unknown; opening history discovers the service. Only an absent history service confirms Unsupported. Transport, permission and malformed-protocol failures remain recoverable without changing capability. The overview includes unknown and supported registrations, plus unsupported sensors with a retained archive.

One `HistorySessionCoordinator`, confined to the main dispatcher, owns the foreground history route family. Overview entry queues eligible registrations; direct detail entry requests one sensor. Requests deduplicate by registration identity within a session, survive overview/detail transitions and configuration changes, and cancel on leaving history or genuinely backgrounding the app. Explicit retry starts a fresh request. Scanning pauses while history is open.

`HistoryRepository` serializes transfers before reading the saved cursor. GATT, decoding and import run on IO. The transaction checks the registration identity again before updating capability, samples or cursor, so a late result cannot repopulate a deleted/re-registered sensor. Cursor distance is validated against received positions, including normalized sector-boundary cursors and generation wrap. Retention also applies during import; undated samples remain stored.

`HistoryViewModel` observes archives independently of transfer progress. Cached data remains readable during updates and failures; an EMPTY incremental response does not erase it. Chart mapping runs off the main thread. Exploration state is retained across configuration changes and overview Back navigation; the initial window covers the latest week ending at the newest available sample (or the available coverage of a shorter archive). It follows incoming downloads until the user changes the window or requests All history; subsequent imports preserve that choice. Detail inherits an existing overview window.

The overview shares one measurement and time window across sensors. Detail uses a landscape chart with a fixed central cursor, centered pinch zoom, pan, exact sample readouts and optional dashed comparison. Each comparison readout has its own recorded timestamp; values are not interpolated. Charts preserve acquisition discontinuities and UTC regressions, retain extrema when reducing data, and expose local timezone, missing-time and coverage caveats. Accessibility actions and the timeline controls provide alternatives to chart gestures.
