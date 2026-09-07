# Current state

Initial source review: 2026-09-05. These observations describe the implementation and are not redesign decisions. No visual or on-device review has been performed during this discovery step.

This is the pre-redesign baseline. For the implemented native flows and device validation, see [implementation-plan.md](implementation-plan.md) and [the current application architecture](../architecture.md).

## Navigation and behavior

- The Android app uses Jetpack Compose. Routes cover the main dashboard, registration, sensor detail, history, and global settings.
- The main route owns live scanning; leaving that route stops scanning.
- Registration and detail editing support a local name, room, and optional image.
- The detail route provides entry to sensor history.
- The active history screen offers a download action, progress, a completion summary, and error text. It does not provide historical data exploration.
- The history repository supports full and incremental downloads and persists samples and synchronization state.

Sources: [app navigation](../../app/src/main/java/io/github/mpolci/bthomeclient/BTHomeClientApp.kt), [history screen](../../app/src/main/java/io/github/mpolci/bthomeclient/ui/history/HistoryScreen.kt), [history view model](../../app/src/main/java/io/github/mpolci/bthomeclient/ui/history/HistoryViewModel.kt), [history repository](../../app/src/main/java/io/github/mpolci/bthomeclient/data/repository/HistoryRepository.kt).

## Existing documented behavior to consider in design

- Registered sensors appear before nearby unregistered sensors.
- Registered sensors remain listed when live observations become stale.
- Downloaded-history retention is configurable and defaults to unlimited.
- Removing a registration also deletes its downloaded history and synchronization state.

Source: [existing architecture documentation](../architecture.md). These behaviors need to be evaluated against the intended user scenarios before being carried into the target design.

## Documentation discrepancy

The existing README describes a history placeholder and lists GATT as out of scope, while the active code includes a history download flow. For history UX discovery, use the active code as the current baseline. Updating general project documentation is outside this documentation step.
