# category-icons-and-assets — Implementation Plan

Covers: `docs/detailed-spec.md` §3 (Indicator Categories and Assets), §11 (Asset Spec — excluding
the MVP default-color line, which belongs to `docs/plans/mvp/fake-sleep-overlay/PLAN.md`).

**Scope note**: this plan covers the category icon assets themselves, the `IndicatorCategory`
model, and swapping the overlay's dot for a category icon at render time. It does not cover color
tinting (`docs/plans/other/indicator-color-customization/` — icons are tinted the same way the dot
is), and does not cover multi-icon layout/collision avoidance
(`docs/plans/other/multi-notification-stacking/` — this plan assumes single-icon rendering, same
as MVP's single-dot simplification, just swapping which drawable is shown).

**App-to-category mapping mechanism**: resolved (`docs/QA.md` #13, `docs/DECISIONS.md` §21) —
user-assigned per app via a settings screen, not auto-inferred. §4 below defines a `CategoryMapper`
interface so the calling code (render-time icon selection) doesn't depend on the resolver's
internal implementation.

## Index

1. Category Model
2. Asset Files
3. Fallback Default
4. App-to-Category Mapping
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

Any app the user hasn't (yet) assigned a category to renders as `IndicatorCategory.OTHER`
(`ic_notify_other.xml`) — this is the default for every app until the user visits the settings
screen and assigns one, not an error state.

## 4. App-to-Category Mapping (resolved, QA #13 / DECISIONS §21)

```kotlin
interface CategoryMapper {
    fun resolve(packageName: String): IndicatorCategory
}
```

User-assigned: a settings screen listing monitored apps (installed-app picker via `PackageManager`,
same shape as `docs/plans/other/importance-based-sizing/`'s `KeywordSettingsScreen`), each with a
category picker; backed by a `packageName -> IndicatorCategory` map persisted via DataStore,
write-through on every change (no separate save action). Any app with no assigned category falls
back to `IndicatorCategory.OTHER` (§3) — this includes every app before the user has visited the
settings screen at all, so the feature is usable (all `OTHER`) from first install without requiring
setup first.

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

## Implementation Deviation Notes

- `FakeSleepSessionController.recomputeImportance()`/`recomputeImportanceNow()` were renamed to
  `recomputeIndicatorState()`/`recomputeIndicatorStateNow()` and merged category resolution into
  the same method/pass, rather than adding a sibling `applyCategory()` helper as sketched in §5.
- `applyCategory()` was not implemented as a standalone function; its logic (single-slot
  `mapIndexed` write) lives inline inside `recomputeIndicatorState()` alongside the existing
  `highImportance` write.
- Category for the single MVP slot resolves from `pendingRepo.snapshot().maxByOrNull { it.postTime
  }` (most recently posted pending notification), not any other pending notification.
- The attribution comment location is a per-drawable-file XML comment (in each
  `ic_notify_*.xml`), not a shared `res/drawable/THIRD_PARTY_LICENSES.md` — Android resource
  directory filename rules (lowercase alphanumeric/underscore only) reject a `.md` file in
  `res/drawable/`.
- The five icons use classic Material Icons (not the newer variable-font Material Symbols) path
  data for `call`, `email`, `forum`, `event`, and `notifications`; both icon sets are Apache
  License 2.0 from the same Google repository.
- `InstalledAppInfo`/`loadInstalledApps()` were factored out of `KeywordSettingsScreen.kt` into a
  new shared file `app/src/main/java/com/virtualed/app/ui/InstalledAppPicker.kt`, used by both
  `KeywordSettingsScreen` and `CategorySettingsScreen`, rather than duplicating the app-picker
  logic.
- The settings UI's 5th category chip ("その他") writes `category = null` (clears the assignment)
  rather than writing an explicit `IndicatorCategory.OTHER` value, per §4's "any app with no
  assigned category falls back to OTHER" — this keeps unassigned and explicit-OTHER visually
  merged into one option rather than exposing the null/OTHER distinction in the UI.
- `FakeSleepSessionController` gained a constructor parameter `categoryMapper: CategoryMapper =
  DefaultCategoryMapper` (defaulted, so the existing `VirtuaLedForegroundService` call site is
  unchanged) rather than having `DefaultCategoryMapper` referenced directly inside the recompute
  method body.
