# Design decisions

Record agreed decisions here. Proposed solutions and unanswered questions belong in the brief until resolved.

| ID | Date | Decision | Rationale / source |
| --- | --- | --- | --- |
| D1 | 2026-09-05 | Work on the current `ux` branch and track design in `docs/ux`. | Explicit product owner instruction. |
| D2 | 2026-09-05 | Historical data originates from sensor storage; do not build history from live reception. | Explicit product constraint; see R4 in the brief. |
| D3 | 2026-09-05 | The guest scenario uses the guest's own app installation and requires immediate live consultation without sensor registration. | Explicit product owner clarification; history and device interactions are not needed for guests. This does not imply a separate guest mode. |
| D4 | 2026-09-05 | History review should begin with a whole-period overview and support drilling into moments of interest. | Explicit product owner clarification; absences may last many months. Highlighting rules remain undecided. |
| D5 | 2026-09-05 | Show environmental and technical sensors together in the initial view. | The owner prefers all sensors together over the proposed separate technical group. |
| D6 | 2026-09-05 | Provide one command to download history from all compatible saved sensors. | Initial owner choice; superseded by D21, which uses opening history as the update trigger. |
| D7 | 2026-09-05 | Use broadcast sensor names to identify readings for guests. | The owner considers the broadcast name sufficient; sharing local metadata is not needed for this scenario. Firmware editability was not confirmed. |
| D8 | 2026-09-05 | Show registered sensors first and other detected sensors afterward, with clear visual differentiation. | Explicit owner preference to avoid confusing the two groups. This refines D5 without separating environmental and technical sensors. |
| D9 | 2026-09-05 | Use a vertical history overview with one compact chart per sensor for a shared measurement and period. Open a sensor in a single large detail chart with room for controls and comparison. | The owner explicitly accepted this direction after clarifying the smartphone layout. Fine interactions and visual styling remain to be evaluated. |
| D10 | 2026-09-05 | Replace fixed period selection as the primary history navigation with an immediate way to move through time. | The owner requested a slider or similarly direct mechanism. A draggable, resizable time window is being evaluated; its exact interaction is not yet approved. |
| D11 | 2026-09-05 | Keep the detail cursor fixed at the center, with sample date/time and recorded values always available. Pan data beneath it and zoom around the central time; allow endpoint samples to reach the center using empty space beyond the data. | The owner explicitly accepted this recommendation after finding tap positioning imprecise. This supersedes tap-to-position inspection in the wireframe. |
| D12 | 2026-09-05 | Show all available measured quantities directly in each live sensor entry. | Explicit owner choice after considering whether to emphasize one quantity and reduce the others. |
| D13 | 2026-09-05 | Registered sensors retain their latest available readings and remain listed. Use elapsed time since reception as the age indication. Remove unregistered sensors after five minutes without updates. | Explicit owner response to the stale-reading question. Persistence of last readings across restarts is not yet specified. |
| D14 | 2026-09-05 | Put the unregistered indicator beside the name and show a compact battery indicator only when reported by the sensor. | Explicit owner request to keep card heights consistent and include sensor battery information. |
| D15 | 2026-09-05 | Placement is an optional free-text description and does not group sensors into rooms. | Explicit owner choice during registration discovery. |
| D16 | 2026-09-05 | Retain the optional sensor image feature. | Explicit owner confirmation; an image is not required for registration. |
| D17 | 2026-09-05 | Prefill the registration name from the broadcast name and allow local editing. | Explicit owner confirmation; the edit does not change the name announced by the device. |
| D18 | 2026-09-05 | Show optional placement and image thumbnail in the live sensor card when present. | Explicit owner confirmation to aid sensor recognition. |
| D19 | 2026-09-05 | Place sensor status below the measurements, with reception age aligned left and the battery icon/percentage aligned right when reported. | Explicit owner approval after comparing alternative battery placements. Supersedes previous alignment experiments. |
| D20 | 2026-09-05 | Tapping a registered sensor prioritizes its history; sensor information remains secondary. | Explicit owner preference because sensor information is accessed infrequently. Behavior for sensors without history remains open. |
| D21 | 2026-09-05 | Automatically update individual history on opening it and all compatible saved sensors on opening the history overview. Keep existing data viewable and fall back to saved history when an update cannot complete; remove the separate download command. | Explicit owner confirmation of automatic bulk updating, extending the proposed per-sensor behavior. Supersedes D6. |
| D22 | 2026-09-05 | Show `History →` beside the name of registered sensors with history support and `Live only` in the same position for those without it. | Explicit owner approval after reviewing the visual trial; makes card behavior predictable. |

The initial combined sensor view, reading visibility/age behavior, bulk download interaction, history overview/detail structure, direct time-navigation preference, fixed-center inspection, and primary registered-sensor history destination are agreed. Remaining navigation, device control mechanism, and visual style remain open.
