# User scenarios

Source: product owner discovery answers, 2026-09-05.

The contexts and needs below are confirmed by the owner. Success criteria are design proposals awaiting review, not approved screen or feature specifications. Scenario order does not establish implementation priority.

## S1: Compare current conditions aboard

Related requirements: R1, R2, R3, R6.

**Context:** The owner is aboard, with sensors in cabins, the galley/dinette area, the bathroom, and outdoors. Technical spaces and equipment-specific sensors are also envisioned.

**Need:** Understand current temperature, humidity, and pressure, including how much warmer it is inside than outside and how interior spaces differ.

**Proposed success criteria:**

- Identify which space each reading belongs to.
- Compare the same measurement across relevant spaces with little navigation.
- Understand reading freshness and distinguish unavailable readings from current ones.

**Confirmed presentation direction:** Show environmental and technical sensors together in the initial view (R11).

**Open:** Exact sensor count, ordering, preferred comparison presentation, and acceptable reading age.

## S2: Review conditions after an absence

Related requirements: R4, R5, R7.

**Context:** The owner arrives after the boat has been closed, potentially for many months. Compatible sensors store measurements internally; whether they retain the entire absence is not established.

**Need:** Inspect the conditions experienced while away to decide whether closer inspection or future precautions may be appropriate. The owner mentions concerns about condensation, freezing, and heat stress.

**Proposed success criteria:**

- Begin with an overview of the requested absence period and inspect shorter intervals of interest.
- Understand which period the available history covers and whether another download is needed.
- Locate unusual periods and identify their time, duration, and associated space where data allows.
- Distinguish missing data from periods with recorded measurements.
- Understand the difference between measured conditions and any interpretation the app may eventually provide.

**Confirmed download interaction:** Opening the history overview automatically updates all compatible saved sensors; opening individual history updates that sensor. Existing history remains viewable when updates cannot complete (R12, D21).

**Open:** Download progress and partial-success handling, cross-sensor review, and criteria for possible critical-period highlights. The concerns above are investigation goals, not evidence of damage or approved rules for diagnosing risk.

## S3: Explore previous months

Related requirements: R4, R5, R8.

**Context:** The owner wants to understand how the past period evolved climatically.

**Need:** Explore historical environmental trends over previous months using available sensor downloads, including retained earlier downloads.

**Proposed success criteria:**

- Select a relevant period and measurement.
- Understand the broad trend and inspect a shorter interval when useful.
- Recognize the limits of available historical coverage.

**Open:** Preferred periods, aggregation, comparisons, and retention expectations. This scenario does not assume that a sensor can retain an entire absence or that the app has continuous historical coverage.

## S4: Let a visiting guest consult readings

Related requirements: R1, R2, R3.

**Context:** Visiting guests install the app on their own phones. At sea, the owner expects nearby visible sensors to belong to the boat.

**Need:** Immediately consult live sensor readings without registering or configuring sensors. History and other interactions are not needed.

**Proposed success criteria:**

- Reach nearby live readings without app-specific setup, after any necessary Bluetooth and platform permission steps.
- Do not require local registration to read broadcasts.

**Confirmed identification:** The owner considers broadcast sensor names sufficient for guests. Sharing the owner's local metadata is not needed for this scenario.

**Open:** Presentation when a broadcast name is absent. No account, separate guest mode, or permission model has been selected.
