# auto-stop-conditions: battery threshold — Implementation Plan

Covers: `docs/detailed-spec.md` §6 (Battery threshold sub-condition only).

**Scope note**: this file covers only the low-battery auto-stop condition. Proximity and quiet
hours are separate, independent sibling files (`PLAN-1-proximity.md`, `PLAN-3-quiet-hours.md`).

## Index

1. Module Structure
2. Data Structure
3. Battery Level Receiver
4. Auto-Stop Behavior
5. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.BatteryThresholdSettings` — DataStore-backed threshold, 1% steps,
  default 20.
- `com.virtualed.app.autostop.BatteryLevelReceiver` — dynamically-registered `BroadcastReceiver`
  for `Intent.ACTION_BATTERY_CHANGED` (a sticky broadcast — registering it also delivers the
  current level immediately, so no separate initial-state poll is needed).

## 2. Data Structure

```kotlin
object BatteryThresholdSettings {
    const val DEFAULT_THRESHOLD_PERCENT = 20
    // DataStore Preferences: Int entry "battery_threshold_percent", 1-100, step 1, default 20
    suspend fun getThresholdPercent(context: Context): Int
    suspend fun setThresholdPercent(context: Context, percent: Int)
}
```

## 3. Battery Level Receiver

```kotlin
class BatteryLevelReceiver(
    private val thresholdPercent: Int,
    private val onBelowThreshold: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        if (percent <= thresholdPercent) onBelowThreshold()
    }
}
```

Registered in `VirtuaLedForegroundService.onCreate()`
(`docs/plans/mvp/foreground-service-setup/PLAN.md`) via
`context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))`, unregistered in
`onDestroy()`. Because the broadcast is sticky, registering it while the battery is already below
threshold delivers `onBelowThreshold()` immediately — this is intentional (§4 covers what that
triggers), not an edge case needing special handling.

## 4. Auto-Stop Behavior

Per `docs/detailed-spec.md` §6 ("auto-stop the Foreground Service"), a literal full stop —
equivalent to the in-app-switch-OFF path (`docs/plans/mvp/onoff-notification-toggle/PLAN.md` §5),
not the notification-action-OFF pause path:

```kotlin
onBelowThreshold = {
    stopSelf() // service fully terminates, notification disappears — same effect as switch OFF
}
```

A one-time user-facing notice (e.g. a toast or a final notification update explaining "stopped:
battery below Nx%") is a reasonable addition but not specified by `docs/detailed-spec.md` §6 — left
to implementation-time discretion, non-blocking for this plan.

## 5. Cross-Feature Touchpoints

- Registered/unregistered by `docs/plans/mvp/foreground-service-setup/`'s
  `VirtuaLedForegroundService` lifecycle.
- Threshold setting UI lives alongside other auto-stop settings in the main settings screen — no
  shared state with `PLAN-1-proximity.md` or `PLAN-3-quiet-hours.md`.
