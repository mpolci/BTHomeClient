# UX brief

Status: initial discovery. Source: product owner introduction, 2026-09-05.

## Purpose

The app displays information from a collection of environmental sensors, with technical measurements aboard the boat also envisioned.

## Users and context

The primary user is the product owner, using the app aboard their boat. Planned placements include cabins, the central galley/dinette area, the bathroom, and outdoors. Possible additions include technical spaces such as the engine compartment and transformer compartment, and specific measurements such as battery temperature, engine temperature, and surface seawater temperature. The exact sensor count and support for these technical measurements remain open.

Visiting guests may install the app on their own phones and view nearby live readings immediately, without registration or setup work beyond platform prerequisites. They do not need history or device interactions. The owner expects that at sea the visible sensors will belong to the boat; this is a usage context, not a guaranteed discovery filter or ownership check.

While aboard, the owner wants to compare current temperature, humidity, and pressure across spaces, including indoor versus outdoor conditions. After an absence with the boat closed, the owner wants to understand the conditions experienced during that period and identify possible reasons for inspection or future preventive action. Reviewing climatic trends over previous months is also a use case.

Absences may last many months. At return, the desired history experience starts with an overview of the entire period, followed by closer inspection of moments that deserve attention. Highlighting critical periods is of interest, but the interpretation mechanism is undecided.

See [user scenarios](scenarios.md) for concrete tasks and proposed success criteria.

## Confirmed requirements and constraints

| ID | Requirement |
| --- | --- |
| R1 | Display environmental sensor information. |
| R2 | Sensors autonomously broadcast measurements at regular intervals. The currently supported sensor type broadcasts unencrypted data that any listener can receive; live reception does not require registration or a connection. |
| R3 | Support registration within the app to associate personal metadata, such as a name and placement, with a sensor. This is distinct from connecting to or configuring the physical device. |
| R4 | History is available only for sensors that store it internally. The app already downloads that history and retains data from earlier downloads. It must not construct history from live advertisements. |
| R5 | Design how users consult downloaded historical data. The presentation and interactions are not yet decided. |
| R6 | Support comparison of current temperature, humidity, and pressure across boat spaces, including indoor/outdoor comparison when an outdoor sensor is present. |
| R7 | Support retrospective review after an absence, including investigation of periods the owner associates with condensation, freezing, or heat stress concerns. Automatic risk assessment and thresholds are not yet requirements. |
| R8 | Support exploration of historical environmental trends over previous months, subject to available downloaded data. |
| R9 | Let guests consult nearby live readings on their own app installation without registering sensors. History and other interactions are not needed for this scenario. |
| R10 | Start retrospective review with a whole-period overview and allow inspection of shorter intervals. Absences can last many months; full historical coverage must not be assumed. |
| R11 | Present environmental and technical sensors together in the initial view. |
| R12 | Opening the general history overview automatically updates history from all compatible saved sensors, replacing the separate bulk download command (D21). Opening individual history updates that sensor. Keep existing history viewable if an update cannot complete. |
| R13 | In the initial view, show registered sensors before other detected sensors and visually distinguish the two groups. Environmental and technical sensors remain together within this organization. |
| R14 | Show all available measured quantities for each sensor directly in the live overview, without requiring expansion or selecting a single primary quantity. |
| R15 | Keep registered sensors and their latest available readings visible without new advertisements; indicate reading age using elapsed reception time. Remove unregistered sensors after five minutes without updates. |
| R16 | Place the unregistered indicator beside the sensor name. Show a small battery icon and charge indication only for sensors reporting battery information. |
| R17 | Treat placement as an optional free-text description, not a sensor grouping or room-management feature. |
| R18 | Retain an optional image associated with a registered sensor; do not require an image to complete registration. |
| R19 | Prefill the local name from the sensor's broadcast name when available and allow editing during registration. Editing this name affects only the app, not the device's broadcast name. |
| R20 | Show a registered sensor's optional placement description and image thumbnail in its live card when present. |
| R21 | Make history the primary destination when tapping a registered sensor with history support. Sensor information is a secondary destination because it is accessed infrequently. |
| R22 | Distinguish registered sensor history capability beside the name using `History →` for supported history and `Live only` otherwise. |

## Candidate features

These are ideas to investigate, not committed release scope.

| ID | Candidate | Unresolved aspects |
| --- | --- | --- |
| C1 | Reset history stored on a sensor. | Use case, firmware support, authorization, and intended effect on previously downloaded app data. |
| C2 | Change the name a sensor broadcasts. | Use case, firmware support, authorization, and relationship to the local display name. |
| C3 | Highlight potentially critical periods in historical data. | The owner sees possible value; criteria, thresholds, and interpretation remain undecided. |
| C4 | Accommodate specific technical measurements such as battery, engine, and surface seawater temperatures. | Envisioned use cases; hardware/protocol support and first-release scope require clarification. |

The product owner requires protection against unauthorized changes if device controls are introduced. The authorization mechanism is undecided and requires investigation of device/firmware capabilities. Local app registration must not be assumed to confer authority over the device.

## Discovery questions

### First round: answered, with scale still open

- Q1: Primarily the owner aboard their boat, potentially also visiting guests. Interior sensors and probably an outdoor sensor; counts and exact placement remain open.
- Q2: Check and compare current conditions across interior spaces and outdoors while aboard.
- Q3: Review conditions during absences for potential concerns and understand climatic trends over previous months.

### Second round: answered, with details still open

- Q4: Cabins, galley/dinette, bathroom, outdoors, possibly technical spaces and specific equipment/water measurements. Absences can last many months. Exact sensor count and typical duration remain unspecified.
- Q5: Start with an overview of the entire period, then inspect moments of interest. Highlighting potentially critical periods may be useful; no automatic assessment or threshold approach has been selected.
- Q6: Guests install the app on their own phones and view live broadcasts with minimal friction. They do not need history or other interactions. How they recognize sensor locations remains open.

### Third round: answered for the immediate design choices

- Q7: Show all sensors together in the initial view, rather than separating technical sensors into a dedicated group.
- Q8: The owner considers the broadcast name sufficient for guest identification. Firmware editability was not answered and remains open for future device-control work.
- Q9: Initially a single explicit bulk command was chosen. D21 supersedes that trigger with automatic updates on opening history. Per-sensor results and retry behavior remain to be designed.

### Later rounds

- Q10: After requesting and clarifying a smartphone-oriented recommendation, the owner approved a vertical list of small charts sharing one measurement and period, with a single large chart in the detail view. See [history direction](history-overview.md) and D9. Fine interactions remain proposals.
- Q11: Registered sensors should appear first, followed by other detected sensors, with visual differentiation so the groups cannot be confused.

### Remaining questions

- What terminology best communicates local registration without implying pairing or exclusive ownership?
- How should live reading age, unavailable sensors, and previously downloaded data be presented?
- Which measurements, periods, and comparisons matter for the priority history task?
- When and how should history downloads be initiated, and how should missing or undated data be explained?
- What are the intended retention and deletion behaviors, including removing a registration and resetting device history?
- Are device controls part of this redesign's first release? Who should be allowed to use them, and can the firmware be changed?

## Collaboration preference

Ask discovery questions directly in the conversation: the product owner reported that tool-delivered question cards do not appear in their Codex app.

## Pending outcomes

The primary audience, usage contexts, guest live-only needs with broadcast-name identification, combined initial sensor view, automatic history update on entry, and overview-to-detail history approach are established. The first flow to prototype, agreed success criteria, interpretation level, detailed navigation, and visual direction remain undecided. See the [information architecture proposal](architecture.md).
