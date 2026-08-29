# Decisions

<!-- Background, comparative analysis, and rejected alternatives for spec.md decisions. -->

## Index

1. Revival Background
   - 1a. Why the Project Was Renamed From "NoLED-Revive" to "VirtuaLED" Early
2. Why the Fake-Sleep Overlay Approach Is Necessary
3. Why Importance Detection Is User-Registered-Keyword-Only
4. Why Battery Optimization Exclusion Is Opt-In Only
5. Google Play General Release Is a Deferred Future Milestone
6. Why a Notification Action Toggle Replaces the Old Home-Screen Widget
7. Why Billing Is Deferred but the Settings UI Reserves a Placeholder
8. Design Philosophy: Simple but Properly Cover Necessary Features
9. Old NoLED APK Feature Audit — Superseded/Excluded Components
10. Why Quiet-Hours Scheduling Avoids `SCHEDULE_EXACT_ALARM`
11. Test/Support Device Scope Is Limited to Two Owned Devices; Chinese OEMs Excluded
12. Why Deep-Link Tap-to-Open Is Cut From Scope
13. Why Burn-In Reposition Interval/Style Customization Is Cut From Scope
14. Why the Peek-Preview Interaction Replaces Deep-Link, and Aggregates Rather Than Per-Icon
15. Why Blink Interval Is Kept User-Configurable (Unlike Burn-In Reposition Timing)
16. Why the Overlay Only Engages When a Notification Is Pending, and Exits the Instant None Remain
17. Why the WakeLock Renews Periodically Rather Than Only on Notification/Tap Events
   - See §18 — this section's WakeLock-holds-the-whole-session design is superseded.
18. Why the Fake-Sleep Mechanism Moved From a Raw Overlay Window to a Full-Screen Activity
19. Why the MVP Build Order Was Corrected From `fake-sleep-overlay`-First to `notification-detection-dot-indicator`/`foreground-service-setup`-First
20. Why the `other/`-Tier Visual Extension Surface Is a Separate State-Holder Class With Synchronized Blink and Combined Gating
21. Why App-to-Category Mapping Is User-Assigned, Not Auto-Inferred

## 1. Revival Background

**Correction (this entry supersedes an earlier, incorrect version of this section):** the user is
**not** the original developer of the app called "NoLED" (package `com.led.notify`). That app was
made by an unrelated third party. The user previously relied on it, but its publication has since
ended and, due to subsequent Android OS version changes, it no longer launches on current Android
versions. VirtuaLED (working name during early development: "NoLED-Revive"; see §1a) is an
independently-built app with similar functionality — **not** an official continuation, revival, or
fork of that third party's app — rebuilt from scratch with no affiliation with or rights to the
original.

This project's specification was initially seeded from a legacy design document the user wrote
before this project's `docs/` conventions (spec.md / detailed-spec.md / DECISIONS.md / QA.md
separation) existed. That document mixed "what" (spec), "how in detail" (detailed spec), and
"why" (rationale) together in one prompt-style file. This reorganization pass distilled it into
the current structure so future edits follow the standard separation going forward.

### 1a. Why the Project Was Renamed From "NoLED-Revive" to "VirtuaLED" Early

Because the user has no affiliation with or rights to the original "NoLED" app (see the
correction above), continuing to use the name "NoLED" / "NoLED-Revive", its icon, or its package
name (`com.led.notify`) for a **public** release would risk trademark/impersonation issues — it
could imply an affiliation with or continuation of the original third party's app that does not
exist. "NoLED-Revive" was fine as an internal working name early in development (low risk, not
user-facing), but the project's own name has now been decided: **"VirtuaLED"**, chosen because it
clearly conveys the app's function (a virtual/simulated LED notification indicator) while avoiding
any name overlap with the original third-party app.

The rename was made early — well before public release, rather than deferred to right before it —
because deciding the name early avoids rework later. A rename touches multiple layers at once:
source code identifiers/class names, the package name, all documentation, and (per the user's plan
to publish this project on GitHub) the eventual GitHub repository name/URL. Renaming any of these
after the fact (especially a published GitHub repo, where the URL becomes a public reference point
other people may link to) is more disruptive than settling the name once, early, before those
artifacts exist. This was the deciding factor for renaming now rather than waiting until
immediately before public release as originally considered.

The package name is still being finalized: `com.virtualed.app` is used as a placeholder in
`docs/spec.md` §2, pending the user confirming their preferred reverse-domain identifier (e.g. if
they own a personal domain they'd rather use instead). This also factors into the distribution
timeline decision (§5): keeping the release scope to personal use / limited testing for now gives
time to settle on the final package identifier and icon without rushed pressure from a public
listing.

## 2. Why the Fake-Sleep Overlay Approach Is Necessary

A physically fully-off screen cannot draw an overlay `View` — there is no rendering surface once
the display is truly powered down. To show an indicator at all, the app must keep the screen
technically lit (via `WakeLock`) while drawing a full-screen black overlay, so the visual result
looks like the screen is off except for the indicator. This is the foundational technical
constraint behind the "fake sleep" mechanism (see `docs/detailed-spec.md` §1) and is why the
feature inherently costs more battery than a true screen-off state — offset via the power/context
auto-stop conditions (proximity, battery threshold, quiet hours).

## 3. Why Importance Detection Is User-Registered-Keyword-Only

Automatic content parsing (NLP on notification text) or contacts-based sender detection were both
considered and rejected. Rationale: privacy and least-privilege. Auto-parsing would mean
processing notification content beyond what's needed for category/importance display, and
contacts access would require `READ_CONTACTS`, a sensitive permission with its own Play Store
scrutiny. Manual per-app keyword registration achieves the same "highlight important
notifications" goal without either permission or content analysis.

## 4. Why Battery Optimization Exclusion Is Opt-In Only

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is a Google Play restricted permission. There is a real
risk that Play Console review could reject a "keeps the overlay always active" justification as
insufficient for this permission's core-functionality bar. To reduce that risk and avoid
requesting more than necessary, the request dialog only appears after the user has explicitly
turned the feature ON — never as a forced/mandatory onboarding step.

## 5. Google Play General Release Is a Deferred Future Milestone

The legacy design document originally assumed general public release via Google Play (not
sideload or internal-only distribution) as the default distribution channel. A later discussion
resolving `docs/QA.md` question 10 revised this: general public release is deferred, not an
immediate v1 target. For now, distribution is personal use / limited testing only (sideloaded or
an internal testing track). Two reasons drove this:
- It defers the Play Console restricted-permission justifications, privacy policy page, and Data
  Safety form work (see below) until they're actually needed, rather than front-loading them
  before the app is even validated for personal use.
- It gives time to settle on the remaining public-facing icon and package identifier (see §1a —
  the app name itself, "VirtuaLED", is already decided) before anything goes out under a public
  listing, avoiding rushed decisions under public-facing pressure.

Once general public release is pursued, the following downstream requirements (identified from
the original assumption) still apply: the notification-access disclosure screen, the
`SYSTEM_ALERT_WINDOW` purpose justification in Play Console's restricted permissions form, the
`specialUse` Foreground Service subtype justification, a privacy policy page describing
local-only notification content handling, and accuracy of the Data Safety form (scope: package
name, category, and user-registered-keyword match results only — no notification content stored
or transmitted).

## 6. Why a Notification Action Toggle Replaces the Old Home-Screen Widget

The original NoLED app included a `NoLEDWidget` `AppWidgetProvider` for a home-screen ON/OFF
toggle. This was considered and rejected in favor of adding an ON/OFF `NotificationCompat.Action`
to the Foreground Service's already-existing persistent notification. Cost/benefit comparison:
- The FGS notification already exists as a required component (Android mandates a persistent
  notification for foreground services). Adding an action button to it is a small, near-zero-cost
  addition (a `PendingIntent` and a `BroadcastReceiver`).
- A home-screen widget would require an entirely new component set from scratch
  (`AppWidgetProviderInfo` XML, a widget layout, an `AppWidgetProvider` class) purely to
  reimplement the same ON/OFF toggle already achievable via the notification action.
- A widget also depends on the user manually placing it on their home screen — most users never
  do this, so real-world discoverability/usage is low relative to the implementation cost.
Given equivalent UX value at much lower cost, the widget is dropped and the notification action
is used instead (see `docs/detailed-spec.md` §18).

## 7. Why Billing Is Deferred but the Settings UI Reserves a Placeholder

The original NoLED app integrated Android In-app Billing V1 (AIDL-based,
`com.android.vending.BILLING`), which Google Play no longer supports — that integration is dead
and cannot be ported as-is. Any future billing would need to be built on the current Google Play
Billing Library (Kotlin), a materially different integration.

Decision: do not implement any billing/IAP logic in v1 — this avoids porting a defunct API and
avoids committing engineering effort to monetization before it's been decided whether to pursue
it at all. However, the settings screen reserves an empty "Donate/Pro" menu item placeholder (no
functional billing wired up), so that if billing is added later, it has an existing UI seam and
does not require restructuring the settings screen. Whether to ever actually implement paid
features remains an explicitly open future decision, not a commitment made by adding this
placeholder.

## 8. Design Philosophy: Simple but Properly Cover Necessary Features

Governing principle for this project, stated by the user during the old-APK feature audit:
**evaluate cost/benefit per feature, rather than either porting everything from the old app or
cutting everything down to a bare minimum.** Each legacy feature is judged individually on
whether it's still worth including, and if so, in what form.

This principle directly produced two decisions above:
- §6 (notification action vs. widget): same user-facing value, but the notification-action
  approach costs far less to implement than a full widget component, so it was chosen over both
  "port the widget as-is" and "drop quick-toggle entirely."
- §7 (billing placeholder): full billing implementation is skipped (not currently worth the
  cost/uncertainty), but an empty settings placeholder is kept (near-zero cost) so the option
  isn't foreclosed — neither a full port nor a full omission.

## 9. Old NoLED APK Feature Audit — Superseded/Excluded Components

The user supplied a decompiled manifest/component inspection (`aapt dump badging`/`xmltree`, not
full source) of an old NoLED APK (package `com.led.notify`, `targetSdkVersion` 14, ~2013-era).
This was audited feature-by-feature against the current spec. This decompiled inspection is used
strictly as a **behavioral/technical reference** (what components/permissions the old app used, to
inform which capabilities are still worth building) — never as a source of reused code, assets,
branding, or the package name itself. Given the authorship correction in §1/§1a, this restriction
now also has a legal rationale (avoiding any appearance of reusing a third party's IP), not just a
"rebuild clean" preference. Recorded here so a future reader understands why these old-APK
components are not referenced anywhere in `docs/spec.md` or `docs/detailed-spec.md`:
- `SMSReceiver`, `K9Receiver`, `GmailReceiver` (per-app broadcast receivers) and the
  `AccessibilityService`/`AccessibilityServiceJB` notification-detection fallback — fully
  superseded by `NotificationListenerService` (see `docs/spec.md` §3 Tech Stack). Their
  corresponding old-style permissions (`READ_SMS`, `READ_CONTACTS`, `READ_GMAIL`,
  `GET_ACCOUNTS`-style) are correctly excluded, consistent with the privacy rationale already
  recorded in §3 above.
- `LocaleReceiver`/`LocaleActivity` (twofortyfouram Tasker/Locale plugin integration) and
  `WeatherUpdateReceiver`/`WeatherSetHelper` (weather display) — out of scope, excluded (see
  `docs/spec.md` §5 Non-Goals).
- `com.android.vending.BILLING` (In-app Billing V1) — defunct, excluded; see §7 above.
- `NoLEDWidget` — excluded in favor of the notification action toggle; see §6 above.
- `MonitoredApps`/`AppSelector`/`CustomMonitoredApps`/`TestNotifications`/`CustomPref` (old
  Java/Preference-based settings activities) — the UX concept (choose monitored apps, preview
  indicator appearance, per-app keyword customization) is carried forward into the current spec,
  but none of the old code is reused; the settings screens are rebuilt in Jetpack Compose.

## 10. Why Quiet-Hours Scheduling Avoids `SCHEDULE_EXACT_ALARM`

An earlier pass specified `AlarmManager.setExactAndAllowWhileIdle()` for quiet-hours start/end
triggers. A later gap-review changed this to an inexact scheduling approach
(`AlarmManager.setAndAllowWhileIdle()`, or `WorkManager`). Rationale: quiet hours suppress/resume
the overlay on a coarse time window (e.g. 01:00-07:00) and do not need to-the-second precision, so
exact alarms buy no real user-facing benefit here. `SCHEDULE_EXACT_ALARM` is another Google Play
restricted permission with its own review scrutiny (similar in kind to
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, see §4) — requesting it for a feature that doesn't need
exactness would violate this project's least-privilege philosophy (see §3, §4) for no gain.
Dropping it also means the manifest permission list (`docs/detailed-spec.md` §2) stays free of an
otherwise-unnecessary restricted permission entirely.

## 11. Test/Support Device Scope Is Limited to Two Owned Devices; Chinese OEMs Excluded

As a solo personal project, physical test coverage cannot realistically span all Android OEMs.
The developer owns exactly two physical test devices: Sharp AQUOS sense9 and FCNT (formerly
Fujitsu) arrows We2 Plus, both Japan-domestic-brand devices.

Decision: testing and support scope is explicitly limited to these two devices. Chinese-brand
devices (e.g. Xiaomi, Huawei, OPPO, vivo) are excluded from the testing/support scope. Rationale:
these manufacturers are widely documented (see `dontkillmyapp.com`) for unusually aggressive
background-process/notification-listener killing that goes well beyond stock AOSP power
management, and working around each one individually would require significant OEM-specific
investigation and code branches this project does not have the testing capacity to verify. Sharp
and FCNT devices are not perfectly stock either, but are treated as close enough to AOSP behavior
that generic guidance ("check this app's battery optimization exclusion setting") suffices,
without per-OEM special-casing.

This device-scope limitation reinforces the donationware distribution model under consideration
(see `docs/spec.md` §4 Distribution): since universal device compatibility cannot be verified, no
compatibility guarantee is made beyond the two owned test devices — consistent with the existing
personal-use/limited-testing distribution scope (§5) rather than a paid app implying broader
supported-device commitments.

## 12. Why Deep-Link Tap-to-Open Is Cut From Scope

The original spec had tapping or long-pressing the indicator icon fire that notification's
`PendingIntent` after unlock, bringing the source app to the foreground (`docs/detailed-spec.md`
former §8/§14). This was cut during a scope-review pass questioning which features are genuine
added value versus core to the app's stated purpose (simulating a notification LED, `docs/spec.md`
§1).

Rationale: a physical notification LED is not interactive — it only signals that a notification
exists. Deep-linking pushes the app's role toward an interactive lock-screen notification hub, a
materially different value proposition than "simulate an LED," and it pulls in non-trivial
complexity: holding a `PendingIntent` in a State/Repository layer across the unlock boundary, and
hit-testing to distinguish taps on the moving icon from taps elsewhere. Removing it simplifies
dismiss to "any tap anywhere, or power button" with no icon-region hit-testing at all. Per the
design philosophy in §8 above (evaluate cost/benefit per feature, not blanket-port or blanket-cut),
this feature's cost-to-core-value ratio was judged too high to keep.

## 13. Why Burn-In Reposition Interval/Style Customization Is Cut From Scope

The underlying burn-in mitigation (periodically repositioning the indicator) is still needed — a
full-screen overlay left on-screen for extended periods is a real burn-in risk on OLED panels.
What was cut is exposing the reposition **interval** and **movement style** (instant jump vs.
gradual drift) as user-configurable settings (`docs/detailed-spec.md` §4). This requires a settings
sub-UI and extra code paths for no meaningful user-facing benefit — the mitigation only needs to
happen reliably, not be tunable. Fixed defaults (4-second interval, instant jump to a random
coordinate) are used instead, dropping the settings surface and the gradual-drift code path
entirely.

## 14. Why the Peek-Preview Interaction Replaces Deep-Link, and Aggregates Rather Than Per-Icon

After cutting deep-link (§12), a lighter alternative was designed: tapping while an indicator is
shown reveals a text preview (source app + title/text) instead of launching the app, so the user
can check notification content without ever exposing the real system lock screen or holding a
`PendingIntent` across the unlock boundary (see `docs/detailed-spec.md` §8). Two design questions
were resolved along the way:

- **Tap behavior in the Idle state (no pending notification):** treated as a no-op rather than a
  normal dismiss, so the fake-sleep illusion stays consistent — a real screen-off device (without
  tap-to-wake enabled) does not react to touch either. Power button remains the sole reliable way
  to wake/dismiss regardless of state, matching ordinary phone-wake expectations.
- **Multiple simultaneous notifications (see `docs/detailed-spec.md` §9):** rather than
  hit-testing tap coordinates against each independently-repositioning icon to determine which
  one to preview — which would reintroduce the coordinate-tracking cost cut alongside deep-link
  (§12) — a single position-independent tap reveals an aggregated list of *all* pending
  notifications. This preserves the "tap presence/count only, never coordinates" simplicity
  established for the single-notification case. The tradeoff (can't preview one notification
  without seeing the others listed too) was judged acceptable for a personal-project, at-a-glance
  ambient indicator, rather than reintroducing per-icon hit-testing for a marginal precision gain.

## 15. Why Blink Interval Is Kept User-Configurable (Unlike Burn-In Reposition Timing)

Unlike burn-in reposition's interval/style (§13, cut from scope — no individual-difference
rationale exists there, just an arbitrary cosmetic choice nobody has a real stake in), the
importance-sizing blink interval (`docs/detailed-spec.md` §5) directly determines whether the
user actually notices a notification they themselves flagged as important via keyword
registration. Individual sensitivity to a blinking cue genuinely varies person to person, so a
single fixed default cannot serve everyone — a concern that does not apply to burn-in's
reposition timing. Additionally, the settings-surface and persistence cost for this option must
be paid whenever it is built, now or later — unlike localization (cheap to defer, since string
externalization is already required baseline practice regardless of language count), there is no
"free later" architectural readiness here, so deferring buys nothing. Default remains a fast
interval (1 second) to address the "long interval = easy to miss on an unlucky glance" concern by
default, while the configurable range (e.g. 0.5-3 seconds) accommodates individual differences
beyond that default.

## 16. Why the Overlay Only Engages When a Notification Is Pending, and Exits the Instant None Remain

A pre-implementation gap review found that `docs/detailed-spec.md` §1 (screen-off trigger) and §8
(dismiss/peek interaction) had never been reconciled on one foundational question: does the
fake-sleep overlay engage on *every* screen-off, or only when there is something to show? Read
literally, §1's unconditional "detect screen-off → acquire `WakeLock` → draw overlay" combined
with §8's original "Idle" state ("no unread notification: tap is a no-op, overlay continues
running... until power button") implied the overlay would engage on every single screen-off and
hold the `WakeLock` indefinitely until the user pressed the power button — even with zero
notifications pending. That would mean the screen could never sleep normally while the feature is
enabled: a severe, previously-unstated battery cost, at odds with the project's established
least-privilege/battery-conscious philosophy (see §2, §4, §10).

Resolved with two linked decisions:
- The overlay only engages (`WakeLock` + black overlay) if at least one unread notification is
  pending at the moment of `ACTION_SCREEN_OFF`. If none, the device sleeps normally — no
  `WakeLock`, no overlay, no interception at all.
- Once engaged, if the last pending notification is cleared (read/removed elsewhere, per
  `docs/detailed-spec.md` §12) while the overlay is still showing, the overlay ends itself
  immediately (releases the `WakeLock`, real screen-off resumes) rather than lingering in a blank
  state waiting for the power button.

Together these bound the overlay's lifetime to exactly "there is something to show" — it never
exists as an ambient always-there black screen. This removed the standalone "Idle" state from
§8's model entirely: the overlay is only ever in Indicator Shown or Preview Shown, and the moment
neither applies, the session ends on its own rather than requiring an explicit dismiss.

## 17. Why the WakeLock Renews Periodically Rather Than Only on Notification/Tap Events

**Superseded by §18**: the mechanism this section describes (a `WakeLock` held for the whole
fake-sleep session, renewed periodically) assumed the fake-sleep surface was a raw
`WindowManager` overlay window that needed the screen kept lit by holding a `WakeLock`
throughout. That assumption turned out to be wrong for a different reason (§18: such a window
cannot render above a secure keyguard at all) — the corrected mechanism is a full-screen
`Activity` using `setTurnScreenOn(true)`, which turns the screen on as part of the Activity being
resumed, not via an app-held `WakeLock`. This section is kept for historical record of the
reasoning that led to "renew, don't just timeout" (still valid in spirit — see §18's brief
WakeLock note), but the "held for the whole session" premise no longer applies.

An initial pass (following a similar cost-conscious instinct as elsewhere in this project) chose
to renew the `WakeLock` only on new-notification-arrival or tap events, letting it lapse after a
~10-minute timeout if the phone sat untouched with no new activity — reasoning that this bounded
the worst-case battery cost of an ignored notification. On review, this was wrong: it means a
single unread notification the user hasn't yet noticed would cause the fake-sleep screen to
silently end after ~10 minutes, indistinguishable from the device having gone to real sleep —
directly contradicting the app's core premise (`docs/spec.md` §1): a real notification LED does
not turn itself off just because nobody has looked at it yet. This would specifically fail the
highest-value use case (an overnight or infrequently-checked notification).

The battery-cost concern that motivated the original event-only approach is already the dedicated
job of §6's auto-stop conditions (proximity sensor cover, battery threshold, quiet hours) — using
the `WakeLock`'s leak-prevention timeout as a second, accidental battery-limiting mechanism was
the wrong tool for that job and conflated two different concerns. Corrected design: the
`WakeLock` renews periodically for as long as the session remains active (`docs/detailed-spec.md`
§16), independent of whether new notifications or taps occur; the per-acquire timeout exists
solely as a safety net against a renewal-logic bug, not as an intended session limit.

## 18. Why the Fake-Sleep Mechanism Moved From a Raw Overlay Window to a Full-Screen Activity

While drafting `docs/plans/mvp/fake-sleep-overlay/PLAN.md`, a serious gap surfaced (logged as
`docs/QA.md` #14): since Android 5.0, the platform deliberately prevents
`TYPE_APPLICATION_OVERLAY` windows (the `SYSTEM_ALERT_WINDOW`-backed type the original mechanism,
§1/§2, was specified around) from rendering above a **secure** keyguard (PIN/pattern/password).
This is intentional OS security behavior. On a device with no secure lock configured, there is no
competing keyguard window, so the original mechanism would have worked — but on any device *with*
a secure lock (almost certainly including the project's own test devices), the real keyguard would
render on top of the overlay, silently breaking the core illusion (`docs/spec.md` §1) the first
time it mattered.

Two comparable, currently-maintained apps solving the same problem (LED Blinker,
`com.ledblinker`/`com.ledblinker.pro`; aodNotify, `com.jamworks.aod.notificationlightled`) were
checked for how they handle this. Both use an `AccessibilityService`-backed overlay window
(`TYPE_ACCESSIBILITY_OVERLAY`) to achieve it. This was considered and **rejected** for VirtuaLED:
this project already explicitly rejected `AccessibilityService` for notification detection on
privacy/least-privilege grounds (`docs/DECISIONS.md` §3, §9), and Accessibility permission is
subject to even heavier Google Play scrutiny than the restricted permissions already flagged
elsewhere (§4, §10) — adopting it here for a non-accessibility purpose would contradict the
project's own established principles and add more long-term Play Store risk, not less.

The corrected mechanism: a full-screen `Activity` (`FakeSleepActivity`) that calls
`setShowWhenLocked(true)` and `setTurnScreenOn(true)` in `onCreate()` — the officially-documented
API for showing UI above the (secure or not) lock screen, the same mechanism incoming-call and
alarm UIs use — launched via a notification's `setFullScreenIntent()`, requiring the
`USE_FULL_SCREEN_INTENT` permission (`docs/detailed-spec.md` §2) in place of `SYSTEM_ALERT_WINDOW`.

This was validated on a real device before committing the spec to it (rather than trusting the
API docs alone, given the two comparable apps' choice raised doubt about whether the "clean" API
path is actually sufficient in practice): a throwaway prototype (`prototype-keyguard-test/`, not
part of the app) was built and tested on the arrows We2 Plus test device (Android 16, API 36) with
a secure PIN lock actually configured. Two scenarios both succeeded:
1. An already-resumed `Activity` with these flags survived a screen-off/screen-on cycle, showing
   on top of the secure keyguard rather than being stopped/replaced by it.
2. The realistic production scenario — the app fully force-stopped (nothing in the foreground),
   screen already locked, then a `BroadcastReceiver` posts a full-screen-intent notification (the
   same shape as the real Foreground Service's screen-off handler would) — successfully launched
   the Activity on top of the secure keyguard with no prior foreground state and no user
   interaction. `USE_FULL_SCREEN_INTENT` was granted automatically on this sideloaded/adb-installed
   build; Android 14+'s policy restricting this permission's default grant to call/alarm-category
   apps (source: Android Open Source Project full-screen-intent-limits documentation) only bites
   at Google Play install time, so it is a future Play Store consideration (relevant once
   `docs/DECISIONS.md` §5's deferred general-release milestone is pursued), not a current blocker
   for personal/sideload distribution.

Consequence — this is a net **simplification**, not just a fix: since the Activity-based mechanism
does not depend on whether the lock is secure, there is no need for a hybrid (raw overlay for
no-secure-lock devices, Activity for secure-lock devices) as originally considered. The Activity
mechanism replaces the raw overlay window **universally**. `SYSTEM_ALERT_WINDOW` is dropped from
the manifest entirely (`docs/detailed-spec.md` §2) — one fewer restricted permission to carry into
an eventual Play Store submission. The `WakeLock`'s role shrinks accordingly (§17 above is
superseded): keeping the screen lit for the session's duration is now handled by
`setTurnScreenOn`, not by an app-held `WakeLock`; at most a short-lived `WakeLock` may still guard
the screen-off handler's brief processing window (checking pending-notification state, building
and posting the notification) before the system delivers the full-screen intent, per
`docs/detailed-spec.md` §16.

This device-level validation covered only the arrows We2 Plus (per the user's stated scope for
this round of testing) — not the Sharp AQUOS sense9, the project's other supported device
(`docs/DECISIONS.md` §11). Re-confirming there before relying on it in production is worth doing
opportunistically, though not treated as a blocker given both devices are already the same
close-to-AOSP tier of trust established in §11.

**Correction (found via full MVP on-device testing, Sharp AQUOS sense9)**: this section's claim
above — "keeping the screen lit for the session's duration is now handled by `setTurnScreenOn`,
not by an app-held `WakeLock`" — is wrong. `setTurnScreenOn(true)` only turns the screen on once,
as part of the Activity resuming; it does not suppress the device's normal idle
`screen_off_timeout` afterward. The original keyguard-prototype validation above was a short-lived
check and never ran long enough to observe this. On sense9 (`screen_off_timeout` = 15000ms),
logcat confirmed the OS fires a real `ACTION_SCREEN_OFF` ~15s after `FakeSleepActivity` is
displayed, purely from idle timeout — indistinguishable from a genuine power-button press, so
`dismiss-and-peek-interaction`'s power-button-dismiss logic (§6 of that PLAN.md) treated it as one
and ended the session early, silently defeating the app's core premise (§17 above: an LED that
doesn't turn off just because nobody has looked at it). This is universal to any device/timeout
setting (confirmed the arrows We2 Plus is also configured for 15s), not a sense9-specific quirk —
any finite OS screen-timeout value reproduces it, just later. Fixed by also holding
`WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on `FakeSleepActivity`'s window for the session's
duration (a real power-button press still delivers `ACTION_SCREEN_OFF` even with this flag held,
so §6's heuristic itself remains valid — only the idle-timeout false positive is eliminated).
`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §1/§4/§5 updated to match.

## 19. Why the MVP Build Order Was Corrected From `fake-sleep-overlay`-First to `notification-detection-dot-indicator`/`foreground-service-setup`-First

`docs/plans/README.md`'s original MVP build order (`fake-sleep-overlay` →
`foreground-service-setup` → `notification-detection-dot-indicator` → `permission-onboarding` →
`dismiss-and-peek-interaction` → `onoff-notification-toggle`) was asserted to reflect "real
dependencies between them, not arbitrary," but was never checked against each PLAN.md's actual
code-level references before being written down. A pre-implementation check (requested by the user
before handing the plans to `developer-lite`) found that assertion was wrong.

`fake-sleep-overlay/PLAN.md` §2 types `FakeSleepSessionController`'s constructor parameter directly
as `PendingNotificationsRepository` — a class defined in
`notification-detection-dot-indicator/PLAN.md` §2, not `fake-sleep-overlay`'s own plan. §3 of the
same plan also states its `ScreenStateReceiver` is "registered inside
`VirtuaLedForegroundService.onStartCommand()`" — a method defined in
`foreground-service-setup/PLAN.md` §2, which `fake-sleep-overlay` extends rather than redeclares.
Building `fake-sleep-overlay` first, as originally ordered, means writing code that references two
classes/methods that do not exist yet — it would not compile.

Corrected dependency graph, derived from each plan's actual type references (not just its prose
cross-references, which mention every related feature regardless of build-order need):
- `notification-detection-dot-indicator` and `foreground-service-setup` — no dependencies on any
  other MVP plan; both are self-contained.
- `permission-onboarding` — no dependencies on any other MVP plan; self-contained (gates
  `MainActivity` conceptually, but shares no types with the others).
- `fake-sleep-overlay` — requires `notification-detection-dot-indicator` (the
  `PendingNotificationsRepository` type) and `foreground-service-setup` (the `onStartCommand()`
  method it registers its receiver into).
- `dismiss-and-peek-interaction` — requires `fake-sleep-overlay` (extends `FakeSleepActivity`,
  calls into `FakeSleepSessionController` via `FakeSleepActivityHolder`) and
  `notification-detection-dot-indicator` (reads `PendingNotificationsRepository` for the preview
  text and timeout branch).
- `onoff-notification-toggle` — requires `foreground-service-setup` (extends `buildNotification()`)
  and `fake-sleep-overlay` (adds a gating guard to `onScreenOff()`, calls `endSession()`).

Corrected build order, each step compilable against only what already exists:
1. `notification-detection-dot-indicator`
2. `foreground-service-setup`
3. `permission-onboarding`
4. `fake-sleep-overlay`
5. `dismiss-and-peek-interaction`
6. `onoff-notification-toggle`

`permission-onboarding` is placed at step 3 (ahead of `fake-sleep-overlay`) rather than strictly by
compile dependency alone (it has none, so it could sit anywhere before step 4): doing so means
real-device testing of step 4 onward — which requires notification-listener access to already be
granted — is possible without a placeholder permission grant. This also happens to be the point
where the app's core end-to-end experience (screen off with a pending notification → full-screen
dot appears) first becomes testable, ahead of the interaction/toggle refinements in steps 5-6.

`docs/plans/README.md`'s MVP Build Order table and `docs/HANDOFF.md` were both updated to this
order. No other content in the six MVP `PLAN.md` files changed — this was a build-sequencing
correction, not a design change.

## 20. Why the `other/`-Tier Visual Extension Surface Is a Separate State-Holder Class With Synchronized Blink and Combined Gating

A `reviewer-lite` cross-consistency pass over `docs/plans/other/` (requested before starting
`other/`-tier implementation, per `docs/plans/README.md`'s own stated first step) found that 6 of
the 9 `other/` plans still described a pre-MVP `WindowManager`/`View`-based overlay architecture
(`OverlayWindowManager`, `OverlayIndicatorView`, `MultiIndicatorContainerView`,
`ImageView.setColorFilter()`, `ObjectAnimator`) that no longer exists — the actual, already-shipped
MVP mechanism is a Jetpack Compose-rendered `FakeSleepActivity` (§18) with a single
`previewLinesState: MutableState<List<String>?>`. Discussed directly with the user (not delegated)
before handing a rewrite to `developer-lite`, since it required deciding a new architecture, not
just fixing stale references.

**Why a separate `FakeSleepIndicatorUiState` class, not more properties on `FakeSleepActivity`
itself**: the six affected features (burn-in reposition, color, blink/size, category icons,
multi-notification slots, proximity-cover visibility) will keep accumulating visual-state needs over
time (e.g. dot shape, per-category color, fade transitions were named as plausible future asks, not
committed plans). Keeping `FakeSleepActivity` scoped to lock-screen lifecycle concerns
(`setShowWhenLocked`/`setTurnScreenOn`/window flags, §18) and moving pure visual state into its own
class avoids the Activity growing indefinitely as more `other/`-tier features land. The alternative
(folding all state directly into `FakeSleepActivity`) was rejected for this reason alone — there is
no functional difference between the two.

**Why blink is synchronized across all shown slots, not per-slot/staggered**: per-slot staggered
blinking was considered on the assumption it might reduce battery draw (spreading lit-pixel time out
over time rather than concentrating it). That assumption does not hold: total lit-pixel-time across
a full blink cycle is identical whether slots blink in sync or staggered — only the *peak*
instantaneous lit-pixel count differs, and that has no material effect on total energy draw for a
handful of small icons. Staggering also requires one timer/phase per slot instead of one shared
timer, meaning *more* separate recomposition events over time, not fewer — working against battery
efficiency rather than for it. Synchronized blink (one shared `blinkIntervalMs`, applied to every
currently-shown slot whenever any of them has `highImportance = true`) was chosen as strictly
simpler with no battery downside.

**Why `BurnInProtector`'s three pause/resume triggers (session active/inactive, `OverlayUiState`
`INDICATOR_SHOWN`/`PREVIEW_SHOWN`, proximity covered/uncovered) are combined through one
`recompute()` gate rather than each trigger calling `start()`/`stop()` directly**: an initial
`docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md` rewrite added a direct
`burnInProtector.start()` call on uncover, mirroring the existing peek-preview pause/resume calls.
This is unsafe once a third independent trigger exists: covering the proximity sensor while
`PREVIEW_SHOWN` is active, then uncovering, would call `start()` from the proximity trigger alone and
incorrectly resume repositioning during the text preview, even though the peek-preview trigger never
signaled a resume. Combining all three conditions into a single `shouldRun = sessionActive &&
uiState == INDICATOR_SHOWN && !proximityCovered` check, recomputed on every trigger, removes this
class of bug entirely — each trigger reports its own state change, and only the combined gate ever
calls the real `start()`/`stop()`.

**Why `multi-notification-stacking` reconciles onto the shared `IndicatorSlotUiState` without an
`isOverflow` flag**: an earlier draft of that plan (written before this reconciliation) had its own
`IndicatorSlot` data class with a separate `isOverflow: Boolean` field. That field was purely
descriptive and never actually consumed by the render path (its own §4/§6 only ever branched on
`category`/`count`), so it was dropped rather than carried into the shared
`IndicatorSlotUiState` — a collapsed "other" slot is already distinguishable as an ordinary slot with
`category = IndicatorCategory.OTHER`, with no separate flag needed.

Per the "Documentation Content Separation Rule" (`~/.claude/shared/dev-workflow.md` §8), this
rationale lives here, not in the PLAN.md files themselves — `docs/plans/mvp/fake-sleep-overlay/
PLAN.md` §7, `docs/plans/other/burn-in-protection/PLAN.md` §4, and
`docs/plans/other/multi-notification-stacking/PLAN.md` §2 state only the resulting What/How and link
back to this section.

## 21. Why App-to-Category Mapping Is User-Assigned, Not Auto-Inferred

Resolves `docs/QA.md` #13. `docs/plans/other/category-icons-and-assets/PLAN.md` §4 defined two
candidate implementations behind a shared `CategoryMapper` interface — a hardcoded
package-name-to-category lookup table, or a settings screen where the user assigns a category to
each monitored app, persisted via DataStore. The user chose the latter, asked directly (a product
decision, not a technical implementation detail).

Same underlying rationale as §3 (user-registered keywords over auto-parsing): a hardcoded table
only covers apps someone thought to add to it ahead of time — any app not listed silently falls
back to `OTHER` (`PLAN.md` §3) until a future code change adds it, which does not scale to
whatever apps a given user actually has installed. A user-assigned settings screen correctly
categorizes any app the user has, with no code change ever needed to support a new app — at the
cost of requiring a one-time manual setup pass per monitored app, judged worth it for correctness.
This also keeps a consistent pattern with `importance-based-sizing`'s `KeywordSettingsScreen`
(per-app settings, an installed-app picker via `PackageManager`, write-through persistence, no
navigation library) — `category-icons-and-assets` reuses that same UI shape rather than
introducing a new one.
