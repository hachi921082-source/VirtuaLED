# burn-in-protection — Implementation Plan

Covers: `docs/detailed-spec.md` §4 (Burn-In Protection Cycle).

**Scope note**: fixed 4-second reposition interval, fixed instant-jump movement style, no settings
UI — deliberately simplified, see `docs/DECISIONS.md` §13 for why interval/style customization was
cut (not repeated here; see the "Documentation Content Separation Rule" in this project's
`CLAUDE.md`/dev-workflow — rationale lives in `DECISIONS.md`, not here). This plan repositions the
whole indicator slot group shown during the **Indicator Shown** state
(`docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §2) as a single anchor — it does not reach
into per-slot layout, which is `docs/plans/other/multi-notification-stacking/`'s concern; it does
not apply to the peek-preview text screen.

## Index

1. Module Structure
2. Reposition Loop
3. Position Calculation
4. Session Lifecycle Integration
5. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.overlay.BurnInProtector` — new class, owns a repeating `Handler` loop that
  writes to `FakeSleepIndicatorUiState.indicatorOffset`
  (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7) directly. There is no `WindowManager`/`View`
  layer to call into in this architecture — moving the indicator is just a `MutableState` write,
  which recomposes `FakeSleepActivity`'s content.

## 2. Reposition Loop

```kotlin
class BurnInProtector(
    private val indicatorUiState: FakeSleepIndicatorUiState,
    private val screenBoundsPx: () -> IntSize, // supplied by FakeSleepActivity, e.g. from
        // WindowManager.currentWindowMetrics.bounds — read once per jump, not cached, in case of
        // rotation/config change
) {
    companion object { const val REPOSITION_INTERVAL_MS = 4_000L } // fixed, not configurable

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    fun start() {
        if (running) return
        running = true
        scheduleNext()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun scheduleNext() {
        handler.postDelayed({
            jumpToRandomOffset() // §3 — instant jump, no gradual-drift animation
            if (running) scheduleNext()
        }, REPOSITION_INTERVAL_MS)
    }
}
```

## 3. Position Calculation

```kotlin
private fun jumpToRandomOffset() {
    val bounds = screenBoundsPx()
    val maxX = (bounds.width - SLOT_GROUP_SIZE_PX).coerceAtLeast(0)
    val maxY = (bounds.height - SLOT_GROUP_SIZE_PX).coerceAtLeast(0)
    indicatorUiState.indicatorOffset.value = IntOffset(
        Random.nextInt(0, maxX + 1),
        Random.nextInt(0, maxY + 1),
    )
}
```

`SLOT_GROUP_SIZE_PX` is the bounding box of the whole slot group (one dot in MVP; up to a 2x2 icon
grid once `docs/plans/other/multi-notification-stacking/` lands), not a single icon's size — the
group moves as one anchor (§4 of that plan). Instant jump: no `Animatable`/interpolation on
`indicatorOffset` — the new value is applied directly, and Compose recomposes at the new position on
the next frame, per the fixed "instant jump" style (`docs/DECISIONS.md` §13).

**Implementation note (deviation from the sketch above)**: `BurnInProtector` is a plain non-Compose
class with no `Density` access, so `SLOT_GROUP_SIZE_PX` cannot be a self-contained constant computed
inside the class. It is instead a `footprintPx: () -> IndicatorFootprint` constructor parameter
(`IndicatorFootprint(sizePx: IntSize, topInsetPx: Int)`), supplied alongside `screenBoundsPx` and
re-evaluated on every jump rather than fixed at construction time — `FakeSleepActivity`'s
`computeIndicatorFootprintPx()` derives it from the current `slots.value` (column/row count from
slot count, `highImportance`'s 1.5x scale, and the count badge's corner overhang from
`CountBadgeSettings`/`multi-notification-stacking`'s `SlotIcon`), not a single fixed dot size.
Fixed via GitHub issue #23 — a single fixed-size `slotGroupSizePx: Int` computed once at
`onCreate()` under-counted the group's real footprint once multi-slot/high-importance/badge
rendering shipped, letting jumps push part of the group off-screen.

## 4. Session Lifecycle Integration

`BurnInProtector` runs exactly when the indicator slot group is actually being drawn on screen —
three independent conditions gate that, combined through one `recompute()` call rather than each
trigger site calling `start()`/`stop()` directly (`docs/DECISIONS.md` §20). Added to the
`BurnInProtector` class from §2 (same instance, `start()`/`stop()` unchanged):

```kotlin
private var sessionActive = false
private var uiState: OverlayUiState = OverlayUiState.INDICATOR_SHOWN // docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §2
private var proximityCovered = false

fun onSessionActiveChanged(active: Boolean) { sessionActive = active; recompute() }
fun onUiStateChanged(state: OverlayUiState) { uiState = state; recompute() }
fun onProximityCoveredChanged(covered: Boolean) { proximityCovered = covered; recompute() }

private fun recompute() {
    val shouldRun = sessionActive && uiState == OverlayUiState.INDICATOR_SHOWN && !proximityCovered
    if (shouldRun) start() else stop()
}
```

Call sites:

```kotlin
// FakeSleepSessionController.startSession() (docs/plans/mvp/fake-sleep-overlay/PLAN.md §2):
burnInProtector.onSessionActiveChanged(true)

// FakeSleepSessionController.endSession():
burnInProtector.onSessionActiveChanged(false)

// PeekInteractionController (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §2), on any
// OverlayUiState transition (INDICATOR_SHOWN <-> PREVIEW_SHOWN):
burnInProtector.onUiStateChanged(newState)

// ProximityMonitor callback (docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md §3):
burnInProtector.onProximityCoveredChanged(covered)
```

`start()`/`stop()` themselves are unchanged from §2 (idempotent via the existing `running` guard) —
`recompute()` is the single place that decides which one to call.

**Implementation note (deviation from the call sites above)**: `BurnInProtector` is instantiated
per `FakeSleepActivity` instance (constructed in `onCreate()`, alongside `indicatorUiState`, and
exposed via `FakeSleepActivityHolder.burnInProtector` following the same hand-off pattern as
`indicatorUiState`/`sessionController`) rather than as a long-lived field on
`FakeSleepSessionController`, because it needs `screenBoundsPx`/`slotGroupSizePx` derived from the
Activity's `WindowManager`/`Density`. Consequence: `FakeSleepSessionController.startSession()`'s
`FakeSleepActivityHolder.burnInProtector?.onSessionActiveChanged(true)` call is always a no-op in
practice — the notification is posted before the OS has launched `FakeSleepActivity`, so
`burnInProtector` does not exist yet at that point. The actual activation happens directly in
`FakeSleepActivity.onCreate()`, which calls `burnInProtector.onSessionActiveChanged(true)` right
after constructing it (the Activity instance and the session are 1:1, matching
`PeekInteractionController`'s own `uiState` initialization assumption). `endSession()`'s
`onSessionActiveChanged(false)` call is unchanged and does have effect, since the Activity (and
therefore `burnInProtector`) still exists when `endSession()` runs. `FakeSleepActivity.onDestroy()`
also calls `onSessionActiveChanged(false)` as a backstop for the Activity-destroyed-first path.

**Implementation note (`PeekInteractionController` wiring, unspecified above)**:
`PeekInteractionController` gains an additional constructor parameter,
`onUiStateChanged: (OverlayUiState) -> Unit = {}`, invoked right after each of its two `uiState`
reassignments (`onTap()`'s `INDICATOR_SHOWN` branch, `onPreviewTimeout()`'s reversion branch).
`FakeSleepActivity.onCreate()` passes `{ state -> burnInProtector.onUiStateChanged(state) }`.

## 5. Cross-Feature Touchpoints

- Depends on `docs/plans/mvp/fake-sleep-overlay/`'s `FakeSleepIndicatorUiState.indicatorOffset`
  (§7) and on `FakeSleepSessionController` (start/stop hooks),
  `docs/plans/mvp/dismiss-and-peek-interaction/`'s `PeekInteractionController` state transitions
  (pause/resume around the preview text state), and
  `docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md`'s cover/uncover callback (pause/resume
  around `indicatorVisible == false`) — all three report into `recompute()`'s combined gate (§4)
  rather than calling `start()`/`stop()` directly, so any one condition being unfavorable (session
  ended, `PREVIEW_SHOWN`, or covered) keeps the loop stopped regardless of the other two.
- `docs/plans/other/multi-notification-stacking/` places multiple slots within the group in a fixed
  2x2 quadrant layout; this plan only moves the group's anchor (`indicatorOffset`), so no
  further change is needed here when that plan lands — the quadrant layout already stays fixed
  relative to whatever anchor this plan sets (see that plan's §4).
- Independent of `docs/plans/other/importance-based-sizing/`'s `blinkIntervalMs`/`highImportance`
  writes and `docs/plans/other/indicator-color-customization/`'s `tintColor` writes — all four
  properties (`indicatorOffset`, `blinkIntervalMs`, `slots[].highImportance`, `tintColor`) are
  independent fields on the same `FakeSleepIndicatorUiState` instance, so none of these features'
  writes conflict with this plan's.
