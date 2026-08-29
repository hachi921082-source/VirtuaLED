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
