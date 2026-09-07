# Sensor page — first screen wireframe

Status: the displayed registered-sensor layout was approved on 2026-09-07 (D24), following D23. It is now used by the connected prototype.

## Information order

1. Sensor identity: local name when registered, otherwise broadcast name; optional photo and placement.
2. Latest available measurements in a compact row.
3. Immediately visible reception age, exact battery percentage when reported, broadcast name, Bluetooth address, and history support.
4. Separate history entry for compatible registered sensors, or registration for an unregistered sensor.
5. Local metadata editing directly at each editable field, as agreed in D25.

After the owner found the initial screen too similar to live, this revision exposes identification and status information immediately instead of hiding it in a disclosure. The owner requested starting without an immediately visible history chart. Opening the sensor page does not itself trigger a history update; opening history does.

## Review examples

`wireframes/sensor-detail-v2.html` includes registered/history-capable, unregistered, and registered/live-only examples through the preview's optional design controls. Addresses and readings are illustrative. No firmware version or hardware identity is inferred.

The unregistered example and its registration flow were approved in D30. It exposes broadcast name, Bluetooth address, current readings, last reception, exact reported battery level, and history capability. Local editing and removal are absent until registration. Register uses the broadcast name, stays on the sensor page, and reveals the editable local fields; focus moves to local-name editing without opening its dialog automatically.

The registered live-only example was approved in D31 and remains the starting example in this wireframe. This example reports only a temperature and no battery charge, so other measurements and the battery indicator are absent. History support is explicitly unavailable, and there is no history-opening action. Local metadata editing and registration removal remain available. Preview starting states are not app launch behavior.

Sensor information is always visible, without an expansion action. The standalone wireframe implements individual field editing and registration locally. The current connected prototype also links live and history navigation through this page.

## Editing in place

Approved on 2026-09-07 (D25, refined by D26): registered sensors have pencil buttons immediately following the local name and placement text. Each icon matches its text size, with a subtle stroke and a larger 44px touch target. This replaces the fixed-column pencil placement; the live battery icon remains in its own fixed position. Each opens a single text dialog with Cancel and Save; saving updates only that field. Missing placement remains discoverable through `Add placement`. Technical fields are read-only.

Tapping the photo opens image selection directly. An absent image shows `Add photo`; a present image can be removed. Image selection stays within the preview and does not upload the image. The exact removal control placement remains a layout detail for review.

For unregistered sensors, Register sensor saves the broadcast name as the local name and reveals editable fields on the same page. Optional customization happens afterward, with no separate registration form. Preview changes last only until reload. Empty local names currently fall back to the broadcast name, as in earlier experiments; final empty-name handling remains open.

Dialog save/cancel, local-name isolation, optional placement/photo removal, and registration state changes were exercised in JavaScriptCore with a simulated DOM. Native image selection and mobile keyboard layout have not been verified in this iteration.

The displayed compact reading layout, immediately visible information, and separate history action are approved. Registration removal has an agreed consequence: downloaded history is deleted from the app (D29). Its control and confirmation are now implemented in the individual-screen wireframe for review, as described below. Device controls and security enrollment remain outside this screen iteration. The old-reading threshold is now five minutes (D28); reception age is always visible on the sensor page. The unregistered and live-only variants have been reviewed and approved in D30 and D31.

## Removal interaction — review proposal

A secondary `Remove sensor` action appears at the bottom of a registered sensor's page, separated from the history entry by a divider. It opens a confirmation naming the sensor and stating that its local name, placement, photo, and downloaded history will be deleted from the app. It explicitly distinguishes this from erasing history on the physical sensor. Cancel receives initial focus; Cancel or Escape makes no change.

Confirmation removes only the example's local registration metadata and simulated downloaded-history state. The sample remains on screen as an unregistered, still-advertising sensor, with its broadcast identity, measurements, capabilities, and Register action. Re-registering does not restore the deleted example history. The location/wording of the action and this post-removal presentation are proposals for review; the real app's list expiry rules still apply.

The interaction was checked in JavaScriptCore with a simulated DOM: open/cancel, targeted removal, unchanged broadcast identity/capability, cleared local metadata/history state, re-registration, and Escape cancellation. The wireframe does not access or delete real sensor data, and history opening remains a visual placeholder only in the standalone screen; the connected prototype wires it to history.
