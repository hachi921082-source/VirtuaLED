# dnd-handling — Implementation Plan

Covers: `docs/detailed-spec.md` §7 (DND Handling).

**Scope note**: this plan covers only the DND display toggle and DND-state detection. It reuses
the existing `NotificationListenerService` from
`docs/plans/mvp/notification-detection-dot-indicator/` rather than polling
`NotificationManager.getCurrentInterruptionFilter()` on a timer.

## Index

1. Module Structure
2. Data Structures
3. DND State Detection
4. Settings Toggle
5. Session Gating Integration
6. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.DndSettings` — DataStore-backed toggle, default `true` (hide during
  DND).
- `com.virtualed.app.data.DndState` — in-memory current-DND-active flag.
- Extends `NotificationSyncListenerService`
  (`docs/plans/mvp/notification-detection-dot-indicator/PLAN.md`) with
  `onInterruptionFilterChanged()` — does not add a second listener service.

## 2. Data Structures

```kotlin
object DndSettings {
    const val DEFAULT_HIDE_DURING_DND = true
    // DataStore Preferences: Boolean entry "hide_during_dnd", default true
    suspend fun getHideDuringDnd(context: Context): Boolean
    suspend fun setHideDuringDnd(context: Context, hide: Boolean)
}

object DndState {
    private var active = false
    @Synchronized fun isActive(): Boolean = active
    @Synchronized fun setActive(value: Boolean) { active = value }
}
```

## 3. DND State Detection

```kotlin
// Added to NotificationSyncListenerService (docs/plans/mvp/notification-detection-dot-indicator/PLAN.md):

override fun onListenerConnected() {
    super.onListenerConnected() // existing resync logic unchanged
    DndState.setActive(currentInterruptionFilter != NotificationListenerService.INTERRUPTION_FILTER_ALL)
}

override fun onInterruptionFilterChanged(interruptionFilter: Int) {
    DndState.setActive(interruptionFilter != NotificationListenerService.INTERRUPTION_FILTER_ALL)
    // if DND just turned on and hide-during-DND is enabled, end any active session — see §5
}
```

`INTERRUPTION_FILTER_ALL` means DND is off; any other filter value (`PRIORITY`, `NONE`, `ALARMS`)
means some form of DND is active — treated uniformly as "DND active," matching
`docs/detailed-spec.md` §7's binary phrasing (no distinction between DND sub-modes).

## 4. Settings Toggle

Single settings-screen switch: "Show indicator during Do Not Disturb" (inverse phrasing of the
internal `hide_during_dnd` flag — UI copy detail, not a data-model concern). Default OFF (i.e.
`hide_during_dnd = true`), matching `docs/detailed-spec.md` §7.

## 5. Session Gating Integration

Adds a guard to `docs/plans/mvp/fake-sleep-overlay/PLAN.md`'s screen-off gating check:

```kotlin
fun onScreenOff() {
    if (DndSettings.hideDuringDndCached && DndState.isActive()) return
    // ...existing checks (pending-empty, feature-enabled, quiet-hours if built)...
}
```

(`hideDuringDndCached` — the DataStore value is read once at service start and cached in memory,
refreshed on settings-screen save, so this synchronous gating check doesn't need a suspend read on
every screen-off event.)

If DND turns on mid-session (`onInterruptionFilterChanged` fires `true`) and
`hide_during_dnd` is enabled, the active session ends immediately — same asymmetric pattern already
used by `docs/plans/mvp/onoff-notification-toggle/PLAN.md` §2 and
`docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md` §6:

```kotlin
if (hideDuringDndCached && interruptionFilter != INTERRUPTION_FILTER_ALL && fakeSleepSessionController.sessionActive) {
    fakeSleepSessionController.endSession()
}
```

DND turning back off does not force re-engagement — a natural subsequent screen-off event (with
notifications still pending) re-evaluates normally.

## 6. Cross-Feature Touchpoints

- Extends `docs/plans/mvp/notification-detection-dot-indicator/`'s
  `NotificationSyncListenerService` — does not define a separate listener component.
- Adds a guard to `docs/plans/mvp/fake-sleep-overlay/`'s screen-off gating check, following the
  same pattern as `docs/plans/mvp/onoff-notification-toggle/` and
  `docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md`.
