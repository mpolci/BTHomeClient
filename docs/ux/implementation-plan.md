# Native implementation plan

Status: implementation and validation completed, 2026-09-07. The owner authorized autonomous implementation, milestone commits, subagents, testing on the development phone, and a final independent visual review.

## Scope and source of truth

- Implement the latest approved decisions in `decisions.md`, especially D23–D36. Older contradictory brief items do not override later decisions.
- Keep the HTML wireframes as behavioral references and the approved dark Petrol Material live screen as the visual reference. Native Compose components provide actual interactions.
- Implement sensor-stored history only. Retain the latest live packet separately without creating live history.
- Preserve existing protocol support and retention settings. Device reset/renaming/security enrollment, new sensor protocols, and automatic risk diagnosis remain unapproved candidate features, outside this implementation.
- Work on branch `ux`. Leave the pre-existing untracked `AGENTS.md` untouched. Commit only task changes at verified milestones.

## Milestones

### M0 — Reviewed plan and live-screen baseline

- [x] Obtain independent review of this plan; record findings and resolutions below.
- [x] Review and commit the pending native live/theme/latest-reading implementation and database migration.
- [x] Confirm registered/detected ordering, all supported readings, optional metadata, fixed battery icon, five-minute age/expiry, and latest-reading restoration.

### M1 — Sensor page and navigation

- [x] Route every live card to a sensor page, including unregistered and live-only sensors.
- [x] Show identity, compact readings, original reception age, exact battery, and known technical facts immediately.
- [x] Provide same-page registration with broadcast-name/address fallback; edit local name and optional placement in single-field dialogs.
- [x] Keep pencil icons adjacent to and sized with their text, with accessible touch areas; support photo selection/removal with persisted URI access.
- [x] Confirm removal and delete local registration/history/sync/snapshot state without resetting the device. Handle the resulting unregistered or absent sensor state.
- [x] Use persisted Unknown/Supported/Unsupported history capability. Unknown registered sensors may enter history to discover the service and participate in overview discovery. Confirmed unsupported sensors have no history action. Never infer capability from names or BTHome parse support.
- [x] Keep the opened unregistered sensor usable if background feed expiry occurs while its page is being read or edited; do not invent new reception times.
- [x] Preserve clear Back behavior and scanning lifecycle across live and sensor pages; avoid competing scan/download operations.
- [x] Add meaningful ViewModel and native UI tests; commit M1.

### M2 — Observable history and automatic updates

- [x] Expose downloaded samples, coverage, synchronization information and per-sensor progress/errors through repository/ViewModels.
- [x] Use one history-session coordinator for the overview/detail route family. Automatically queue known-supported and unknown saved sensors on initial overview entry, or one sensor on direct entry. Reuse pending/active transfers across overview/detail transitions and rotation; cancel when leaving history entirely or genuinely backgrounding. Back must not restart the batch.
- [x] Keep cached history readable during updates and failures. Distinguish connecting, transfer progress, empty sensor history, no local history, unreachable/interrupted transfer, and unknown/unsupported capability.
- [x] Provide contextual retry and user-initiated Bluetooth/permission/settings actions without blocking saved data.
- [x] Cancel/clean up connections on leaving history and preserve completed imports. Serialize deletion with import or validate a registration-generation identity inside the commit transaction, including deletion followed immediately by re-registration at the same address. Never attach an old transfer or cursor to the new registration.
- [x] Derive successful empty/unchanged presentation from the persisted archive, not just imported count; preserve an existing archive on incremental EMPTY and explain an undated-only archive.
- [x] Add coordinator/repository/ViewModel tests covering concurrency, cancellation, failure and cached-data fallback; commit M2.

### M3 — Historical overview and exploration

- [x] Add general History navigation and a portrait overview with one chart per compatible registered sensor, one shared measurement and time window.
- [x] First History entry shows the latest week ending at the latest recorded sample (D37); shorter archives fit their available coverage. Replace preset-only periods with immediate time navigation, pan/range adjustment and a full-coverage reset; preserve overview period, measurement and scroll position on Back and while updates finish.
- [x] Implement a landscape individual chart; restore the prior orientation when leaving and preserve period/cursor on rotation and Back.
- [x] Keep a fixed central cursor and sample readout. Drag to move the timeline; pinch to zoom around its center; permit endpoint samples to reach the cursor.
- [x] Compare compatible saved sensors with actual sample timestamps and units. Expose exact recorded values rather than presenting interpolation as observations.
- [x] Plot dated samples chronologically while retaining acquisition identity, duplicate timestamps, and time-regression/known-gap boundaries. Display local timezone explicitly; comparison readouts show each actual sample timestamp. Explain undated samples and coverage limitations; preserve extrema when reducing long series for display.
- [x] Ensure charts remain bounded/readable for empty, singleton, equal-value, irregular, and multi-month data. Provide accessible labels and non-gesture exploration alternatives.
- [x] Add pure chart/viewport tests and native overview/detail interaction tests; commit M3.

### M4 — Integration, native validation and visual review

- [x] Apply consistent Petrol Material styling to sensor pages, dialogs, states, settings, overview and landscape detail. Preserve a usable light counterpart and make the theme preference explicit if needed.
- [x] Complete routing and prerequisite handling; remove obsolete reachable placeholder/editor paths once replacements are verified.
- [x] Run JVM tests, lint, debug assembly and connected Android tests on the development phone. Inspect failures and rerun only affected checks before the final suite.
- [x] Exercise real scan/registration/latest-reading restoration and, when a compatible sensor is available, a real history download. Use clearly isolated synthetic fixtures for chart states unavailable on hardware.
- [x] Capture native screenshots covering live, registered/unregistered sensor pages, edit/removal dialogs, history overview/detail and error states; include large-font/narrow-layout checks.
- [x] Obtain independent code review and a separate visual review of native screenshots against approved references; resolve material findings and repeat relevant checks.
- [x] Update documentation, acceptance checklist and known limits with actual results; commit the completed milestone.
- [x] Leave the final app installed on the development phone, including after test-runner cleanup. Do not claim real-device transfer validation if no compatible sensor was available.

## Working rules

- Keep one shared owner for navigation/integration files. Delegate independently bounded work with explicit file ownership to avoid conflicting edits.
- Use latest compatible stable dependencies already available unless a required behavior justifies a version change. Do not equate the newest alpha library with native visual fidelity.
- Treat existing user data carefully during migration; use named test databases/fixtures. The connected phone is explicitly a development device, but final installation must be restored after instrumentation cleanup.
- Resolve routine implementation details autonomously and document them. Escalate only genuine missing product/protocol requirements that block safe completion.

## Plan review

Reviewed by an independent agent (`plan_review`). All five actionable findings were accepted:

1. P1 capability gate: firmware inspection confirms the history service is not advertised and current BTHome payloads do not provide a reliable capability discriminator. Resolve with persisted tri-state discovery and an explicit Unknown history entry.
2. P2 session ownership: use one application-wired history-session coordinator; overview/detail share active work, exploration state lives with their retained ViewModels, rotation does not restart transfers.
3. P2 deletion/re-registration race: validate a stable registration identity inside the same transaction that imports samples/cursor, or serialize all relevant mutations. Include this race in tests.
4. P2 zero imports: inspect existing archive and dated/undated counts before choosing the empty screen.
5. P2 chart acceptance: explicitly cover full initial range, stable viewport across update/Back, chronological rendering with acquisition identity, timezone and asynchronous sample timestamps.

Implementation interface ownership will be recorded when work is delegated.

## Implementation and validation log

M0/M1: independent baseline review found historical database-version collisions. Explicit v3 migrations now cover original/reworked v1 and both prior v2 shapes. Shared registration identity and mutation coordination support safe later history imports. Native live/sensor-page implementation passed JVM tests and 21/21 connected tests on the development Pixel 4a (5G), Android 14. The first commit groups the live baseline, migration correction, and sensor-page integration; history UI/session work continues separately.

M2/M3 implementation: observable archives and tri-state capability, one automatic download queue, identity-guarded imports, cursor validation, import retention, overview navigation, centered chart gestures and exact comparison readouts are implemented. Repository, session, ViewModel and pure chart cases are included in the JVM suite. Native routing, gestures, failure states and appearance passed the complete 57-test device suite. All 113 JVM tests, lint and debug assembly passed. Independent screenshot review passed after targeted corrections.

Physical sensor validation, 2026-09-07: SENE-A was discovered with plausible live temperature/humidity/pressure/battery readings and registered from its sensor page. Opening history automatically completed a real 130,306-position transfer and displayed 1,010 dated samples plus four undated samples. After force-stop/restart the registration and archive remained available. Opening the overview automatically performed an incremental update, increasing dated samples to 1,011 without losing the archive. The saved timeline includes an old 2023 timestamp; it is preserved as recorded rather than silently discarded. A coverage-warning regression seen on this increment was corrected: known truncation remains attached to the archive even when a subsequent transfer has no new truncation flag.

Independent integration review found and corrected history overview orientation ownership and failure handling for persistable photo permissions. Independent visual review of the real history page requested a smaller readout overlay; normal-font height is now capped at 22%, with coverage/synchronization details available through Info. The orientation/session regression test now passes, including explicit Activity recreation and overview/detail transitions. Final regenerated screenshots are recorded in `native-review.md`.


M4 completion: the independent visual review covered twelve synthetic native scenarios and a real sensor archive. Fixes include readable one-column measurements at high font scale, empty states without fictitious epoch axes, measured chart insets that keep controls clear of samples, compact time controls, and a two-line large-font cursor readout. All 113 JVM tests, the complete 57-case native suite, lint and debug assembly passed. The final empty-overview test raised the native count to 58; all 17 affected HistoryScreen cases and the final large-font capture subsequently passed. See `../testing.md` for exact scope and remaining hardware-validation limits.

The final debug build remains installed and open on Live on the development phone. After instrumentation's reinstall/reset behavior, SENE-A was registered again and its production full history transfer completed with 1,013 dated samples. Final process restart retained the registration and current readings. Approved screenshots and findings are stored in `native-review.md` and `review/native/`.

Milestone commits: `85e6d6b` (live, migration and sensor page), `2b44238` (automatic history, native charts, integration and tests), followed by the final validation/documentation commit.

D37 refinement: the initial seven-day window follows new download results until the owner starts time exploration; an inherited overview window and explicit All history selection remain stable. ViewModel regression coverage includes multi-month archives, later downloads, shorter archives and overview-to-detail selection.

D37 validation: all 12 HistoryViewModel tests and debug assembly passed. An in-place APK update retained the development phone's saved data; opening the real SENE-A overview displayed 31 August–7 September 2026, ending at the latest available sample.
