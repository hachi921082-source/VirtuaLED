# auto-stop-conditions: quiet hours — Implementation Plan

Covers: `docs/detailed-spec.md` §6 (Quiet hours sub-condition and its inexact-`AlarmManager`
scheduling mechanism), `docs/DECISIONS.md` §10 (why exact alarms are avoided).

**Scope note**: this file covers only the quiet-hours window and its scheduling. Proximity and
battery threshold are separate, independent sibling files (`PLAN-1-proximity.md`,
`PLAN-2-battery.md`). This is the only feature (MVP or other) that uses `RECEIVE_BOOT_COMPLETED` —
that permission and its `<receiver>` are declared here, not in
`docs/plans/mvp/foreground-service-setup/PLAN.md` (which explicitly excludes it, since MVP has no
boot-triggered scheduling need).

## Index

1. Module Structure
2. Data Structures
3. Alarm Scheduling (inexact, §6)
4. Alarm Receiver and Daily Re-Arming
5. Boot Re-Registration
6. Session Gating Integration
7. Manifest Additions
8. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.QuietHoursSettings` — DataStore-backed window config.
- `com.virtualed.app.data.QuietHoursState` — in-memory active/inactive flag, read by the overlay
  gating check.
- `com.virtualed.app.autostop.QuietHoursScheduler` — schedules/cancels the two `AlarmManager`
  triggers.
- `com.virtualed.app.receiver.QuietHoursAlarmReceiver` — handles both start/end alarm firings.
- `com.virtualed.app.receiver.BootCompletedReceiver` — re-registers alarms after device reboot.

## 2. Data Structures

```kotlin
object QuietHoursSettings {
    data class Window(val enabled: Boolean, val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int)
    // DataStore Preferences: enabled (Boolean), start/end hour+minute (Int), e.g. default 01:00-07:00
    suspend fun getWindow(context: Context): Window
    suspend fun setWindow(context: Context, window: Window)
}

object QuietHoursState {
    private var active = false
    @Synchronized fun isActive(): Boolean = active
    @Synchronized fun setActive(value: Boolean) { active = value }
}
```

## 3. Alarm Scheduling (inexact, §6)

```kotlin
class QuietHoursScheduler(private val context: Context) {
    companion object {
        const val ACTION_QUIET_HOURS_START = "com.virtualed.app.action.QUIET_HOURS_START"
        const val ACTION_QUIET_HOURS_END = "com.virtualed.app.action.QUIET_HOURS_END"
    }

    fun reschedule(window: QuietHoursSettings.Window) {
        cancel(ACTION_QUIET_HOURS_START)
        cancel(ACTION_QUIET_HOURS_END)
        if (!window.enabled) {
            QuietHoursState.setActive(false)
            return
        }
        scheduleNextOccurrence(window.startHour, window.startMinute, ACTION_QUIET_HOURS_START)
        scheduleNextOccurrence(window.endHour, window.endMinute, ACTION_QUIET_HOURS_END)
    }

    fun scheduleNextOccurrence(hour: Int, minute: Int, action: String) {
        val triggerAtMillis = nextOccurrenceMillis(hour, minute) // next future timestamp for hour:minute
        val pendingIntent = pendingIntentFor(action)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        // inexact — no SCHEDULE_EXACT_ALARM permission needed, per docs/DECISIONS.md §10
    }

    private fun cancel(action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntentFor(action))
    }

    private fun pendingIntentFor(action: String): PendingIntent {
        val intent = Intent(context, QuietHoursAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
```

## 4. Alarm Receiver and Daily Re-Arming

`setAndAllowWhileIdle()` is one-shot — each trigger must reschedule its own next occurrence
(+1 day) after firing:

```kotlin
class QuietHoursAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            QuietHoursScheduler.ACTION_QUIET_HOURS_START -> QuietHoursState.setActive(true)
                .also { /* if a session is currently active, end it — see §6 */ }
            QuietHoursScheduler.ACTION_QUIET_HOURS_END -> QuietHoursState.setActive(false)
        }
        // re-arm this same trigger for tomorrow's occurrence
        val scheduler = QuietHoursScheduler(context)
        val window = runBlocking { QuietHoursSettings.getWindow(context) } // small, synchronous read
        if (intent.action == QuietHoursScheduler.ACTION_QUIET_HOURS_START) {
            scheduler.scheduleNextOccurrence(window.startHour, window.startMinute, intent.action!!)
        } else {
            scheduler.scheduleNextOccurrence(window.endHour, window.endMinute, intent.action!!)
        }
    }
}
```

## 5. Boot Re-Registration

```kotlin
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            val window = QuietHoursSettings.getWindow(context)
            QuietHoursScheduler(context).reschedule(window)
            pendingResult.finish()
        }
    }
}
```

Also re-invoked whenever the user changes the quiet-hours window in settings (calls
`QuietHoursScheduler(context).reschedule(newWindow)` directly, no reboot needed).

## 6. Session Gating Integration

Adds a `QuietHoursState.isActive()` guard to
`docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s screen-off gating check (alongside
`pendingRepo.isEmpty()` and, if built, `FeatureEnabledState.isEnabled()` from
`docs/plans/mvp/onoff-notification-toggle/PLAN.md`):

```kotlin
fun onScreenOff() {
    if (QuietHoursState.isActive()) return
    // ...existing checks...
}
```

If the quiet-hours **start** trigger fires while a session is already active (drawing in progress),
that session is ended immediately, same pattern as the feature-disable case in
`docs/plans/mvp/onoff-notification-toggle/PLAN.md` §2:

```kotlin
if (fakeSleepSessionController.sessionActive) fakeSleepSessionController.endSession()
```

The **end** trigger only clears the flag — no forced re-engagement, since a natural screen-off
event (with notifications still pending) will re-evaluate normally afterward.

## 7. Manifest Additions

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver android:name=".receiver.BootCompletedReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<receiver android:name=".receiver.QuietHoursAlarmReceiver" android:exported="false" />
```

## 8. Cross-Feature Touchpoints

- Adds a guard to `docs/plans/mvp/fake-sleep-overlay/`'s screen-off gating check and calls its
  `FakeSleepSessionController.endSession()`.
- Settings UI (time-window picker + enabled toggle) lives in the main settings screen alongside
  `PLAN-2-battery.md`'s threshold setting — no shared state between the two.
