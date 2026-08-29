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

- `com.virtualed.app.overlay.IndicatorSlot` — data model for one rendered icon slot.
- `com.virtualed.app.overlay.IndicatorSlotBuilder` — groups pending notifications into slots.
- `com.virtualed.app.overlay.MultiIndicatorContainerView` — new `View` (`FrameLayout` holding up to
  4 `ImageView`s), replaces the single dot/icon `View` used by
  `docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5 during Indicator Shown.
- `com.virtualed.app.data.CountBadgeSettings` — DataStore-backed toggle, default `false`.

## 2. Data Structures

```kotlin
data class IndicatorSlot(
    val category: IndicatorCategory, // docs/plans/other/category-icons-and-assets/, or OTHER/dot if not yet built
    val count: Int,       // notifications collapsed into this slot
    val isOverflow: Boolean, // true only for the collapsed 5th+ slot
)
```

## 3. Grouping and Overflow Logic

Single app with multiple notifications collapses to one slot (no count badge unless enabled, §5).
Beyond 4 distinct-app slots, the remainder collapse into one "other" overflow slot:

```kotlin
object IndicatorSlotBuilder {
    const val MAX_SLOTS = 4

    fun build(pending: List<PendingNotificationInfo>, categoryMapper: CategoryMapper): List<IndicatorSlot> {
        val perApp = pending.groupBy { it.packageName }.map { (pkg, list) ->
            IndicatorSlot(category = categoryMapper.resolve(pkg), count = list.size, isOverflow = false)
        }
        if (perApp.size <= MAX_SLOTS) return perApp
        val kept = perApp.take(MAX_SLOTS - 1)
        val overflowCount = perApp.drop(MAX_SLOTS - 1).sumOf { it.count }
        return kept + IndicatorSlot(category = IndicatorCategory.OTHER, count = overflowCount, isOverflow = true)
    }
}
```

## 4. Layout (Grid Placement)

`docs/detailed-spec.md` §9 offers either grid split or random-placement collision detection as
acceptable overlap-avoidance approaches. This plan uses a fixed 2x2 grid (deterministic, no
collision math needed): each of the up to 4 slots is placed in one quadrant, indexed 0=top-left,
1=top-right, 2=bottom-left, 3=bottom-right, relative to the container's current anchor position.

```kotlin
class MultiIndicatorContainerView(context: Context) : FrameLayout(context) {
    private val slotViews = List(IndicatorSlotBuilder.MAX_SLOTS) { ImageView(context) }.onEach { addView(it) }

    fun render(slots: List<IndicatorSlot>) {
        slotViews.forEachIndexed { index, view ->
            if (index < slots.size) {
                view.visibility = View.VISIBLE
                view.setImageResource(resolveDrawableRes(slots[index].category))
                positionAtQuadrant(view, index) // fixed 2x2 grid offset within the container bounds
                renderBadge(view, slots[index]) // §5
            } else {
                view.visibility = View.GONE
            }
        }
    }
}
```

`docs/plans/other/burn-in-protection/`'s reposition loop moves this container's anchor as a whole
(the same `jumpToRandomPosition()` call now applies to `MultiIndicatorContainerView` instead of a
single dot) — the quadrant layout inside the container stays fixed relative to that anchor.

## 5. Count Badge Option

```kotlin
object CountBadgeSettings {
    const val DEFAULT_ENABLED = false
    suspend fun getEnabled(context: Context): Boolean
    suspend fun setEnabled(context: Context, enabled: Boolean)
}
```

```kotlin
private fun renderBadge(view: ImageView, slot: IndicatorSlot) {
    val show = CountBadgeSettings.enabledCached && slot.count > 1
    badgeViewFor(view).apply {
        visibility = if (show) View.VISIBLE else View.GONE
        text = slot.count.toString()
    }
}
```

Settings toggle: "Show notification count badge," default OFF (per `docs/detailed-spec.md` §9's
"no count badge by default").

## 6. Render Integration

Replaces `OverlayWindowManager.showDot()`/`showCategoryIcon()`
(`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5, `docs/plans/other/category-icons-and-assets/PLAN.md`
§5) with:

```kotlin
fun showIndicators(slots: List<IndicatorSlot>) {
    multiIndicatorContainerView.render(slots)
}
```

Recomputed via `IndicatorSlotBuilder.build()` whenever
`docs/plans/mvp/notification-detection-dot-indicator/`'s `PendingNotificationsRepository` changes
while `OverlayUiState.INDICATOR_SHOWN` is active — same recompute trigger point already established
by `docs/plans/other/importance-based-sizing/PLAN.md` §6 for size/blink.

## 7. Cross-Feature Touchpoints

- Replaces the single-icon render path from `docs/plans/mvp/fake-sleep-overlay/` and
  `docs/plans/other/category-icons-and-assets/` — those plans' single-icon logic becomes the
  `MAX_SLOTS == 1` degenerate case once this plan lands.
- Does not alter `docs/plans/mvp/dismiss-and-peek-interaction/`'s tap-handling or preview-text
  logic — both remain position-independent and app-agnostic exactly as already specified there.
- `docs/plans/other/burn-in-protection/`'s reposition loop now moves the whole slot container, not
  a single dot (see §4).
- `docs/plans/other/importance-based-sizing/`'s size/blink effect, once combined with this plan,
  applies per-slot (a slot whose notifications include a keyword match renders at 1.5x/blinking;
  others in the same group render normally) — noted here as the natural extension, not yet detailed
  as a separate function since it only matters once both features are implemented together.
