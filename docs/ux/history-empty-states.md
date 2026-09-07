# First history access: no downloaded data

Status: the grouped state presentation and behaviors were approved on 2026-09-07 (D32). This covers a registered sensor that supports internal history but has no local history yet. It is distinct from a sensor without history support.

## Grouped state review

At the owner's request, related small states are now presented together in `wireframes/history-state-board.html`, rather than requesting approval for each separately. The board contains UI excerpts, not complete screens or wired actions. Cases 5–8 include context labels explaining which saved content remains available; those labels are review annotations, not proposed replacement screens.

| Case | Behavior being proposed |
| --- | --- |
| First access | Indeterminate connection feedback while no local history exists. |
| Transfer | Determinate progress only when a reliable total is available; otherwise retain indeterminate progress. The example's 40% is illustrative. |
| Unreachable, no local history | Explicit absence of downloaded data, connection explanation, and retry. |
| Successful but empty | Explain that the sensor has no recorded history, without reporting a connection error. |
| Updating with saved history | Keep the existing chart usable; use compact progress feedback. |
| Update failed with saved history | Keep the existing chart and exploration state; show brief feedback and retry. |
| Bluetooth disabled | Offer a user-initiated Bluetooth enable action. Do not block saved readings or history behind it. |
| Permission unavailable | Explain the need for nearby-device access, then offer a user-initiated permission request or app-settings action as appropriate. Keep saved data viewable. |

Group review is complete (D32). Platform-specific permission wording, cancellation, and partial-download recovery remain open; the board does not establish a retry schedule, timeout, or automatic permission prompts.

## Proposed behavior

- Opening history automatically attempts the download. Until a result is known, show a concise connection/progress state rather than an empty chart.
- If the sensor cannot be reached and there is no saved history, state both facts clearly and offer Try again. A retry repeats the attempt without requiring the user to leave and reopen the page.
- If the sensor is reached and reports empty history, explain that no measurements were recorded. Do not present this as a connection failure.
- A successful non-empty download leads to the existing history visualization; this transition is not wired in this screen-only example.

The history surface remains landscape, as in the detailed chart, to avoid an extra orientation change when data becomes available. The grouped state layout and contextual retry presentation are approved in D32. The initial attempt continues to require no explicit Download command.

## Wireframe

`wireframes/history-first-download.html` automatically shows a short simulated connection attempt and then an unreachable-sensor result. Try again repeats the simulation. Optional preview controls can instead select a successful connection with empty history.

The demonstration delay is not a proposed timeout. It does not contact a sensor or determine actual reachability from the age of an advertisement. The back arrow is a visual destination marker; this screen is not linked to the sensor page yet.

The approved saved-history update states keep the chart usable when an update fails. The grouped board also covers missing Bluetooth permissions and disabled Bluetooth; partial transfers, unknown capabilities, and exact platform interactions still require further design.
