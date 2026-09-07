# Navigation and task flows

Status: current direction reflected in the connected prototype as of 2026-09-07. See [prototype](prototype.md) for implemented paths and limitations.

## Entry points

| Trigger | Destination / behavior | Basis |
| --- | --- | --- |
| Tap any live sensor card | Sensor page with compact readings and immediately visible identification/status. | D23, D24, D30, D31. Replaces direct live-card-to-history navigation. |
| Select History on the sensor page | Individual landscape history and automatic update. | D21, D24. Offered for compatible registered sensors. |
| Select general History from live | Multi-sensor overview and automatic updates for compatible registered sensors. | D21. Exact global button placement remains a prototype choice. |
| Tap a pencil | Single-field local-name or placement dialog. | D25, D26. |
| Tap a photo or Add photo | Local image selection. | D25. |
| Select Register sensor | Register using the broadcast name, stay on the sensor page, and reveal editing. | D25, D30. |
| Select Remove sensor | Confirmation explaining loss of local metadata and downloaded history. | D29 and reviewed removal wireframe. |

## Return paths

History entered from a sensor returns there; a chart entered from the overview returns to the overview. Leaving the overview or sensor page returns to live. Field-dialog Cancel or Escape leaves the field unchanged; Save updates only that field.

No overflow menu or separate metadata editor is needed. History capability labels on live cards were superseded by the uniform sensor-page destination; history support is visible on that page.

## Automatic history update

Opening the sensor page does not initiate a download or display a chart. Opening history does. Existing downloaded data remains immediately consultable during attempts and when updates fail.

| Local data | Update outcome | Presentation |
| --- | --- | --- |
| Present | Updating | Keep chart, period and cursor usable; show concise progress. |
| Present | Successful | Refresh data without resetting exploration. |
| Present | Unreachable/interrupted | Keep saved data and show feedback with retry. |
| Absent | Connecting/downloading | Show progress, not a blank chart. |
| Absent | Unreachable/interrupted | Explain the absence of local data and failure, with retry. |
| Absent | Successful but empty | Explain that no measurements were recorded. |

Bluetooth or permission problems offer user-initiated enable/access/settings actions without blocking saved data. A stale advertisement is not proof of connection failure; the prototype's unreachable outcome is explicitly simulated.

## Remaining design work

- Final native back/rotation, scroll restoration, permission wording, transfer cancellation, timeout, and retry behavior.
- Unknown capabilities, partial live advertisements, historical gaps/undated samples, and retention controls.
- Whether unregistered sensors should ever access history without registration; the current flow registers first.
- Device control/security and optional critical-period highlights.

These flows are UX artifacts. No production app behavior is changed by updating them.
