# Android visual direction

Status: the complete dark Petrol live-screen treatment was approved on 2026-09-07 (D36), following the Android-native direction and palette selection in D33–D35. Created after prototype checkpoint `776c94a`.

## Reference and implementation direction

Use the Material 3 design system and Compose components as the platform reference. The native implementation uses the approved fixed Petrol palette, defaults to Dark, and offers Light and System appearance choices in Settings. Wallpaper-derived dynamic color is not enabled, so the approved palette remains predictable. Reference: [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).

Material 3 Expressive extends M3 rather than requiring an unrelated visual language. Its current design resources are listed on the [official Material site](https://m3.material.io/). For this app, prioritize readable readings and familiar controls over decorative effects.

Prefer Material component defaults and theme roles in Android. Use native Material Symbols for final icons; the browser preview uses the supplied Lucide placeholders. Reference: [Compose Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3).

## Approved live-screen treatment

| Element | Treatment in the style study |
| --- | --- |
| App bar | Live title and a tonal History action. |
| Sensor containers | Filled tonal cards by default; outlined cards as the second Material variant. |
| Readings | Equal 24px numeral size, smaller units, tabular figures. |
| Sensor names | 16px medium weight; optional placement is secondary text. |
| Status | Small fixed top-right battery icon; reception age only on old readings. |
| Color | Tonal surfaces and one restrained accent family. |
| Shape | 12px card corners, fully rounded action treatment. |

The examples preserve the approved information and navigation structure. Neither alternative removes labels or moves management into a new navigation section.

The initial blue-based palette was rejected by the owner. The study offers Graphite, Petrol, and Amber palettes, with dark appearance selected by default. Petrol was approved and translated into native Material color roles. Reference: [Android color guidance](https://developer.android.com/design/ui/mobile/guides/styles/color).

## Preview and scope

`wireframes/live-material.html` is a static style study for the live screen. Preview controls switch Filled/Outlined, Graphite/Petrol/Amber, and System/Light/Dark. The compact `wireframes/dark-palettes.html` comparison shows all three dark palettes together. It does not alter the connected prototype or Android code, and browser pixels are not a claim of pixel-identical native rendering.

The current app already uses Compose Material 3, with manually defined light/dark colors. The checked-in dependency catalog notes an SDK constraint on newer BOM versions. Selecting and validating implementation dependencies is a later step; no dependency or SDK upgrade is made for this style study.

Native font scaling, controls, system insets, rotation and contrast are covered by the implementation review. See [implementation plan](implementation-plan.md) for results; the browser study itself is not native validation.

## Preview checks

Filled cards were visually inspected in light and dark appearance; the outlined variant was inspected at a 360px container width. The checked body/secondary/action text color pairs have a minimum contrast ratio of 7.25:1 in the illustrative palettes. This does not replace native accessibility and dynamic-color testing.

## Dark palette review

The owner prefers a dark appearance and rejected the pale blue tint. Graphite, Petrol, and Amber were compared using the same content and structure. Petrol was selected; the other palettes remain available only for reference. The owner selected Petrol in dark appearance (D35). It is the initial palette in the full live-screen study and the native app; the connected prototype remains a wireframe.
