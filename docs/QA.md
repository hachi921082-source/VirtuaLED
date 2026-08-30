# Questions for the User

<!-- Unresolved questions requiring the user's own judgment, appended by designer-lite. -->

## Initial Spec Questions

Background: `NO-LED` was originally a brand-new, empty repo with no source code or prior
discussion. The user has since supplied a legacy design document (written before this project's
`docs/` conventions existed) plus context establishing that this app is independently built, with
functionality similar to a third-party "NoLED" app the user used to rely on (see the authorship
correction in `docs/DECISIONS.md` §1). All 12 questions below are now resolved; see `docs/spec.md`
and `docs/DECISIONS.md` for details.

### Resolved

1. ~~What does NO-LED do? What problem does it solve, and for whom?~~ Resolved — see
   `docs/spec.md` §1 (Purpose).
2. ~~What does the name "NO-LED" refer to?~~ Resolved — "NO" + "LED": the app simulates a
   notification LED on devices that lack a physical one.
3. ~~Target platform?~~ Resolved — Android (Kotlin), see `docs/spec.md` §2.
4. ~~Is this related to any existing project, e.g. `SimpleAlarm`?~~ Resolved — not related to
   `SimpleAlarm`. It is an independently-built app with functionality similar to a third-party
   "NoLED" app the user used to rely on, whose publication ended and which no longer launches on
   current Android versions. See `docs/DECISIONS.md` §1.
5. ~~Preferred language and framework?~~ Resolved — Kotlin/Android native + Jetpack Compose, see
   `docs/spec.md` §3.

### Resolved (continued)

6. ~~Is there an existing design, mockup, or reference implementation elsewhere that should inform
   the spec?~~ Resolved — no additional reference material beyond what's already in use: the
   legacy design document and the original APK's decompiled manifest/component inspection. Both
   are already reflected in the current docs (`docs/DECISIONS.md` §1, §9).
7. ~~What is the minimum viable feature set for a first version?~~ Resolved — all current
   `docs/spec.md` Core Features ship in v1; no feature deferral to a later version.
8. ~~Any other known constraints (offline-only, licensing, etc.)?~~ Resolved — no offline-only
   requirement or other licensing constraint was raised. The one real constraint is the
   naming/branding independence requirement established by the authorship correction: avoid
   implying affiliation with or continuation of the original NoLED app; do not reuse its name,
   icon, or package name; the decompiled APK is treated strictly as a behavioral/technical
   reference, never as a source of reused code or assets. See `docs/DECISIONS.md` §1, §1a, §9.
9. ~~Minimum supported SDK version (`minSdkVersion`)~~ Resolved — API 31 (Android 12); see
   `docs/spec.md` §2. Compatibility handling for the `specialUse` Foreground Service type (only
   available from API 34) is documented in `docs/detailed-spec.md` §2.
10. ~~Play Store publish timeline — is general public release the immediate goal for the first
    working version, or is a later milestone?~~ Resolved — general public release is deferred; for
    now distribution is personal use / limited testing only (sideloaded or internal testing
    track). See `docs/spec.md` §4 (Core Features) and `docs/DECISIONS.md` §5.
11. ~~Is the original "NoLED" app's source code, branding (app name/icon), or Play Store package
    name available to reference or reuse for this revival, or is everything being rebuilt
    independently with a new identity?~~ Resolved — fully new: the app is now named "VirtuaLED"
    (was "NoLED-Revive" as a provisional working name early in development), with a new icon (TBD)
    and new package name. Nothing from the original APK's branding, package name
    (`com.led.notify`), or source code is reused. See `docs/DECISIONS.md` §1 (the authorship
    correction) and §1a. Note: the package name currently used in `docs/spec.md` §2,
    `com.virtualed.app`, is a **placeholder** pending the user confirming their preferred
    reverse-domain identifier (e.g. if they own a personal domain they'd rather use instead).
12. ~~Exact asset design (icon artwork) source — will the user supply/commission indicator icon
    artwork, or should placeholder vector assets be implemented first pending final art?~~
    Resolved — interim indicator icons (dot/phone/mail/SNS/calendar/other) are sourced from a
    freely-licensed icon set (e.g. Material Symbols, Apache 2.0), tinted via the color
    customization feature. Final custom-branded icons are deferred until closer to the
    rename/public-release milestone. See `docs/detailed-spec.md` §11.

All 12 initial spec questions are now resolved.

## Implementation Planning Questions

Background: arose during the pre-PLAN.md gap-review pass (checking for undefined UI, spec gaps,
and contradictions before writing the MVP feature plans).

### Open

16. Does an OEM's own ambient/doze wakefulness path (`PowerManager` `WAKEFULNESS_DOZING`, distinct
    from full `WAKEFULNESS_ASLEEP`) ever prevent `FakeSleepActivity`'s full-screen-intent
    notification from auto-launching over the lock screen?

    Background: during the MVP integration test on arrows We2 Plus (`M06`), one screen-off cycle
    left the device in `WAKEFULNESS_DOZING` (confirmed via `dumpsys power`) with `FakeSleepActivity`
    never launching (`FakeSleepActivity` absent from `dumpsys activity activities`, keyguard genuinely
    locked). `Settings.Secure.doze_enabled=1` on this device, but no user-facing "Always-on
    display"/"Ambient display" toggle was found in Settings to test disabling it — the OEM (Fujitsu)
    may implement its own always-enabled doze component (e.g. for a notification-pulse feature) with
    no user opt-out. Logcat also showed a `WAKE_REASON_PLUGGED_IN` wake-from-Dozing event around the
    same window, consistent with the adb USB cable's charging-state flicker interacting with the
    doze cycle. After a full reset (USB unplugged/replugged, app force-stopped and relaunched), every
    subsequent screen-off cycle went straight from `Awake` to the notification launching
    `FakeSleepActivity` correctly (no repeat of the Dozing dead-end) for the remainder of the test
    session — so the failure was not reproduced again, and it isn't confirmed whether the cause was
    the doze component itself, the USB-cable interference, or a transient combination of both.

    Not confirmable purely from this session's evidence — needs either a repeat with adb over Wi-Fi
    (no USB cable in the loop) or a battery-only (no adb attached) test, ideally with logcat capture
    of a reproduction, before deciding whether `FakeSleepSessionController` needs to handle
    `WAKEFULNESS_DOZING` explicitly (e.g. via `PowerManager.isInteractive()`/wakefulness-aware
    retry) or whether this was purely a test-harness artifact. Not a blocker — MVP integration test
    passed once retried under normal (cable-free-equivalent) conditions.

    **Reproduced a second time** later in the same session (unrelated `phone-call-overlay-suppression`
    ad hoc test, §15's real-device evidence), again with the device in `WAKEFULNESS_DOZING` and
    `FakeSleepActivity` never launching. This time the fix was app force-stop + relaunch **without**
    unplugging the USB cable, and it worked — every screen-off cycle for the rest of the session
    launched `FakeSleepActivity` correctly. This weakens the USB-cable-flicker hypothesis (the fix
    worked without touching the cable) and points more toward some in-app or in-process state (stale
    receiver registration, a wedged `PendingNotificationsRepository`/session-controller state, etc.)
    that a fresh process clears — still not conclusively isolated, but "kill and relaunch the app"
    is now a confirmed reliable workaround either way.

19. `auto-stop-conditions`/quiet-hours (`docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md`,
    merged PR #14): the "alarm fires for real while a fake-sleep session is actively displayed →
    session force-ends" path was verified by code review only (mirrors the already-proven
    `NotificationSyncListenerService`/DND `endSession()` reach-in pattern exactly), not by an actual
    timed real-time test on-device — the test device's aggressive re-lock behavior interrupted the
    timed setup needed (arm a near-future alarm, keep a session visibly active, wait for the real
    firing) during this feature's live-testing session. What *was* directly confirmed live on real
    hardware: `AlarmManager` scheduling/re-arming correctness and idempotency (`dumpsys alarm`
    inspection across repeated reschedules), and — after a real defect was found and fixed — that
    quiet-hours suppression correctly engages immediately following a cold FGS start already inside
    a configured window (before/after comparison, two consecutive real screen-off events). Not a
    blocker (the reach-in code path is structurally identical to an already-live-verified one), but
    worth an opportunistic real-time confirmation if a device is available and cooperative.

20. `indicator-color-customization` (merged PR #12) applies exactly one shared `tintColor` value
    across every indicator display mode — the plain dot and all five category icons alike (see
    `docs/detailed-spec.md` §19: "Applies uniformly across indicator display modes"). This was
    confirmed intentional (spec'd this way from the start, not a later scope cut) when
    `category-icons-and-assets` live-device testing (§15 in `docs/HANDOFF.md`) surfaced a user
    expectation mismatch — the user had assumed each category icon could get its own independent
    color. Not implemented, not currently planned; the user chose (2026-08-30) to record it here as
    an open request rather than block PR #15 on it or implement it immediately. If pursued later,
    the change would touch `docs/detailed-spec.md` §19, `docs/plans/other/indicator-color-customization/PLAN.md`,
    and `FakeSleepIndicatorUiState.tintColor` (would need to become per-category, e.g. a
    `Map<IndicatorCategory, Color>` or five separate `MutableState`s instead of one shared value).
    **Follow-up (2026-08-30)**: during a freemium-monetization discussion (`docs/DECISIONS.md` §22),
    the user deprioritized this in favor of source-app-icon display (see item 22 below) — judged
    more valuable to users for the same implementation slot. Still not implemented, still not
    currently planned; left here as a candidate for later, not picked up by §22's premium-feature
    work.

22. Should this app's freemium tier (`docs/DECISIONS.md` §22) gate on a real Google Play Billing
    purchase, or something simpler for a personal/low-price app (e.g. a one-time unlock code, or
    even trusting a settings toggle)? Not decided — §22 deliberately defers this by building the
    premium feature (source-app-icon indicator display) behind a local `isPremiumUnlocked` flag
    that defaults to unlocked, so the real gating mechanism can be decided later without touching
    the feature code itself. Also open: exact price point (the user mentioned ¥100–200 as a rough
    figure, not finalized) and whether moving to real billing requires the Play Console
    distribution-model transition `docs/DECISIONS.md` §5 already deferred.

### Resolved (continued)

13. ~~How does an installed third-party app get mapped to one of the five indicator categories
    (Phone/Mail/SNS/Calendar/Other — see `docs/detailed-spec.md` §3)? Is this user-assigned per
    app (a settings screen where the user picks a category for each monitored app), or
    auto-inferred (e.g. a hardcoded package-name-to-category table)?~~ Resolved — user-assigned,
    asked directly. A settings screen lets the user assign a category to each monitored app,
    persisted via DataStore, reusing `importance-based-sizing`'s `KeywordSettingsScreen` UI shape
    (installed-app picker, write-through persistence, no navigation library). See
    `docs/DECISIONS.md` §21.

14. ~~Does the fake-sleep mechanism work on devices with a secure lock screen (PIN/pattern/
    password)?~~ Resolved — no, not via the originally-specified raw `TYPE_APPLICATION_OVERLAY`
    window (confirmed: Android deliberately blocks this above a secure keyguard). Resolved by
    switching the mechanism to a full-screen `Activity` using `setShowWhenLocked`/
    `setTurnScreenOn`, launched via a full-screen-intent notification — validated on a real device
    (arrows We2 Plus, Android 16/API 36, secure PIN lock configured) via a throwaway prototype,
    covering both an already-foregrounded activity surviving a screen-off/on cycle and the
    realistic cold-background-trigger scenario. This mechanism works universally (secure lock or
    not), so no secure-lock-specific branching or hybrid mechanism is needed. See
    `docs/DECISIONS.md` §18 for full detail, and `docs/detailed-spec.md` §1/§2/§16/§17 for the
    updated spec. Not yet re-confirmed on the Sharp AQUOS sense9 (the project's other test device)
    — worth doing opportunistically, not currently a blocker.

15. ~~With the fake-sleep mechanism now `FakeSleepActivity` (an Activity) rather than a raw overlay
    window, does §13's phone-call-overlay-suppression mechanism (explicit `TelephonyManager`/
    `TelephonyCallback` call-state detection) still describe the right approach, or does normal
    Android Activity/task-stack behavior already achieve the required outcome with no call-state
    detection needed?~~ Resolved via real-device test on arrows We2 Plus (`M06`): a real call was
    placed while `FakeSleepActivity` was showing the dot over a secure lock. **Ringing**: the
    incoming-call banner (reject/answer buttons) overlaid the dot without taking foreground —
    `FakeSleepActivity` stayed `topResumedActivity`/focused — but the reject button was still
    tappable and worked. **Answered**: a genuine full-screen in-call Activity *did* take over the
    foreground (confirmed visually), and once the call ended, `FakeSleepActivity` resumed
    automatically (same `ActivityRecord`) with no app-side code involved — ordinary Android
    Activity/task lifecycle handled the exact scenario `phone-call-overlay-suppression` exists to
    prevent. Conclusion: drop the plan's `TelephonyManager`/`CallStateMonitor`/`READ_PHONE_STATE`
    mechanism and rely on `FakeSleepActivity`'s own `onPause()`/`onResume()` instead; revise
    `docs/plans/other/phone-call-overlay-suppression/PLAN.md` accordingly before its
    `reviewer-lite` cross-consistency pass.

18. ~~`BatteryLevelReceiver`'s threshold value is captured as a local `val` when the receiver is
    registered in `VirtuaLedForegroundService.onCreate()`, with no live-refresh mechanism — unlike
    `DndSettings.hideDuringDndCached`, which `setHideDuringDnd()` updates in place so a running
    service picks up a DND-toggle change immediately. If the user drags the battery-threshold
    slider while the feature is already running, the active `BatteryLevelReceiver` keeps using the
    stale threshold until the feature is toggled OFF and back ON (re-running `onCreate()`).~~
    Resolved (2026-08-30), as part of a generic fix for the recurring "settings toggle doesn't
    live-refresh mid-session" pattern (see also #21/#23 below): `BatteryThresholdSettings` now
    carries a `DndSettings`-style live cache (`thresholdPercentCached`, updated immediately in
    `setThresholdPercent()`, refreshed via a new `refreshCache()` at service startup);
    `BatteryLevelReceiver.onReceive()` reads it fresh on every `ACTION_BATTERY_CHANGED` broadcast
    instead of a value captured once at construction, so a mid-session slider change now takes
    effect on the very next broadcast without needing to toggle the feature off and on.

21. ~~`multi-notification-stacking` (PR #16)'s `CountBadgeSettings.enabledCached` toggle doesn't
    take effect live while `FakeSleepActivity` is already showing — same class of gap as #18
    above. `SlotIcon` reads `CountBadgeSettings.enabledCached` as a plain Kotlin property at
    composition time rather than a Compose `State`, so toggling the setting from
    `MainSwitchScreen` mid-session only becomes visible at the next unrelated recomposition —
    confirmed via live on-device testing (arrows We2 Plus / `M06`): the badge appeared only after
    a power-button screen off→on cycle, not immediately on toggling the switch.~~ Resolved
    (2026-08-30): `CountBadgeSettings.enabledCached` is now backed by Compose `mutableStateOf`
    instead of a plain `@Volatile var`. `SlotIcon` reads it during composition, so it is now
    subscribed to Compose's snapshot system and automatically recomposes when the value changes —
    including from a background coroutine (`setEnabled()`/`refreshCache()`). No call-site changes
    were needed anywhere else in the codebase; plain non-Compose reads/writes behave identically
    to before.

23. ~~`source-app-icon-indicator-mode`'s "show app's own icon" toggle
    (`AppIconIndicatorSettings.enabledCached`) doesn't take effect live while `FakeSleepActivity`
    is already showing — same class of gap as #18/#21 above. `SlotIcon` reads it as a plain
    Kotlin property at composition time rather than a Compose `State`, so toggling the setting
    mid-session only becomes visible at the next unrelated recomposition.~~ Resolved (2026-08-30):
    same fix as #21, applied to `AppIconIndicatorSettings.enabledCached`. `PremiumState` was
    deliberately left untouched by this fix — there is currently no live in-session way to toggle
    premium status (no billing UI exists yet), so there was no live-refresh bug to fix there.

17. ~~`VirtuaLedForegroundService.onCreate()` launches `DndSettings.refreshCache()` fire-and-forget
    (not awaited) before `onStartCommand()`/`ScreenStateReceiver` registration proceeds. If a
    screen-off event fires before that DataStore read completes, `FakeSleepSessionController
    .onScreenOff()` uses the still-default `hideDuringDndCached = true` for that one event.~~
    Resolved (2026-08-30): `ScreenStateReceiver` registration (`onStartCommand()`) now happens
    inside the same coroutine as `DndSettings.refreshCache()`, after it completes, instead of the
    refresh running as a separate fire-and-forget launch in `onCreate()`. Since the receiver can't
    receive `ACTION_SCREEN_OFF` until it's actually registered, this structurally eliminates the
    race rather than just narrowing its window — a screen-off event can no longer arrive before
    the DND cache is populated. A boolean guard (`screenStateReceiverRegistrationStarted`) replaces
    the old `screenStateReceiver == null` check to prevent double-registration across repeated
    `onStartCommand()` calls, since the field itself is now only assigned after registration
    actually completes (mirroring the same async-registration-then-field-assignment shape already
    used for `batteryLevelReceiver`, `docs/QA.md` #18's now-resolved fix).
