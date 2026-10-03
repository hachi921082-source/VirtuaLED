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
20. Source-App Icon Indicator Mode
21. Premium Feature Gating (Pre-Billing)

## 1. Fake-Sleep Overlay Mechanism

1. Trigger point — either of:
   - Detect `ACTION_SCREEN_OFF` broadcast (via a receiver dynamically registered inside the
     already-running Foreground Service — see §17), or
   - A new notification arrives while the screen is already off and no session is active yet
     (checked via `PowerManager.isInteractive()`). See `docs/DECISIONS.md` §16 for why this second
     trigger exists (GitHub issue #30).
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
  Play Store release, `docs/DECISIONS.md` §5/§18, not for current personal-use distribution).
  On Android 14+ the grant is **revocable after install** — by the user through Settings > Apps >
  Special app access > Full-screen notifications, and in practice also by an OS update
  (`docs/QA.md` #24). The app therefore checks it at runtime; see §15.
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
- High importance: indicator at 1.5x size. Blink was removed — see `docs/DECISIONS.md` §15
  (superseded)
- Normal importance: standard size

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

- `values/strings.xml` — default/Japanese (every feature's strings were added here directly as
  implemented; see `docs/plans/other/localization-additional-languages/PLAN.md`'s correction note
  for why this superseded the originally-planned "default = English" design)
- `values-en/strings.xml` — English
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
must not be obstructed by the fake-sleep dot. No explicit call-state detection is needed to
guarantee this: `FakeSleepActivity`'s own `Activity` lifecycle already handles it. Confirmed via
real-device testing (`docs/QA.md` #15, resolved) — an answered call's own full-screen in-call
Activity takes over the foreground, pausing `FakeSleepActivity`, which resumes automatically once
the call ends. See `docs/plans/other/phone-call-overlay-suppression/PLAN.md` for detail; no
`TelephonyManager`/`READ_PHONE_STATE` mechanism is implemented.

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
is no longer used at all, see §1/§2.)

**Full-screen-intent step (GitHub issue #49).** The earlier assumption that `USE_FULL_SCREEN_INTENT`
never needs a check (auto-granted for sideload builds, `docs/DECISIONS.md` §18) still holds at
install time, but on Android 14+ the grant can be revoked later (`docs/QA.md` #24). The app checks it
with `PermissionChecker.canUseFullScreenIntent()` (`NotificationManager.canUseFullScreenIntent()` on
API 34+, always `true` below 34) and sends the user to
`Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (`package:` URI, which opens this app's page
directly):

- **Onboarding** shows a second card for it and completes only when both notification listener
  access and full-screen intent are granted. When FSI is already granted, the card simply shows
  "granted".
- **Root routing** still gates on notification listener access alone. If only FSI is revoked after
  onboarding, the user is not sent back to onboarding; `MainSwitchScreen` handles it instead.
- **`MainSwitchScreen`** rechecks on every `ON_RESUME`. While FSI is denied:
  - the main ON switch is disabled, and a warning with an "Open settings" button is shown;
  - if the feature was already ON when the revocation is detected, it is switched OFF
    automatically, so the service never keeps running while sessions can't appear.

  Turning the system switch back on restores FSI immediately, with no reinstall needed (verified
  live, issue #49).

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

- Settings UI: a row of exactly 5 preset color swatches — no free-form HEX/text input. Reduced
  from an original wider palette plus HEX field; see `docs/DECISIONS.md` §23 for why.
- Presets: the exact 5 colors are an implementation-time visual-design detail, not a product
  decision.
- Persistence: stored via DataStore/SharedPreferences alongside other settings.
- Applied at draw time: `ImageView.setColorFilter()` for the View-based overlay indicator (or the
  equivalent Compose `Color` tint if the indicator is rendered in Compose), using the persisted
  color value. Applies uniformly across indicator display modes (simple dot and per-category
  icons, see §3).
- Does **not** apply to the source-app icon indicator mode (§20) — a real app icon is a multi-color
  image; a single-color filter would defeat the purpose of showing it. `tintColor` continues to
  apply only to the plain dot and the fixed category icon set.

## 20. Source-App Icon Indicator Mode

Premium feature — see `docs/DECISIONS.md` §22 for the monetization background and §21 below for
the gating mechanism.

- New setting: "Show app's own icon" boolean, default `false` (opt-in even once unlocked, since
  it's a bigger visual departure from the fixed dot/category-icon look than a color change).
  Forced off when `isPremiumUnlocked` (§21) is `false`, regardless of this setting's stored value.
- Icon source: `PackageManager.getApplicationIcon(packageName)`, where `packageName` is the same
  most-recently-posted-notification's source app already resolved for category lookup (§3's
  existing recompute path) — no new plumbing needed to obtain it.
- Rendering: drawn at the same size as the plain dot / category icon, scaled to fit. Not tinted by
  `tintColor` (§19, see the note added there).
- Fallback: if the source app's icon can't be resolved (app uninstalled since the notification
  posted, `NameNotFoundException`, etc.), fall back to the existing category icon / dot rendering
  for that slot — never a broken or blank indicator.
- Interaction with categories (§3): this mode replaces the category *icon* for rendering, but does
  not remove or change the underlying per-app category assignment — a category is still resolved
  and remains available to other consumers.
- **Interaction with `multi-notification-stacking`** (resolved 2026-08-30, by reading the
  already-implemented `IndicatorSlotBuilder.kt`, not just its `PLAN.md`): grouping there is
  strictly per-app (`pending.groupBy { it.packageName }`), so each of the up to 4 real slots
  already maps 1:1 to exactly one app — no ambiguity for those slots. Two concrete follow-ups this
  implies:
  - `IndicatorSlotUiState` (`FakeSleepIndicatorUiState.kt`) does not currently carry a
    `packageName` field — `IndicatorSlotBuilder.build()` resolves `pkg` per group but only uses it
    for `category`, discarding it afterward. This mode needs `packageName` added to that data
    class and written through in `build()` — a small, additive change, not a redesign.
  - The 5th-and-beyond "OTHER" overflow slot represents multiple apps combined, so it has no single
    representative app icon. This mode must always fall back to the existing category icon (OTHER)
    or dot for that specific slot, regardless of whether app-icon mode is enabled — the other (up
    to 4) real slots are unaffected by this fallback.

## 21. Premium Feature Gating (Pre-Billing)

- Mechanism: one process-wide `PremiumState` (mirrors the existing `FeatureEnabledState`/
  `DndState` in-memory-singleton-plus-DataStore-cache pattern) exposing `isPremiumUnlocked():
  Boolean`.
- Persistence: DataStore boolean (e.g. key `premium_unlocked`).
- Default value: `true` (unlocked) — see `docs/DECISIONS.md` §22 for why: no real billing exists
  yet, so defaulting to unlocked lets §20 be fully built, reviewed, and tested on-device now,
  without a paywall UI or purchase flow blocking that work.
- Consumers: currently only §20 checks this flag. Any future premium feature should read the same
  `PremiumState`, not introduce a second flag.
- Explicitly out of scope here (deferred — see `docs/DECISIONS.md` §22 and `docs/QA.md` #22):
  Google Play Billing Library integration, purchase verification, restore-purchase flow, and
  pricing/tiering mechanics. When that work happens, only the code that writes `PremiumState`'s
  persisted value changes (from a hardcoded default to a verified purchase result) — no consumer
  of `isPremiumUnlocked()` needs to change.
