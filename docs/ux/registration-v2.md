# Registration and editing screen

Status: superseded by D25 on 2026-09-07. The owner chose individual field editing directly on the sensor page instead of this separate editor. Retained as a record of the earlier experiment; the connected prototype is unchanged.

## Proposed layout

Use the same editor for registration and local metadata editing:

- Compact header with Cancel, a Register sensor / Edit sensor title, and Save.
- A small broadcast-name reference identifies the sensor being edited.
- Name in this app, prefilled from the broadcast name during registration or the saved local name during editing.
- Optional free-text placement.
- Optional photo preview with Choose/Change and Remove actions.

The local-name explanation clarifies that this editor does not change the device's broadcast name. No location grouping, history controls, or device configuration is added.

## Behavior in the screen wireframe

`wireframes/registration-v2.html` allows text editing and local image selection, replacement, and removal. Save stores the draft in preview memory; Cancel restores the last saved version. Brief feedback stands in for returning to the sensor page, which is not linked in this screen-only iteration. Reload discards preview data; image contents are not uploaded.

The optional preview design controls switch between registration and editing examples. Blank names fall back to the broadcast name in this experiment, matching the earlier connected prototype; final empty-name and missing-broadcast-name rules remain undecided.

## Still to review

Header action placement, photo preview size, unsaved-change handling when leaving by other means, and the editing example with an already saved photo. This screen does not remove a registration or delete history.
