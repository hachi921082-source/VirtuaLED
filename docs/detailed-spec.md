# Detailed Specification

<!-- Elaboration of spec.md to a level usable for implementation plans (docs/plans/<feature-name>/PLAN.md). Created/updated by designer-lite as features are planned. -->

## Index

1. Fake-Sleep Overlay Mechanism
2. Manifest Permissions and Foreground Service Declaration
3. Indicator Categories and Assets
4. Burn-In Protection Cycle
5. Importance-Based Sizing
6. Power/Context Auto-Stop Conditions
7. DND Handling
8. Dismiss and Peek Interaction
9. Multi-Notification Stacking
10. Localization File Structure
11. Asset Spec
12. Notification Removal Sync
13. Phone-Call Overlay Suppression
14. Unlock Auto-Dismiss
15. Permission Onboarding Flow
16. WakeLock Discipline
17. Foreground Service Startup Model
18. Notification Action Toggle (FGS Notification)
19. Indicator Color Customization

## 1. Fake-Sleep Overlay Mechanism

1. Detect `ACTION_SCREEN_OFF` broadcast (via a receiver dynamically registered inside the
   already-running Foreground Service — see §17).
2. Check whether at least one unread notification is currently pending (see §12). If none, do
   nothing — the device sleeps normally, no interception at all. See `docs/DECISIONS.md` §16 for
   why this gating exists.
3. If one or more unread notifications are pending: post a high-priority notification whose
   `setFullScreenIntent()` targets `FakeSleepActivity` — a full-screen `Activity` that calls
   `setShowWhenLocked(true)` and `setTurnScreenOn(true)` in `onCreate()`. This renders the
   Activity's black background + indicator UI on top of the lock screen, **secure or not**, with
   no raw `WindowManager` overlay window and no `SYSTEM_ALERT_WINDOW` permission involved.
4. Net effect: screen appears off (it's `FakeSleepActivity`'s black background), but is actually
   still lit, allowing the indicator to render — only for as long as there is something to show.
   See §8 for the session's exit condition (it ends on its own once nothing remains to show, not
   just on explicit dismiss).

This replaces an earlier raw-`WindowManager`-overlay design that turned out not to work on
devices with a secure lock screen (PIN/pattern/password) — Android deliberately prevents
`TYPE_APPLICATION_OVERLAY` windows from rendering above a secure keyguard. Validated on a real
device before finalizing; see `docs/DECISIONS.md` §18 for the investigation and the validation
result, and `docs/QA.md` #14 (resolved).

## 2. Manifest Permissions and Foreground Service Declaration

Required `<uses-permission>` entries:
- `USE_FULL_SCREEN_INTENT` (launches `FakeSleepActivity` over the lock screen — see §1;
  auto-granted on sideloaded/adb-installed builds, but Android 14+ restricts default grant to
  call/alarm-category apps at Google Play install time — a consideration for the deferred general
  Play Store release, `docs/DECISIONS.md` §5/§18, not for current personal-use distribution)
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `WAKE_LOCK` (much narrower role now — see §16)
- `POST_NOTIFICATIONS` (required Android 13+; needed both for the Foreground Service's own
  notification and for §1's full-screen-intent notification)
- `RECEIVE_BOOT_COMPLETED` (required so §6's quiet-hours triggers are re-scheduled after device
  reboot, via `ACTION_BOOT_COMPLETED`)

**`SYSTEM_ALERT_WINDOW` is no longer needed** — the originally-specified raw overlay window
mechanism was replaced (§1, `docs/DECISIONS.md` §18) before it was ever built.

**`BIND_NOTIFICATION_LISTENER_SERVICE` is not a `<uses-permission>` entry** — it is a
signature/system-level permission the app declares on its own `NotificationListenerService`
component instead, so the OS can verify the binding:

```xml
<service
    android:name=".service.NotificationSyncListenerService"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

An app can never hold this permission by requesting it; there is nothing for a `<uses-permission>`
entry to do here. Actual user-granted listener access is a separate settings-screen toggle (see
`docs/plans/mvp/permission-onboarding/PLAN.md`), unrelated to this manifest declaration.

`specialUse` Foreground Service type requires a `<property>` declaration inside `<service>`:

```xml
<service
    android:name=".VirtuaLedForegroundService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="screen-notification-indicator-overlay" />
</service>
```

Compatibility note (minSdkVersion 31 vs. `specialUse` FGS type): the static manifest
`android:foregroundServiceType="specialUse"` attribute and the
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property above require no conditional/branched manifest
entries, even though `specialUse` itself only exists from API 34 onward — older OS versions
safely ignore unrecognized manifest attributes/properties. The one code-level branch required is
in the Foreground Service's `startForeground()` call:
- API >= 34: call the type-parameterized overload, passing
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
- API < 34 (down to minSdkVersion 31): call the overload without a type parameter.

## 3. Indicator Categories and Assets

Category table (default target app example, asset filename convention):

| Category | Example target app | Asset filename |
|---|---|---|
| Phone | Phone/Dialer | `ic_notify_phone.xml` |
| Mail | Email client | `ic_notify_mail.xml` |
| SNS | Messaging/social apps | `ic_notify_sns.xml` |
| Calendar | Calendar app | `ic_notify_calendar.xml` |
| Other | Uncategorized | `ic_notify_other.xml` |

Fallback default: any app not yet mapped to a category (the mapping mechanism itself is still
open — see `docs/QA.md` #13) renders as "Other" (`ic_notify_other.xml`). This applies regardless
of how — or whether — per-app category assignment ends up being implemented.

## 4. Burn-In Protection Cycle

- Reposition interval: fixed at 4 seconds, not user-configurable
- Movement style: fixed as instant jump to a random X/Y coordinate (no gradual-drift option, no
  settings UI) — see `docs/DECISIONS.md` §13

## 5. Importance-Based Sizing

- Settings UI: register "important sender/keyword" strings per monitored app (e.g. register
  "Mom" under the LINE app)
- Match rule: if a notification's `EXTRA_TITLE`/`EXTRA_TEXT` partially matches a registered
  keyword for that app, treat as high importance. Matching is case-insensitive and normalizes
  full-width/half-width (zenkaku/hankaku) character variants as equivalent before comparing. No
  regular-expression support — plain substring match only, consistent with this project's
  simplicity-first philosophy (`docs/DECISIONS.md` §8).
- Apps with zero registered keywords are always normal importance
- High importance: indicator at 1.5x size, or blink at a user-configurable interval (default 1
  second; e.g. 0.5-3s range, persisted setting) — see `docs/DECISIONS.md` §15
- Normal importance: standard size, static (no blink)

## 6. Power/Context Auto-Stop Conditions

- Proximity: monitor `Sensor.TYPE_PROXIMITY`; suspend drawing while covered, resume on uncover
- Battery: monitor `ACTION_BATTERY_CHANGED`; auto-stop the Foreground Service below a
  configurable threshold, 1% steps, default 20%
- Quiet hours: auto-stop drawing during a configured time window (e.g. 01:00-07:00)
- Quiet hours mechanism: inexact scheduling rather than continuous clock polling — quiet hours do
  not need to-the-second precision, and this avoids requiring the `SCHEDULE_EXACT_ALARM`
  permission. Schedule one inexact alarm at the window start time and one at the window end time
  via `AlarmManager.setAndAllowWhileIdle()` (or equivalent `WorkManager` one-time work); each
  trigger flips a quiet-hours-active state flag (suppress overlay on start-trigger, resume on
  end-trigger). Re-schedule both triggers on boot (`ACTION_BOOT_COMPLETED`, requires the
  `RECEIVE_BOOT_COMPLETED` permission — see §2) and whenever the user changes the configured
  window in settings.

## 7. DND Handling

- Settings toggle, default ON = do not show indicator during DND (follows OS setting)
- User may switch to always show during DND
- DND state determined via `NotificationManager.getCurrentInterruptionFilter()`

## 8. Dismiss and Peek Interaction

Two-state model: **Indicator Shown** / **Preview Shown**. There is no standalone "idle, nothing
to show" state — per §1, the overlay only exists while at least one unread notification is
pending, and ends itself the moment none remain (see `docs/DECISIONS.md` §16), rather than
lingering in a blank state. No `PendingIntent` state-holding and no coordinate-based hit-testing
against the (burn-in-repositioned) indicator's position at any point — taps are counted, never
located. See `docs/DECISIONS.md` §12 (why deep-link tap-to-open was cut) and §14 (why this peek
design replaces it).

- **Indicator Shown** (one or more unread notifications, see §9 for stacking rules — this is the
  overlay's entry state per §1, since it never engages with zero pending): first tap anywhere on
  the overlay → reveal a preview listing all currently pending notifications (source app +
  title/text per notification, one line each — not per-icon; see §9), and start a timeout timer.
  Power button → full dismiss. If the last pending notification is cleared externally (§12) while
  in this state → the overlay ends itself immediately (real screen-off resumes), no dismiss
  animation/state needed.
- **Preview Shown** (timer running): second tap anywhere → full dismiss (exit fake-sleep, real
  lock screen shown). Timeout (default 3-5 seconds) → if unread notifications remain, revert to
  Indicator Shown; if none remain, the overlay ends itself immediately (same as above). Power
  button → full dismiss. If the last pending notification is cleared externally while in this
  state → the overlay ends itself immediately.

## 9. Multi-Notification Stacking

- Max simultaneous indicators: e.g. up to 4; beyond that, collapse into a single "other"
  category icon
- Overlap avoidance: grid split, or collision detection during random placement
- Single app with multiple notifications: collapse to one icon, no count badge by default (count
  badge configurable as an option)
- Peek interaction (§8): the first tap reveals a list of *all* currently pending notifications
  (one line each), regardless of which icon — if any — was tapped. No per-icon coordinate
  hit-testing.

## 10. Localization File Structure

- `values/strings.xml` — default/English
- `values-ja/strings.xml` — Japanese
- `values-zh-rCN/strings.xml` — Simplified Chinese (use `zh-rCN`, not Traditional Chinese
  folder naming)

## 11. Asset Spec

- Vector Asset (SVG / Android Vector Drawable XML) preferred
- PNG fallback: fully transparent background (alpha channel), all-white (`#FFFFFF`) foreground,
  tinted via `ImageView.setColorFilter()`
- Density buckets: MDPI (24x24) / HDPI (36x36) / XHDPI (48x48) / XXHDPI (72x72) / XXXHDPI
  (96x96 px)
- Icon source (interim, v1): indicator category icons (dot/phone/mail/SNS/calendar/other) are
  sourced from a freely-licensed icon set (e.g. Material Symbols, Apache 2.0 license) rather than
  custom-commissioned artwork. These serve as placeholder/interim icons, tinted via the indicator
  color customization feature (§19). A final custom-branded icon set is deferred until closer to
  the public-release milestone (see `docs/spec.md` Purpose note and `docs/DECISIONS.md` §1a).
- Default indicator color before any color-customization UI exists (§19 not yet built): white
  (`#FFFFFF`), matching the PNG fallback foreground color above.

## 12. Notification Removal Sync

`NotificationListenerService` implements both `onNotificationPosted()` and
`onNotificationRemoved()`. When a notification is read/swiped away (including from another
device or the tray), the corresponding overlay indicator disappears immediately.

On `onListenerConnected()` (listener (re)connect, including after OS/process-kill restart), the
pending-notification set is re-synced via `getActiveNotifications()` rather than assumed empty —
this is what the §1 screen-off gating check and §8's "ends the instant none remain" behavior
read from, so a stale/empty state here would make the overlay silently fail to engage or exit
too early.

## 13. Phone-Call Overlay Suppression

The system's incoming-call full-screen intent (`Notification.Builder.setFullScreenIntent()`)
must not be obstructed. During an active call (detected via `TelephonyManager` call state
listener), the overlay is temporarily hidden and restored after the call ends.

Note: this mechanism (explicit `TelephonyManager` call-state detection driving explicit
hide/restore) was written against the earlier raw-overlay-window design (§1 as it stood before
`docs/DECISIONS.md` §18). Whether it's still the right approach — versus relying on
`FakeSleepActivity`'s own `onPause()`/`onResume()` lifecycle now that the fake-sleep surface is an
Activity — is open; see `docs/QA.md` #15 (not yet resolved).

## 14. Unlock Auto-Dismiss

- On `ACTION_USER_PRESENT` (auth success without touching `FakeSleepActivity`, e.g. biometric
  unlock), auto-finish `FakeSleepActivity` (§1).
- No `PendingIntent` state-holding is needed (deep-link removed from scope — see
  `docs/DECISIONS.md` §12).

## 15. Permission Onboarding Flow

Notification-listener access cannot be requested via a normal runtime permission dialog. Lead-in
flow directs the user to system settings and re-checks on return:

```kotlin
// Notification listener access permission
Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
```

Compose onboarding screen checks the permission's grant state and only guides to settings if not
yet granted. (The overlay-permission step from an earlier design is gone — `SYSTEM_ALERT_WINDOW`
is no longer used at all, see §1/§2. `USE_FULL_SCREEN_INTENT` needs no onboarding step either: it
was confirmed auto-granted for this project's sideload/personal-use distribution, `docs/DECISIONS.md`
§18 — this would need revisiting only if/when the deferred Play Store release, `docs/DECISIONS.md`
§5, is pursued.)

## 16. WakeLock Discipline

**Narrowed scope** (see `docs/DECISIONS.md` §17, §18): keeping the screen lit for the whole
fake-sleep session is no longer a `WakeLock` responsibility — `FakeSleepActivity`'s
`setTurnScreenOn(true)` (§1) handles that as part of the Activity being resumed, so no
session-long or periodically-renewed `WakeLock` is needed. What may still be worth a `WakeLock` is
a short-lived guard over the screen-off handler's brief processing window (checking
pending-notification state via §12, building and posting the full-screen-intent notification)
against the CPU suspending before that work completes — a classic short `PARTIAL_WAKE_LOCK`
pattern for a broadcast/callback handler, not a session-length mechanism.

- If used, always specify a timeout via `acquire(timeoutMs)` on the order of seconds (a brief
  processing guard, not a session-length value)
- Always call `release()` deterministically via `try/finally`
- Guard against double-acquire via an `isHeld` check

## 17. Foreground Service Startup Model

Foreground Service starts persistently the moment the user turns the feature ON in-app (not
per screen-off event). Screen-off detection is handled as an internal state transition within
the already-running service, to avoid Android 14's `BackgroundServiceStartNotAllowedException`
when starting a service from a background broadcast receiver.

On a qualifying screen-off (§1), the service does not start any new component from the
background directly — it posts the full-screen-intent notification (§1, §2), and the OS itself
launches `FakeSleepActivity` via that notification's `PendingIntent`. This sidesteps background
Activity-start restrictions the same way incoming-call and alarm apps do, without needing the
service to hold any special background-launch allowance itself.

## 18. Notification Action Toggle (FGS Notification)

- The Foreground Service's persistent notification includes a `NotificationCompat.Action`
  labeled ON/OFF, backed by a `PendingIntent` targeting a `BroadcastReceiver`.
- Both the in-app ON/OFF switch and the notification action control the same feature-enabled
  state flag, but they are **not symmetric** in what else happens when turning the feature OFF:
  - **In-app switch → OFF**: sets the flag to OFF *and* calls `stopSelf()` on the Foreground
    Service — the service fully terminates and its persistent notification disappears. To turn
    the feature back on, the user must reopen the app (there is no longer a notification action
    to tap).
  - **Notification action → OFF**: sets the flag to OFF *only* — the `BroadcastReceiver` behind
    it must **not** call `stopSelf()`. The Foreground Service keeps running and its notification
    stays visible, with the action's label/icon/content text updated to reflect the OFF state, so
    the user can tap the same action again later to flip back ON without opening the app.
- Example walkthrough:
  1. User enables the feature via the in-app switch → Foreground Service starts; notification
     shows an "OFF" action (tap to turn the feature off).
  2. User taps the notification's "OFF" action → flag flips to OFF; overlay drawing and
     quiet-hours suppression stop; the service and its notification remain running, and the
     action now reads "ON" (tap to turn the feature back on).
  3. User taps the notification's "ON" action → flag flips back to ON; overlay drawing resumes.
     The service was never restarted, since it never stopped.
  4. Alternatively, from step 1's ON state, the user instead opens the app and flips the in-app
     switch OFF → flag flips to OFF *and* the service calls `stopSelf()`; the notification
     disappears entirely. Re-enabling requires reopening the app and using the in-app switch
     again.

## 19. Indicator Color Customization

- Settings UI: a color picker (palette swatches) plus a HEX-input text field, so the user can set
  the indicator's display color freely.
- Persistence: stored via DataStore/SharedPreferences alongside other settings.
- Applied at draw time: `ImageView.setColorFilter()` for the View-based overlay indicator (or the
  equivalent Compose `Color` tint if the indicator is rendered in Compose), using the persisted
  color value. Applies uniformly across indicator display modes (simple dot and per-category
  icons, see §3).
