# category-icons-and-assets — Implementation Plan

Covers: `docs/detailed-spec.md` §3 (Indicator Categories and Assets), §11 (Asset Spec — excluding
the MVP default-color line, which belongs to `docs/plans/mvp/fake-sleep-overlay/PLAN.md`).

**Scope note**: this plan covers the category icon assets themselves, the `IndicatorCategory`
model, and swapping the overlay's dot for a category icon at render time. It does not cover color
tinting (`docs/plans/other/indicator-color-customization/` — icons are tinted the same way the dot
is), and does not cover multi-icon layout/collision avoidance
(`docs/plans/other/multi-notification-stacking/` — this plan assumes single-icon rendering, same
as MVP's single-dot simplification, just swapping which drawable is shown).

**Open question blocking the app-to-category mapping mechanism**: see `docs/QA.md` #13. Not
decided: whether category assignment is user-assigned per app (a settings screen) or
auto-inferred (a hardcoded package-name table). §4 below defines a `CategoryMapper` interface so the
calling code (render-time icon selection) does not need to change regardless of which answer QA #13
resolves to — only the resolver's internal implementation depends on that answer.

## Index

1. Category Model
2. Asset Files
3. Fallback Default
4. App-to-Category Mapping (open question, QA #13)
5. Render-Time Integration
6. Cross-Feature Touchpoints

## 1. Category Model

```kotlin
enum class IndicatorCategory(val assetName: String) {
    PHONE("ic_notify_phone"),
    MAIL("ic_notify_mail"),
    SNS("ic_notify_sns"),
    CALENDAR("ic_notify_calendar"),
    OTHER("ic_notify_other"),
}
```

## 2. Asset Files

Per `docs/detailed-spec.md` §11:
- Vector Asset (Android Vector Drawable XML) preferred, one per category, placed at
  `res/drawable/ic_notify_{phone,mail,sns,calendar,other}.xml`.
- PNG fallback (only if a given source icon isn't available as a vector): fully transparent
  background, all-white (`#FFFFFF`) foreground, tinted via `ImageView.setColorFilter()` at draw
  time — same tinting mechanism `docs/plans/other/indicator-color-customization/` uses for the dot.
  Density buckets if PNG fallback is used: `drawable-mdpi` (24x24), `drawable-hdpi` (36x36),
  `drawable-xhdpi` (48x48), `drawable-xxhdpi` (72x72), `drawable-xxxhdpi` (96x96).
- Source: a freely-licensed icon set (e.g. Material Symbols, Apache 2.0) per
  `docs/detailed-spec.md` §11 — interim placeholder art, not custom-commissioned. Attribution/
  license file recorded alongside the asset (e.g. `res/drawable/THIRD_PARTY_LICENSES.md` or
  project-root `NOTICE` file — exact location not yet decided; a minor packaging detail, not a
  product decision, can be settled at implementation time).

## 3. Fallback Default

Any app not (yet) resolved to a specific category renders as `IndicatorCategory.OTHER`
(`ic_notify_other.xml`) — this applies regardless of which answer QA #13 resolves to, including
the case where the mapping mechanism itself isn't implemented yet (i.e., `CategoryMapper` can ship
as an always-`OTHER` stub before the real mapping mechanism lands, without blocking other features
from integrating against the interface).

## 4. App-to-Category Mapping (open question, QA #13)

```kotlin
interface CategoryMapper {
    fun resolve(packageName: String): IndicatorCategory
}
```

Two candidate implementations, either of which can sit behind this same interface once QA #13 is
answered:
- **User-assigned**: a settings screen listing monitored apps, each with a category picker; backed
  by a `packageName -> IndicatorCategory` map persisted via DataStore.
- **Auto-inferred**: a hardcoded `packageName -> IndicatorCategory` lookup table (e.g. known dialer/
  email/messaging/calendar package names), falling back to `OTHER` for anything not listed.

Until QA #13 is resolved, ship a stub `CategoryMapper` that always returns `IndicatorCategory.OTHER`
(§3) so this feature's rendering integration (§5) can be built and tested independent of the
mapping decision.

## 5. Render-Time Integration

Sets a slot's `category` field on `FakeSleepIndicatorUiState.slots`
(`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7), replacing the plain dot (`category = null`) with
a resolved `IndicatorCategory`:

```kotlin
fun applyCategory(uiState: FakeSleepIndicatorUiState, category: IndicatorCategory) {
    // Before docs/plans/other/multi-notification-stacking/ exists there is exactly one slot (the
    // MVP default), so this sets that slot's category directly:
    val slots = uiState.slots.value
    uiState.slots.value = slots.mapIndexed { index, slot ->
        if (index == 0) slot.copy(category = category) else slot
    }
    // tint applied by docs/plans/other/indicator-color-customization/, not this plan; the actual
    // icon/dot lookup from `category` happens in FakeSleepActivity's SlotIcon composable
    // (docs/plans/mvp/fake-sleep-overlay/PLAN.md §7), not here
}
```

Which single notification's category is shown (when multiple are pending) follows the same
single-indicator simplification MVP already uses — resolving to one category and rendering one
icon is a placement/collision question owned by
`docs/plans/other/multi-notification-stacking/PLAN.md`, not this plan. Once that plan lands, it
constructs the whole `slots` list itself (each slot's `category` already resolved via
`CategoryMapper`), superseding this single-slot `applyCategory()` helper.

## 6. Cross-Feature Touchpoints

- Writes `docs/plans/mvp/fake-sleep-overlay/`'s `FakeSleepIndicatorUiState.slots[].category` (§7)
  — does not define a new overlay view.
- Icon tint color comes from `docs/plans/other/indicator-color-customization/`'s shared `tintColor`
  state once built; until then, `FakeSleepIndicatorUiState.tintColor`'s default (white) applies,
  same as the MVP dot default.
- `docs/plans/other/multi-notification-stacking/` decides how to place/collapse multiple
  simultaneous category icons; this plan only defines the assets and single-slot render path.
- `PendingNotificationInfo.packageName` (`docs/plans/mvp/notification-detection-dot-indicator/`)
  is the `CategoryMapper.resolve()` input.
