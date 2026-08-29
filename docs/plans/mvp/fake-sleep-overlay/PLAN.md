# fake-sleep-overlay — Implementation Plan

Covers: `docs/detailed-spec.md` §1 (Fake-Sleep Overlay Mechanism), §16 (WakeLock Discipline).

**Scope note**: this plan covers only the fake-sleep engine mechanics — screen-off detection, the
pending-notification gating check, the short processing-guard `WakeLock` (§16), posting/cancelling
the full-screen-intent notification, and `FakeSleepActivity`'s minimal content (full-screen black
background + a fixed-position, fixed-color dot). Explicitly out of scope (deferred elsewhere):
category icons (`docs/plans/other/category-icons-and-assets/`), color customization
(`docs/plans/other/indicator-color-customization/`), burn-in repositioning
(`docs/plans/other/burn-in-protection/`), tap/peek semantics and unlock auto-dismiss
(`docs/plans/mvp/dismiss-and-peek-interaction/`), multi-notification visual stacking
(`docs/plans/other/multi-notification-stacking/`). MVP renders exactly one dot regardless of
pending-notification count (see `docs/plans/mvp/notification-detection-dot-indicator/PLAN.md`).

**Revision note**: this plan previously specified a raw `WindowManager`
`TYPE_APPLICATION_OVERLAY` window (`SYSTEM_ALERT_WINDOW`) with a session-long, periodically-renewed
`WakeLock`, pending resolution of `docs/QA.md` #14 (whether it works above a secure lock screen).
That question is now resolved — it does not — and the mechanism has been corrected to the
`FakeSleepActivity` design below, validated on a real device. See `docs/DECISIONS.md` §18 for the
investigation and validation, and `docs/QA.md` #14 (resolved) for the question history. No
secure-lock branching is needed: this mechanism works universally.

## Index

1. Module Structure
2. Data Structures
3. Receiver Registration and Screen-Off Flow
4. WakeLock Discipline (§16)
5. `FakeSleepActivity` and the Full-Screen-Intent Notification (§1)
6. Session End Conditions
7. Cross-Feature Touchpoints

## 1. Module Structure

Package: `com.virtualed.app.overlay` (unless noted otherwise)

- `FakeSleepSessionController` — session state machine. Instantiated and held by
  `VirtuaLedForegroundService` (`docs/plans/mvp/foreground-service-setup/PLAN.md`). On a qualifying
  screen-off, builds and posts the full-screen-intent notification (no longer draws anything
  itself); on session end, cancels that notification and finishes `FakeSleepActivity` if it is
  currently resumed.
- `FakeSleepActivity` — full-screen `Activity`; calls `setShowWhenLocked(true)`,
  `setTurnScreenOn(true)`, and holds `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` (see §5) in
  `onCreate()`; content view is the black background + centered dot (a plain view, not a
  `WindowManager`-added overlay window).
- `FakeSleepActivityHolder` — process-wide singleton exposing two references: the
  currently-resumed `FakeSleepActivity` instance (or `null`), and the single
  `FakeSleepSessionController` instance the Service owns (set once, at Service startup — see §2).
  This lets `FakeSleepSessionController` (running inside the Service) reach the Activity to
  `finish()` it, and lets `FakeSleepActivity`'s own tap/power handling (owned by
  `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md`) call back into that same controller
  instance — concretely:

  ```kotlin
  object FakeSleepActivityHolder {
      var current: FakeSleepActivity? = null
      var sessionController: FakeSleepSessionController? = null // set once, see §2
  }
  ```

  Both run in the same process, so a plain singleton reference is sufficient — no cross-process
  IPC needed. `PendingNotificationsRepository` (§2) does not need a hand-off through this holder —
  it is already its own top-level singleton `object`
  (`docs/plans/mvp/notification-detection-dot-indicator/PLAN.md`), directly referenceable from
  anywhere, including `FakeSleepActivity`.
- `receiver.ScreenStateReceiver` (package `com.virtualed.app.receiver`) — dynamically-registered
  `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF`, unchanged in registration mechanism from the
  original design (see §3).

## 2. Data Structures

```kotlin
class FakeSleepSessionController(
    private val context: Context,
    private val pendingRepo: PendingNotificationsRepository, // notification-detection-dot-indicator
) {
    var sessionActive: Boolean = false
        private set

    init {
        FakeSleepActivityHolder.sessionController = this // self-registers; the Service just needs
        // to construct this once and hold the instance — no separate wiring call required
    }

    fun onScreenOff() {
        if (pendingRepo.isEmpty()) return // gating check, §1 step 2 — device sleeps normally
        startSession()
    }

    fun startSession() { /* see §4, §5 */ }
    fun endSession() { /* see §6 */ }
}
```

`PendingNotificationsRepository` (owned/defined in
`docs/plans/mvp/notification-detection-dot-indicator/PLAN.md`) is read here only via `isEmpty()`
and an "became empty" callback — this plan does not redefine it.

## 3. Receiver Registration and Screen-Off Flow

```kotlin
class ScreenStateReceiver(private val onScreenOff: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
    }
}
```

Registered inside `VirtuaLedForegroundService.onStartCommand()` (after `startForeground()`
succeeds), via `context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))`.
Unregistered in `onDestroy()`. `ACTION_SCREEN_OFF` is an implicit broadcast that cannot be
manifest-declared on modern Android — dynamic registration from the already-running service is
required (see `docs/plans/mvp/foreground-service-setup/PLAN.md` §4).

Flow: `ScreenStateReceiver.onReceive()` → `FakeSleepSessionController.onScreenOff()` → gating check
against `PendingNotificationsRepository.isEmpty()` → if non-empty, `startSession()` → posts the
full-screen-intent notification (§5) → the OS launches `FakeSleepActivity` via that notification's
`PendingIntent`, without the Service itself starting any new component from the background (§17,
`docs/plans/mvp/foreground-service-setup/PLAN.md`).

## 4. WakeLock Discipline (§16)

**Narrowed scope**: no session-long or periodically-renewed `WakeLock` — `FakeSleepActivity`'s
`setTurnScreenOn(true)` (§5) turns the screen on as part of the Activity being resumed, and its
`FLAG_KEEP_SCREEN_ON` window flag (§5) keeps it lit for the session's duration, so keeping the
screen lit is no longer a `WakeLock` responsibility at all. What remains is a short-lived guard
over `onScreenOff()`'s brief processing window (the gating check plus building/posting the
full-screen-intent notification), against the CPU suspending before that work completes:

```kotlin
private var wakeLock: PowerManager.WakeLock? = null // held field, so double-acquire can be checked

private fun withScreenOffProcessingGuard(block: () -> Unit) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val lock = wakeLock ?: pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, "VirtuaLED:ScreenOffProcessing",
    ).also { wakeLock = it }
    if (lock.isHeld) { block(); return } // guard against double-acquire; already held, just run
    try {
        lock.acquire(SCREEN_OFF_PROCESSING_TIMEOUT_MS) // e.g. 5000L — safety net, not a session value
        block()
    } finally {
        if (lock.isHeld) lock.release()
    }
}
```

`startSession()` (§5) wraps its notification-build-and-post work in `withScreenOffProcessingGuard`.
Release is deterministic via `try/finally`; the timeout is a safety net against a bug in this
handler, not an intended session limit. No renewal loop, no `Handler.postDelayed` — this WakeLock's
lifetime is the width of one `onScreenOff()` call, not the fake-sleep session's duration.

## 5. `FakeSleepActivity` and the Full-Screen-Intent Notification (§1)

Manifest entry (this plan owns `FakeSleepActivity`'s declaration; `docs/plans/mvp/foreground-service-setup/PLAN.md`
does not redefine it):

```xml
<activity
    android:name=".overlay.FakeSleepActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:launchMode="singleTask"
    android:theme="@style/Theme.VirtuaLed.FakeSleep" />
```

```kotlin
class FakeSleepActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_fake_sleep) // or a Compose setContent { } equivalent
        FakeSleepActivityHolder.current = this
    }

    override fun onDestroy() {
        if (FakeSleepActivityHolder.current === this) FakeSleepActivityHolder.current = null
        super.onDestroy()
    }
}
```

`FLAG_KEEP_SCREEN_ON` is required in addition to `setShowWhenLocked`/`setTurnScreenOn`: without it,
the device's own idle screen-timeout (`screen_off_timeout`, e.g. 15s) fires a real
`ACTION_SCREEN_OFF` broadcast while this Activity is on screen — indistinguishable from an actual
power-button press — which prematurely ends the fake-sleep session via
`docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §6's power-button-dismiss handling. The flag
does not suppress a genuine power-button press; `ACTION_SCREEN_OFF` still fires normally for that
case, so §6's heuristic remains correct.

Content view: full-screen black (`#000000`) background, one centered dot, fixed white
(`#FFFFFF`, per `docs/detailed-spec.md` §11's MVP default-color note), fixed position (arbitrary —
e.g. screen center; not user-configurable in MVP, since burn-in repositioning and color
customization are both deferred). No category icon rendering, no tap-count/click-handling logic
here (added by `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md`, which sets a click listener
directly on this content view — a normal Activity view, no `WindowManager` hit-testing concerns).

Building and posting the notification (`startSession()` body):

```kotlin
private fun startSession() {
    sessionActive = true
    withScreenOffProcessingGuard {
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, FakeSleepActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, FAKE_SLEEP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_dot) // placeholder small-icon asset
            .setContentTitle(getString(R.string.fake_sleep_notification_title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenPendingIntent, /* highPriority = */ true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(FAKE_SLEEP_NOTIFICATION_ID, notification)
    }
}
```

`FAKE_SLEEP_CHANNEL_ID` is a dedicated `NotificationChannel` (`IMPORTANCE_HIGH`), separate from the
FGS's own low-importance persistent-notification channel (`docs/plans/mvp/foreground-service-setup/PLAN.md`
§3) — a full-screen-intent notification needs high enough channel importance to be heads-up/
full-screen eligible. This notification exists to carry the full-screen intent, not to be read by
the user in the shade; content wording is minimal.

Both this notification and the FGS's own persistent notification share the app's `packageName`, so
both are already excluded by `docs/plans/mvp/notification-detection-dot-indicator/PLAN.md` §4's
own-notification filter (`sbn.packageName == packageName`) — no additional filtering change needed
there.

## 6. Session End Conditions

`endSession()` is triggered by, in order of how this plan's collaborators reach it:
- `PendingNotificationsRepository` firing a "became empty" callback while `sessionActive` (per
  `docs/DECISIONS.md` §16 — the session must not linger once nothing remains to show). Wired via
  `pendingRepo.setOnBecameEmptyListener { if (sessionActive) endSession() }` during
  `FakeSleepSessionController` init.
- Explicit full-dismiss from tap/power-button handling — owned by
  `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md`, which calls
  `FakeSleepSessionController.endSession()` directly (via `FakeSleepActivityHolder`'s reference to
  the shared controller instance).

```kotlin
fun endSession() {
    NotificationManagerCompat.from(context).cancel(FAKE_SLEEP_NOTIFICATION_ID) // no-op if already gone
    FakeSleepActivityHolder.current?.finish()
    sessionActive = false
}
```

No renewal `Handler` to cancel (§4 no longer has one) and no overlay `View` to remove — teardown is
just cancelling the notification (in case it was posted but not yet auto-cancelled) and finishing
the Activity if it is currently resumed.

## 7. Cross-Feature Touchpoints

- Hosted inside `VirtuaLedForegroundService` (`docs/plans/mvp/foreground-service-setup/`) — this
  plan does not define its own Service; it does define `FakeSleepActivity`'s manifest entry (§5).
- Gating check reads `PendingNotificationsRepository`, owned by
  `docs/plans/mvp/notification-detection-dot-indicator/`.
- `endSession()` is also called externally by `docs/plans/mvp/dismiss-and-peek-interaction/`
  (explicit dismiss) and by `docs/plans/mvp/onoff-notification-toggle/`'s in-app-switch-OFF path
  (which stops the whole FGS, implicitly ending any active session via `onDestroy()`).
- `FakeSleepActivityHolder` is the hand-off point `docs/plans/mvp/dismiss-and-peek-interaction/`
  uses to wire its tap/click handling into `FakeSleepActivity`'s content view and to call back into
  this plan's `FakeSleepSessionController`.
- Once `docs/plans/other/burn-in-protection/` is implemented, it will periodically reposition the
  dot within `FakeSleepActivity`'s content view — this plan's "fixed position" note is superseded at
  that point.
- Once `docs/plans/other/indicator-color-customization/` is implemented, the hardcoded
  `#FFFFFF` dot color here is superseded by the persisted user color.
- `docs/plans/mvp/onoff-notification-toggle/` adds a `FeatureEnabledState.isEnabled()` guard to
  `onScreenOff()`'s gating check (alongside the `pendingRepo.isEmpty()` check already shown in §2
  above), and calls `endSession()` directly when the feature is flipped OFF via the notification
  action while a session is active.
