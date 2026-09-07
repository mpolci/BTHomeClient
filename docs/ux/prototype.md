# Connected UX prototype

Status: current integrated wireframe, 2026-09-07, built after checkpoint commit `bcac193`. This document records the browser prototype work. Subsequent native Android implementation and validation are tracked in [implementation-plan.md](implementation-plan.md); the prototype remains schematic.

## Entry point and sources

The active preview is `wireframes/prototype.html`. The builder combines `live-v2.html`, `sensor-detail-v2.html`, and `history.html` with `prototype-v2-shell.html`, which owns shared example state, navigation, and simulated update outcomes.

Rebuild after editing a source:

```text
python3 docs/ux/wireframes/build_prototype.py
```

Earlier navigation shells and `prototype-clean.html` remain historical references. The builder no longer publishes the rejected clean variant.

## Connected paths

- Every live card opens its sensor page, including unregistered and live-only sensors.
- Sensor page → history starts an automatic update. Back returns to that sensor page, then live.
- General History → overview updates all compatible registered sensors. A chart returns to the overview; leaving the overview returns to live.
- Pencil actions open single-field dialogs. Save updates local metadata across screens; Cancel leaves it unchanged. The broadcast name remains separate.
- Photo selection and removal are available on the sensor page. Chosen image contents stay in the preview.
- Registration uses the broadcast name and reveals local editing on the same page.
- Removal confirms loss of local metadata and downloaded history. Re-registration does not restore deleted historical data.

## State simulation

The default examples include a reachable cabin with saved history, an unreachable outdoor sensor with saved history, a live-only technical sensor, and an unregistered sensor without downloaded history.

Opening history starts a short simulated connection/transfer. Saved charts remain usable. A successful first download makes illustrative history available; unreachable, empty, or interrupted results do not fabricate samples. Comparison includes only registered sensors with available history.

Optional preview controls adjust Bluetooth/permission availability, history outcome, and elapsed minutes without advertisements. These are review controls, not app settings. Live reception age is hidden until five minutes; unregistered sensors disappear at that threshold. Saved readings remain available when updates or prerequisites fail. Enable/access actions resolve only simulated conditions and do not operate device settings.

## Verification

Browser walkthrough verified live → sensor → history returns, overview/detail return nesting, local-name propagation, same-page registration, first-download progress/success, cancellation of removal, removal followed by re-registration, and the absence of deleted history when a sensor is unreachable. Saved-history fallback and landscape chart layout were visually checked.

JavaScriptCore checks covered source syntax, automatic updates, empty/interrupted outcomes, Bluetooth/permission/settings actions, the five-minute rule, and cancellation of pending updates when leaving history.

## Limits and implementation choices

- State and images live in preview memory only. D27 requires production persistence across restarts; browser reload resets the examples.
- Historical curves and Bluetooth addresses are illustrative. Example history is predefined separately from the live readings.
- Timings, daily sample resolution, battery icon thresholds, and blank-name fallback are prototype choices.
- Leaving history cancels pending simulated transfers; final production cancellation/retry behavior remains open.
- Native rotation, BLE transfer, mobile touch/keyboard behavior, file-picker behavior, and OS permission prompts are not verified by this browser walkthrough.
- Device controls, security enrollment, retention controls, and critical-period analysis are not implemented.
