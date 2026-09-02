# SENE-A History Download Plan

## 1. Goal

Replace the current history placeholder with a production client for the
SENE-A History Download GATT protocol. The app will connect to a registered
sensor, download either its full history or the records after the last saved
cursor, validate and decode the transfer, and persist the imported samples
locally.

The app history is cumulative rather than a mirror of the sensor ring. The
first successful synchronization downloads the full available log. Every later
synchronization starts from the last cursor committed by the app and requests
only subsequent positions. If the sensor invalidates that cursor and requires a
new full download, the returned samples are merged into the existing archive;
older app data is never deleted merely because it has rolled out of the sensor.
Only the app's configured retention policy, explicit sensor-registration
deletion, or the user-confirmed clock-conflict replacement described below may
remove already imported history.

The implementation must preserve passive BTHome scanning for live display, but
must stop persisting advertisement measurements. Charts, CSV export, background
synchronization, pairing, deletion of sensor-side history, and support for
unrelated proprietary history protocols remain out of scope.

## 2. Sources inspected

This plan is based on the implemented firmware contract, not only on the
earlier protocol proposal:

- `../SENE-A/firmware/SENE-A-proto/src/history_download.h`
- `../SENE-A/firmware/SENE-A-proto/src/history_download.cpp`
- `../SENE-A/firmware/SENE-A-proto/src/data_logger.h`
- `../SENE-A/firmware/SENE-A-proto/src/data_logger.cpp`
- `../SENE-A/firmware/SENE-A-proto/src/main.cpp`
- `../SENE-A/tools/android-ble-throughput/app/src/main/java/io/github/mpolci/seneblethroughput/MainActivity.java`
- `../SENE-A/tools/android-ble-throughput/README.md`

The app already has the required `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`
permissions, a registered sensor identity based on its BLE address, Room-backed
advertisement readings, and a history route. The current advertisement-reading
persistence is explicitly replaced by direct-download-only history in this
plan. The history route currently renders only `HistoryPlaceholderScreen`.

## 3. Implemented SENE-A protocol contract

### GATT service

| Element | UUID | Properties |
| --- | --- | --- |
| History service | `7f510001-1b15-4f0d-8f6c-7e9c5a1e0000` | Not advertised |
| Control point | `7f510002-1b15-4f0d-8f6c-7e9c5a1e0000` | Write, indicate |
| Data stream | `7f510003-1b15-4f0d-8f6c-7e9c5a1e0000` | Read, notify |

The app already knows the registered BLE address, so it should connect directly
with `BluetoothAdapter.getRemoteDevice(address)` and discover the service after
connection. A second scan by device name is unnecessary and would make the
client depend on a SENE-A presentation detail rather than the registration.

The service is open: it does not use pairing, bonding, or application
authentication. Control indications and data notifications must both be enabled
before an open command is written.

### Commands and responses

Protocol version is `0x01`, all multibyte fields are little-endian, and exact
message lengths are mandatory.

| Message | Opcode | Layout |
| --- | ---: | --- |
| `OPEN_FULL` | `0x01` | opcode, version, Unix UTC `u32` (6 bytes) |
| `OPEN_AFTER` | `0x02` | opcode, version, Unix UTC `u32`, opaque cursor (16 bytes) |
| `CANCEL` | `0x03` | opcode, version, transfer ID (3 bytes) |
| `READY` | `0x81` | version, transfer ID, flags, format `u16`, position count `u32`, cursor (20 bytes total) |
| `EMPTY` | `0x82` | version, flags, reserved zero, cursor (14 bytes total) |
| `STALE_CURSOR` | `0x83` | opcode, version |
| `BUSY` | `0x84` | opcode, version, active transfer ID |
| `NOT_READY` | `0x85` | opcode, version |
| `INVALID_REQUEST` | `0x86` | opcode, version |
| `STORAGE_ERROR` | `0x87` | opcode, version, transfer ID |
| `CANCELED` | `0x88` | opcode, version, transfer ID |
| `DONE` | `0x89` | opcode, version, transfer ID |

`READY` flag bit 0 is `SYNC_PENDING` and bit 1 is `HISTORY_TRUNCATED`; all
other bits are reserved. A valid `READY` has exactly 20 bytes, a transfer ID in
`1..255`, format version 1, a position count in `1..130560`, and a valid final
cursor. A valid `EMPTY` has exactly 14 bytes, only the two known flags, a zero
reserved byte, and a valid cursor. The 10-byte cursor contains format version,
generation, next slot, and CRC-16/CCITT-FALSE; format must be 1, next slot must
be in `0..255`, and the CRC must match. These checks are mandatory before any
allocation or persistence. The client must never synthesize or advance a wire
cursor itself.

After `READY` is confirmed at the ATT layer, the sensor sends data notifications.
Each notification contains a 4-byte header (`transfer_id`, then a 24-bit first
position index) followed by one or more complete 16-byte raw positions. The
packet length must therefore be at least 20 bytes and `(length - 4)` must be a
multiple of 16. The next packet index must equal the number of positions already
received.

The maximum snapshot is 130,560 positions, or 2,088,960 raw bytes. Erased and
CRC-invalid positions are deliberately included. A successful transfer requires
the expected transfer ID, gap-free packet indices, exactly the `READY` position
count, and a final `DONE`. Only then may the app save the final cursor. `EMPTY`
is also successful and may update the cursor without a transfer ID.

### Persistent record format

Every physical position is 16 bytes. Bytes 11-12 contain CRC-16/CCITT-FALSE over
bytes 0-10 and bytes 13-15 must be `0xFF`. Sample fields have the following
exact layout:

| Offset | Width | Field |
| ---: | ---: | --- |
| 0 | 1 | Record type |
| 1 | 4 | Unix or monotonic seconds, unsigned little-endian |
| 5 | 2 | Raw absolute pressure before configurable offset, unsigned little-endian in 10 Pa units |
| 7 | 2 | Temperature, signed little-endian in 0.1 C units |
| 9 | 1 | Humidity percent |
| 10 | 1 | Battery percent |
| 11 | 2 | Record CRC |
| 13 | 3 | `0xFF` padding |

| Type | Value | Payload |
| --- | ---: | --- |
| `SAMPLE_UNSYNCED` | `0x01` | monotonic seconds, pressure in 10 Pa, temperature in 0.1 C, humidity %, battery % |
| `SAMPLE_UTC` | `0x02` | Unix UTC seconds followed by the same measurements |
| `BOOT` | `0x10` | reset reason in bytes 1-4 |
| `TIME_SYNC` | `0x11` | Unix UTC seconds in bytes 1-4 and monotonic seconds in bytes 5-8 |

Acquisition order is authoritative even when UTC is corrected backwards.
`BOOT` starts a new time-mapping session. A `SAMPLE_UNSYNCED` can be dated only
from a valid `TIME_SYNC` in the same session; otherwise its UTC remains unknown.
Pressure converts from 10 Pa units to `0.1 hPa`, and signed temperature converts
from tenths of a degree Celsius.

## 4. Minimal production design

### 4.1 Pure protocol and record codecs

Add a small Android-independent package, for example
`history/sene/SeneHistoryProtocol.kt`, containing:

- UUIDs, opcodes, sizes, flags, geometry limits, and record types;
- exact `OPEN_FULL`, `OPEN_AFTER`, and `CANCEL` encoders;
- strict control response and data-header decoders, including all mandatory
  length, version, ID, flag, reserved-byte, format, and count checks above;
- cursor validation and CRC-16/CCITT-FALSE;
- raw-position validation and decoding;
- session-aware timestamp reconstruction for unsynchronized samples.

Keep this code free of Android and Room dependencies so the binary contract and
time reconstruction can be exhaustively covered by JVM tests. Decode the
completed raw buffer in acquisition order. Use `BOOT` boundaries and the
closest valid monotonic anchor in the same session; retain a sample with a null
timestamp when no anchor exists rather than inventing UTC. Count erased,
CRC-invalid, unsupported, and undated positions for the completion summary.

### 4.2 GATT transport

Add one `HistoryDownloader` interface and one `AndroidHistoryDownloader`
implementation under `ble/`. The interface should expose a single cancellable
suspending transfer with progress callbacks or a small `Flow`; it should not
leak `BluetoothGatt` objects outside the transport.

The Android implementation will:

1. Check `BLUETOOTH_CONNECT`, adapter state, and address validity.
2. Connect directly with `autoConnect = false` and `TRANSPORT_LE`.
3. Request high connection priority, discover services, and verify the service
   and both characteristics.
4. Request MTU 247 and preferred PHY 2M as optional optimizations. An immediate
   rejection or an explicit unsuccessful MTU callback may fall back to MTU 23.
   If an accepted MTU request never receives its callback before the operation
   deadline, close that GATT and retry once without requesting MTU; do not queue
   a CCCD write behind an operation whose completion is unknown. PHY failure is
   non-fatal because it does not share the serialized attribute-operation queue.
5. Serialize GATT operations by confirmed completion, not just successful
   enqueue: enable the control indication CCCD and validate its
   `onDescriptorWrite`, then do the same for the data notification CCCD, then
   write exactly one open command with `WRITE_TYPE_DEFAULT` and validate its
   `onCharacteristicWrite`. Check the immediate return code and the callback
   status, UUID, characteristic/descriptor instance, and active session for
   every operation before advancing.
6. Copy callback byte arrays immediately and feed a single transfer state
   machine. Support both characteristic-change callback forms needed by API
   31-32 and newer Android releases, and use the compatible write APIs for the
   same range.
7. Preallocate the raw buffer only after validating `positionCount <= 130560`
   and the multiplication by 16. Copy valid packets directly into that buffer.
8. On lifecycle cancellation, run an idempotent, strictly bounded cleanup in a
   `NonCancellable` context. If no GATT operation is pending and a transfer ID is
   active, enqueue `CANCEL` with `WRITE_TYPE_DEFAULT`, wait at least for the
   matching write callback and briefly for `CANCELED`, then disconnect and
   close. If the queue is not safe or any step fails, disconnect and close
   immediately; firmware disconnect handling also returns the sensor to idle.
   Never change the saved cursor on cancel, disconnect, timeout, malformed
   input, or any error response.
9. Always close `BluetoothGatt` on every terminal path.

Use bounded connection and individual GATT-operation timeouts, a response
timeout from completed open write to `READY`/`EMPTY`/error, a sliding data
inactivity timeout, and a terminal timeout from the last expected position to
`DONE`. Do not impose a short fixed whole-transfer timeout:
the integrated sensor completes a full ring in about 18 seconds at MTU 247/PHY
2M, but a valid MTU 23 transfer can take materially longer. The connection
timeout must also allow for SENE-A's 20-second advertising duty cycle.

The current dashboard scan stops when its route leaves the started lifecycle,
so the history route should use the same `LifecycleStartEffect` pattern. Its
`onStopOrDispose` explicitly cancels any transfer when navigating away or when
the app genuinely enters the background, but not while the host Activity has
`isChangingConfigurations == true`; the retained ViewModel and application-
context GATT session can continue across that recreation. `ViewModel.onCleared`
remains a fallback because a destination ViewModel can stay alive on the
navigation back stack after the UI is stopped. Continuing a download in the
background would require a foreground service and remains out of scope.

Enforce one foreground transfer with a mutex or equivalent guard inside the
single downloader instance in `AppContainer`; UI button disabling alone is not
sufficient. Deliver callbacks and cancellation to one serialized event loop,
tag every connection with a session token, and ignore callbacks from an older
or already closed GATT instance.

Only `BLUETOOTH_CONNECT` is required by this direct-connect path. Map permission
loss throughout the session, not only at entry: catch `SecurityException` around
every protected call and handle the API 33+ missing-permission result codes.
Terminate once, close the active GATT, and expose a typed permission-lost result.

### 4.3 Atomic local import and cursor state

Do not insert records while notifications are still arriving. A full raw ring
is only about 2 MiB, so holding it in memory until validated `DONE` is simpler
and safer than a temporary file or a partially committed staging schema.

Increment Room from version 1 to version 2 and add two focused entities in the
existing local-data layer:

- `DownloadedHistorySampleEntity`, with an autogenerated row ID, sensor address,
  local physical acquisition sequence, source format/generation/slot, the 16-byte
  raw source position, nullable `recordedEpochMillis: Long`, a time-quality value
  (`DIRECT_UTC`, `RECONSTRUCTED`, or `UNAVAILABLE`), temperature, humidity,
  pressure, and battery;
- `HistorySyncStateEntity`, keyed by sensor address, with the 10-byte cursor,
  the next local physical sequence, last successful download time, and the last
  `SYNC_PENDING`/`HISTORY_TRUNCATED` flags.

Both entities should have a foreign key to `registered_sensors` with cascade
deletion. Remove `SensorReadingEntity` and its `sensor_readings` table in the v2
migration, including all advertisement samples previously stored there. Room
history must contain only records obtained from the direct GATT history service.
Add a non-unique index on `(sensorAddress, recordedEpochMillis)` for time queries
and an index on the source-position fields for merge lookup. Do not make UTC
unique: the sensor allows backward clock corrections and distinct acquisitions
can legitimately share the same UTC second.

Remove `SensorReadingDao`, its mappers, and the advertisement-backed
`SensorManager.observeHistory*` implementation. Replace history access with the
downloaded-history DAO/repository. In `SensorCoordinator.observe` and
`register`, keep live observations and registered technical metadata but perform
no measurement insert. Remove the latest-reading Room combine and any UI
fallback from a live observation to a persisted advertisement measurement. A
registered offline sensor therefore has no stored live value unless a future
history presentation explicitly reads its downloaded logger archive.

Remove `MergedSensor.latestReading`, the stored-reading half of
`mergedMeasurements`, and the old `SensorReading` domain/history API if no
downloaded-history code reuses that name. Dashboard cards and details use only
the current in-memory observation for live measurements. When a registered
sensor has no current observation, render an explicit `No live reading
available` state rather than an empty measurement block; editing, diagnostics,
and history navigation remain available.

The existing minimum advertisement-persistence interval setting becomes
meaningless and should be removed from `AppSettings`, DataStore, settings UI,
tests, and documentation. Retention remains and applies only to downloaded
logger history. Sensors that do not expose the SENE-A history service have live
advertising values only and no locally persisted history.

History capability is independent of the existing `SensorSupportStatus`, which
describes only BTHome advertisement parsing. Do not hide or disable the History
action based on that status. For a registered sensor the history capability is
`Unknown` until direct connection and service discovery prove it supported or
return a missing-service/unsupported result. No persistent capability cache is
required for the first implementation.

Convert both direct and reconstructed unsigned seconds to
`recordedEpochMillis` with checked `seconds * 1000L` arithmetic before applying
retention or exposing data to the app.

For format 1 `OPEN_AFTER`, validate continuity from the exact saved request
cursor to the validated final cursor. Compute the unsigned wrapped generation
distance, require it to fit the retained chain, then use a checked wide
intermediate to require
`generationDistance * 255 + finalSlot - savedSlot == positionCount`. For a zero
generation distance, a negative slot difference is invalid. Do not compare a
single backward-derived start tuple: `(generation, 255)` and `(generation + 1,
0)` are equivalent sector-boundary limits. This check is confined to the
format-1 persistence codec; the transport still stores and returns the wire
cursor unchanged. A mismatch is a protocol/data-integrity failure and must not
commit. Apply the same rule to incremental `EMPTY` with a logical position count
of zero even though `EMPTY` has no count field; its returned cursor must describe
the same boundary as the requested cursor.

Use the same checked format-1 cursor arithmetic to map every received relative
position to its canonical source generation and slot. Canonicalize a limit at
slot 255 as the next generation at slot 0 before walking backward. This identity
is for local merge verification only; the cursor sent back to the sensor remains
the exact opaque 10-byte value it supplied.

Add one transactional DAO operation for each successful outcome:

- Initial full download: insert the decoded samples in acquisition order and
  create cursor/state in one transaction.
- Full download after `STALE_CURSOR`: merge the currently retained sensor
  snapshot into the archive and replace cursor/state in one transaction. Update
  a row only when sensor address, source format/generation/slot, and all 16 raw
  bytes prove it is the same persistent sensor record; otherwise insert a new
  row, even if its UTC equals another row. Never delete archive rows that are
  absent from the new sensor snapshot.
- Incremental download: append every decoded sample using `previous next
  sequence + raw position index`, then advance cursor/state in the same
  transaction. Exact cursor continuity already proves these are later physical
  positions, so timestamp-based deduplication would be both unnecessary and
  unsafe.
- Full `EMPTY`: preserve archived samples and replace cursor/state.
- Incremental `EMPTY`: keep samples and update cursor/state.

#### Conflict detection and confirmed suffix replacement

Do not use a time tolerance or environmental sampling window for persistence
conflicts. Before changing Room, remove positions already proven to be the same
source/raw record from the comparison, then scan all genuinely new dated samples
in acquisition order. Select the first one whose `recordedEpochMillis` is
strictly lower than the latest dated timestamp already in the downloaded archive
at the start of the operation. A conflict exists if such a sample is found,
even when earlier new samples in the same transfer passed the check. Equality is
not a clock regression and does not trigger this workflow. Undated samples
cannot take part in this comparison.

The BLE transfer remains read-only and completes into the validated in-memory
buffer first. If there is no regression, commit normally. If a regression is
found, close the GATT connection without saving its cursor and present a
blocking confirmation before any database mutation. The dialog must show:

- the latest archived timestamp;
- the first conflicting new timestamp;
- the number and time range of archived rows that would be removed;
- that the sensor clock may have been set incorrectly;
- `Cancel` as the safe default and an explicit `Replace history from here`
  destructive action.

If the user cancels or the process dies while awaiting the decision, discard the
downloaded buffer and retain both archive and cursor unchanged. The next update
will download the same range again.

If the user confirms, find the earliest downloaded-history row in acquisition
order whose known timestamp is greater than or equal to the conflicting new
timestamp. In one Room transaction:

1. delete that row and every later downloaded-history row by local acquisition
   sequence;
2. replay the newly downloaded positions in physical acquisition order,
   using the deleted suffix's first sequence as the new base and retaining any
   exact source/raw records that remain in the preserved prefix;
3. replace the cursor and next-sequence state only after all inserts succeed.

Never select the suffix with a bulk `timestamp >= cutoff` deletion: timestamps
are precisely the suspect data and an existing archive can contain a previous
clock discontinuity. The timestamp locates the first acquisition-order row, and
the local acquisition sequence defines the suffix. No advertisement-history
rows exist in the target schema.

Advance the local physical sequence by the declared position count, including
erased or invalid positions, so acquisition order and holes remain stable. For
an incremental transfer, its first position uses the saved next sequence. A
full fallback uses the old exclusive sequence as the base for positions not
already identified as the same raw source record. An exact raw/source match
retains the existing row ID and acquisition sequence and may improve its derived
timestamp/time quality; a non-match is appended. This implements the rule that
a full fallback adds to the app archive and overwrites only a proven copy of the
same sensor sample. Timestamp equality alone is not proof because the clock can
move backwards. An undated record can still be matched safely by its physical
source identity and identical raw bytes. In normal stale-cursor behavior the new
ring no longer overlaps the archive physically, so almost all fallback records
simply append.

If decoding or the Room transaction fails, retain both the old samples and old
cursor. Commit the fallback only after its successful terminal response. Use an
indexed source-position lookup plus exact raw comparison and explicit
update-or-insert inside the transaction rather than a timestamp-based Room
upsert.

Decode/import the validated raw buffer in bounded batches inside one Room
transaction. A first pass may collect per-session time anchors; a second pass
decodes and inserts small batches. This avoids a second 130,000-object in-memory
list while preserving atomicity.

Apply the retained history-retention preference to downloaded samples with known
UTC, both when importing and when the user shortens retention. Keep undated
samples because their age cannot be evaluated. Update the existing
`SensorManager.saveSettings`/`SensorCoordinator.saveSettings` path to prune only
the downloaded-history DAO; do not rely on a repository method that the current
settings UI never calls. Deleting a registration must remove downloaded samples
and cursor state through foreign key cascades.

### 4.4 Repository, ViewModel, and UI

Add a narrow `HistoryRepository` that owns transfer orchestration, decoding,
the stale-cursor full fallback, transactional import, retention filtering, and
progress reporting. It should choose:

- `OPEN_FULL` only when no saved cursor exists;
- `OPEN_AFTER` from the exact last committed cursor on every normal subsequent
  synchronization, regardless of how much history the app already holds;
- one automatic `OPEN_FULL` after `STALE_CURSOR`.

Each open command supplies current phone Unix UTC as an unsigned 32-bit value.
Reject a host time outside the protocol range instead of truncating it.

Replace `HistoryPlaceholderScreen` with `HistoryScreen` and add
`HistoryViewModel`. Keep the first production UI intentionally small:

- registered sensor name/address and count of downloaded samples;
- `Download history` for the first sync and `Update history` afterwards;
- connection and transfer state with progress based on physical positions;
- cancel/back behavior;
- a completion summary distinguishing newly added and proven-identical updated
  samples, invalid/erased positions, undated samples, whether the source history
  was truncated, and whether time synchronization remains pending;
- a clock-regression confirmation state that keeps data and cursor unmodified
  until the user cancels or explicitly replaces the affected archive suffix;
- actionable errors for permission loss, Bluetooth disabled, connection or
  service discovery failure, unsupported service, protocol error, storage
  error, and disconnection.

The existing details History action remains available for every registered
sensor because advertisement support does not imply history support. A missing
GATT history service is discovered only after connection and becomes a clear
unsupported result on the history screen.

Charts and a detailed sample browser remain deferred. The screen confirms that
data was imported without expanding this task into visualization work.

Wire the ViewModel through `AppContainer` and the existing history route. The
route's started-lifecycle cleanup and explicit user cancel both stop the active
transfer; `onCleared` is the final fallback. Every path must close the connection
and leave persisted state unchanged. Process death has the same safe outcome:
the previous cursor remains valid and the next attempt repeats the uncommitted
range.

## 5. Implementation sequence

1. Add the pure protocol/control/data/record codecs and their JVM fixtures.
2. Add Room v2 downloaded-history entities and DAOs, drop the old advertisement
   readings table/API, add the migration and schema export, and cover the
   transactional import.
3. Add the GATT transport and a fake callback/event source for deterministic JVM
   state-machine tests.
4. Add `HistoryRepository`, including cursor selection, stale fallback,
   decoding, atomic commit, and retention integration.
5. Replace the placeholder with the ViewModel-driven download screen and update
   route/container wiring.
6. Run unit, lint, build, and instrumentation verification, then validate against
   the SENE-A history-test firmware and a production sensor.

## 6. Verification

### JVM tests

- Golden command/response/cursor vectors for every opcode and exact length,
  including mandatory READY/EMPTY field validation. Allow transfer ID zero only
  where firmware legitimately emits it, such as a pre-transfer `STORAGE_ERROR`
  or idle mailbox-overflow `BUSY`; active `DONE`, `CANCELED`, and stream errors
  must match the nonzero active ID.
- Little-endian `u16`/`u24`/`u32`, transfer-ID wrap acceptance, reserved flags,
  CRC known vectors, cursor corruption, and the 130,560-position limit.
- Data packets at MTU 23 and 247; wrong transfer ID, gaps, duplicates, malformed
  lengths, overflow, data before `READY`, premature `DONE`, and extra data.
- Erased, invalid-CRC, bad-padding, unknown-type, signed-temperature, and range
  validation cases.
- Multiple `BOOT` sessions; direct UTC samples; unsynchronized samples before a
  later anchor; truncated first session; clock correction; and a session with no
  anchor.
- Two distinct physical samples with the same sensor and UTC after a backward
  correction both survive in acquisition order; UTC is never a uniqueness key.
- Exact timestamp equality does not trigger a conflict. A genuinely new sample
  strictly before the operation's latest archived timestamp does, with the
  earliest such sample selected deterministically after exact raw deduplication.
- Cursor unchanged after cancel, timeout, disconnect, storage/protocol error, or
  failed Room commit; cursor updated only after validated `DONE` or `EMPTY`.
- Initial full, repeated incremental growth, incremental empty, stale-to-full
  source-identity merge,
  incremental forward-distance validation across sector and `u32` generation
  wrap, the valid `(G, 255) -> (G + 1, 1)` with count 1 boundary case, a
  mismatched distance/count, valid incremental `EMPTY` equivalence at `(G, 255)
  -> (G + 1, 0)`, a discontinuous incremental `EMPTY` cursor, and failure during
  the fallback full transfer.
- GATT progress only after matching descriptor/characteristic write callbacks;
  immediate and callback failures; missing MTU callback retry; permission loss;
  response, stream, and terminal timeouts; duplicate starts; lifecycle stop;
  and late callbacks from a closed session.

### Room and UI instrumentation tests

- Migration from the checked-in v1 schema without losing registrations or local
  metadata, while deleting all legacy advertisement-reading rows and leaving the
  new downloaded-history tables empty.
- Full source-identity merge and incremental import are atomic; a truncated stale
  fallback preserves older local rows absent from the sensor, inserts new
  records, updates only exact source-position/raw matches, and does not overwrite
  equal-UTC distinct samples. Re-downloaded undated records deduplicate only on
  the same exact identity. Cascade deletion covers both new tables.
- Canceling a clock-regression prompt changes neither archive nor cursor.
  Confirming it deletes by acquisition-sequence suffix from the first archived
  row at or after the conflicting time, inserts the validated new positions, and
  advances the cursor atomically. Cover a process death while the prompt is open.
- Saving a shorter retention through `SensorManager.saveSettings` removes only
  dated expired downloads at the exact millisecond boundary; cover direct and
  reconstructed timestamps and preserve undated rows.
- Advertisements update live state and registered metadata but never insert a
  history row; registration also does not persist the currently observed
  measurement. Removing the persistence-interval setting leaves retention
  migration and settings behavior valid.
- After restart or migration an offline sensor shows `No live reading
  available`; tests must not expect the former persisted temperature/battery
  fallback. History navigation remains enabled independently of BTHome parsing
  support, and missing-service discovery produces the unsupported history state.
- Loading, unsupported, connecting, downloading, cancel, success, empty,
  truncated/sync warnings, and error UI states.
- Activity recreation during a transfer keeps the same operation alive, while
  navigation away and genuine backgrounding cancel it.

### Build gates

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew compileDebugAndroidTestKotlin
./gradlew packageDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

### Real sensor gates

Use `SENE-A-proto-history-test` and the existing deterministic fixtures:

- partial ring at MTU 247/PHY 2M and degraded MTU 23/PHY 1M;
- full ring with exact position count and no sequence error;
- `OPEN_AFTER` followed by `EMPTY`;
- CRC-invalid position with successful completion and skip accounting;
- cancel, mid-stream disconnect, reconnect, and retry from the old cursor;
- stale cursor followed by an automatic full merge that retains older app data;
- normal incremental and stale-full clock regressions, exercising both cancel
  and confirmed suffix replacement;
- logger append during a fixed snapshot;
- sensor-side `STORAGE_ERROR`;
- production firmware service discovery and full/incremental download.

Confirm that returning to the dashboard restarts passive BTHome scanning and
that repeated advertisements never create Room history. A sensor without the
history service must remain usable live while clearly reporting that history
download is unsupported.

## 7. Acceptance criteria

- A registered SENE-A can complete full and incremental foreground downloads on
  Android API 31 and newer without relying on the history service being
  advertised.
- Progress is sequence-checked against `READY`, and malformed or incomplete
  transfers never change local samples or cursor state.
- The cursor is committed atomically with imported data only after `DONE`, or
  updated according to the `EMPTY` rules.
- Erased or corrupt slots are reported and skipped without failing an otherwise
  complete transfer.
- UTC, reconstructed, and undated samples remain distinguishable, while physical
  acquisition order is preserved.
- The first synchronization is full; every normal later synchronization starts
  from the last committed cursor and grows the archive only with later data.
- A stale cursor causes one safe full merge: rolled-off local history remains,
  new samples are added, and only a source-position/raw match proving the same
  sensor record is updated. Equal timestamps from distinct acquisitions remain
  separate. A failed fallback leaves all committed history intact.
- A genuinely new timestamp earlier than the latest archived timestamp never
  mutates history automatically: the user either cancels or explicitly confirms
  replacement of the affected downloaded-history suffix.
- Registration deletion and retention settings cover the newly downloaded data.
- Advertising measurements remain live-only and never enter Room; all persistent
  history comes from a successful direct download on a supported sensor.
- The existing BTHome scanner, registration, metadata editing, diagnostics,
  navigation, and remaining retention setting continue to work. Offline
  measurement fallback and the advertisement-persistence interval are
  intentionally removed and their tests are replaced with the new live-only
  expectations.
