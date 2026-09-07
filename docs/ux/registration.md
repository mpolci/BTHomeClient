# Sensor registration

Status: discovery in progress. Related requirements: R3, R17.

## Confirmed behavior

Registration associates local information with a sensor and does not imply pairing, device ownership, or authorization to change the device.

Placement is an optional free-text description. Users can leave it empty. It does not create a room, a category, or a sensor grouping.

Retain the existing ability to associate an image with a sensor. The image is optional and registration must be possible without choosing one.

Prefill the local display name from the broadcast name when available. Let the user edit it during registration. The change applies only within the app and does not rename the physical device or change its broadcast name.

Show the placement description and image thumbnail in the live sensor card when present, to help identify the sensor. Both remain optional.

## Proposed flow

Open a detected sensor from the live overview → choose registration → review the local display name, initially populated from the broadcast name when available → optionally add a placement description → save → return to the live overview with the sensor in the registered section.

Name-prefill and local editing are confirmed in D17, optional placement in D15, and the optional image in D16. The overall entry and return flow remains a proposal.

The [connected prototype](prototype.md) now implements direct card-to-registration and menu-to-registration entry for review, along with save, cancel, optional photo editing, and a read-only sensor information view. These interactions update temporary prototype data only.

## Open decisions

- Presentation of the optional image in sensor detail; its presence in the live card is confirmed.
- Local name requirements and fallback when no broadcast name exists.
- Presentation of the optional placement description in sensor detail; its presence in the live card is confirmed.
- Registration entry point and save/cancel behavior, including loss of live reception while editing.

## Registration removal

Confirmed in D29: removing a registration also deletes its downloaded history from the app; no archive remains for that registration. This is a local operation, separate from resetting history on the device.

Proposed interaction for review: expose removal at the bottom of the sensor page and show a confirmation that explicitly mentions loss of downloaded history. The entry point and dialog are now present in `wireframes/sensor-detail-v2.html`; removal changes only the in-memory example. See [sensor page](sensor-page-v2.md#removal-interaction--review-proposal).
