# Revised live screen

Status: current approved live-screen direction, now connected through `wireframes/prototype.html`.

## Confirmed direction

- Persist the latest available reading and its original reception timestamp for registered sensors across app/process restarts (D27). Calculate age from reception, not app reopening. This remains a latest-reading snapshot, not a live-derived history.
- Keep a battery icon in the live view when the sensor reports battery information. Move the exact percentage to the sensor page.
- Show elapsed reception time in the live view once five minutes have elapsed without an update (D28). For recent readings, omit the age line entirely. Do not add a generic stale-data message alongside the timestamp.
- Every sensor card will lead to a sensor page, including unregistered and live-only sensors. Registration, editing, technical information, and history access belong there.
- Remove per-card overflow menus and history capability labels from the live view.
- Retain all available measurements, optional placement and photo, and the registered/detected distinction. Registered sensors remain listed; unregistered sensors still expire after five minutes without an update.

## Layout under review

`wireframes/live-v2.html` retains the familiar card structure and measurement labels. The battery icon is restored to a fixed top-right position in the identity area, small and without a percentage. After trying the name-adjacent and bottom-right positions, the owner requested returning to this placement because the bottom icon unbalanced the card. Only the stale outdoor example retains a bottom reception-age line; recent readings have no footer. Exact battery percentage remains reserved for the sensor page.

The sample includes one card with photo and placement, one stale card with placement, one sensor without reported battery charge, and one unregistered sensor. Battery levels are illustrative; no icon thresholds are specified.

## Still to decide

- Whether one reception timestamp accurately represents all measurements when advertisements contain partial updates.
- The sensor page layout, including exact battery percentage, last reception, registration, and history entry.

The revised card destination supersedes direct live-card-to-history navigation. Automatic history updating remains tied to opening history, not implicitly to opening the new sensor page.

## Scope of this iteration

This is a static layout wireframe for one screen, using illustrative readings. The standalone file remains layout-only. The current connected `prototype.html` implements the revised sensor-page destination and shared state.
