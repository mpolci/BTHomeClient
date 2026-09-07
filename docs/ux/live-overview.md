# Live overview

Status: first wireframe for review. Related requirements: R1, R2, R3, R6, R9, R11, R13, R14.

## Confirmed direction

- Registered sensors precede other detected sensors, with visual differentiation.
- Environmental and technical sensors share this organization.
- All available measured quantities appear directly within each sensor entry; no expansion or single-quantity selection is required.
- Guests can consult unregistered sensor readings using broadcast names.
- Registered sensors remain visible with their latest available readings even without new advertisements. The owner considers the elapsed time since reception sufficient as the age indication.
- Remove unregistered sensors from the live list after five minutes without an update. A subsequent advertisement makes them eligible to appear again.
- Place `Not registered` beside the sensor name, without adding a separate row.
- Show a small sensor battery icon and charge indication only when the sensor reports this information.
- Show the optional placement description and image thumbnail in the live card when present. Omit missing optional fields without reserving blank space.

## First layout proposal

Use full-width sensor cards with a consistent order and equal visual treatment for measured quantities. The example places temperature, humidity, and pressure in three columns; additional measurements wrap onto another row. A sensor that reports only temperature shows only that quantity. Do not display unsupported quantities as zero or as an error.

Section headings and spacing distinguish registered sensors from other nearby sensors. A registration label beside the sensor name reinforces this distinction without increasing card height. The owner accepted the bottom status row, following the sequence identity → measurements → sensor status. The approved layout places reception age on the left and the battery icon and percentage on the right of this bottom row, when reported. Sensors without reported battery information show only reception age. The owner explicitly approved this bottom-row alignment (D19); it supersedes the earlier battery placements. The wireframe uses a single column of sensors and illustrative values; typography, terminology, and exact density remain proposals.

Each entry shows when a reading was received. The revised example retains an outdoor sensor last received two hours ago with its last readings and battery level. Its age line is the only freshness indication, following the owner's preference. The technical temperature sensor deliberately has no battery icon because that example does not report charge; equipment temperature and the sensor's own battery status are distinct information.

The revised identity area places a small thumbnail beside the name and the optional placement description below the name. The first card includes both (with a clearly labeled photo placeholder), the outdoor card includes only a description, and the technical card includes neither. Measurements retain their full-width row. Exact image size and text wrapping remain layout proposals.

Retaining the latest reading does not create a live-derived time series and does not change R4. The owner subsequently confirmed persistence of the latest reading and its original reception timestamp across app/process restarts (D27).

## Open decisions

- Whether freshness is tracked per measurement when one advertisement does not contain all supported quantities.
- Ordering within each section and whether owners can rearrange sensors.
- Whether and where signal strength is useful. Battery presentation is now defined above.
- Entry points for registration, sensor management, and history. The first content-layout wireframe does not prescribe navigation.

## Artifact

See `wireframes/live.html`. This is a layout mockup, not live Bluetooth reception; reception ages are illustrative and the mockup does not run an expiry timer. The optional density adjustment changes spacing for review; it is not a proposed app setting.

## History capability labels

Capability wording was approved in D22. The original placement beside the name is now being revised after the owner found it too close to the overflow menu in the connected prototype.

The current trial places `History →` or `Live only` on a dedicated row below the measurements and before reception age/battery. The overflow menu remains at the top right. This adds a row to registered cards while separating the primary history action from secondary management actions. The new placement is awaiting owner review.

The cabin and outdoor sensor illustrate history support; the technical sensor illustrates live-only support. These are illustrative capabilities, not hardware facts. Unregistered sensor registration presentation remains unchanged; unknown capabilities still need a design.

The standalone `live.html` is a layout-only view. In the connected `prototype.html`, the whole history-capable card remains an accessible action opening history and initiating its simulated automatic update; the overflow menu is a separate target.
