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

13. How does an installed third-party app get mapped to one of the five indicator categories
    (Phone/Mail/SNS/Calendar/Other — see `docs/detailed-spec.md` §3)? Is this user-assigned per
    app (a settings screen where the user picks a category for each monitored app), or
    auto-inferred (e.g. a hardcoded package-name-to-category table)? Not decided yet. Deferred —
    category icons are out of MVP scope (MVP uses a plain dot only), so this only needs resolving
    before the category-icons feature gets its own PLAN.md.

15. With the fake-sleep mechanism now `FakeSleepActivity` (an Activity) rather than a raw overlay
    window (`docs/detailed-spec.md` §1, `docs/DECISIONS.md` §18), does §13's phone-call-overlay-
    suppression mechanism — explicit `TelephonyManager`/`TelephonyCallback` call-state detection
    driving an explicit hide()/restore() of the indicator's drawn content — still describe the
    right approach, or does normal Android Activity/task-stack behavior already achieve the
    required outcome (the incoming call's own full-screen-intent Activity taking over the
    foreground, naturally pausing/stopping `FakeSleepActivity`, with `FakeSleepActivity` naturally
    resuming once the call Activity finishes) with no need for any call-state detection,
    `CallStateMonitor`, or `READ_PHONE_STATE` permission at all?

    Background: `docs/plans/other/phone-call-overlay-suppression/PLAN.md`'s mechanism was carried
    over unchanged from when the fake-sleep display was a raw `TYPE_APPLICATION_OVERLAY`
    `WindowManager` View — a window type that sits above ordinary app windows/Activities and does
    not automatically yield foreground to a newly-launched Activity, so an explicit "detect call,
    hide the overlay View, restore after" step was genuinely necessary to avoid obstructing the
    incoming-call UI. Under the current Activity-based mechanism, `FakeSleepActivity` is an
    ordinary (if `showWhenLocked`/`turnScreenOn`) Activity, and the incoming call's own full-
    screen-intent Activity is also an ordinary Activity launched via its own `PendingIntent` —
    under normal Android task/window management, launching a new foreground Activity would be
    expected to push `FakeSleepActivity` to `onPause()`/`onStop()` on its own, without needing our
    app's cooperation. This raises real doubt about whether §13's whole call-detection/hide-
    restore mechanism is still needed, or whether it should instead rely on `FakeSleepActivity`'s
    own `onPause()`/`onResume()` lifecycle callbacks (in which case the entire `CallStateMonitor`
    module, the `TelephonyCallback` listener, and the `READ_PHONE_STATE` runtime permission this
    plan introduces could potentially be dropped entirely).

    This is not confirmable purely from documentation — it depends on actual OS/OEM behavior for
    two competing `showWhenLocked`/`turnScreenOn` full-screen Activities (ours + the phone app's),
    exactly the kind of assumption that turned out to need real-device validation before
    (`docs/DECISIONS.md` §18's secure-keyguard test). Blocks: whether to keep
    `docs/plans/other/phone-call-overlay-suppression/PLAN.md`'s `TelephonyManager`-based mechanism
    as-is, replace it with a lifecycle-callback-only approach, or validate on-device before
    deciding — and, if `TelephonyManager` detection turns out unnecessary, whether to drop
    `READ_PHONE_STATE` from the permission set entirely (`docs/detailed-spec.md` §2 does not
    currently list it, since it was introduced only within this plan).

### Resolved (continued)

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
