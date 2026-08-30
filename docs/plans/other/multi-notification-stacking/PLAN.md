# multi-notification-stacking — Implementation Plan

Covers: `docs/detailed-spec.md` §9 — the visual layer only (multiple simultaneous icons up to 4,
collision avoidance/grid placement, "other" collapse beyond 4, per-app count badge option).

**Scope note**: the text-based peek-preview aggregation (§9's other half — "the first tap reveals a
list of *all* currently pending notifications... regardless of which icon was tapped") is already
fully covered by `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §4 — **not duplicated here**.
This plan only replaces the single-dot/single-icon rendering used during the **Indicator Shown**
state (`docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §2) with a multi-icon layout. No
coordinate hit-testing is added — tap handling remains exactly as
`docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §3 already defines it (position-independent).

## Index

1. Module Structure
2. Data Structures
3. Grouping and Overflow Logic
4. Layout (Grid Placement)
5. Count Badge Option
6. Render Integration
7. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.overlay.IndicatorSlotBuilder` — groups pending notifications into slots and
  writes the result to `FakeSleepIndicatorUiState.slots`
  (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7). This plan does not define its own parallel slot
  data class or a `View`-based container — see §2.
- `com.virtualed.app.data.CountBadgeSettings` — DataStore-backed toggle, default `false`.

## 2. Data Structures

`IndicatorSlotUiState` is defined once, by `docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7 — this
plan does not redefine it:

```kotlin
data class IndicatorSlotUiState(
    val category: IndicatorCategory? = null,
    val count: Int = 1,
    val highImportance: Boolean = false,
)
```

A collapsed "other" slot (§3) is an ordinary `IndicatorSlotUiState` with
`category = IndicatorCategory.OTHER` — the same value any single OTHER-category app would already
render as, distinguished only by its (summed) `count` (see `docs/DECISIONS.md` §20 for why this
plan does not carry its own separate overflow flag).

## 3. Grouping and Overflow Logic

Single app with multiple notifications collapses to one slot (no count badge unless enabled, §5).
Beyond 4 distinct-app slots, the remainder collapse into one "other" overflow slot:

```kotlin
object IndicatorSlotBuilder {
    const val MAX_SLOTS = 4

    fun build(
        pending: List<PendingNotificationInfo>,
        categoryMapper: CategoryMapper,
        keywordsByApp: (String) -> List<String>,
    ): List<IndicatorSlotUiState> {
        val perApp = pending.groupBy { it.packageName }.map { (pkg, list) ->
            IndicatorSlotUiState(
                category = categoryMapper.resolve(pkg),
                count = list.size,
                highImportance = list.any { info ->
                    val keywords = keywordsByApp(pkg)
                    keywords.isNotEmpty() &&
                        keywords.any { matchesKeyword("${info.title.orEmpty()} ${info.text.orEmpty()}", it) }
                },
            )
        }
        if (perApp.size <= MAX_SLOTS) return perApp
        val kept = perApp.take(MAX_SLOTS - 1)
        val overflow = perApp.drop(MAX_SLOTS - 1)
        return kept + IndicatorSlotUiState(
            category = IndicatorCategory.OTHER,
            count = overflow.sumOf { it.count },
            highImportance = overflow.any { it.highImportance },
        )
    }
}
```

**Implementation note**: `com.virtualed.app.importance.ImportanceResolver.isHighImportance()` (added by
`importance-based-sizing`) currently returns one aggregate `Boolean` for the whole pending list — its
own doc comment already labels this an MVP-scoped simplification for the single-indicator case. This
plan needs per-app importance instead, so `IndicatorSlotBuilder.build()` above calls the already-public
`matchesKeyword()` (same file) directly per app rather than adding a new `ImportanceResolver` method —
no signature change to `ImportanceResolver` itself is required. `FakeSleepSessionController.
recomputeIndicatorState()`'s existing whole-snapshot `ImportanceResolver.isHighImportance()` call (used
only for `blinkIntervalMs`, which stays a single group-wide value per §7 below) is unaffected and does
not need to change.

## 4. Layout (Grid Placement)

`docs/detailed-spec.md` §9 offers either grid split or random-placement collision detection as
acceptable overlap-avoidance approaches. This plan uses a fixed 2x2 grid (deterministic, no
collision math needed): each of the up to 4 slots in `FakeSleepIndicatorUiState.slots` is placed in
one quadrant, indexed 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right, relative to the slot
group's current anchor (`FakeSleepIndicatorUiState.indicatorOffset`,
`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7).

```kotlin
@Composable
private fun IndicatorGroup(uiState: FakeSleepIndicatorUiState) {
    // Base structure (offset/visibility/blink handling) defined by
    // docs/plans/mvp/fake-sleep-overlay/PLAN.md §7; this plan supplies the per-slot quadrant
    // placement once more than one slot is present.
    Box(modifier = Modifier.offset { uiState.indicatorOffset.value }) {
        uiState.slots.value.forEachIndexed { index, slot ->
            SlotIcon(slot = slot, modifier = Modifier.quadrantOffset(index))
        }
    }
}

private fun Modifier.quadrantOffset(index: Int): Modifier = this.offset(
    x = if (index % 2 == 0) 0.dp else QUADRANT_GAP_DP,
    y = if (index < 2) 0.dp else QUADRANT_GAP_DP,
) // fixed 2x2 grid offset within the group's bounding box; a no-op when only one slot is present
```

`docs/plans/other/burn-in-protection/`'s reposition loop moves `indicatorOffset` — the group's
single anchor — as a whole; the quadrant layout inside the group stays fixed relative to that
anchor, unaffected by this plan (see that plan's §5).

No coordinate hit-testing is added by this layout — tap handling remains position-independent,
exactly as `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §3 already defines it.

## 5. Count Badge Option

```kotlin
object CountBadgeSettings {
    const val DEFAULT_ENABLED = false
    suspend fun getEnabled(context: Context): Boolean
    suspend fun setEnabled(context: Context, enabled: Boolean)
}
```

```kotlin
@Composable
private fun SlotIcon(slot: IndicatorSlotUiState, modifier: Modifier) {
    // Renders slot's category icon (or plain dot if category == null), tinted by
    // FakeSleepIndicatorUiState.tintColor and blinking per FakeSleepIndicatorUiState.blinkIntervalMs
    // (docs/plans/other/category-icons-and-assets/PLAN.md §5,
    // docs/plans/other/indicator-color-customization/PLAN.md §5,
    // docs/plans/other/importance-based-sizing/PLAN.md §6).
    if (CountBadgeSettings.enabledCached && slot.count > 1) {
        // overlay a small count badge (e.g. a Text composable) in a corner of this slot
    }
}
```

Settings toggle: "Show notification count badge," default OFF (per `docs/detailed-spec.md` §9's
"no count badge by default").

## 6. Render Integration

```kotlin
fun updateIndicatorSlots(
    uiState: FakeSleepIndicatorUiState,
    pending: List<PendingNotificationInfo>,
    categoryMapper: CategoryMapper,
) {
    uiState.slots.value = IndicatorSlotBuilder.build(pending, categoryMapper) { pkg ->
        KeywordRegistry.keywordsCache[pkg] ?: emptyList()
    }
}
```

Recomputed via `IndicatorSlotBuilder.build()` whenever
`docs/plans/mvp/notification-detection-dot-indicator/`'s `PendingNotificationsRepository` changes
while `OverlayUiState.INDICATOR_SHOWN` is active — same recompute trigger point already established
by `docs/plans/other/importance-based-sizing/PLAN.md` §6 for blink/size.

## 7. Cross-Feature Touchpoints

- Writes `docs/plans/mvp/fake-sleep-overlay/`'s `FakeSleepIndicatorUiState.slots` (§7), replacing
  that plan's single-slot MVP default and
  `docs/plans/other/category-icons-and-assets/`'s single-slot `applyCategory()` helper — both
  become the `MAX_SLOTS == 1` degenerate case once this plan lands.
- Does not alter `docs/plans/mvp/dismiss-and-peek-interaction/`'s tap-handling or preview-text
  logic — both remain position-independent and app-agnostic exactly as already specified there.
- `docs/plans/other/burn-in-protection/`'s reposition loop already moves the whole slot group via
  the single `indicatorOffset` anchor (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7) — no change
  needed on that plan's part when this plan lands; the quadrant layout (§4 above) stays fixed
  relative to wherever that anchor currently is.
- `docs/plans/other/importance-based-sizing/`'s effect, once combined with this plan, applies
  differently per property: the 1.5x scale is per-slot (only the slot(s) whose notifications match
  a registered keyword enlarge), but blinking is **not** per-slot — `blinkIntervalMs` is a single
  value shared across the whole group (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7's
  synchronized-blink decision), so once any slot is high-importance, every currently-shown slot
  blinks together, not just the matching one.

## Implementation Deviation Notes

- **§4**: `IndicatorGroup` keeps its pre-existing signature (individual `MutableState<T>` parameters —
  `offsetState`, `tintColorState`, `slotsState`, `blinkIntervalMsState`), not the single
  `uiState: FakeSleepIndicatorUiState` parameter this plan's sketch used. That call-site convention
  predates this plan (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7's `FakeSleepContent`) and this
  plan does not change it. `quadrantOffset()` is implemented exactly as sketched, as a private
  `Modifier` extension in `FakeSleepActivity.kt`.
- **§6**: no standalone `updateIndicatorSlots()` function was added. `IndicatorSlotBuilder.build()` is
  called directly from `FakeSleepSessionController.recomputeIndicatorState()` (the same existing
  recompute function `importance-based-sizing`/`category-icons-and-assets` already write into), rather
  than through an intermediate wrapper — consistent with how that function already inlines both of
  those features' logic rather than delegating to separate per-feature update functions.
- **§5**: the count badge is a small red circle (top-end corner of the slot, `Text` in white) rather
  than a specific visual spec — `docs/detailed-spec.md` §9 does not mandate exact badge styling, only
  that it exists and defaults off; concrete look is an implementation-time judgment call, same as this
  project's other cosmetic PLAN.md sketches (e.g. `indicator-color-customization`'s preset palette).
