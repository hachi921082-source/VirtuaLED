# foreground-service-setup — Implementation Plan

Covers: `docs/detailed-spec.md` §2 (Manifest Permissions and Foreground Service Declaration), §17
(Foreground Service Startup Model).

**Scope note**: this plan covers only the manifest declarations, the `Service` skeleton, its
startup/shutdown lifecycle, and the minimal persistent notification needed to legally call
`startForeground()`. The notification's ON/OFF action button and its asymmetric stop behavior are
out of scope here — see `docs/plans/mvp/onoff-notification-toggle/PLAN.md`. Screen-off detection,
the full-screen-intent notification, and `FakeSleepActivity` are out of scope here — see
`docs/plans/mvp/fake-sleep-overlay/PLAN.md`, which hooks into the lifecycle points this plan
defines. This service no longer draws anything itself on screen-off — it posts a notification and
the OS launches `FakeSleepActivity` via that notification's `PendingIntent` (§17). `RECEIVE_BOOT_COMPLETED` and quiet-hours
scheduling are explicitly excluded from MVP (deferred to `docs/plans/other/auto-stop-conditions/`).

## Index

1. Manifest Declarations
2. Service Class Skeleton
3. Notification Channel and Minimal Notification
4. Startup Model (§17)
5. Lifecycle Summary
6. Cross-Feature Touchpoints

## 1. Manifest Declarations

`<uses-permission>` entries (MVP subset only — excludes `RECEIVE_BOOT_COMPLETED`, needed only once
`docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md` is implemented):

```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`SYSTEM_ALERT_WINDOW` is no longer needed — the raw-overlay mechanism it backed was replaced before
being built; see `docs/plans/mvp/fake-sleep-overlay/PLAN.md` and `docs/DECISIONS.md` §18.
`USE_FULL_SCREEN_INTENT` is auto-granted on this project's current sideload/adb-installed
distribution (`docs/detailed-spec.md` §2, `docs/DECISIONS.md` §18) — no onboarding step needed for
it (see `docs/plans/mvp/permission-onboarding/PLAN.md`).

`<service>` declaration (from `docs/detailed-spec.md` §2, verbatim):

```xml
<service
    android:name=".service.VirtuaLedForegroundService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="screen-notification-indicator-overlay" />
</service>
```

No conditional/API-level manifest branching needed (unrecognized attributes/properties on API < 34
are safely ignored by the platform). The one required code-level branch is in `startForeground()`
(see §2 below).

## 2. Service Class Skeleton

Package: `com.virtualed.app.service`

```kotlin
class VirtuaLedForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "virtualed_fgs_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.virtualed.app.action.START"
        // ACTION_STOP = in-app switch OFF path only; see onoff-notification-toggle PLAN
        // for the notification-action OFF path, which does NOT use this action.
        const val ACTION_STOP = "com.virtualed.app.action.STOP"
    }

    // Collaborators instantiated in onCreate(); owned/detailed by their respective feature plans:
    // - PendingNotificationsRepository (notification-detection-dot-indicator)
    // - FakeSleepSessionController      (fake-sleep-overlay)
    // - overlay tap/dismiss wiring       (dismiss-and-peek-interaction)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // init collaborators here (see cross-feature touchpoints, §6)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // in-app switch OFF path (docs/detailed-spec.md §18) — full stop.
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification(enabled = true) // extended in onoff-notification-toggle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // register ScreenStateReceiver here — see fake-sleep-overlay PLAN §"Receiver Registration"
        return START_STICKY
    }

    override fun onDestroy() {
        // unregister dynamically-registered receivers, release the screen-off processing WakeLock
        // if held (fake-sleep-overlay §16), cancel any pending full-screen-intent notification and
        // finish FakeSleepActivity if resumed (fake-sleep-overlay §6)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null // not a bound service
}
```

## 3. Notification Channel and Minimal Notification

```kotlin
private fun createNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.fgs_channel_name),
        NotificationManager.IMPORTANCE_LOW, // no sound/heads-up; this is a persistent status notification
    )
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

private fun buildNotification(enabled: Boolean): Notification {
    // Minimal MVP shell: title + short status text, no action button yet.
    // onoff-notification-toggle PLAN replaces this body with the ON/OFF-action version.
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notify_dot) // placeholder small-icon asset
        .setContentTitle(getString(R.string.fgs_notification_title))
        .setContentText(getString(if (enabled) R.string.fgs_status_on else R.string.fgs_status_off))
        .setOngoing(true)
        .setSilent(true)
        .build()
}
```

## 4. Startup Model (§17)

The Foreground Service is started **once, persistently**, the moment the user turns the feature ON
via the in-app switch (`MainActivity`) — not per screen-off event:

```kotlin
// MainActivity, on switch ON:
ContextCompat.startForegroundService(
    context,
    Intent(context, VirtuaLedForegroundService::class.java).setAction(VirtuaLedForegroundService.ACTION_START)
)

// MainActivity, on switch OFF (in-app path only — see onoff-notification-toggle for the
// notification-action OFF path, which never sends ACTION_STOP):
context.startService(
    Intent(context, VirtuaLedForegroundService::class.java).setAction(VirtuaLedForegroundService.ACTION_STOP)
)
```

Screen-off detection is **not** done via a manifest-declared `<receiver>` for
`ACTION_SCREEN_OFF` — implicit broadcasts like this cannot start a background service on Android 8+,
and attempting to start the FGS from a receiver at that point risks
`BackgroundServiceStartNotAllowedException` on Android 14+ if the service isn't already running.
Instead, screen-off detection is handled as an internal state transition within the
already-running service: a `BroadcastReceiver` is registered dynamically via
`context.registerReceiver()` inside `onStartCommand()` (after `startForeground()` succeeds) and
unregistered in `onDestroy()`. See `docs/plans/mvp/fake-sleep-overlay/PLAN.md` for that receiver's
implementation.

## 5. Lifecycle Summary

| Trigger | Effect |
|---|---|
| In-app switch ON | `startForegroundService()` with `ACTION_START` → service starts, `startForeground()` called, dynamic receivers registered |
| In-app switch OFF | `startService()` with `ACTION_STOP` → `stopSelf()` called, service fully terminates, notification disappears |
| Notification action OFF (see onoff-notification-toggle) | Flag flipped only; service and notification untouched; **not** routed through `ACTION_STOP` |
| Process death / OS restart while `START_STICKY` | Service restarts with `null` intent; `onStartCommand` falls into the non-`ACTION_STOP` branch, re-establishing the FGS state and receivers |

## 6. Cross-Feature Touchpoints

- `fake-sleep-overlay`: registers `ScreenStateReceiver` inside this service, owns
  `FakeSleepSessionController`, briefly holds a processing `WakeLock` during `onScreenOff()` (§16;
  not session-long), posts the full-screen-intent notification that launches `FakeSleepActivity`.
- `notification-detection-dot-indicator`: runs as a separate system-bound
  `NotificationListenerService`, not owned by this FGS, but both read/write the shared
  `PendingNotificationsRepository` singleton within the same process.
- `permission-onboarding`: gates whether `MainActivity`'s in-app switch is enabled at all (service
  should not be started until notification-listener access is granted — the only permission that
  needs a custom onboarding flow now; see `docs/plans/mvp/permission-onboarding/PLAN.md`).
- `dismiss-and-peek-interaction`: adds tap/power-button handling on `FakeSleepActivity`'s content
  view — a separate `Activity` component, not owned by this service.
- `onoff-notification-toggle`: replaces `buildNotification()` above with the ON/OFF-action version
  and adds the `BroadcastReceiver` for the notification-action tap.
