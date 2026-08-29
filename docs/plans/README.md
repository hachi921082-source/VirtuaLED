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

- [`category-icons-and-assets`](other/category-icons-and-assets/PLAN.md) — blocked on
  `docs/QA.md` #13 (app-to-category mapping mechanism) for one section; rest of the plan is ready.
- [`burn-in-protection`](other/burn-in-protection/PLAN.md)
- [`importance-based-sizing`](other/importance-based-sizing/PLAN.md)
- `auto-stop-conditions` — split into three independent sub-plans:
  - [`PLAN-1-proximity.md`](other/auto-stop-conditions/PLAN-1-proximity.md)
  - [`PLAN-2-battery.md`](other/auto-stop-conditions/PLAN-2-battery.md) — Done (implemented,
    reviewed; PR pending). See `docs/QA.md` #18 for a minor non-blocking follow-up found during
    review.
  - [`PLAN-3-quiet-hours.md`](other/auto-stop-conditions/PLAN-3-quiet-hours.md)
- [`dnd-handling`](other/dnd-handling/PLAN.md) — Done (implemented, reviewed, merged PR #8). See
  `docs/QA.md` #17 for a minor non-blocking follow-up found during review.
- [`multi-notification-stacking`](other/multi-notification-stacking/PLAN.md)
- [`localization-additional-languages`](other/localization-additional-languages/PLAN.md)
- [`phone-call-overlay-suppression`](other/phone-call-overlay-suppression/PLAN.md)
- [`indicator-color-customization`](other/indicator-color-customization/PLAN.md) — replaces
  `fake-sleep-overlay`'s hardcoded white default once implemented.
