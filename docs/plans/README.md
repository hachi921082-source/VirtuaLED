# Implementation Plans

Each feature's implementation plan lives in its own directory:
`docs/plans/<mvp|other>/<feature-name>/PLAN.md` (or split into `PLAN-1-<topic>.md`,
`PLAN-2-<topic>.md`, etc. within that directory, if a single feature's plan gets large — see
`auto-stop-conditions` below for an example). PLAN.md files are never accumulated into one big
dependency-ordered file — each feature directory is self-contained and independently addable.

## `mvp/` vs `other/`

- `docs/plans/mvp/` — the 6 features that make up the first buildable version of the app. Build
  these first, in the order below (real dependencies between them, verified against each
  PLAN.md's actual type/method references — see `docs/DECISIONS.md` §19 for the correction history
  and dependency graph).
- `docs/plans/other/` — every other feature from `docs/detailed-spec.md`. Fully planned now so
  each is ready to implement independently, in any order, without re-deriving context later. No
  strict sequencing between these — implement whichever is wanted next.

## MVP Build Order

| Order | Feature | Status |
|---|---|---|
| 1 | [`notification-detection-dot-indicator`](mvp/notification-detection-dot-indicator/PLAN.md) | Done (merged, PR #1) |
| 2 | [`foreground-service-setup`](mvp/foreground-service-setup/PLAN.md) | Done (merged, PR #2) |
| 3 | [`permission-onboarding`](mvp/permission-onboarding/PLAN.md) | Done (merged, PR #3) |
| 4 | [`fake-sleep-overlay`](mvp/fake-sleep-overlay/PLAN.md) | Done (merged, PR #4) |
| 5 | [`dismiss-and-peek-interaction`](mvp/dismiss-and-peek-interaction/PLAN.md) | Done (merged, PR #5) |
| 6 | [`onoff-notification-toggle`](mvp/onoff-notification-toggle/PLAN.md) | Done (merged, PR #7) |
| 7 | **MVP Integration Test** (see below) | Done (passed on arrows We2 Plus / M06, secure PIN lock) |

### 7. MVP Integration Test

Not a feature-PLAN — a required verification step once all 6 features above are implemented,
before considering the MVP "done." Per-feature review (`~/.claude/shared/dev-workflow.md` §7 step
3) checks each PLAN.md in isolation; this step instead runs the whole build together end-to-end on
a real device (per `docs/DECISIONS.md` §11: Sharp AQUOS sense9 and/or FCNT arrows We2 Plus, with a
**secure** lock actually configured — see `docs/DECISIONS.md` §18 for why that specific condition
matters), since cross-feature bugs (like the secure-keyguard issue found during planning, §18)
tend to surface only when features interact, not when reviewed individually. Minimum checklist:

1. Grant permissions via the onboarding flow, enable the feature via the in-app switch.
2. Receive a real notification (any app), turn the screen off — confirm `FakeSleepActivity`
   appears over the (secure) lock screen showing the dot.
3. Tap once — confirm the preview text appears; wait for timeout — confirm it reverts to the dot
   (notification still pending) or ends the session (if cleared).
4. Tap twice (peek shown → tap again) — confirm full dismiss to the real lock screen.
5. Repeat step 2, then dismiss via the notification's OFF action instead — confirm the session
   ends but the FGS notification and its action remain.
6. Turn the feature off via the in-app switch — confirm the FGS and its notification are fully
   gone.
7. Clear the notification from another surface (e.g. notification shade) while
   `FakeSleepActivity` is showing — confirm the session ends itself immediately.

Update this row's status once this checklist has actually been run and passed.

**Result**: run on arrows We2 Plus (`M06`) with a secure PIN lock configured. All 7 steps passed.
One retry was needed for steps 2/7 after an unrelated device-side interaction issue (see
`docs/QA.md` #16 — an OEM ambient/doze wakefulness path occasionally prevented `FakeSleepActivity`
from auto-launching; not reproduced after a clean USB/app reset, not treated as an MVP blocker).

`fake-sleep-overlay`'s secure-keyguard mechanism was already validated via a standalone prototype
(`docs/DECISIONS.md` §18) — this integration test is about the full built app, not a re-validation
of that specific finding.

### After MVP Integration Test Passes

Once row 7 above is marked passed: **notify the user** (do not silently proceed), then move to
the `other/` tier in this order:
1. A reviewer-lite cross-consistency pass over the `docs/plans/other/` PLAN.md files (mirroring
   the pre-implementation review already done for the `mvp/` tier), since several of them
   reference/extend MVP internals that may have shifted during MVP implementation. Doing this
   review before MVP is finalized risks reviewing against a moving target. **Status: Done.** This
   pass found 6 of the 9 `other/` plans still described a pre-MVP `WindowManager`/`View`-based
   overlay architecture that no longer exists; `docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7 was
   extended with a `FakeSleepIndicatorUiState` extension surface (see `docs/DECISIONS.md` §20) and
   those 6 plans (`burn-in-protection`, `indicator-color-customization`, `importance-based-sizing`,
   `category-icons-and-assets`, `multi-notification-stacking`,
   `auto-stop-conditions/PLAN-1-proximity.md`) were rewritten against it and re-reviewed, approved.
2. Implement `other/` features in whatever order is wanted (no strict sequencing between them),
   with their own per-feature integration testing as each is built. **This is the current step.**

## Other Features (no strict order)

- [`category-icons-and-assets`](other/category-icons-and-assets/PLAN.md) — Done (implemented,
  reviewed, merged PR #15). App-to-category mapping resolved as user-assigned per app via a new
  `CategorySettingsScreen`, not auto-inferred (`docs/DECISIONS.md` §21, resolves `docs/QA.md` #13).
  Adds `IndicatorCategory`/`CategoryMapper`, 5 solid-white vector icon assets (Material Icons,
  Apache 2.0), and `FakeSleepIndicatorUiState.slots[].category` — the last of the 5
  `FakeSleepIndicatorUiState` properties, all now fully wired. An extensive live-device testing
  round found and fixed 8 real bugs beyond the two reviewer-lite passes (status-bar inset overlap
  in both settings screens, category-chip row overflow, `<queries>` missing so ordinary apps
  weren't visible in the picker, `MainSwitchScreen`'s switch not reflecting real service state,
  indicator size too small for icon detail at 12dp, no discoverable way to un-assign a category,
  and `MATCH_DEFAULT_ONLY` silently excluding real launcher apps like Rakuten Link from the
  picker) — see `docs/HANDOFF.md` §15 for the full writeup. `docs/QA.md` #20 records one related,
  deliberately-deferred request (per-category tint color) that surfaced during this testing.
- `source-app-icon-indicator-mode` — **spec only, no PLAN.md yet.** New premium feature (freemium
  monetization direction, `docs/DECISIONS.md` §22): shows the notifying app's own launcher icon
  instead of the fixed category icon set, gated by a local `isPremiumUnlocked` flag defaulting to
  unlocked ahead of real billing. Spec'd in `docs/detailed-spec.md` §20 (feature) and §21 (gating
  mechanism); per-category tint color (`docs/QA.md` #20) was considered as an alternative premium
  feature and deprioritized in favor of this one. Needs a `PLAN.md` written before implementation.
- [`burn-in-protection`](other/burn-in-protection/PLAN.md) — Done (implemented, reviewed, merged
  PR #11). Adds `BurnInProtector` and `FakeSleepIndicatorUiState.indicatorOffset`, gated on
  session-active/`OverlayUiState`/proximity-covered via a combined `recompute()`
  (`docs/DECISIONS.md` §20). Two non-blocking review findings: `FakeSleepSessionController.
  startSession()`'s `onSessionActiveChanged(true)` call is dead in every path (real activation
  happens in `FakeSleepActivity.onCreate()`, documented as a deviation in that plan's file); the
  indicator sits at `(0,0)` for the first ~4s of each session/rotation before the first jump
  (matches the plan's own pseudocode, not a defect).
- [`importance-based-sizing`](other/importance-based-sizing/PLAN.md) — Done (implemented,
  reviewed, merged PR #13). Adds `KeywordRegistry`/`BlinkSettings` (DataStore, synchronous caches
  mirroring `DndSettings`'s pattern), `ImportanceResolver` (NFKC/case-normalized substring match),
  and a new `KeywordSettingsScreen` (installed-app picker + per-app keyword add/remove, reached via
  a boolean-gated screen swap — no navigation library). First feature to code
  `FakeSleepIndicatorUiState.slots`/`blinkIntervalMs` (scoped to `count`/`highImportance` only,
  `category` deferred to `category-icons-and-assets`). One review-found defect (keyword-cache
  dedup drift) was fixed before merge.
- `auto-stop-conditions` — split into three independent sub-plans:
  - [`PLAN-1-proximity.md`](other/auto-stop-conditions/PLAN-1-proximity.md) — Done (implemented,
    reviewed, merged PR #10). This is the first feature to actually implement
    `FakeSleepIndicatorUiState` (previously only described in `docs/DECISIONS.md` §20 /
    `docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7, never coded) — scoped to just its
    `indicatorVisible` property; the other 4 documented properties (`slots`/`tintColor`/
    `blinkIntervalMs`/`indicatorOffset`) are still unimplemented, added by whichever feature needs
    them next. `burn-in-protection` (now also done, PR #11, see above) extended the
    `ProximityMonitor` callback in `FakeSleepSessionController.kt` with its own
    `onProximityCoveredChanged()` call, per that plan's own §4 — already anticipated, not a gap.
  - [`PLAN-2-battery.md`](other/auto-stop-conditions/PLAN-2-battery.md) — Done (implemented,
    reviewed, merged PR #9). See `docs/QA.md` #18 for a minor non-blocking follow-up found during
    review.
  - [`PLAN-3-quiet-hours.md`](other/auto-stop-conditions/PLAN-3-quiet-hours.md) — Done (implemented,
    reviewed, merged PR #14). Inexact `AlarmManager` scheduling (`QuietHoursScheduler`,
    self-re-arming via `QuietHoursAlarmReceiver`, surviving reboot via `BootCompletedReceiver` —
    the only `RECEIVE_BOOT_COMPLETED` consumer in this app). Live device testing (arrows We2 Plus)
    found and fixed a real cold-start gap: `QuietHoursState` previously only updated via the
    edge-triggered alarm, so a service restart already inside a configured window wouldn't
    suppress until the next day's trigger — `QuietHoursScheduler.reschedule()` now also syncs the
    flag to wall-clock reality on every call (`isWithinWindow()`, handles midnight-spanning
    windows). Confirmed fixed via a live before/after re-test.
- [`dnd-handling`](other/dnd-handling/PLAN.md) — Done (implemented, reviewed, merged PR #8). See
  `docs/QA.md` #17 for a minor non-blocking follow-up found during review.
- [`multi-notification-stacking`](other/multi-notification-stacking/PLAN.md) — Done (implemented,
  reviewed, merged PR #16). Adds `IndicatorSlotBuilder` (per-app grouping, up to 4 slots + a summed
  OTHER overflow slot beyond that, per-app not aggregate `highImportance`) and a fixed 2x2 grid
  render in `FakeSleepActivity`'s `IndicatorGroup`/`SlotIcon`; a single pending notification still
  renders identically to the pre-existing MVP path. Adds `CountBadgeSettings` (DataStore, default
  off) for an optional per-slot notification-count badge. Live-device testing (arrows We2 Plus /
  `M06`, real Outlook mail + Rakuten Link missed-call notifications) confirmed the grid and count
  badge both work, and found two follow-up items: the indicator icon needed enlarging from 24dp to
  32dp with the badge offset outward from the corner (fixed before merge, since the badge was
  otherwise covering most of a smaller icon), and `docs/QA.md` #21 records that the count-badge
  toggle doesn't live-refresh mid-session (same non-blocking class of gap as #18).
- [`localization-additional-languages`](other/localization-additional-languages/PLAN.md)
- [`phone-call-overlay-suppression`](other/phone-call-overlay-suppression/PLAN.md)
- [`indicator-color-customization`](other/indicator-color-customization/PLAN.md) — Originally done
  (implemented, reviewed, merged PR #12) as an 8-swatch palette + HEX field. Replaces
  `fake-sleep-overlay`'s hardcoded white default with `IndicatorColorSettings` (DataStore) + an
  inline palette/HEX picker in `MainSwitchScreen` (no separate settings screen — this app has no
  navigation component, same convention already used for the DND/battery settings). Live color
  changes propagate to an active session via `FakeSleepActivityHolder.indicatorUiState?.tintColor`
  independent of persistence. **Needs a follow-up revision, tracked as
  [issue #17](https://github.com/hachi921082-source/VirtuaLED/issues/17), not yet implemented**:
  reduce to 5 presets and remove the HEX field entirely (`docs/DECISIONS.md` §23, `PLAN.md` §3/§4)
  — code change to `ColorPickerSection` in `MainActivity.kt` still pending.
