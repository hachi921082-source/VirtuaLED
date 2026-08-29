# onoff-notification-toggle — Implementation Plan

Covers: `docs/detailed-spec.md` §18 (Notification Action Toggle).

**Scope note**: this plan covers the FGS notification's ON/OFF action and the shared
feature-enabled flag it controls, plus the in-app switch's own (asymmetric) path. It replaces the
minimal notification body defined in `docs/plans/mvp/foreground-service-setup/PLAN.md` §3 with the
action-bearing version below — it does not redefine the `Service` class itself or the manifest
declarations.

## Index

1. Module Structure
2. Data Structure: `FeatureEnabledState`
3. Notification Content (ON/OFF Variants)
4. Notification-Action Path (asymmetric — no `stopSelf()`)
5. In-App Switch Path (asymmetric — calls `stopSelf()`)
6. Behavior Table
7. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.FeatureEnabledState` — in-memory singleton flag (no DataStore
  persistence in MVP — if the whole process is killed and the FGS restarts via `START_STICKY`, the
  flag simply resets to its default `true`; acceptable simplification per `docs/DECISIONS.md` §8,
  since Android rarely kills a running foreground service's process outright).
- `com.virtualed.app.receiver.OnOffActionReceiver` — `BroadcastReceiver`, target of the
  notification action's `PendingIntent`.
- `VirtuaLedForegroundService.buildNotification()` (`docs/plans/mvp/foreground-service-setup/`) is
  extended here, not redefined.

## 2. Data Structure: `FeatureEnabledState`

```kotlin
object FeatureEnabledState {
    private var enabled: Boolean = true
    private var onChanged: ((Boolean) -> Unit)? = null

    @Synchronized fun isEnabled(): Boolean = enabled

    @Synchronized fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        onChanged?.invoke(value)
    }

    fun setOnChangedListener(cb: (Boolean) -> Unit) { onChanged = cb }
}
```

`VirtuaLedForegroundService.onCreate()` registers the listener:

```kotlin
FeatureEnabledState.setOnChangedListener { enabled ->
    notificationManager.notify(NOTIFICATION_ID, buildNotification(enabled)) // §3
    if (!enabled && fakeSleepSessionController.sessionActive) {
        fakeSleepSessionController.endSession() // pause: end any currently-active session immediately
    }
}
```

While `enabled == false`, `docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s
`FakeSleepSessionController.onScreenOff()` gating check must also short-circuit — this plan adds an
`if (!FeatureEnabledState.isEnabled()) return` guard at the top of that method, alongside its
existing `pendingRepo.isEmpty()` check.

## 3. Notification Content (ON/OFF Variants)

```kotlin
fun buildNotification(enabled: Boolean): Notification {
    val actionLabel = getString(if (enabled) R.string.fgs_action_turn_off else R.string.fgs_action_turn_on)
    val toggleIntent = Intent(this, OnOffActionReceiver::class.java).setAction(OnOffActionReceiver.ACTION_TOGGLE)
    val pendingIntent = PendingIntent.getBroadcast(
        this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notify_dot)
        .setContentTitle(getString(R.string.fgs_notification_title))
        .setContentText(getString(if (enabled) R.string.fgs_status_on else R.string.fgs_status_off))
        .addAction(0, actionLabel, pendingIntent)
        .setOngoing(true)
        .setSilent(true)
        .build()
}
```

## 4. Notification-Action Path (asymmetric — no `stopSelf()`)

```kotlin
class OnOffActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE = "com.virtualed.app.action.TOGGLE_FEATURE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        FeatureEnabledState.setEnabled(!FeatureEnabledState.isEnabled())
        // Deliberately does NOT call stopSelf() and does NOT send ACTION_STOP to the service —
        // the service and its notification must stay alive so the action remains tappable (§18).
    }
}
```

Manifest: `<receiver android:name=".receiver.OnOffActionReceiver" android:exported="false" />`.

## 5. In-App Switch Path (asymmetric — calls `stopSelf()`)

```kotlin
// MainActivity, switch ON:
FeatureEnabledState.setEnabled(true) // resume, in case it was left OFF via the notification action
ContextCompat.startForegroundService(
    context,
    Intent(context, VirtuaLedForegroundService::class.java).setAction(VirtuaLedForegroundService.ACTION_START),
)

// MainActivity, switch OFF:
context.startService(
    Intent(context, VirtuaLedForegroundService::class.java).setAction(VirtuaLedForegroundService.ACTION_STOP),
)
// → VirtuaLedForegroundService.onStartCommand() calls stopSelf() (see foreground-service-setup
//   PLAN.md §2) — service fully terminates, notification disappears. Re-enabling requires
//   reopening the app; there is no longer a notification action to tap.
```

## 6. Behavior Table

| Step | Trigger | Flag | Service | Notification |
|---|---|---|---|---|
| 1 | In-app switch ON | → true | starts, `startForeground()` | shows "OFF" action |
| 2 | Tap notification "OFF" action | → false | keeps running | updates to "ON" action |
| 3 | Tap notification "ON" action | → true | keeps running (never restarted) | updates to "OFF" action |
| 4 (alt. to 2/3) | In-app switch OFF | → false | `stopSelf()`, fully terminates | disappears entirely |

## 7. Cross-Feature Touchpoints

- Extends `docs/plans/mvp/foreground-service-setup/PLAN.md`'s `buildNotification()` and adds the
  `BroadcastReceiver` referenced (but not defined) there.
- Adds a guard to `docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s screen-off gating check
  (`FeatureEnabledState.isEnabled()`), and calls that plan's `FakeSleepSessionController.endSession()`
  when the flag flips to `false` mid-session.
- `docs/plans/mvp/permission-onboarding/PLAN.md` gates whether the in-app switch is reachable at
  all, prior to any of this plan's flows running.
