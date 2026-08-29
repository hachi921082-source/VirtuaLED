# notification-detection-dot-indicator — Implementation Plan

Covers: `docs/detailed-spec.md` §12 (Notification Removal Sync).

**Scope note**: this plan covers only notification detection/tracking (the
`NotificationListenerService` and the shared pending-notification store it maintains) — not overlay
drawing (`docs/plans/mvp/fake-sleep-overlay/`), not tap/peek text rendering
(`docs/plans/mvp/dismiss-and-peek-interaction/`), not per-app category assignment
(`docs/plans/other/category-icons-and-assets/` — open question, see `docs/QA.md` #13). **MVP
simplification**: any number of simultaneous pending notifications is exposed to the rest of the
app only as "empty" vs. "non-empty" plus a flat list — no per-app icon/collision/stacking logic,
no count badge (that visual layer is `docs/plans/other/multi-notification-stacking/`, deferred).

## Index

1. Module Structure
2. Data Structures
3. NotificationListenerService Implementation
4. Own-Notification Exclusion (structural necessity)
5. Resync on `onListenerConnected()` (§12)
6. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.service.NotificationSyncListenerService` — extends
  `NotificationListenerService`; system-bound (declared via
  `BIND_NOTIFICATION_LISTENER_SERVICE`, see `docs/plans/mvp/foreground-service-setup/PLAN.md` §1 for
  the permission, plus its own `<service>` manifest entry with an
  `<intent-filter>` for `android.service.notification.NotificationListenerService`).
- `com.virtualed.app.data.PendingNotificationsRepository` — process-wide in-memory singleton
  (Kotlin `object`), the single source of truth for "what notifications are currently pending."
  Both `NotificationSyncListenerService` and `VirtuaLedForegroundService`
  (`docs/plans/mvp/foreground-service-setup/`) run in the same process by default, so a plain
  singleton is sufficient — no cross-process IPC needed.

## 2. Data Structures

```kotlin
data class PendingNotificationInfo(
    val key: String,          // StatusBarNotification.key — unique per posted notification
    val packageName: String,
    val appLabel: String,     // resolved via PackageManager.getApplicationLabel(), for peek preview
    val title: String?,       // Notification.EXTRA_TITLE
    val text: String?,        // Notification.EXTRA_TEXT
    val postTime: Long,
)
```

```kotlin
object PendingNotificationsRepository {
    private val notifications = mutableMapOf<String, PendingNotificationInfo>()
    private var onBecameEmpty: (() -> Unit)? = null
    private var onBecameNonEmpty: (() -> Unit)? = null

    @Synchronized fun isEmpty(): Boolean = notifications.isEmpty()
    @Synchronized fun snapshot(): List<PendingNotificationInfo> = notifications.values.toList()

    @Synchronized fun put(info: PendingNotificationInfo) {
        val wasEmpty = notifications.isEmpty()
        notifications[info.key] = info
        if (wasEmpty) onBecameNonEmpty?.invoke()
    }

    @Synchronized fun remove(key: String) {
        if (notifications.remove(key) != null && notifications.isEmpty()) onBecameEmpty?.invoke()
    }

    @Synchronized fun replaceAll(infos: List<PendingNotificationInfo>) { // used by resync, §5
        val wasEmpty = notifications.isEmpty()
        notifications.clear()
        infos.forEach { notifications[it.key] = it }
        when {
            notifications.isEmpty() && !wasEmpty -> onBecameEmpty?.invoke()
            notifications.isNotEmpty() && wasEmpty -> onBecameNonEmpty?.invoke()
        }
    }

    fun setOnBecameEmptyListener(cb: () -> Unit) { onBecameEmpty = cb }
    fun setOnBecameNonEmptyListener(cb: () -> Unit) { onBecameNonEmpty = cb }
}
```

`onBecameEmpty` is consumed by `docs/plans/mvp/fake-sleep-overlay/PLAN.md` (session auto-end, §6 of
that plan) and by `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` (Preview-Shown → session-end
transition). `onBecameNonEmpty` is not required by any MVP feature as of this writing (the overlay
only reads pending state reactively at screen-off time, per §1's gating check) but is exposed for
forward-compatibility since it costs nothing extra to keep.

## 3. NotificationListenerService Implementation

```kotlin
class NotificationSyncListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // §4 — exclude our own FGS notification
        PendingNotificationsRepository.put(sbn.toPendingInfo(this))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        PendingNotificationsRepository.remove(sbn.key)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val active = activeNotifications
            .filter { it.packageName != packageName }
            .map { it.toPendingInfo(this) }
        PendingNotificationsRepository.replaceAll(active) // §5 resync
    }
}

private fun StatusBarNotification.toPendingInfo(context: Context): PendingNotificationInfo {
    val label = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)
    return PendingNotificationInfo(
        key = key,
        packageName = packageName,
        appLabel = label,
        title = notification.extras.getString(Notification.EXTRA_TITLE),
        text = notification.extras.getString(Notification.EXTRA_TEXT),
        postTime = postTime,
    )
}
```

## 4. Own-Notification Exclusion (structural necessity)

`VirtuaLedForegroundService`'s own persistent notification (`docs/plans/mvp/foreground-service-setup/`)
is itself visible to `NotificationListenerService` callbacks, since it is posted to the same
notification shade. Without excluding `sbn.packageName == packageName`, the repository would never
report `isEmpty() == true` while the feature is running — permanently breaking the §1 gating check
and the "ends the instant none remain" behavior (`docs/DECISIONS.md` §16). This filter is applied in
both `onNotificationPosted`/`onNotificationRemoved` and the `onListenerConnected()` resync.

## 5. Resync on `onListenerConnected()` (§12)

`onListenerConnected()` fires on initial bind and again after any listener reconnect (including
after an OS/process-kill restart of the listener component). Per `docs/detailed-spec.md` §12, the
pending set must be re-derived from `getActiveNotifications()` at that point rather than assumed
empty — `replaceAll()` (§2 above) implements this by fully replacing the in-memory map and firing
the appropriate became-empty/became-non-empty callback based on the before/after state, so the
`fake-sleep-overlay` gating check and its "ends the instant none remain" logic stay correct even
across a listener restart.

## 6. Cross-Feature Touchpoints

- Read by `docs/plans/mvp/fake-sleep-overlay/PLAN.md` for the screen-off gating check
  (`isEmpty()`) and session-end trigger (`onBecameEmpty` callback).
- Read by `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` for the peek-preview's aggregated
  text list (`snapshot()`) and for the Preview-Shown timeout's "revert vs. end" branch.
- `appLabel`/`packageName` fields are also the future join point for
  `docs/plans/other/category-icons-and-assets/` (per-app category mapping — mechanism TBD, see
  `docs/QA.md` #13) and `docs/plans/other/importance-based-sizing/` (per-app keyword matching
  against `title`/`text`).
