# permission-onboarding — Implementation Plan

Covers: `docs/detailed-spec.md` §15 (Permission Onboarding Flow).

**Revision note**: this plan previously also covered an overlay-permission
(`SYSTEM_ALERT_WINDOW`/`ACTION_MANAGE_OVERLAY_PERMISSION`) onboarding step. That permission and
step are gone entirely — the fake-sleep mechanism no longer uses a raw overlay window (see
`docs/plans/mvp/fake-sleep-overlay/PLAN.md`, `docs/DECISIONS.md` §18). `USE_FULL_SCREEN_INTENT`
(the permission the corrected mechanism uses instead) needs no onboarding step either — it is
confirmed auto-granted for this project's sideload/personal-use distribution (`docs/detailed-spec.md`
§15, `docs/DECISIONS.md` §18); this would only need revisiting if/when the deferred Play Store
release (`docs/DECISIONS.md` §5) is pursued.

**Scope note**: this plan covers only notification-listener access — the one remaining permission
that cannot be requested via a normal runtime permission dialog — and the Compose onboarding screen
that walks the user through granting it. `POST_NOTIFICATIONS` (a normal runtime-dialog permission
on API 33+) is requested via the standard `ActivityResultContracts.RequestPermission()` API, not
covered here in detail since it needs no custom flow. Battery-optimization exclusion
(`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is explicitly opt-in-only per `docs/DECISIONS.md` §4 and is
not part of this onboarding flow — it lives in the main settings screen, out of scope here.

## Index

1. Module Structure
2. Permission Check Helpers
3. Onboarding Screen Flow
4. Re-Check on Return From Settings
5. Gating the In-App Switch
6. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.permission.PermissionChecker` — stateless helper object, the notification-
  listener-access check.
- `com.virtualed.app.ui.OnboardingScreen.kt` — Compose screen, shown by `MainActivity` whenever
  the permission is not yet granted.

## 2. Permission Check Helpers

```kotlin
object PermissionChecker {
    fun hasNotificationListenerAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
```

## 3. Onboarding Screen Flow

```kotlin
@Composable
fun OnboardingScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    var listenerGranted by remember { mutableStateOf(PermissionChecker.hasNotificationListenerAccess(context)) }

    // §4 — re-check whenever the user returns from the Settings screen
    RecheckOnResume {
        listenerGranted = PermissionChecker.hasNotificationListenerAccess(context)
    }

    LaunchedEffect(listenerGranted) {
        if (listenerGranted) onAllGranted()
    }

    Column {
        PermissionCard(
            granted = listenerGranted,
            title = stringResource(R.string.onboarding_listener_title),
            description = stringResource(R.string.onboarding_listener_description),
            onGrantClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )
    }
}
```

`PermissionCard` is a small reusable Composable: shows title/description, a checkmark if `granted`,
otherwise a "Grant" button wired to `onGrantClick`. An already-granted card can render as a simple
confirmed row (per §15's "only guides to settings for permissions not yet granted") — with a single
permission left, this screen is a one-card flow rather than a checklist.

## 4. Re-Check on Return From Settings

The permission's grant state cannot be observed via a callback — the user grants it in a separate
system Settings screen and returns via back-navigation. Standard pattern: observe
`Lifecycle.Event.ON_RESUME` on the hosting Activity/Composable and re-run the check each time:

```kotlin
@Composable
fun RecheckOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
```

## 5. Gating the In-App Switch

`MainActivity` shows `OnboardingScreen` (instead of the main settings/switch screen) whenever
`!PermissionChecker.hasNotificationListenerAccess(context)`. Once `onAllGranted()` fires, the main
screen (with the in-app ON/OFF switch, see `docs/plans/mvp/onoff-notification-toggle/PLAN.md`)
becomes reachable. The in-app switch itself should not be presented as toggleable until this gate
passes — this plan does not redefine the switch's own logic, only establishes the precondition.

## 6. Cross-Feature Touchpoints

- Gates `docs/plans/mvp/onoff-notification-toggle/`'s in-app switch (service must not be started
  before notification-listener access is granted).
- Notification-listener access grant is required for
  `docs/plans/mvp/notification-detection-dot-indicator/`'s `NotificationListenerService` to bind at
  all. `docs/plans/mvp/fake-sleep-overlay/`'s `FakeSleepActivity`/full-screen-intent mechanism needs
  no onboarding-gated permission — `USE_FULL_SCREEN_INTENT` is auto-granted (see the revision note
  above).
