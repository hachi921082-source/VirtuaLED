# phone-call-overlay-suppression — Implementation Plan

Covers: `docs/detailed-spec.md` §13 (Phone-Call Overlay Suppression).

**No implementation needed.** `docs/QA.md` #15 (resolved) confirmed via real-device testing
(arrows We2 Plus / `M06`) that the outcome this feature exists to guarantee — an active call must
never be obstructed by the fake-sleep dot — is already achieved by `FakeSleepActivity`'s ordinary
`Activity` lifecycle (`docs/plans/mvp/fake-sleep-overlay/PLAN.md`), with no extra code:

- **Ringing**: the incoming-call notification banner (reject/answer buttons) overlays the dot
  without taking foreground focus, but remains tappable — reject was confirmed to work.
- **Answered**: the phone app's own full-screen in-call `Activity` takes over the foreground,
  pausing/stopping `FakeSleepActivity` exactly as normal Android task/Activity management predicts
  for any two competing foreground Activities. Once the call ends, `FakeSleepActivity` resumes
  automatically (same instance) with no app-side code involved.

This supersedes the original mechanism this plan specified (an explicit `CallStateMonitor` wrapping
`TelephonyManager`/`TelephonyCallback`, driving a manual hide/restore of the overlay's drawn
content) — that design was written against the earlier raw-`WindowManager`-overlay architecture
(pre-`docs/DECISIONS.md` §18), where a persistent overlay window does not naturally yield to a
newly-launched Activity. Under the current `FakeSleepActivity`-based mechanism, that problem does
not exist, so no `CallStateMonitor` module, no `READ_PHONE_STATE` permission, and no
`OverlayWindowManager.hide()`/`restoreCurrentState()` hook are needed for this feature.

No module structure, manifest changes, or cross-feature touchpoints are introduced by this
plan — it exists only to record why §13 requires nothing further.
