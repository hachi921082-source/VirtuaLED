# burn-in-protection — Implementation Plan

Covers: `docs/detailed-spec.md` §4 (Burn-In Protection Cycle).

**Scope note**: fixed 4-second reposition interval, fixed instant-jump movement style, no settings
UI — deliberately simplified, see `docs/DECISIONS.md` §13 for why interval/style customization was
cut (not repeated here; see the "Documentation Content Separation Rule" in this project's
`CLAUDE.md`/dev-workflow — rationale lives in `DECISIONS.md`, not here). This plan only repositions
the single dot/icon shown during the **Indicator Shown** state
(`docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §2); it does not apply to the peek-preview
text screen, and does not cover multi-icon layout (`docs/plans/other/multi-notification-stacking/`).

## Index

1. Module Structure
2. Reposition Loop
3. Position Calculation
4. Session Lifecycle Integration
5. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.overlay.BurnInProtector` — new class, owns a repeating `Handler` loop, calls
  into `OverlayWindowManager` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5) to move the existing
  overlay view rather than recreate it.

## 2. Reposition Loop

```kotlin
class BurnInProtector(
    private val overlayManager: OverlayWindowManager,
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
            overlayManager.jumpToRandomPosition() // §3 — instant jump, no gradual-drift animation
            if (running) scheduleNext()
        }, REPOSITION_INTERVAL_MS)
    }
}
```

## 3. Position Calculation

Added to `OverlayWindowManager` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5):

```kotlin
fun jumpToRandomPosition() {
    val bounds = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        .currentWindowMetrics.bounds
    val maxX = (bounds.width() - dotSizePx).coerceAtLeast(0)
    val maxY = (bounds.height() - dotSizePx).coerceAtLeast(0)
    layoutParams.x = Random.nextInt(0, maxX + 1)
    layoutParams.y = Random.nextInt(0, maxY + 1)
    layoutParams.gravity = Gravity.TOP or Gravity.START
    windowManager.updateViewLayout(view, layoutParams)
}
```

Instant jump: no `ValueAnimator`/interpolation — the coordinate change is applied directly on the
next `updateViewLayout()` call, per the fixed "instant jump" style (`docs/DECISIONS.md` §13).

## 4. Session Lifecycle Integration

`BurnInProtector` only runs while the dot/icon is actually visible:

```kotlin
// FakeSleepSessionController.startSession() (docs/plans/mvp/fake-sleep-overlay/PLAN.md §2):
burnInProtector.start()

// FakeSleepSessionController.endSession():
burnInProtector.stop()

// PeekInteractionController (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §2), on entering
// PREVIEW_SHOWN:
burnInProtector.stop() // dot isn't rendered during the text preview — no point repositioning it

// PeekInteractionController, on reverting PREVIEW_SHOWN -> INDICATOR_SHOWN (timeout, notifications remain):
burnInProtector.start()
```

## 5. Cross-Feature Touchpoints

- Depends on `docs/plans/mvp/fake-sleep-overlay/`'s `OverlayWindowManager` and
  `FakeSleepSessionController` (start/stop hooks) and on
  `docs/plans/mvp/dismiss-and-peek-interaction/`'s `PeekInteractionController` state transitions
  (pause/resume around the preview text state).
- Once `docs/plans/other/multi-notification-stacking/` is implemented, this plan's single-icon
  `jumpToRandomPosition()` needs to reposition the whole icon group (or be re-scoped to move each
  icon's slot within its grid) — flagged here as a forward dependency, not resolved by this plan.
