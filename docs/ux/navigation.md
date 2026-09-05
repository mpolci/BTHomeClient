# Navigation and task flows

Status: connecting the agreed screen designs. Confirmed destinations are distinguished from proposals below.

## Entry points

| Trigger | Destination / behavior | Status |
| --- | --- | --- |
| Tap a registered sensor with history support | Open that sensor's history, without first visiting sensor information. | Confirmed in D20. |
| Access sensor information | A secondary entry point for infrequent use. | Priority confirmed; the proposed card overflow menu is not yet approved. |
| Tap an unregistered sensor | Open registration with the broadcast name prefilled when available. | Direct tap entry is proposed; name prefill is confirmed. |
| Open history from general navigation | Show the multi-sensor overview and automatically update all compatible saved sensors. | Global navigation placement is proposed; overview and download behavior are confirmed. |

## Registered sensor to history

1. The owner taps a registered sensor in the live overview.
2. If it supports internal history, open its individual history view. Start updating automatically on entry without a separate download action, retaining saved history when the sensor is unreachable. See the update flow below.
3. The owner pans beneath the fixed cursor, zooms around it, or compares with another sensor.
4. Back returns to the originating live view. Entry from the history overview must instead return there.

The direct history destination is confirmed. The [connected prototype](prototype.md) now implements both return paths using the standalone history wireframe's navigation interface. Final back-state restoration and the initial measurement/time range remain to be designed.

## Automatic update on entry

Confirmed in D21: opening a sensor's history initiates its download immediately. If the sensor is unreachable, proceed to viewing already downloaded data. A separate per-sensor download action should not be needed.

Agreed interaction direction: display existing history immediately while the update runs, with compact progress/status feedback. On success, refresh available data without resetting the user's current time window or cursor. This avoids making existing history inaccessible during a connection attempt.

| Available local history | Update result | Proposed presentation |
| --- | --- | --- |
| Present | In progress | Existing chart remains usable with an updating indication. |
| Present | Successful | Updated history, with the user's exploration state preserved. |
| Present | Sensor unreachable or transfer failed | Keep saved history visible and briefly report that it could not be updated. |
| Absent | In progress | Initial download progress instead of an empty chart. |
| Absent | Sensor unreachable or transfer failed | Explicit no-downloaded-history state, with the reason the update could not finish. |
| Absent | Successful, but sensor history empty | Explicit no-recorded-history state, distinct from an unsuccessful download. |

Live-advertisement age alone must not be treated as proof that a history connection will succeed or fail. The proposed fallback depends on actual download availability/result.

The owner confirmed that opening the general history overview automatically updates all compatible saved sensors. This supersedes the explicit bulk command in D6; R12 now reflects D21. Download-on-entry retry/cancellation rules and timeout behavior remain to be designed; do not infer continuous background synchronization.

## Cases to resolve before linking the prototype

- **No internal history support:** registered cards display `Live only`, while compatible cards display `History →` beside the name (D22). Live-only cards have no history-opening action; secondary management access remains to be finalized. Do not imply history can be built from live readings.
- **History supported but not downloaded:** opening history should initiate the first download; its empty/progress states remain to be designed and must be distinct from unsupported history.
- **Sensor not currently received:** downloaded history remains useful independently of live reception; design any download-unavailable feedback separately.
- **Capabilities unknown:** decide how to communicate uncertainty without falsely claiming history support.
- **Secondary management:** choose the entry point for information, local metadata editing, and registration removal.

The owner authorized connecting the wireframes and adding secondary menus. The first implementation for review is described in [connected prototype](prototype.md). Work remains within UX documentation and wireframes; production app changes are not part of this step.
