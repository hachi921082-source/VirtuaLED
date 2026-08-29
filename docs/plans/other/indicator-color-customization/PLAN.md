# indicator-color-customization — Implementation Plan

Covers: `docs/detailed-spec.md` §19 (Indicator Color Customization).

**Scope note**: this plan covers only the color picker/HEX-input settings UI, persistence, and
applying the persisted color as a tint at draw time. It **replaces**
`docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s hardcoded white (`#FFFFFF`) default once implemented —
that plan's default remains the fallback value here (§2), not a separately-maintained constant.

## Index

1. Module Structure
2. Data Structure and Default
3. Settings UI
4. HEX Input Validation
5. Render-Time Tint Application
6. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.IndicatorColorSettings` — DataStore-backed persisted color.
- `com.virtualed.app.ui.ColorSettingsScreen.kt` — Compose settings screen (palette swatches + HEX
  field).
- Extends `OverlayIndicatorView`/`MultiIndicatorContainerView` render paths
  (`docs/plans/mvp/fake-sleep-overlay/PLAN.md`,
  `docs/plans/other/multi-notification-stacking/PLAN.md`) with a shared tint-application helper —
  does not define a new indicator view.

## 2. Data Structure and Default

```kotlin
object IndicatorColorSettings {
    const val DEFAULT_COLOR_ARGB = 0xFFFFFFFF.toInt() // white — same default as
        // docs/plans/mvp/fake-sleep-overlay/PLAN.md §5's hardcoded dot color, before this feature exists
    // DataStore Preferences: Int entry "indicator_color_argb", default DEFAULT_COLOR_ARGB
    suspend fun getColorArgb(context: Context): Int
    suspend fun setColorArgb(context: Context, colorArgb: Int)
}
```

## 3. Settings UI

Compose screen: a row of preset palette swatches (e.g. white, red, orange, yellow, green, cyan,
blue, purple — a fixed preset list, exact colors an implementation-time visual-design detail, not a
product decision) plus a HEX text field for free-form input, with a live preview swatch showing the
currently-selected color. Selecting a preset swatch writes its value directly; typing in the HEX
field writes on successful parse (§4).

## 4. HEX Input Validation

```kotlin
fun parseHexColorOrNull(input: String): Int? =
    runCatching { Color.parseColor(if (input.startsWith("#")) input else "#$input") }.getOrNull()
```

Accepts both `#RRGGBB` and `#AARRGGBB` (via `android.graphics.Color.parseColor`'s existing support
for both forms). Invalid input (parse failure) leaves the persisted color unchanged and shows an
inline validation error — no persistence write on invalid input.

## 5. Render-Time Tint Application

```kotlin
fun applyIndicatorTint(view: ImageView, colorArgb: Int) {
    view.setColorFilter(colorArgb, PorterDuff.Mode.SRC_IN)
}
```

Called from every indicator render path, replacing each one's previously-hardcoded white:
- `OverlayIndicatorView.showDot()` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5)
- `OverlayIndicatorView.showCategoryIcon()` (`docs/plans/other/category-icons-and-assets/PLAN.md` §5)
- `MultiIndicatorContainerView.render()`, per slot (`docs/plans/other/multi-notification-stacking/PLAN.md` §4)

The persisted color value is read once at session start and cached in memory (refreshed on
settings-screen save), so render calls don't need a suspend read on every frame/update. Applies
uniformly — one color setting for all indicator display modes (simple dot and per-category icons),
per `docs/detailed-spec.md` §19's "applies uniformly across indicator display modes."

## 6. Cross-Feature Touchpoints

- Replaces `docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s hardcoded white default — that plan's
  `OverlayIndicatorView` render methods call `applyIndicatorTint()` once this feature exists.
- Applies to `docs/plans/other/category-icons-and-assets/`'s icons and
  `docs/plans/other/multi-notification-stacking/`'s per-slot icons identically.
