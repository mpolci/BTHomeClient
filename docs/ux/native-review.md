# Native visual review

Status: review complete, 2026-09-07.

## Method

The review used full-screen native Android screenshots captured on the development Google Pixel 4a (5G), Android 14/API 34. A debug-only `ComponentActivity` hosted the real Compose screens under the selected dark Petrol theme. The capture set covered portrait and landscape layouts, dialogs, empty/error states, and a 1.6× font scale. Screenshots were inspected at their original 1080 × 2340 or 2340 × 1080 resolution against the latest decisions in `decisions.md`, especially D23–D36, and the approved Material treatment in `android-style.md`.

The twelve review scenarios use clearly synthetic fixture data. They do not write the fixtures to the app database and are intended to make layout conditions reproducible. A separate screenshot used a real SENE-A history download. Earlier evidence contained 130,306 raw records, 1,010 dated points and four undated samples; the final installed app completed a later download with 1,013 dated samples as the sensor continued acquiring data. The real archive also contained a 2023 timestamp among otherwise 2026 data. The resulting 2023–2026 `All history` fit is therefore expected evidence of the stored timestamps, not a chart-layout defect.

The first screenshot attempt captured intermediate dialog and rotation frames. It was discarded. The reviewed set waits for Compose idle, Android UI idle, and animation completion before taking a system screenshot.

## Review set

| Screenshot | Scenario | Result |
| --- | --- | --- |
| [01-live-overview.png](review/native/01-live-overview.png) | Registered and nearby sensors, optional image, stale reading | Approved |
| [02-sensor-registered.png](review/native/02-sensor-registered.png) | Registered sensor page | Approved |
| [03-sensor-unregistered.png](review/native/03-sensor-unregistered.png) | Unregistered sensor page and registration action | Approved |
| [04-sensor-name-dialog.png](review/native/04-sensor-name-dialog.png) | Local-name dialog | Approved |
| [05-sensor-removal-dialog.png](review/native/05-sensor-removal-dialog.png) | Registration-removal confirmation | Approved |
| [06-history-overview-six-months.png](review/native/06-history-overview-six-months.png) | Six-month multi-sensor overview with a gap and extreme | Approved after recapture |
| [07-history-detail-landscape-comparison.png](review/native/07-history-detail-landscape-comparison.png) | Landscape detail with comparison | Approved after recapture |
| [08-history-no-data-error.png](review/native/08-history-no-data-error.png) | Unreachable sensor without local history | Approved after recapture |
| [09-live-large-font-160.png](review/native/09-live-large-font-160.png) | Live screen at 1.6× font scale | Approved |
| [10-sensor-large-font-160.png](review/native/10-sensor-large-font-160.png) | Sensor page at 1.6× font scale | Approved after recapture |
| [11-history-detail-landscape-large-font-160.png](review/native/11-history-detail-landscape-large-font-160.png) | Landscape history detail at 1.6× font scale | Approved after recapture |
| [12-live-dense-card-large-font-160.png](review/native/12-live-dense-card-large-font-160.png) | Dense stale Live card at 1.6× font scale | Approved |
| [13-real-history-sene-a.png](review/native/13-real-history-sene-a.png) | Real SENE-A archive after a completed download | Approved |

The status-bar absence or partial clock visible in screenshots 02 and 10 was treated as a cross-orientation capture artifact. The app content itself supplied no matching evidence of an inset failure, so it is not recorded as a production finding.

## Findings and resolution status

### P1 — Sensor measurements collide at 1.6× font scale

Screenshot 10 showed adjacent values and units touching: temperature ran into humidity, and pressure ran into carbon dioxide. The labels remained distinct, but the values were no longer readable as separate measurements. This is an accessibility defect rather than a property of the synthetic values.

Resolution: the sensor page now uses one measurement column above 1.3× font scale. Screenshot 10 confirms that every label, value and unit has separate readable space, including the longer pressure and carbon-dioxide values. Status: resolved.

### P2 — Empty history presents fictitious epoch axes and repeated messages

Screenshot 08 correctly explained that the sensor could not be reached and no local history existed, then rendered a 0–1 °C chart, January 1970 time labels, `No dated samples`, and a second `No dated sample` readout. The epoch labels can be mistaken for observations and the repeated empty messages weaken the state hierarchy.

Resolution: the selected empty archive no longer renders a chart, axes, epoch labels or a sample readout. Screenshot 08 shows one contextual unreachable/no-local-history message with its Retry action. Status: resolved.

### P2 — Landscape overlays hide chart extrema and comparison data

In screenshot 07, the opaque header covered the 35 °C maximum and the readout covered the lower primary and comparison series. Screenshot 11 showed the same issue more strongly at 1.6× font scale, where the multi-row header and enlarged readout left little unobstructed plot area. The real-download screenshot independently showed that the earlier readout occupied more than 40% of the usable plot height. Reducing its height improved the balance but did not by itself prevent data from appearing behind opaque overlays.

Resolution: the chart now reserves measured top and bottom insets for its header and readout. Screenshot 07 keeps the maximum, both complete series, cursor and axis labels outside the opaque surfaces. At 1.6× font scale, screenshot 11 moves measurement and full-history actions into the Info dialog, preserves a compact header, and keeps the enlarged two-sensor readout below the plot. Status: resolved.

### P3 — History overview controls dominate the first viewport

In screenshot 06, the date range, slider, navigation actions, and zoom actions consumed roughly 550 pixels before the first sensor card. Only one chart card and part of the next were visible. Direct time navigation is an approved requirement and remains present; the issue is the vertical distribution of those controls relative to the multi-sensor comparison task.

Resolution: the visible controls now use one action row while the slider retains direct time positioning and accessible earlier/later actions. Screenshot 06 shows the first chart and most of the second chart in the initial viewport without removing zoom or full-history access. Status: resolved.

## Approved visual direction

The reviewed Live screens use the approved filled Petrol surfaces, clear registered/nearby sections, fixed battery placement, optional image and placement, and reception age only for old readings. The registered and unregistered sensor pages present their identity, readings, battery, capability and primary action with a clear hierarchy. Both dialogs use readable native Material structure and preserve the approved deletion wording. The ordinary and dense large-font Live cards reflow without collisions or clipped measurement content.

All twelve synthetic native screenshots are approved after the targeted second pass. The final large-font history screenshot confirms that its compact two-line readout preserves the selected value, sensor name and exact timestamp without clipping. The refreshed real-download screenshot confirms the same unobstructed plot and compact readout with actual sensor data. No material hierarchy, readability, spacing or clipping issue remains in the thirteen-image review set.

## Validation evidence

- 113 JVM tests passed.
- The full connected Android suite passed 57/57 tests.
- A subsequent targeted History screen run passed 17/17 tests, including the final empty-state and large-font readout regressions. This targeted rerun is additional evidence, not a claim that the full suite contained 58 tests.
- Android lint and debug assembly passed.
