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
4. HEX Input Validation (Removed)
5. Render-Time Tint Application
6. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.IndicatorColorSettings` — DataStore-backed persisted color.
- `com.virtualed.app.ui.ColorSettingsScreen.kt` — Compose settings screen (palette swatches + HEX
  field).
- Writes `FakeSleepIndicatorUiState.tintColor`
  (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7) — does not define a new indicator view; since
  `tintColor` is a single shared `MutableState` consumed by every rendered slot, this plan needs no
  per-slot or per-view tint-application helper.

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

Compose screen: a row of exactly 5 preset color swatches (fixed list, exact colors an
implementation-time visual-design detail, not a product decision), with a live preview swatch
showing the currently-selected color. Selecting a preset swatch writes its value directly. No
free-form HEX input (see §4 — removed).

## 4. HEX Input Validation (Removed)

**Revision (2026-08-30), see `docs/DECISIONS.md` §23**: the HEX text field and the wider
(originally 8-swatch) preset palette described in earlier versions of this plan are removed.
Rationale: once color no longer needs to vary per icon (`docs/DECISIONS.md` §22 chose
source-app-icon display over per-category color), a single shared indicator color doesn't need
free-form input or 8 choices — and the wider row/text-field combination risked the same
wrap/scroll layout inconsistency the project's own `category-icons-and-assets` bug hunt already hit
for a comparable row (`docs/HANDOFF.md` §15, item 3).

This is a revision to an already-shipped feature (merged PR #12), not new-feature work — the
implementing session needs to update `ColorPickerSection` (`MainActivity.kt`) to drop the HEX
`TextField` and reduce the swatch list to 5, and remove `parseHexColorOrNull`'s UI call site
(`com.virtualed.app.data.IndicatorColorSettings.kt`; the function itself can be deleted unless kept
for validating a pre-existing persisted value, an implementation-time judgment call). A previously
HEX-set custom color already persisted on a user's device before this revision ships will simply
render as whatever `IndicatorColorSettings.getColorArgb()` returns — no migration needed, since the
persisted value is still a plain ARGB int regardless of how it was originally entered; the settings
UI will just no longer show a swatch highlighted as "selected" for a value outside the 5 presets,
which is cosmetic only.

## 5. Render-Time Tint Application

```kotlin
fun applyIndicatorColor(uiState: FakeSleepIndicatorUiState, colorArgb: Int) {
    uiState.tintColor.value = Color(colorArgb)
}
```

Called once at session start (reading the persisted color) and again whenever the settings screen
saves a new value while a session is active — writing `tintColor` recomposes every currently
rendered slot immediately, since `FakeSleepActivity`'s `IndicatorGroup`/`SlotIcon` composables
(`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7) all read this single shared state. Because there is
exactly one `tintColor` field for the whole slot group, this plan needs only one call site, not a
separate one per render path (plain dot, category icon, or per-slot in a multi-icon layout) — it
applies uniformly across indicator display modes by construction, per `docs/detailed-spec.md` §19.

## 6. Cross-Feature Touchpoints

- Writes `docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s `FakeSleepIndicatorUiState.tintColor` (§7),
  replacing that plan's hardcoded white default.
- Applies to `docs/plans/other/category-icons-and-assets/`'s icons and
  `docs/plans/other/multi-notification-stacking/`'s per-slot icons identically, with no extra
  wiring needed on their part — they render whatever `IndicatorSlotUiState`s are in `slots`, all
  tinted by the one shared `tintColor` value.

## Implementation Deviation Notes

- Settings UI implemented as a `ColorPickerSection` composable inline in `MainSwitchScreen`
  (`MainActivity.kt`), not as a separate `com.virtualed.app.ui.ColorSettingsScreen.kt` file — matches
  this codebase's established pattern (DND toggle, battery threshold slider), since there is no
  navigation component/route in this app.
- `parseHexColorOrNull` lives in `com.virtualed.app.data.IndicatorColorSettings.kt` (top-level
  function, not inside a UI file), alongside the `IndicatorColorSettings` object.
- `FakeSleepActivity.onCreate()` loads the persisted color via `androidx.lifecycle.lifecycleScope`
  (the `androidx.lifecycle:lifecycle-runtime-ktx` dependency was already present in
  `app/build.gradle.kts`, unused elsewhere), not a manually-managed `CoroutineScope`.
