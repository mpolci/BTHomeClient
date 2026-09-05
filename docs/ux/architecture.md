# Information architecture proposal

Status: draft for discussion. The owner has confirmed a combined initial sensor view and automatic history updates on opening the overview. Other proposals are not approved navigation or screen layouts.

## Concepts

| Concept | Meaning | Design implication to evaluate |
| --- | --- | --- |
| Sensor | A physical source of measurements with its own capabilities. | Live reception, stored history, and device controls have different prerequisites. |
| Placement description | Optional free text describing where a sensor is placed, such as a cabin, galley/dinette, or technical compartment. | Provide context without creating room entities, grouping sensors, or requiring a placement value. |
| Measurement subject | A specific item or medium, such as a battery, engine, or surface seawater. | A temperature does not always describe the surrounding room. |
| Local registration | Metadata saved within one app installation. | Do not assume guests receive the owner's local names or organization. |
| Downloaded history | Locally retained measurements originating from sensor storage. | Show historical coverage independently of current reception. |

These are conceptual distinctions for design, not a proposed database schema or a requirement to add configuration fields.

## Proposed information groups

| Group | User question | Proposed content |
| --- | --- | --- |
| Current conditions | What is happening aboard now? | Registered sensors first, other detected sensors afterward, with visual differentiation. Environmental and technical readings share this view. Show all available measured quantities directly for each sensor. Recognizable source, freshness, and comparison across spaces and outdoors; detailed layout remains open. |
| History | What happened while I was away? | Overview of a selected period, coverage/download status, and inspection of shorter intervals; potential highlights still under discussion. |
| Sensor information and management | What is this sensor, and how do I organize it? | Local name and placement, capabilities, and relevant device information; device controls remain candidate features. |
| App settings | How should the app behave? | App-wide preferences, including historical retention policy once confirmed. |

These groups do not prescribe tabs or separate screens. Navigation will follow the agreed task flows.

## Draft task sequences

- **Guest live consultation (S4):** Open app → resolve required platform/Bluetooth prerequisites if needed → receive nearby broadcasts → read values identified by broadcast names. The owner considers these names sufficient; local registration is not a prerequisite.
- **Owner live comparison (S1):** Open app → see all sensors together with current conditions and freshness → compare relevant spaces or sources → tap a registered source to open its history when supported. Sensor information is secondary; see [navigation](navigation.md) for unresolved cases.
- **Return after absence (S2):** Access history → understand available coverage → automatically update all compatible saved sensors while existing history stays viewable → review updated coverage and per-sensor outcomes → review the requested period → select a moment of interest → inspect its measurements and context. The overview spans multiple sensors. Per-sensor outcome presentation remains a proposal; automatic update on entry is confirmed.

These are discussion sketches, not complete user flows. Permission denial, unavailable sensors, partial downloads, missing/undated samples, and empty history will be expanded after the main interactions are agreed.

## Next design decisions

1. Card layout and ordering within each group of the confirmed registered-first sensor view. Proposed differentiation uses explicit section labels and spacing, with color only as an additional cue.
2. Fallback identification when a broadcast name is absent; broadcast names are the agreed normal identification for guests.
3. Progress, partial-success, and retry behavior for the automatic history updates on entry.
4. Evaluate the wireframe of the [approved history direction](history-overview.md): vertically stacked sensor charts for one measurement and period, with optional comparison in the detail view. Detailed controls remain proposals.
