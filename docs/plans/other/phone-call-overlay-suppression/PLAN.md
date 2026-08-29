# phone-call-overlay-suppression — Implementation Plan

Covers: `docs/detailed-spec.md` §13 (Phone-Call Overlay Suppression).

**Open question blocking this plan**: see `docs/QA.md` #15. This plan's mechanism below was written
against the earlier raw-`WindowManager`-overlay design. The fake-sleep display is now
`FakeSleepActivity`, an ordinary (if `showWhenLocked`/`turnScreenOn`) `Activity`
(`docs/plans/mvp/fake-sleep-overlay/PLAN.md`, `docs/DECISIONS.md` §18) — whether this plan's
explicit `TelephonyManager` call-state detection and hide/restore is still needed, or whether normal
Android Activity/task-stack behavior (the incoming call's own full-screen-intent Activity naturally
taking foreground and pausing `FakeSleepActivity`, which naturally resumes once the call Activity
finishes) already achieves the required outcome with no `CallStateMonitor`/`READ_PHONE_STATE` at
all, is unresolved. Do not implement against this plan as written until `docs/QA.md` #15 is
answered — the content below is left as-is (not rewritten) pending that answer, since the correct
mechanism depends on which way it resolves.

**Scope note**: this plan covers only detecting an active call and temporarily hiding/restoring
the overlay around it. It does not end the fake-sleep session (`WakeLock` stays held, pending
notifications keep being tracked) — same "hide drawing only" pattern already used by
`docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md`. **This pattern (shared with that
plan) is itself part of what's in question — see the open-question note above.**

**New permission required**: `docs/detailed-spec.md` §13 specifies detection via
`TelephonyManager` call-state listening, which requires the `READ_PHONE_STATE` runtime permission —
not part of the MVP permission set in `docs/plans/mvp/foreground-service-setup/PLAN.md` §1. Since
`docs/detailed-spec.md` §13 already specifies this exact mechanism, this plan treats the permission
requirement as a settled consequence of that choice (not a fresh product decision to re-litigate),
and designs for graceful degradation if the user denies it (§3).

## Index

1. Module Structure
2. Manifest / Runtime Permission
3. Call State Monitor
4. Session Lifecycle Integration
5. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.autostop.CallStateMonitor` — wraps `TelephonyManager`/`TelephonyCallback`.
  Since `minSdkVersion` is 31, the modern `TelephonyCallback` API is used directly — no
  `PhoneStateListener` (deprecated since API 31) compatibility branch is needed.

## 2. Manifest / Runtime Permission

```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

Requested at runtime via `ActivityResultContracts.RequestPermission()` (standard dangerous-permission
flow, unlike the two permissions in `docs/plans/mvp/permission-onboarding/`, which cannot use this
flow at all). Requested lazily — only when this feature's toggle is first enabled in settings, not
as part of app-wide onboarding, consistent with `docs/DECISIONS.md` §4's least-privilege pattern for
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. If denied, this feature simply does not suppress the overlay
during calls — no other feature is blocked.

## 3. Call State Monitor

```kotlin
class CallStateMonitor(
    private val context: Context,
    private val onCallStateChanged: (inCall: Boolean) -> Unit,
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var callback: TelephonyCallback? = null

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return // graceful no-op if not granted, see §2
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                onCallStateChanged(state != TelephonyManager.CALL_STATE_IDLE)
            }
        }
        callback = cb
        telephonyManager.registerTelephonyCallback(context.mainExecutor, cb)
    }

    fun stop() {
        callback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        callback = null
    }
}
```

Started once in `VirtuaLedForegroundService.onCreate()`
(`docs/plans/mvp/foreground-service-setup/PLAN.md`) — runs regardless of session state, same
pattern as `docs/plans/other/auto-stop-conditions/PLAN-2-battery.md`'s receiver, so an incoming
call is caught even if a fake-sleep session becomes active partway through the call.

## 4. Session Lifecycle Integration

```kotlin
callStateMonitor = CallStateMonitor(context) { inCall ->
    if (inCall) overlayManager.hide() else overlayManager.restoreCurrentState()
    // WakeLock and PendingNotificationsRepository tracking are untouched — only the drawn View
    // is removed/restored, so the system's own incoming-call full-screen intent
    // (Notification.Builder.setFullScreenIntent()) is never obstructed, per §13.
}
```

Uses the same `overlayManager.hide()`/`restoreCurrentState()` pair already introduced by
`docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md` §3 — this plan does not add a second
hide/restore mechanism.

## 5. Cross-Feature Touchpoints

- Reuses `OverlayWindowManager.hide()`/`restoreCurrentState()` from
  `docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md`.
- Started/stopped alongside `VirtuaLedForegroundService`'s own lifecycle
  (`docs/plans/mvp/foreground-service-setup/`), independent of session start/end.
