# Smartphone history overview proposal

Status: the core overview/detail direction is approved by the product owner. Detailed controls, chart treatment, and ancillary behavior below remain proposals for wireframe review, not implementation tasks.

Related requirements: R4, R5, R7, R8, R10, R12. Related scenarios: S2 and S3.

## Approved approach

Use a vertically scrolling overview with one compact chart per sensor, all showing the same selected period and measurement. Open a sensor for detailed inspection and offer comparison with another sensor there.

This separates two tasks: surveying conditions across the boat, and comparing specific sources closely. The proposal avoids requiring the user to interpret many overlapping curves and a large legend on a phone. It also avoids placing temperature, humidity, and pressure into each compact card at once.

## Overview content

1. A shared time navigator with explicit selected dates. The owner rejected fixed period selection as the primary interaction and requested immediate movement through time. The revised wireframe proposes a draggable selected window: move its center to pan, move its edges to resize, and use `All history` to restore full coverage. This exact control is awaiting evaluation. Do not infer an absence start from the last download.
2. A shared measurement selector, such as temperature, humidity, or pressure.
3. History update status for the automatic update of all compatible saved sensors on overview entry. Existing downloaded data remains consultable without requiring another download.
4. Full-width sensor cards in a vertical list. Each contains the sensor name, a compact trend chart, recorded minimum and maximum with units, and coverage information when incomplete.

Sensors without the selected measurement should be distinguished from sensors that support it but have no downloaded samples. Sensors without internal history must not acquire a synthetic history from live readings. The exact presentation of these states remains to be designed.

Confirmed in D21: opening individual history updates that sensor; opening the overview updates all compatible saved sensors. Existing data remains viewable and is retained when sensors cannot be reached. See [navigation](navigation.md#automatic-update-on-entry). The wireframe no longer has an explicit download button and now opens in the overview to demonstrate a simulated automatic update and partial result.

## Detail interaction

Tap a card to open a larger chart while retaining the selected period and measurement. Select or zoom into a shorter interval to inspect values and times. A comparison action can add another compatible sensor, such as outdoors versus a cabin. Start with a small comparison set; its exact limit is undecided.

### Detail space revision

Owner feedback: the first detail view devoted too much space to controls and did not provide a sufficiently larger chart than the overview.

The first space revision kept the same 740px phone frame for both views, with a compact header and the timeline below the chart. It has now been superseded in the wireframe by the landscape proposal below.

This layout is a proposal awaiting review. Validate actual usable plot area and touch interaction on small phones; increasing the overall screen height is not a substitute for recovering space within the detail view.

### Landscape detail proposal

The owner proposed forcing landscape orientation for detail, extending the chart horizontally, and reducing controls to essential overlays. This is interpreted as applying to historical detail, not the entire app; no production orientation behavior has been changed.

The current wireframe models a 740 × 390 landscape detail and a 390 × 740 portrait overview. The detail opens directly for review. A compact overlay at the top contains back navigation, the sensor name, and measurement selection. The initial landscape experiment used period and comparison buttons; these have been superseded by the gesture revision below. The chart keeps its labels outside the persistent header controls. Comparison uses a dashed second curve and a compact legend.

The proposal preserves the selected period and measurement when returning to the overview. Device rotation and platform orientation locking are not implemented by this mockup. Evaluate whether the landscape benefit justifies requiring rotation before finalizing the navigation behavior.

### Gesture and sample inspection revision

Initial owner proposal: navigate time with gestures, use a small comparison icon over the plot, and inspect samples with a vertical cursor. After evaluating tap accuracy, the owner approved a permanently centered cursor instead of tap positioning (D11).

The revised detail implements:

- A permanently centered vertical cursor and a stable readout of its sample timestamp, temperature, humidity, and pressure. Neither requires a tap to appear.
- One-finger drag moves the data beneath the cursor and updates the selected sample. The demo snaps the central time to the nearest daily sample.
- Two-finger pinch zooms around the central time, retaining the selected sample. The desktop preview also supports centered wheel/trackpad zoom and horizontal wheel panning.
- The center is bounded by the first and last sample, while the visible time window may extend beyond the data. Empty space allows both endpoint samples to reach the cursor. Curves stop at actual demo samples; no extra measurements are created in the padding.
- A small overlaid comparison icon with an accessible name. In this experiment it toggles a fixed second source; a real sensor chooser remains to be designed.
- No visible time navigation controls in detail. The existing timeline remains in the portrait overview.

Inspection in this mockup uses generated daily samples at 00:00 UTC and can zoom down to a one-day interval. This demonstrates gesture and selection behavior, not production sampling resolution. All demo sensors share timestamps. For real asynchronous samples, specify matching rules and show actual timestamps or missing values; do not present interpolated values as exact recorded samples. Timezone presentation is also undecided.

The fixed-center interaction is approved; its detailed presentation remains under review. Touch discoverability, alternatives for assistive technology, day/hour navigation with actual data, and a convenient way to restore full history within detail need evaluation. Basic gesture/state logic was exercised in JavaScriptCore with a simulated DOM, including fixed cursor position, persistent readout, centered wheel/pinch zoom, and access to both endpoint samples. Native touch handling and visual layout have not been verified on a physical phone.

When returning to the overview, the demo moves any padded range back inside available history while retaining its duration. This edge behavior is a prototype choice to review, not a separate approved product decision.

Returning to the overview should preserve the user's overview filters and scroll position.

## Chart integrity and readability

- Use the same time range across overview cards so events can be related in time.
- Make value scales explicit. Do not imply that identical chart heights represent equal values if ranges differ. Environmental and technical temperatures may require different ranges; scale behavior needs evaluation in the wireframe/prototype.
- Long-period reduction must retain visible extremes, for example with a min/max envelope; a mean-only trend could hide short events that motivated this scenario.
- Show coverage gaps rather than drawing a continuous trend through missing data. Do not assign invented dates to undated samples.
- Label minima and maxima as observed values within available data, not guaranteed extrema for an incompletely recorded absence.
- Provide readable values and labels; do not rely only on color or precise touch gestures to communicate chart information.

## Potential highlights

Critical-period highlights remain an optional candidate feature. Do not invent risk thresholds or equate a recorded extreme with damage. First establish the overview and detail interaction; define criteria and explanations separately if the owner chooses to pursue highlights.

## Tradeoffs to validate

- The owner must scroll when there are many sensors; the overview does not promise to show the whole boat on one phone screen.
- Viewing one measurement at a time requires switching to review other measurements, but leaves each chart readable.
- Compact cards support surveying; direct quantitative comparison is better assessed in the detail chart.
- Test with both environmental and technical temperature ranges, a multi-month period, incomplete coverage, and partial bulk-download success.

## Next review

Review the first interactive phone wireframe in `wireframes/history.html`, using illustrative data. Evaluate overview density and the transition to a single large detail chart before deciding finer chart interactions or visual styling.

The revised wireframe supports opening a sensor, moving/resizing the overview time window, gesture navigation and sample inspection in landscape detail, switching among three measurements, and toggling a second sensor. Overview timeline handles support mouse/touch dragging and arrow keys; Shift plus an arrow moves seven days. Overview entry simulates an automatic update followed by partial success; it does not contact devices. It does not yet implement sub-day inspection, scroll restoration, min/max envelopes, or coverage gaps. Its daily curves are generated illustrative data, not sensor readings or the final chart rendering strategy.

For this control experiment, the time domain is March–August 2026. Overview resize handles use a minimum span of 35 days; detail gestures can narrow this to one day. These are prototype limitations, not product requirements. Further design must address comfortable touch targets and fine day/hour inspection across multi-year archives. The vertical scales remain stable while panning so changes in apparent amplitude do not result from automatic rescaling.
