# Handoff

<!-- Session-handoff summary: what changed, what remains open, recommended next step. -->

## Index

1. What Happened This Session
2. Current State of the Specs
3. MVP Implementation Plans — Ready to Build
4. MVP Build Order Correction (Follow-Up Session)
5. Repo Setup and Feature 1 Progress (Follow-Up Session)
6. Feature 2 Progress (Follow-Up Session)
7. Still Open
8. Recommended Next Step

## 1. What Happened This Session

This was a long planning/design session, picking up after the previous session's initial
`docs/` scaffolding (spec.md/detailed-spec.md/DECISIONS.md/QA.md, rename to VirtuaLED). No
Android source code exists yet — this session was entirely spec refinement, scope decisions, one
critical architecture correction (validated on real hardware), and MVP implementation planning.

**Scope/monetization discussion** — dev-duration estimates under Pro-plan weekly usage limits;
narrowed test/support device scope to Sharp AQUOS sense9 and FCNT arrows We2 Plus only, Chinese
OEMs explicitly excluded (`docs/DECISIONS.md` §11); donationware distribution model under
consideration (ties into §11's no-universal-compatibility-guarantee stance).

**Feature scope trims** (cost/benefit review, per the "simple but properly cover necessary
features" philosophy, `docs/DECISIONS.md` §8):
- Burn-in reposition interval/style: fixed (4s, instant jump), no settings UI (§13)
- Deep-link tap-to-open + `PendingIntent` holding: cut entirely (§12)
- Replaced by a lighter **peek-preview interaction**: 2-state tap model (Indicator Shown /
  Preview Shown), aggregated text list of all pending notifications, no coordinate hit-testing,
  no `PendingIntent` holding (§14; `docs/detailed-spec.md` §8/§9)
- Blink interval (importance sizing) kept user-configurable, unlike burn-in — individual
  perception differences are a real reason, unlike burn-in's arbitrary cosmetic choice (§15)
- Overlay/session only engages while a notification is pending, and ends itself instantly once
  none remain — no lingering "Idle" state (§16)

**Critical architecture correction (the big one this session)**: the originally-specified
mechanism (raw `WindowManager` `TYPE_APPLICATION_OVERLAY` window, `SYSTEM_ALERT_WINDOW`
permission, session-long `WakeLock`) does not work on devices with a **secure** lock screen
(PIN/pattern/password) — Android blocks that window type above a secure keyguard. Found while
`developer-lite` was drafting the MVP `PLAN.md` files (logged as `docs/QA.md` #14, now resolved).
Researched two comparable real apps (LED Blinker, aodNotify) — both use `AccessibilityService`,
which was rejected here (contradicts this project's own privacy stance, `docs/DECISIONS.md` §3/
§9, and carries heavier Play Store risk). Designed and **validated on real hardware** (a
throwaway prototype, since deleted, tested on arrows We2 Plus / Android 16 / API 36 with a real
PIN lock configured) a corrected mechanism: a full-screen `FakeSleepActivity` using
`setShowWhenLocked(true)` + `setTurnScreenOn(true)`, launched via a full-screen-intent
notification (`USE_FULL_SCREEN_INTENT` permission — confirmed auto-granted for this project's
sideload distribution). This works universally (secure lock or not) — no hybrid mechanism needed,
and `SYSTEM_ALERT_WINDOW` is dropped from the app entirely. Full writeup: `docs/DECISIONS.md`
§18. All of `docs/spec.md` §3, `docs/detailed-spec.md` §1/§2/§8/§14/§15/§16/§17, and the affected
MVP `PLAN.md` files were updated to match.

**MVP implementation planning**: `developer-lite` wrote `PLAN.md` files for all 19
`detailed-spec.md` feature areas, split into `docs/plans/mvp/` (6 features that make up the first
buildable version) and `docs/plans/other/` (9 features/12 files, fully planned but deferred).
After the architecture correction above, 4 of the 6 MVP plans were revised to match. A
`reviewer-lite` cross-consistency pass then found and (I) fixed two real bugs in how the MVP
plans referenced each other (see §3 below for detail) — this is normal pre-implementation
hardening, not a sign anything is untrustworthy.

**Housekeeping**: `.gitignore` created (standard Android patterns; git itself not yet
initialized). The keyguard-test prototype folder and its installed test app were both cleaned up
after validation. `docs/plans/README.md` now documents an "MVP Integration Test" step (a
checklist for testing the whole build end-to-end on a real device once all 6 MVP features are
implemented) and what to do after it passes.

## 2. Current State of the Specs

`docs/spec.md`, `docs/detailed-spec.md` (19 sections), `docs/DECISIONS.md` (18 sections), and
`docs/QA.md` are all internally consistent as of this session's end (numbering/cross-references
verified twice). Read each file's own index first, then jump to the section needed — don't read
end-to-end unless doing another full gap-review pass like this session's.

## 3. MVP Implementation Plans — Ready to Build

`docs/plans/mvp/` contains 6 feature-`PLAN.md` files, in build order (see
`docs/plans/README.md` for the full table + status column, currently all "Not started"):

1. `fake-sleep-overlay` — the corrected `FakeSleepActivity` mechanism
2. `foreground-service-setup` — manifest + FGS declaration
3. `notification-detection-dot-indicator` — `NotificationListenerService` + dot rendering
4. `permission-onboarding` — notification-listener access onboarding only (overlay-permission
   step removed; `USE_FULL_SCREEN_INTENT` needs no onboarding step, auto-granted)
5. `dismiss-and-peek-interaction` — full peek-preview state model (not simplified)
6. `onoff-notification-toggle` — asymmetric in-app-switch vs. notification-action ON/OFF

A `reviewer-lite` pass caught and I fixed two real cross-plan bugs before implementation: (a)
`dismiss-and-peek-interaction`'s screen-off handler was duplicating `fake-sleep-overlay`'s gating
logic instead of delegating to it, which would have silently bypassed
`onoff-notification-toggle`'s "feature disabled" guard; (b) `FakeSleepActivityHolder` was
referenced by multiple plans as exposing a `FakeSleepSessionController` hand-off, but never
actually declared that field anywhere — added concretely. Two minor/cosmetic findings from that
same review were left as-is (judged non-blocking by the reviewer).

**Known limitation of that review**: it checked cross-file consistency (naming, stale references,
contradictions) — it did not (and structurally could not, as a documentation-only pass) validate
individual code snippets' technical correctness, or lifecycle/threading edge cases. Those can only
surface once code is actually written and run — which is exactly what the MVP Integration Test
(`docs/plans/README.md`, step 7) is for.

`docs/plans/other/` has 9 features (12 files, `auto-stop-conditions` split into 3 sub-plans) fully
planned but deferred — see `docs/plans/README.md` for the list and what each depends on from MVP.

## 4. MVP Build Order Correction (Follow-Up Session)

Before handing the 6 MVP plans to `developer-lite`, the build order in §3 above (and originally in
`docs/plans/README.md`) was checked against each PLAN.md's actual type/method references rather
than taken on faith. It was wrong: the original order built `fake-sleep-overlay` first, but that
plan's own code directly types a constructor parameter as `PendingNotificationsRepository`
(defined in `notification-detection-dot-indicator`) and registers its receiver inside
`VirtuaLedForegroundService.onStartCommand()` (defined in `foreground-service-setup`) — neither
would exist yet at that point in the original order, so it would not compile.

**Corrected MVP build order** (already updated in `docs/plans/README.md`'s table; full dependency
graph and rationale in `docs/DECISIONS.md` §19):

1. `notification-detection-dot-indicator` — no dependencies
2. `foreground-service-setup` — no dependencies
3. `permission-onboarding` — no dependencies (placed here so notification-listener access is
   already grantable by the time device-testable features start, in step 4)
4. `fake-sleep-overlay` — needs 1 and 2
5. `dismiss-and-peek-interaction` — needs 4 and 1
6. `onoff-notification-toggle` — needs 2 and 4

**This is now the build order to follow going forward** — supersedes the order listed in §3 above
and in the earlier `docs/plans/README.md` version. No content within the 6 PLAN.md files themselves
changed; this was a sequencing-only correction.

## 5. Repo Setup and Feature 1 Progress (Follow-Up Session)

The package name placeholder (`com.virtualed.app`) was deliberately left unresolved rather than
blocking on it — per `docs/DECISIONS.md` §1a, renaming is cheap before public release, and this
project is still personal-use/sideload only, so there's no urgency.

**Project scaffold and repo**: `git init` run in the project root; `developer-lite` scaffolded a
minimal buildable Android Studio project (Gradle 8.9, AGP 8.6.0, Kotlin 2.0.20, Jetpack Compose,
package `com.virtualed.app`, minSdk 31, compileSdk/targetSdk 35) — no MVP feature code, just the
empty-app skeleton plus a `Theme.VirtuaLed.FakeSleep` style pre-declared for `fake-sleep-overlay`'s
later use. A private GitHub repo was created (`hachi921082-source/VirtuaLED`) and the scaffold
pushed as the initial commit on `master`.

**Established conventions for feature implementation** (apply to all remaining MVP/other
features going forward): branch name `feature/mvp-<feature-name>` (or `feature/<feature-name>` for
`other/` tier features); implement via `developer-lite`, review via `reviewer-lite`
(`~/.claude/shared/dev-workflow.md` §7); merge via GitHub PR (`gh pr create` / `gh pr merge`), not
direct `git merge`.

**Feature 1 (`notification-detection-dot-indicator`) shipped**: implemented by `developer-lite`,
reviewed and approved by `reviewer-lite` (cross-checked against its PLAN.md, build verified), and
merged via PR #1. Along the way, `reviewer-lite` found and fixed a real doc bug (not a code bug):
`docs/detailed-spec.md` §2 and `docs/plans/mvp/foreground-service-setup/PLAN.md` §1 both listed
`BIND_NOTIFICATION_LISTENER_SERVICE` as a `<uses-permission>` entry, which is wrong — it's a
system-bound permission declared via `android:permission` on the `<service>` element itself. Both
docs were corrected; the implementation itself already had it right. `docs/plans/README.md`'s
status column now shows this feature as done.

Also added this session: a project-level `CLAUDE.md` rule ("Confirm Scope Before Acting") after an
incident where an ambiguous "proceed" was over-interpreted as authorization to scaffold the entire
project unprompted — a corresponding persistent-memory entry
(`feedback_confirm_scope_before_acting`) was also recorded. Bias going forward: confirm before each
new non-trivial action rather than chaining through a to-do list from one ambiguous reply.

## 6. Feature 2 Progress (Follow-Up Session)

Implemented MVP feature 2, `foreground-service-setup`, following the recommended next step from
the previous handoff and the same branch/implement/review/PR flow established for feature 1 (§5):
branch `feature/mvp-foreground-service-setup`, implemented by `developer-lite` (manifest
permissions + `<service>` declaration per PLAN §1, `VirtuaLedForegroundService` skeleton per PLAN
§2/§3, temporary in-app switch in `MainActivity` wired to `ACTION_START`/`ACTION_STOP` per PLAN
§4 — `permission-onboarding`, which normally gates this switch, hasn't landed yet so a stand-in
switch was added), reviewed by `reviewer-lite` (conditionally approved: code and build clean, no
defects against the plan, but live device behavior couldn't be verified in that sandboxed
environment — no adb devices/emulator available there).

**Real-device smoke test** (done directly in the main session, not by an agent, since the agent
sandbox had no device access): connected a physical Android device (model `M06` — not one of the
two officially supported test devices from `docs/DECISIONS.md` §11, used only as a smoke-test
stand-in here, not a validation of officially supported hardware) via `adb`. Confirmed via
`dumpsys activity services`: switch ON → `VirtuaLedForegroundService` running with
`isForeground=true` and the correct notification channel/flags; switch OFF → the service record
disappears, confirming a full stop. One expected/documented gap surfaced: the notification itself
isn't visually posted (`dumpsys notification` showed `importance=NONE`, `numBlocked=1`) because
`POST_NOTIFICATIONS` runtime permission hasn't been requested yet — that's explicitly
`permission-onboarding`'s scope (the next feature in build order), not a defect here.

Merged via PR #2 (`gh pr merge --merge --delete-branch`). `docs/plans/README.md`'s status column
now shows this feature as done.

**New workflow preference recorded this session**: when a `gh pr merge` (or similar) is blocked by
the Claude Code auto-mode permission classifier, present the user a choice via `AskUserQuestion`
(retry / merge manually / hold) rather than just describing the block in text and waiting. Recorded
globally in `~/.claude/CLAUDE.md` under "Blocked Git/GitHub Actions" (applies to all projects, not
just this one), so no project-scoped memory entry was kept for it.

## 7. Still Open

- **Package name placeholder**: `com.virtualed.app` is used throughout `docs/spec.md` and all
  `PLAN.md` files, still unconfirmed by the user — explicitly deferred (§5 above), not a blocker.
- **Old `NO-LED` folder cleanup**: `c:\Users\m9112\vscode2\NO-LED` — status not re-checked this
  session; carried over from before.
- **App icon**: interim Material Symbols plan stands, final art deferred (carried over).
- `docs/QA.md` #13 — app-to-indicator-category mapping mechanism (manual vs. auto). Deferred,
  only blocks the (non-MVP) `category-icons-and-assets` feature.
- `docs/QA.md` #15 — whether `phone-call-overlay-suppression`'s explicit
  `CallStateMonitor`/`READ_PHONE_STATE` mechanism is even needed, or whether normal Activity
  lifecycle (an incoming call's own full-screen-intent Activity naturally taking foreground)
  already achieves it for free. Not confirmable from docs alone — needs the same kind of
  real-device prototype validation the keyguard question got (§18), when that (non-MVP) feature
  is picked up.
- Secure-keyguard mechanism (`docs/DECISIONS.md` §18) was validated only on arrows We2 Plus, not
  yet re-confirmed on the Sharp AQUOS sense9 — worth doing opportunistically, not a blocker.

## 8. Recommended Next Step

1. Implement MVP feature 3, `permission-onboarding`, following the same
   branch/implement/review/PR flow established in §5/§6 (branch
   `feature/mvp-permission-onboarding`). This is also the feature that should replace
   `foreground-service-setup`'s temporary `MainActivity` switch with the real permission-gated
   onboarding flow (§6).
2. Continue through the remaining corrected build order (§4): `fake-sleep-overlay`,
   `dismiss-and-peek-interaction`, `onoff-notification-toggle`.
3. Run the MVP Integration Test checklist (`docs/plans/README.md`) on a real device with a
   **secure** lock configured, once all 6 land — use one of the two officially supported test
   devices (Sharp AQUOS sense9 or FCNT arrows We2 Plus, `docs/DECISIONS.md` §11) for this, not an
   arbitrary device like the `M06` used for feature 2's quick smoke test (§6).
4. Once that passes: **tell the user** (don't silently proceed), then start the `other/` tier —
   `reviewer-lite` cross-consistency pass on those `PLAN.md` files first (they reference MVP
   internals that may have shifted during implementation), then implement in any order.
5. Resolve the package name placeholder before any public release is pursued (not urgent now,
   §5/§6).
