# auto-stop-conditions: proximity — Implementation Plan

Covers: `docs/detailed-spec.md` §6 (Proximity Sensor sub-condition only).

**Scope note**: this file covers only the proximity-cover auto-suspend condition. Battery
threshold and quiet hours are separate, independent sibling files in this same feature directory
(`PLAN-2-battery.md`, `PLAN-3-quiet-hours.md`) — no dependency between the three.

## Index

1. Module Structure
2. Sensor Monitor
3. Session Lifecycle Integration
4. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.autostop.ProximityMonitor` — wraps `SensorManager`/`SensorEventListener`.

## 2. Sensor Monitor

```kotlin
class ProximityMonitor(
    context: Context,
    private val onCoveredChanged: (covered: Boolean) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // if sensor == null (device has no proximity sensor), this feature silently no-ops
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val covered = event.values[0] < (sensor?.maximumRange ?: Float.MAX_VALUE)
        onCoveredChanged(covered)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
```

## 3. Session Lifecycle Integration

Suspends *drawing only* — the session (`WakeLock`, pending-notification tracking) stays active
while covered; only the indicator slot group is hidden/shown, per `docs/detailed-spec.md` §6
("suspend drawing while covered, resume on uncover"):

```kotlin
// FakeSleepSessionController.startSession() (docs/plans/mvp/fake-sleep-overlay/PLAN.md §2):
proximityMonitor.start()

// FakeSleepSessionController.endSession():
proximityMonitor.stop()

// Callback wiring, during controller init:
proximityMonitor = ProximityMonitor(context) { covered ->
    FakeSleepActivityHolder.indicatorUiState?.indicatorVisible?.value = !covered
    // The dot/preview-text content itself (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md's
    // previewLinesState) is untouched — indicatorVisible only gates the slot group's own
    // rendering, so whatever was showing before cover reappears unchanged on uncover; no state is
    // lost or needs restoring separately.
}
```

`indicatorVisible` toggling (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7) is independent of the
session lifecycle: session state, pending-notification tracking, and the `WakeLock` guard (§4 of
that plan) all stay active while `indicatorVisible = false` — only the rendered slot group is
hidden. This is a temporary visibility suspend within an active session, not the session-end
teardown (`endSession()`, §6 of that plan).

## 4. Cross-Feature Touchpoints

- Hooks into `docs/plans/mvp/fake-sleep-overlay/`'s session start/end lifecycle.
- Interacts with `docs/plans/other/burn-in-protection/`'s reposition loop (paused while covered,
  resumed on uncover, same as the peek-preview pause pattern already established there).
