# dismiss-and-peek-interaction — Implementation Plan

Covers: `docs/detailed-spec.md` §8 (Dismiss and Peek Interaction — full two-state model, not
simplified), §9 (text-only: the peek-preview's aggregated list of all pending notifications — the
*content* of what's shown on first tap), §14 (Unlock Auto-Dismiss).

**Revision note**: this plan previously wrapped a raw `WindowManager` overlay `View`
(`OverlayIndicatorView`) with an `OnTouchListener`. The fake-sleep display is now `FakeSleepActivity`,
an ordinary `Activity` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md`, `docs/DECISIONS.md` §18), so
tap handling below is expressed as normal Activity content-view click handling instead — simpler
than before, since there is no `WindowManager` window to manage touchability flags on. The
tap-count-based state model itself (Indicator Shown / Preview Shown, no coordinate hit-testing) is
unchanged.

**Scope note**: this plan covers tap-counting, the Indicator-Shown/Preview-Shown state machine, the
preview's aggregated **text** rendering, power-button full dismiss, and unlock auto-dismiss.
Explicitly out of scope: multi-icon visual rendering, collision avoidance, per-app count badges
(`docs/plans/other/multi-notification-stacking/` — that plan covers the *Indicator Shown* state's
visual layer only; this plan's preview-text rendering is not duplicated there). No `PendingIntent`
state-holding, no coordinate-based hit-testing anywhere in this plan (per `docs/DECISIONS.md` §12,
§14) — taps are counted, never located.

## Index

1. Module Structure
2. State Model
3. Tap Handling
4. Preview Text Rendering (§9)
5. Timeout Behavior
6. Power-Button Dismiss
7. Unlock Auto-Dismiss (§14)
8. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.overlay.PeekInteractionController` — new class, instantiated by
  `FakeSleepActivity` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5) in `onCreate()`; composes
  with the shared `FakeSleepSessionController` instance (reached via `FakeSleepActivityHolder`'s
  hand-off point) rather than replacing it. Owns the Indicator-Shown/Preview-Shown sub-state.
- Extends `FakeSleepActivity`'s content view (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5) with a
  second render mode for the text preview — a plain child-view visibility toggle within the
  Activity's own layout, not a new overlay/window class.
- `Intent.ACTION_USER_PRESENT` is still registered on `docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s
  `ScreenStateReceiver`, inside `VirtuaLedForegroundService` (extends its `IntentFilter` rather than
  adding a second receiver) — this detection point stays in the Service, since it must fire
  regardless of whether `FakeSleepActivity` happens to still be resumed at that moment.

## 2. State Model

```kotlin
enum class OverlayUiState { INDICATOR_SHOWN, PREVIEW_SHOWN }
```

There is no "idle, nothing to show" state (per §1/§8 — a session only exists while
`PendingNotificationsRepository` is non-empty; see `docs/DECISIONS.md` §16). `OverlayUiState` is
scoped 1:1 with a single `FakeSleepActivity` instance's lifetime (a session and an Activity instance
are 1:1 — see `docs/plans/mvp/fake-sleep-overlay/PLAN.md`), so it always starts at
`INDICATOR_SHOWN`: a new session always means a freshly-created Activity instance, with no separate
"reset" step needed.

```kotlin
class PeekInteractionController(
    private val activity: FakeSleepActivity, // owns the content view being toggled
    private val sessionController: FakeSleepSessionController,
    private val pendingRepo: PendingNotificationsRepository,
) {
    private var uiState: OverlayUiState = OverlayUiState.INDICATOR_SHOWN
    private var previewTimeoutHandler: Handler? = null

    fun onTap() { /* see §3 */ }
    fun cancelPreviewTimeout() { previewTimeoutHandler?.removeCallbacksAndMessages(null) }
}
```

Power-button dismiss (§6) and unlock auto-dismiss (§7) are not methods on this controller — both are
cross-component dismiss triggers detected in `VirtuaLedForegroundService`'s `ScreenStateReceiver`,
not driven by a tap on `FakeSleepActivity`'s content view.

## 3. Tap Handling

Concrete wiring, extending `FakeSleepActivity.onCreate()`
(`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5) after `super.onCreate()` and `setContentView()`:

```kotlin
// FakeSleepActivity.onCreate(), extended:
val peekController = PeekInteractionController(
    activity = this,
    sessionController = FakeSleepActivityHolder.sessionController!!, // set at Service startup,
    // see fake-sleep-overlay/PLAN.md §1/§2 — never null here, since a session (and therefore this
    // Activity) cannot exist before the Service has constructed the controller
    pendingRepo = PendingNotificationsRepository, // top-level singleton, no hand-off needed
)
findViewById<View>(R.id.fake_sleep_root).setOnClickListener { peekController.onTap() }
```

`FakeSleepActivity`'s content view registers a single `View.OnClickListener` (a tap anywhere on the
Activity's root view counts once, regardless of location — no coordinate hit-testing) that forwards
to `PeekInteractionController.onTap()`:

```kotlin
fun onTap() {
    when (uiState) {
        OverlayUiState.INDICATOR_SHOWN -> {
            uiState = OverlayUiState.PREVIEW_SHOWN
            activity.showPreviewText(buildPreviewLines()) // §4
            startPreviewTimeout() // §5
        }
        OverlayUiState.PREVIEW_SHOWN -> {
            cancelPreviewTimeout()
            sessionController.endSession() // full dismiss — exits fake-sleep, real lock screen shown
        }
    }
}
```

## 4. Preview Text Rendering (§9)

One line per currently-pending notification, regardless of which icon (if any) was tapped — the
same aggregated list every time, since taps are not coordinate-tested against any icon:

```kotlin
private fun buildPreviewLines(): List<String> =
    pendingRepo.snapshot().map { "${it.appLabel}: ${it.title.orEmpty()} ${it.text.orEmpty()}".trim() }
```

`FakeSleepActivity.showPreviewText(lines)` swaps its content view from the dot to a simple
vertically-stacked text list (still the same Activity instance, same full-screen black background —
no separate window, no Activity restart). Multi-icon visual layout is not part of this rendering
path at all (see `docs/plans/other/multi-notification-stacking/`, which only concerns the
*Indicator Shown* dot/icon layer).

## 5. Timeout Behavior

Default timeout: 4 seconds (a fixed single value within `docs/detailed-spec.md` §8's stated
3-5 second range; not user-configurable in MVP — no settings UI is specified for this).

```kotlin
private fun startPreviewTimeout() {
    previewTimeoutHandler = Handler(Looper.getMainLooper()).also {
        it.postDelayed({ onPreviewTimeout() }, PREVIEW_TIMEOUT_MS)
    }
}

private fun onPreviewTimeout() {
    if (pendingRepo.isEmpty()) {
        sessionController.endSession() // nothing left to show — session ends itself, no dismiss animation
    } else {
        uiState = OverlayUiState.INDICATOR_SHOWN
        activity.showDot()
    }
}
```

If the last pending notification is cleared externally (`docs/plans/mvp/notification-detection-dot-indicator/`
§12) while `PREVIEW_SHOWN` is active, `PendingNotificationsRepository`'s `onBecameEmpty` callback
(wired in `docs/plans/mvp/fake-sleep-overlay/`) fires `sessionController.endSession()` directly —
independent of this timeout. `endSession()` finishes `FakeSleepActivity` (via
`FakeSleepActivityHolder`), and `FakeSleepActivity.onDestroy()` calls
`peekController.cancelPreviewTimeout()` as a backstop so no stale callback fires after teardown.

## 6. Power-Button Dismiss

Power button → full dismiss, from either state, unconditionally. Detection: the fake-sleep session
begins because `Intent.ACTION_SCREEN_OFF` fired once and `FakeSleepActivity`'s
`setTurnScreenOn(true)` (see `docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5) relit the display as
part of the Activity resuming, so while a session is active the display is technically on. A
second, real `Intent.ACTION_SCREEN_OFF` broadcast arriving **while
`sessionController.sessionActive == true`** can only mean the user pressed the power button again
to truly turn the (relit) display off — this is treated as the power-button-dismiss signal:

```kotlin
// In ScreenStateReceiver's callback (docs/plans/mvp/fake-sleep-overlay/PLAN.md §3), extended:
fun onScreenOff() {
    if (sessionController.sessionActive) {
        // second SCREEN_OFF while a session is already active — power button pressed again
        FakeSleepActivityHolder.current?.onPowerButtonPressed() ?: sessionController.endSession()
        return
    }
    // Delegates to the shared gating method (docs/plans/mvp/fake-sleep-overlay/PLAN.md §2) —
    // do NOT reimplement the pendingRepo.isEmpty() check here. onoff-notification-toggle adds a
    // FeatureEnabledState guard to that exact shared method; duplicating the check here would let
    // a new session start even while the feature is flagged OFF via the notification action.
    sessionController.onScreenOff()
}
```

```kotlin
// FakeSleepActivity
fun onPowerButtonPressed() {
    peekController.cancelPreviewTimeout()
    sessionController.endSession()
}
```

The `FakeSleepActivityHolder.current?.onPowerButtonPressed() ?: sessionController.endSession()`
fallback covers the (unexpected) case where the Activity reference is already gone by the time the
Service observes the second `SCREEN_OFF` — `endSession()` alone still tears down the notification
and flips `sessionActive` off correctly.

## 7. Unlock Auto-Dismiss (§14)

`Intent.ACTION_USER_PRESENT` is added to the same `IntentFilter` as `ACTION_SCREEN_OFF` on the
dynamically-registered receiver (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §3):

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
        Intent.ACTION_SCREEN_OFF -> onScreenOff()
        Intent.ACTION_USER_PRESENT -> onUnlock()
    }
}

fun onUnlock() {
    if (sessionController.sessionActive) sessionController.endSession()
}
```

No `PendingIntent` is held across this boundary (deep-link removed from scope, see
`docs/DECISIONS.md` §12) — `onUnlock()` only tears down the session/`FakeSleepActivity`, it does not
launch any app.

## 8. Cross-Feature Touchpoints

- Instantiated by and lives inside `docs/plans/mvp/fake-sleep-overlay/`'s `FakeSleepActivity`; calls
  into that plan's `FakeSleepSessionController` (via `FakeSleepActivityHolder`) rather than
  duplicating session/`WakeLock` management.
- Reads `docs/plans/mvp/notification-detection-dot-indicator/`'s `PendingNotificationsRepository`
  for both the gating/timeout logic and the preview text content.
- `docs/plans/other/multi-notification-stacking/` will replace `FakeSleepActivity.showDot()`'s
  single-dot rendering with multi-icon layout, but must not alter this plan's tap-count/timeout/
  preview-text logic.
