# Connected UX prototype

Status: first connected version for product owner review. No Android application code is changed.

## Entry point and sources

Open `wireframes/prototype.html` in the conversation preview. This combines the reviewed `live.html` and `history.html` fragments with `navigation-shell.html`, which owns navigation and temporary sensor metadata.

Rebuild after changing a source fragment:

```text
python3 docs/ux/wireframes/build_prototype.py
```

## Implemented review paths

- Tap a registered history-capable live card → individual landscape history → back to live.
- Open general History → overview of compatible registered sensors → individual history → back to overview → back to live.
- Open the card's overflow menu → edit local details or read sensor information. Live-only sensors expose the same secondary menu without a history action.
- Tap an unregistered sensor or choose Register in its menu → prefilled registration editor → save → registered live card.
- Edit name, optional placement, and optional image → save updates the live card and local history labels. Cancel leaves the existing metadata unchanged.
- View sensor information to distinguish the unchanged broadcast name from the edited local name.

History retains the fixed cursor, gestures, and comparison from the existing wireframe. Simulated automatic updating reports an unreachable outdoor sensor and retains its illustrative historical data. A newly registered demo sensor appears in the general history overview.

## Prototype choices for review

- A top-level History button, card overflow menu, and a compact actions dialog provide navigation; their exact appearance is not yet a separate approved design decision.
- Blank local names fall back to the broadcast name in this experiment. Final name validation is still open.
- Local images can be chosen for preview, replaced, or removed. They are read in the page and are not uploaded.
- Metadata lives only in memory until reload. Demonstration history is preloaded even for the initially unregistered sensor.
- Sensor information contains only relevant known demo facts, not invented firmware versions or real addresses.

## Verification

Browser walkthrough on 2026-09-05 verified direct live-to-history return, overview/detail return nesting, registration with a changed name and placement, transition into the registered list, unchanged broadcast name, cancellation of an edit, and access to the secondary menu for a live-only sensor. Live and landscape detail layouts were visually inspected. The save action was adjusted to work inside the preview sandbox.

Still unverified: native phone rotation, actual BLE updates, touch behavior on hardware, and local file-picker interaction. No real device data is used. The prototype does not implement sensor removal, history deletion, device controls, or security enrollment; their semantics remain unresolved design work.
