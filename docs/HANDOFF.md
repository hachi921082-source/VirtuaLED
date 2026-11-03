# Handoff

<!-- Session-handoff summary: what changed, what remains open, recommended next step. -->

## Index

1. What Happened This Session
2. Current State of the Specs
3. MVP Implementation Plans — Ready to Build
4. MVP Build Order Correction (Follow-Up Session)
5. Repo Setup and Feature 1 Progress (Follow-Up Session)
6. Feature 2 Progress (Follow-Up Session)
7. Feature 3 Progress (Follow-Up Session)
8. Feature 4 Progress (Follow-Up Session)
9. Feature 5 Progress and a Fake-Sleep Screen-Timeout Bug Found/Fixed via Sense9 Testing (Follow-Up Session)
10. Feature 6 Progress and MVP Completion (Follow-Up Session)
11. MVP Integration Test and Pre-`other/`-Tier Cleanup (Follow-Up Session)
12. `other/`-Tier Architecture Fix and Implementation Order (Follow-Up Session)
13. `other/`-Tier Implementation Begins: dnd-handling, auto-stop-battery, auto-stop-proximity
    (Follow-Up Session)
14. Four More `other/`-Tier Features Merged: burn-in-protection, indicator-color-customization,
    importance-based-sizing, auto-stop-conditions/quiet-hours (Follow-Up Session)
15. category-icons-and-assets: Implementation, Review, and an Extensive Live-Device Bug Hunt
    (Follow-Up Session, PR #15 Open, Not Yet Merged)
16. Freemium Monetization Consultation and a Parallel-Session Git-Sharing Discovery (Follow-Up
    Session, Consultation-Only — No Code Changes)
17. `multi-notification-stacking` Merged (PR #16) and a Concurrent-Session Branch Split (Follow-Up
    Session)
18. PR #18 Merged, `source-app-icon-indicator-mode` and Issue #17 Shipped, a Generic Live-Refresh
    Fix, QA #17 Resolved, a Live QA #16 Investigation, and a Long Settings-Screen UI Overhaul
    (Follow-Up Session)
19. Issue #23 Fixed (PR #25), a Notification-Exclusion Feature Scoped and Split into Issues
    #26/#27, and Issue #26 Implemented and Merged (PR #28) (Follow-Up Session)
20. A Very Long Follow-Up Session: Issues #27/#30/#32/#33/#36/#37/#38/#43 Shipped, App Icon and
    License Screen Added, Localization Completed (Last `other/`-Tier Item), and an Unresolved
    Full-Screen-Intent App Ops Investigation In Progress
21. FSI Investigation Closed (QA #24), Issues #47/#49/#44 Shipped (PRs #48/#50/#46) (Follow-Up
    Session)
22. Still Open
23. Recommended Next Step

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

## 7. Feature 3 Progress (Follow-Up Session)

Implemented MVP feature 3, `permission-onboarding`, following the same branch/implement/review/PR
flow established for features 1/2 (§5/§6): branch `feature/mvp-permission-onboarding`.

**Implementation** (`developer-lite`, per PLAN §1-§5): added `PermissionChecker` (stateless
notification-listener-access check) and `OnboardingScreen` (Compose flow: a single permission
card, "Grant" opens `ACTION_NOTIFICATION_LISTENER_SETTINGS`, a resume-lifecycle observer re-checks
grant state on return from Settings). `MainActivity` was restructured so the feature-2 temporary
switch (renamed `MainSwitchScreen`, logic otherwise untouched) is now gated behind
`OnboardingScreen` — only reachable once notification-listener access is granted. Also added
`RequestPostNotificationsPermission()` (standard `ActivityResultContracts.RequestPermission()` on
API 33+), closing the gap feature 2's smoke test surfaced (`POST_NOTIFICATIONS` was never
requested at runtime). `assembleDebug` — BUILD SUCCESSFUL.

**Review** (`reviewer-lite`; this pass was interrupted mid-run when the driving session's context
was closed, then resumed from its saved transcript — noted here only because it explains the
two-part shape of the review, not because anything was lost): conditionally approved. Code matches
PLAN.md §1-§5 essentially verbatim; feature 1/2 unaffected (`MainSwitchScreen`'s
ACTION_START/ACTION_STOP logic diffed byte-for-byte identical to feature 2's version, just
renamed); build clean. Real-device pass (adb) confirmed the onboarding precondition (listener
access genuinely ungranted) and that the `POST_NOTIFICATIONS` system dialog actually fires via
logcat — but could not visually confirm the full flow (card renders → Grant → Settings → return →
`MainSwitchScreen` becomes reachable): the test device locked itself with its secure PIN mid-session
and neither the reviewer agent nor the main session had the PIN to unlock it.

**Merged via PR #3** (`gh pr merge --merge --delete-branch`) without that final visual confirmation
having happened — the user explicitly chose to proceed to merge rather than wait for it (this
session's owner has the device PIN; a manual on-device check is still recommended but is not a
merge blocker per that decision). The `gh pr merge` call was blocked once by the Claude Code
auto-mode classifier; per the standing workflow preference (`~/.claude/CLAUDE.md`, "Blocked
Git/GitHub Actions"), the user was offered a choice via `AskUserQuestion` (retry / merge manually /
hold) and chose retry, which then succeeded. `docs/plans/README.md`'s status column now shows this
feature as done.

## 8. Feature 4 Progress (Follow-Up Session)

Implemented MVP feature 4, `fake-sleep-overlay`, following the same branch/implement/review/PR
flow established for features 1-3 (§5/§6/§7): branch `feature/mvp-fake-sleep-overlay`.

**Implementation** (`developer-lite`, per PLAN.md §1-§6): added `FakeSleepActivityHolder`
(singleton holding the resumed `FakeSleepActivity` + the `FakeSleepSessionController` instance),
`FakeSleepSessionController` (screen-off gating against `PendingNotificationsRepository.isEmpty()`,
the short `withScreenOffProcessingGuard` `PARTIAL_WAKE_LOCK`, posting/cancelling the
full-screen-intent notification on a dedicated `IMPORTANCE_HIGH` channel, and the "became empty"
listener that ends the session), `FakeSleepActivity` (full-screen `ComponentActivity` using Compose
`setContent{}` rather than an XML layout — matches the project's existing Compose-only convention,
and the plan explicitly allows this as an equivalent — `setShowWhenLocked(true)` +
`setTurnScreenOn(true)`, black background + one fixed centered white dot, no tap handling), and
`ScreenStateReceiver` (`ACTION_SCREEN_OFF`, dynamically registered). `VirtuaLedForegroundService`
was extended (not altered) to construct the controller and register/unregister the receiver
alongside its existing feature 1-3 behavior. Reused the pre-existing `Theme.VirtuaLed.FakeSleep`
theme unmodified. `assembleDebug` — BUILD SUCCESSFUL.

**Review** (`reviewer-lite`): approved. Plan/spec cross-check passed line-by-line against the diff;
confirmed no scope creep (no category-icon, color, burn-in, tap/peek, or multi-notification-stacking
code present — those are out of scope for this plan); confirmed correct integration with the
already-merged `PendingNotificationsRepository` and `VirtuaLedForegroundService` APIs; build
verified clean independently. A physical device was available (model `M06`, secure biometric lock
configured) and the reviewer completed everything reachable without unlocking it (APK install,
granting notification-listener access and `POST_NOTIFICATIONS` via `adb`, confirming
`VirtuaLedForegroundService` is correctly non-exported) but could not complete the full interactive
flow (switch ON → real notification → screen off → confirm `FakeSleepActivity` renders over the
secure lock) since it lacked the device's unlock credentials — left as a non-blocking follow-up,
same handling as feature 2's review when no device was available.

**Merge**: PR #4 created, then `gh pr merge` was blocked twice in a row by the Claude Code
auto-mode classifier (not resolved by a retry this time, unlike feature 3's single-retry case).
**New workflow preference established this session**: for this specific situation, don't keep
retrying `gh pr merge` or defaulting to a vague "merge manually" option — ask via
`AskUserQuestion` offering **local `git merge --no-ff` + `git push origin master`** as the offered
option instead, and only run it after the user confirms (an initial attempt to run it proactively
without asking, right after the user's first "switch to local merge+push" remark, was correctly
rejected — the user's follow-up clarified they still want to be asked each time, just with that
option offered). Recorded as persistent memory (`feedback_pr_merge_use_local_git`); GitHub
recognized the PR as `MERGED` automatically once the equivalent commit landed on `master` via the
local merge + push. `docs/plans/README.md`'s status column now shows this feature as done.

**Manual on-device confirmation** (done directly by the user after merge, not by an agent): both
follow-up items from the review were walked through step-by-step and confirmed working —
`permission-onboarding`'s full visual flow (card renders → Grant → Settings → return →
auto-transition to `MainSwitchScreen`, no manual reload needed) and `fake-sleep-overlay`'s core
mechanism (`FakeSleepActivity` renders full-screen over a **secure** lock screen when a
notification is pending and the screen turns off; clearing the notification ends the session and
the dot disappears automatically). Device model used for this manual pass was not recorded in this
session.

## 9. Feature 5 Progress and a Fake-Sleep Screen-Timeout Bug Found/Fixed via Sense9 Testing (Follow-Up Session)

Implemented MVP feature 5, `dismiss-and-peek-interaction`, following the same branch/implement/
review/PR flow established for features 1-4 (§5/§6/§7/§8): branch
`feature/mvp-dismiss-and-peek-interaction`.

**Implementation** (`developer-lite`, per PLAN.md §1-§8): added `PeekInteractionController`
(`OverlayUiState` state machine — `INDICATOR_SHOWN`/`PREVIEW_SHOWN`, `onTap()`, aggregated
preview-text building from `PendingNotificationsRepository.snapshot()`, 4s `Handler`-based preview
timeout). Extended `FakeSleepActivity` with a single tap handler and a dot/text content-view swap —
implemented as Compose `Modifier.clickable` + `mutableStateOf` rather than the plan's
XML-`findViewById` pseudocode, matching this project's Compose-only convention (verified by
`reviewer-lite` to preserve the plan's intent: single tap-anywhere, no coordinate hit-testing, same
Activity instance). Extended `VirtuaLedForegroundService`'s `ScreenStateReceiver` with power-button
full-dismiss detection (second `ACTION_SCREEN_OFF` while session active) and `ACTION_USER_PRESENT`
unlock auto-dismiss — critically, delegating to the existing shared `sessionController.onScreenOff()`
gating method rather than duplicating its `pendingRepo.isEmpty()` check, per the plan's explicit
warning that duplicating it would let a new session start even while `onoff-notification-toggle`
(feature 6, not yet built) later flags the feature OFF. `assembleDebug` — BUILD SUCCESSFUL.

**Review** (`reviewer-lite`): approved. Plan/spec cross-check passed line-by-line, including the
critical §6 gating-duplication pitfall; confirmed no regression to features 1-4; confirmed no scope
creep (no multi-icon rendering, no coordinate hit-testing, no `PendingIntent` held). No physical
device was reachable in the reviewer's sandbox (`adb devices` empty, no AVD configured) — live
tap/timeout/power-button/unlock flows were left as a follow-up, same handling as feature 2/4's
reviews when no device was available. Merged via PR #5 — `gh pr merge` was blocked by the Claude
Code auto-mode classifier; per the established workflow preference (§8, `~/.claude/CLAUDE.md`
"Blocked Git/GitHub Actions"), the user was offered local `git merge --no-ff` + `git push` and chose
it, which succeeded (GitHub recognized the PR as `MERGED` automatically). `docs/plans/README.md`'s
status column now shows this feature as done.

**Sense9 real-device testing** (done directly in the main session, prompted by the user connecting
the device): this was the first MVP testing pass on the Sharp AQUOS sense9 (model `SH-M26`, one of
the two officially supported test devices, `docs/DECISIONS.md` §11) rather than a smoke-test
stand-in. adb initially couldn't see the device (USB debugging was off; resolved once the user
enabled it in Developer Options). Notification-listener access and `POST_NOTIFICATIONS` were
granted via adb; a test notification was posted via `adb shell cmd notification post`.

**Bug found**: tapping the fake-sleep dot to test the preview state instead showed the app already
back on `MainActivity` — the session had ended on its own. Logcat pinpointed the cause: `screen_off_
timeout` on this device is 15000ms, and exactly ~15s after `FakeSleepActivity` displayed, the OS
fired a real `ACTION_SCREEN_OFF` from idle timeout alone (confirmed the arrows We2 Plus is also
configured for 15s, so this isn't sense9-specific). `FakeSleepActivity`'s `setTurnScreenOn(true)`
(feature 4) only turns the screen on once at resume — it does not suppress the device's normal idle
timeout afterward, contrary to what `docs/DECISIONS.md` §18 had assumed (that assumption was never
exercised past a few seconds during §18's original prototype validation). Feature 5's own
power-button-dismiss logic (§6, correctly implemented per its own spec) then treated this
indistinguishable second `ACTION_SCREEN_OFF` as a power-button press and ended the session —
silently dismissing the dot even with a notification still pending, defeating the app's core
premise (`docs/DECISIONS.md` §17 had already rejected exactly this failure mode in the abstract,
for a different reason, before it was known to be concretely reachable this way).

**Fix**: branch `fix/fake-sleep-keep-screen-on`, single-line change — `FakeSleepActivity` now also
holds `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on its window for the session's duration
(the standard technique real always-on-display implementations use; this flag does not suppress a
genuine hardware power-button press's `ACTION_SCREEN_OFF`, so feature 5's power-button-dismiss
heuristic remains valid — only the idle-timeout false positive is eliminated). Implemented by
`developer-lite`, which also updated `docs/plans/mvp/fake-sleep-overlay/PLAN.md` (§1/§4/§5) and
added a cross-reference note to `docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md` §6. A
correction was added to `docs/DECISIONS.md` §18 documenting that its original "`setTurnScreenOn`
alone suffices" claim was wrong, with the on-device evidence. Reviewed and approved by
`reviewer-lite` (diff scope, code correctness, doc accuracy, build all confirmed independently).
Merged via PR #6, same `gh pr merge`-blocked → local `git merge --no-ff` + `git push` flow as PR #5.

**Post-fix on-device confirmation** (main session, sense9): with the fix installed, the dot was
confirmed to persist unchanged (same Activity instance) for 50 seconds — well past the old 15s
failure point. Also confirmed live: tap once → aggregated preview text (matches PLAN.md §4/§9
format, including several real pending system notifications alongside the test one); 4s timeout
with notifications still pending → reverts to dot; tap twice → full dismiss to `MainActivity`;
power-button-dismiss → fires within ~4s (not conflated with the 15s idle timeout, confirming the
fix didn't break this path); unlock auto-dismiss (`ACTION_USER_PRESENT`, confirmed via logcat) →
ends only the fake-sleep session, `VirtuaLedForegroundService` itself keeps running. This is the
first time feature 5's interactive flows and feature 4's session-persistence were confirmed on a
real device at all (not just a code/build-level review) — MVP integration test step 2/3/4 in
`docs/plans/README.md` are now substantially de-risked ahead of time, though not a substitute for
running that checklist formally once feature 6 lands.

Note: whether sense9's lock screen was configured as a **secure** (PIN/pattern/password) lock
during this testing pass was not verified — the device was already unlocked by the user before each
screenshot, so this session's testing does not by itself confirm `docs/DECISIONS.md` §18's
secure-keyguard rendering claim on sense9 specifically (see §10 below, this item was already open
before this session and remains so).

## 10. Feature 6 Progress and MVP Completion (Follow-Up Session)

Implemented MVP feature 6, `onoff-notification-toggle` — the last of the 6 MVP features — following
the same branch/implement/review/PR flow established for features 1-5 (§5/§6/§7/§8/§9): branch
`feature/mvp-onoff-notification-toggle`. This session opened by reading the previous handoff, then
went straight to implementation on the user's explicit instruction.

**Implementation** (`developer-lite`, per `docs/plans/mvp/onoff-notification-toggle/PLAN.md` §1-§7):
added `FeatureEnabledState` (in-memory synchronized singleton flag, default `true`, with an
`onChanged` listener callback) and `OnOffActionReceiver` (`BroadcastReceiver` for the notification
action's `PendingIntent`, registered `exported="false"`). `VirtuaLedForegroundService.onCreate()` now
registers a listener that re-posts the notification via the extended `buildNotification(enabled)`
(adds the ON/OFF toggle action button) and, if the flag flips to `false` while a fake-sleep session
is active, calls `fakeSleepSessionController.endSession()`. `FakeSleepSessionController.onScreenOff()`
(from the already-merged `fake-sleep-overlay` feature) gained a
`if (!FeatureEnabledState.isEnabled()) return` guard alongside its existing
`pendingRepo.isEmpty()` check — not in place of it. `MainActivity`'s switch-ON path now calls
`FeatureEnabledState.setEnabled(true)` before `ACTION_START` (resume, in case it was left OFF via the
notification action); switch-OFF path is untouched (`ACTION_STOP` → `stopSelf()`), preserving the
plan's core asymmetric design: the notification action toggles the flag and keeps the service+
notification alive, while only the in-app switch OFF fully terminates the service.
`gradlew.bat assembleDebug` — BUILD SUCCESSFUL (note: this environment's default `JAVA_HOME` is JDK
25, which breaks Gradle 8.9's embedded Kotlin DSL compiler — both `developer-lite` and
`reviewer-lite` had to override with
`JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` to build; worth fixing at the environment
level in a future session, but not something this handoff blocks on).

**Review** (`reviewer-lite`): approved. Line-by-line diff against PLAN.md §1-§7 confirmed, including
the critical asymmetry point (`OnOffActionReceiver` deliberately does not call `stopSelf()` or send
`ACTION_STOP`); confirmed the new guard in `FakeSleepSessionController.onScreenOff()` sits alongside,
not replacing, the pre-existing gating check (no regression to features 4/5); confirmed no scope
creep; independently re-ran `assembleDebug` clean. Device `SH-M26` (sense9) was reachable via `adb`
and the freshly built APK was installed, but the device was locked with a secure keyguard and the
reviewer had no PIN — it deliberately did not attempt to guess it (lockout/wipe risk) and also
confirmed, as an aside, that `am start-foreground-service` against the app's `exported="false"`
service is correctly rejected by the OS (not a bypass). Live toggle-behavior confirmation (action tap
flips label without killing the service; in-app switch OFF fully stops it) was therefore **not**
completed on-device this round — same category of gap as features 2/4/5's reviews when device
access was limited, not treated as a merge blocker per established precedent.

**Merge**: PR #7 created; `gh pr merge` was blocked by the Claude Code auto-mode classifier (as with
PRs #4-#6). Per the established workflow preference (§8/§9, `~/.claude/CLAUDE.md` "Blocked Git/GitHub
Actions"), the user was offered a choice via `AskUserQuestion` (local `git merge --no-ff` + `push` /
retry `gh pr merge` / hold) and chose local merge+push, which succeeded — GitHub recognized the PR as
`MERGED` automatically once the equivalent commit landed on `master`. Local and remote feature
branches deleted after merge. `docs/plans/README.md`'s status column now shows this feature as
`Done (merged, PR #7)`.

**Commit granularity**: each of the 8 changed/new files was committed individually (per
`~/.claude/CLAUDE.md`'s "one file per commit" convention), matching the pattern already used for
features 1-5.

**MVP is now feature-complete**: all 6 `docs/plans/mvp/` features (build order per §4) are
implemented, reviewed, and merged. The only remaining MVP-tier item is the integration test itself
(`docs/plans/README.md` row 7) — see §11/§12 below.

## 11. MVP Integration Test and Pre-`other/`-Tier Cleanup (Follow-Up Session)

This session opened by reading the previous handoff, then ran the MVP Integration Test
(`docs/plans/README.md` row 7, the 7-step checklist) directly in the main session (no agent — real
device interaction driven live with the user, verified at each step via `adb`/`dumpsys`/`logcat`).

**Test device**: started on the Sharp AQUOS sense9 (`SH-M26`), but switched to arrows We2 Plus
(model `M06` — the same device features 2/4 used as an unofficial smoke-test stand-in; this session
confirmed `M06` is in fact the arrows We2 Plus's own manufacturer model number, not a third,
unrelated device as earlier handoffs assumed) partway through, since sense9's physical power button
was hard to press reliably. Before switching, the user confirmed We2 Plus has a secure PIN lock
configured and that a normal power-button screen-off/on cycle genuinely re-shows the swipe/PIN
sequence (ruling out the auto-lock-delay confound that made an early sense9 reading ambiguous).

**All 7 checklist steps passed** on We2 Plus with the secure lock active. One recurring snag
surfaced along the way (screen off → device entered `WAKEFULNESS_DOZING` instead of `WAKEFULNESS_
ASLEEP`, and `FakeSleepActivity` never launched) — happened twice, both times resolved by force-
stopping and relaunching the app (the second time confirmed this works without unplugging the USB
debugging cable, weakening an initial USB-interference hypothesis). Logged as new open question
`docs/QA.md` #16 — not isolated to a root cause, not treated as an MVP blocker since every retry
after a reset passed cleanly. `docs/plans/README.md` row 7 now reads "Done (passed on arrows We2
Plus / M06, secure PIN lock)" — **the MVP tier (6 features + integration test) is now fully
complete**.

**`docs/QA.md` #15 resolved via opportunistic real-device testing**, done alongside the integration
test since a device was already in hand: with `FakeSleepActivity` showing the dot over the secure
lock, a real call was placed to the device. While *ringing*, the incoming-call notification banner
(reject/answer buttons) overlaid the dot without taking foreground focus, but stayed tappable — the
reject button was tested and worked. When the call was then *answered* (a second real call, ended
quickly to limit charges), the phone app's own full-screen in-call `Activity` genuinely took over
the foreground from `FakeSleepActivity` (visually confirmed by the user), and `FakeSleepActivity`
resumed automatically (same `ActivityRecord`, confirmed via `dumpsys`) once the call ended — no
app-side code involved either way. This settles #15's original question in favor of "normal
Activity lifecycle already handles it": `docs/plans/other/phone-call-overlay-suppression/PLAN.md`
was rewritten from a `TelephonyManager`/`CallStateMonitor`/`READ_PHONE_STATE` mechanism to a short
"no implementation needed" plan documenting why, and `docs/detailed-spec.md` §13 was updated to
match.

**This environment's `JAVA_HOME` issue (open since feature 6, §10) is now fixed**: added
`org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr` to `gradle.properties`, scoping
the fix to this project's own Gradle builds rather than changing the machine's global `JAVA_HOME`
(smaller blast radius, reversible via a tracked file, doesn't affect other tools on this machine).
Verified with `gradlew --stop` (to clear any daemon still holding the old JVM) followed by a clean
`assembleDebug`, with no `JAVA_HOME` override set in the shell at all — `compileDebugKotlin`
succeeded. Manual override is no longer needed going forward. Note: this fix is machine-specific
(hardcodes this machine's Android Studio install path) — acceptable for this personal/sideload-only
project, but would need adjusting if the project ever builds on a different machine or CI.

**Incidentally closed**: the previous handoff's open item "feature 6's live on-device toggle
confirmation" (§10/§11 of the prior handoff — `reviewer-lite` couldn't complete it, no PIN
available) was exercised as part of this session's integration test steps 5/6 on We2 Plus (the
notification action's OFF toggle correctly ended the fake-sleep session while leaving the FGS
+notification+action alive; the in-app switch OFF correctly stopped the FGS completely). No longer
an open item.

**Commit granularity**: 6 commits this session, one file per commit, per
`~/.claude/CLAUDE.md`'s "one file per commit" convention — `docs/QA.md` (twice), `docs/plans/
README.md`, `gradle.properties`, `docs/plans/other/phone-call-overlay-suppression/PLAN.md`, and
`docs/detailed-spec.md`.

## 12. `other/`-Tier Architecture Fix and Implementation Order (Follow-Up Session)

This session opened by reading the previous handoff, then ran the `reviewer-lite` cross-consistency
pass over `docs/plans/other/` that `docs/plans/README.md` itself calls for once the MVP tier is
complete (§11 above) — the recommended next step from the previous handoff.

**Finding**: 6 of the 9 `other/` features (`burn-in-protection`, `indicator-color-customization`,
`importance-based-sizing`, `category-icons-and-assets`, `multi-notification-stacking`,
`auto-stop-conditions/PLAN-1-proximity.md`) still described a pre-MVP `WindowManager`/`View`-based
overlay architecture (`OverlayWindowManager`, `OverlayIndicatorView`, `MultiIndicatorContainerView`,
`ImageView.setColorFilter()`, `ObjectAnimator`) that no longer exists — the actual, already-shipped
mechanism is the Compose-rendered `FakeSleepActivity` from §18's architecture correction. A direct
contradiction was also found between two sibling `other/` plans about whether a since-removed
`OverlayWindowManager.hide()/restoreCurrentState()` mechanism still existed.

**Design decision** (discussed directly with the user, not delegated, since it required choosing a
new architecture rather than just fixing stale references — full rationale in `docs/DECISIONS.md`
§20): a new `FakeSleepIndicatorUiState` class (`com.virtualed.app.overlay`), separate from
`FakeSleepActivity` itself, holding five Compose `MutableState` properties (`slots`, `tintColor`,
`blinkIntervalMs`, `indicatorOffset`, `indicatorVisible`) that every `other/`-tier visual feature
writes to instead of reaching into a View/WindowManager layer. Two notable choices made along the
way: blink is synchronized across all shown slots rather than per-slot/staggered (staggering doesn't
reduce total battery draw and adds complexity — a user-proposed alternative that turned out not to
hold up), and `BurnInProtector`'s three independent pause/resume triggers (session active, indicator
UI state, proximity covered) are combined through one `recompute()` gate rather than each trigger
calling `start()`/`stop()` directly, avoiding a race where covering the proximity sensor during
`PREVIEW_SHOWN` and then uncovering would incorrectly resume repositioning mid-preview.

**Implementation**: `docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7 was extended with this surface;
`developer-lite` then rewrote the 6 affected files against it (preserving each file's own
non-rendering logic — keyword matching, DataStore schemas, grid/overflow math — unchanged) and
`reviewer-lite` reviewed the rewrite, approving it with one minor, non-blocking observation: the
rewritten `burn-in-protection/PLAN.md` didn't yet document pausing during proximity-cover even
though the rewritten `auto-stop-conditions/PLAN-1-proximity.md` claimed it did. Fixed directly (not
delegated) with the combined-gate design described above, since a naive fix (just adding another
independent `start()`/`stop()` call pair) would have reintroduced exactly the race condition that
design avoids.

**Documentation-convention cleanup**: per the "Documentation Content Separation Rule"
(`~/.claude/shared/dev-workflow.md` §8), rationale/rejected-alternatives content that `developer-lite`
and the burn-in fix had written directly into `fake-sleep-overlay/PLAN.md` §7,
`burn-in-protection/PLAN.md` §4, and `multi-notification-stacking/PLAN.md` §2 was extracted into the
new `docs/DECISIONS.md` §20, leaving those PLAN.md sections stating only What/How with a link back.

**Commits**: 9 files committed individually (one file per commit, per `~/.claude/CLAUDE.md`'s
convention) — `docs/DECISIONS.md` (new §20), the 6 rewritten `other/` plans plus
`fake-sleep-overlay/PLAN.md` §7, and `docs/plans/README.md` (marking the cross-consistency pass as
done).

**Implementation difficulty/scope assessment and recommended order** (requested by the user, to plan
the `other/`-tier build sequence): each of the 9 features was assessed for difficulty and scope
against the current codebase and the now-finalized `FakeSleepIndicatorUiState` surface —

| Order | Feature | Difficulty | Scope | Key notes |
|---|---|---|---|---|
| — | `phone-call-overlay-suppression` | Trivial | None | Already resolved as "no implementation needed" (§11) — nothing left to build. |
| 1 | `dnd-handling` | Easy | Small | Extends the existing `NotificationSyncListenerService` with two lifecycle overrides, one `DataStore` boolean, one gating guard line. No new permissions or components. |
| 2 | `auto-stop-conditions` (battery, `PLAN-2-battery.md`) | Easy | Small | One new `BroadcastReceiver` (`ACTION_BATTERY_CHANGED`, no permission needed), one `DataStore` int, a full-stop gating hook. Fully self-contained. |
| 3 | `auto-stop-conditions` (proximity, `PLAN-1-proximity.md`) | Easy–Medium | Small | One `SensorEventListener` wrapper, writes `indicatorVisible`. Shares a callback-wiring point with `burn-in-protection` (both hook into the same proximity-covered signal) — implementing it right before burn-in-protection avoids a follow-up edit to wire them together later. |
| 4 | `burn-in-protection` | Easy–Medium | Small–Medium | One `Handler` reposition loop plus the combined 3-condition `recompute()` gate designed this session. Depends on `dismiss-and-peek-interaction`'s `OverlayUiState` transitions (already shipped) and proximity's covered/uncovered signal (step 3). |
| 5 | `indicator-color-customization` | Medium | Small–Medium | Mostly a settings-UI task (palette swatches + HEX field + validation); the render-side change is a single shared `tintColor` write, no per-view wiring needed. |
| 6 | `importance-based-sizing` | Medium | Medium | The most substantial settings UI among the smaller features (per-app keyword add/remove list), NFKC-normalized substring matching, a blink-interval slider, and a re-evaluation hook on every pending-notification change. |
| 7 | `auto-stop-conditions` (quiet hours, `PLAN-3-quiet-hours.md`) | Medium–Hard | Medium | The most OS-mechanism-heavy of the nine: two self-re-arming `AlarmManager` alarms, a `BroadcastReceiver` for firing, a `BootCompletedReceiver`, and a new `RECEIVE_BOOT_COMPLETED` permission — the only `other/` feature needing a boot receiver. Worth extra real-device time (including an actual reboot) to verify re-arming survives it. |
| 8 | `category-icons-and-assets` | Medium | Medium | Blocked on `docs/QA.md` #13 for real product value — it can ship behind an always-`OTHER` stub `CategoryMapper` per its own §4, but stays cosmetically inert until #13 is resolved. Also needs non-coding legwork: sourcing/licensing 5 icon assets. Resolving QA #13 alongside or just before this step is recommended rather than shipping the stub and revisiting later. |
| 9 | `multi-notification-stacking` | Hard | Large | The most complex `other/` feature: slot grouping/overflow logic, a fixed 2x2 grid layout, count-badge rendering, and interactions with both `category-icons-and-assets` (per-slot icons) and `importance-based-sizing` (per-slot high-importance). Most meaningful — and most testable — once both of those already exist; also the hardest to verify on-device (needs several real, simultaneous notifications from different apps). |
| 10 | `localization-additional-languages` | Easy (mechanically) | Small, but sequencing-sensitive | Pure translation pass over whatever `values/strings.xml` keys already exist — trivial code, but its own plan explicitly frames it as tracking "whatever keys exist at the time it's implemented," so it belongs last (or as a recurring sweep) rather than early, to avoid redoing it after every subsequent feature adds new strings. |

Rationale for this order beyond raw difficulty: features 1–2 are the simplest and validate the
already-established "extend the gating check" pattern with no new dependencies. Features 3–4 are
sequenced together because they share a wiring point discovered during this session's design work.
Features 5–6 are independent and can be done in either order. Feature 7 is saved for after some
implementation momentum/confidence is built, given it carries the most new OS-mechanism surface.
Features 8–9 are sequenced last among the visual features specifically because
`multi-notification-stacking` is far more meaningful to build and test once real category icons and
importance-based sizing already exist to combine with, rather than testing it against plain OTHER
icons first and having to re-verify later. Feature 10 is last by its own plan's design.

No new `docs/QA.md` entries were raised by this session's `other/`-tier work.

## 13. `other/`-Tier Implementation Begins: dnd-handling, auto-stop-battery, auto-stop-proximity (Follow-Up Session)

This session opened by reading the previous handoff, then implemented the first three `other/`-tier
features in the recommended order from §12: `dnd-handling` → `auto-stop-conditions`/battery →
`auto-stop-conditions`/proximity. Same branch/implement (`developer-lite`)/review
(`reviewer-lite`)/PR flow established for the MVP features (§5-§10), branches named
`feature/<feature-name>` (not `feature/mvp-<feature-name>`, per the `other/`-tier naming convention
already recorded in earlier handoffs).

**Housekeeping before the first PR**: `master` was 17 commits ahead of `origin/master` (all of the
previous session's MVP-integration-test and `other/`-tier-review work, §11/§12, had never been
pushed). Flagged to the user before creating `dnd-handling`'s PR (an unpushed `master` would have
made that PR's GitHub diff show all 27 commits instead of just the new feature's 10) — the user
chose to push `master` first, which was done, then feature-branch pushes/PRs proceeded normally for
the rest of this session.

**`dnd-handling`** (branch `feature/dnd-handling`, PR #8, merged): extends the existing
`NotificationSyncListenerService` with `onListenerConnected()`/`onInterruptionFilterChanged()` to
track DND state (`DndState`, in-memory singleton) and a DataStore-backed "hide during DND" toggle
(`DndSettings`, default `true`). Adds a gating guard to `FakeSleepSessionController.onScreenOff()`
— composed alongside the existing `FeatureEnabledState`/pending-empty checks, not replacing them
(the exact failure mode this project has been burned by before, §3). No dedicated settings screen
exists yet, so the toggle was added inline to `MainSwitchScreen`. `reviewer-lite` approved; found
one non-blocking finding — `VirtuaLedForegroundService.onCreate()` launches the DataStore cache
refresh fire-and-forget (not awaited), so a screen-off arriving before that read completes uses the
still-default cached value for one event. Logged as `docs/QA.md` #17, not fixed immediately (low
severity — only visible if the user deviated from the default toggle value).

**`auto-stop-conditions`/battery** (branch `feature/auto-stop-battery`, PR #9, merged):
`BatteryThresholdSettings` (DataStore Int, 1-100%, default 20) and `BatteryLevelReceiver` (sticky
`ACTION_BATTERY_CHANGED`, registered/unregistered in `VirtuaLedForegroundService`), calling
`stopSelf()` on trigger — confirmed by `reviewer-lite` to be the same full-stop path as the
in-app-switch-OFF (`onoff-notification-toggle`), correctly distinct from the notification-action
pause path. A `Slider` was added to `MainSwitchScreen` alongside the DND row. `reviewer-lite`
approved; found one non-blocking finding — unlike the DND toggle's `@Volatile` cached value,
`BatteryLevelReceiver`'s threshold is captured as a plain local `val` at registration time with no
live-refresh, so dragging the slider while the feature is already running has no effect until the
next OFF→ON cycle. Not a plan deviation (the plan's own pseudocode never specifies re-registration
on setting change) but a real UX inconsistency between two sibling settings — logged as
`docs/QA.md` #18, left for a future design decision rather than fixed immediately.

**`auto-stop-conditions`/proximity** (branch `feature/auto-stop-proximity`, PR #10, merged):
`ProximityMonitor` (wraps `SensorManager`/`TYPE_PROXIMITY`, safely no-ops on devices without the
sensor), started/stopped alongside `FakeSleepSessionController.startSession()`/`endSession()`. A
significant discovery here: `FakeSleepIndicatorUiState` — the 5-property visual extension surface
(`slots`/`tintColor`/`blinkIntervalMs`/`indicatorOffset`/`indicatorVisible`) that §12's
architecture-fix session had documented in `docs/DECISIONS.md` §20 and
`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7 — had actually **never been coded**; that session
only rewrote plan *documents* to describe the intended surface, not the class itself. This feature
is the first to actually need `indicatorVisible`, so `developer-lite` implemented
`FakeSleepIndicatorUiState` now, deliberately scoped to just that one property (not all 5), reasoning
that building the other 4 with no consumer yet would be exactly the speculative-code pattern this
project's conventions discourage. This is a visibility-suspend only — confirmed by `reviewer-lite`
that the proximity callback touches only `indicatorVisible`, never `sessionActive`, the `WakeLock`
guard, or pending-notification tracking, and that both the dot and preview-text states (not just the
dot) are gated, with `previewLinesState` itself untouched (whatever was showing reappears unchanged
on uncover). `reviewer-lite` approved with no blocking issues, only two informational notes for
whoever implements `burn-in-protection` next: (a) that plan's own §4 already anticipates extending
this same `ProximityMonitor` callback with `burnInProtector.onProximityCoveredChanged(covered)` —
confirmed real, not a gap; (b) `FakeSleepActivity`'s current render code just wraps the existing
dot/preview `Box`/`Column` in a single `if (indicatorVisible)` rather than the `IndicatorGroup`/
`SlotIcon` composable shape `fake-sleep-overlay/PLAN.md` §7 sketches — whoever adds `slots`/
`tintColor` will need to introduce that structure fresh.

**Docs updated along the way**: `docs/QA.md` #17 and #18 added (see above); `docs/plans/README.md`'s
"Other Features" list now shows all three of these sub-plans as done with their PR numbers, and two
previously-stale "PR pending" statuses (`dnd-handling`, battery) were corrected to their actual
merged PR numbers once each successive PR landed. `docs/HANDOFF.md` (this file) updated at the
user's request ("引き継いで" — hand off) rather than mid-session, so all three features' progress is
recorded together in this one section instead of three separate ones.

## 14. Four More `other/`-Tier Features Merged: burn-in-protection, indicator-color-customization, importance-based-sizing, auto-stop-conditions/quiet-hours (Follow-Up Session)

This session opened by reading the previous handoff, then implemented the next four features in
§12's recommended order, one at a time, each via the same branch/implement
(`developer-lite`)/review (`reviewer-lite`)/PR flow established since §5. All four are merged.

**`burn-in-protection`** (branch `feature/burn-in-protection`, PR #11, merged): adds `BurnInProtector`
(a fixed 4000ms instant-jump reposition `Handler` loop) and `FakeSleepIndicatorUiState.indicatorOffset`
— the first feature to actually code `indicatorOffset` (previously only sketched in docs). Gated on
session-active/`OverlayUiState`/proximity-covered via a single combined `recompute()`
(`docs/DECISIONS.md` §20's already-designed combined-gate shape). `reviewer-lite` approved; two
non-blocking notes: `FakeSleepSessionController.startSession()`'s `onSessionActiveChanged(true)` call
is dead in every path (the Activity/`BurnInProtector` don't exist yet at that point — real activation
happens in `FakeSleepActivity.onCreate()`, documented as a deviation in that plan's PLAN.md), and the
indicator sits at `(0,0)` for the first ~4s of each session before the first jump (matches the plan's
own pseudocode, not a defect).

**`indicator-color-customization`** (branch `feature/indicator-color-customization`, PR #12, merged):
`IndicatorColorSettings` (DataStore, default white) + an inline palette-swatch/HEX picker in
`MainSwitchScreen` (no separate settings screen — matches this app's no-navigation-library convention),
writing `FakeSleepIndicatorUiState.tintColor` (also newly coded here). Live color changes propagate to
an active session via `FakeSleepActivityHolder.indicatorUiState?.tintColor`, independent of
persistence. `reviewer-lite` approved with no blocking issues; confirmed the theoretical
async-load-vs-live-update race (session-start color load vs. a settings-screen edit) is practically
unreachable, since a new `FakeSleepActivity` is only created screen-off (untouchable), while the
settings screen requires screen-on/foregrounded.

**`importance-based-sizing`** (branch `feature/importance-based-sizing`, PR #13, merged) — the largest
`other/`-tier feature so far (12 commits): `KeywordRegistry`/`BlinkSettings` (DataStore, synchronous
caches mirroring `DndSettings`'s pattern), `ImportanceResolver` (NFKC/case-normalized substring match),
and a new `KeywordSettingsScreen` (installed-app picker + per-app keyword add/remove, reached via a
boolean-gated screen swap — the first `other/`-tier screen beyond the simple inline settings). First
feature to code `FakeSleepIndicatorUiState.slots`/`blinkIntervalMs` (scoped to `count`/`highImportance`
only; `category` deliberately deferred to `category-icons-and-assets`, per the same
scope-to-only-what's-needed precedent `burn-in-protection`/proximity already established). `reviewer-lite`
found one real (low-severity) defect — `KeywordRegistry.setKeywords()` deduplicated the DataStore write
but not the in-memory cache, so double-adding the same keyword could drift the two representations
apart — **fixed before merge** (dedup both sides).

**`auto-stop-conditions`/quiet-hours** (branch `feature/auto-stop-quiet-hours`, PR #14, merged) — the
most OS-mechanism-heavy `other/`-tier feature (self-re-arming inexact `AlarmManager` alarms + the only
`RECEIVE_BOOT_COMPLETED` consumer in this app). First `reviewer-lite` pass: conditionally approved,
flagged one real finding (a `goAsync()` coroutine body not guaranteeing `pendingResult.finish()` on a
thrown exception) — **fixed** (`try/finally` wrap). The main session then did live on-device testing
(arrows We2 Plus / `M06`, driven directly via `adb`, not by an agent — no device was reachable in
either agent's sandbox this round) and **found a real defect**: `QuietHoursState` only updated via the
edge-triggered alarm receiver, so a cold-started FGS (reboot, force-stop+relaunch, or first enable)
already inside a configured quiet-hours window wouldn't suppress the overlay until the *next day's*
trigger — confirmed directly (screen-off inside the window still launched the overlay). **Fixed and
re-tested live**: `QuietHoursScheduler.reschedule()` now also computes whether `now` is inside
`[start, end)` (handling midnight-spanning windows via a new `isWithinWindow()`) and syncs
`QuietHoursState` immediately on every call; re-tested on the same device/scenario, confirmed fixed
(two consecutive real screen-off events after a mid-window FGS restart both correctly stayed
suppressed). Final `reviewer-lite` pass on that fix: approved. `docs/QA.md` #19 logged the one
remaining live-untested path (alarm-fires-while-session-visibly-active → force-end), verified by code
review only (mirrors the already-proven DND `endSession()` reach-in pattern) since the test device's
aggressive re-lock behavior interrupted the timed setup needed for a full real-time test.

**Docs updated along the way**: `docs/plans/README.md`'s status column updated after each merge (PR
numbers #11-#14); `docs/QA.md` #19 added. This session established (via the quiet-hours live-testing
episode) that **this project's main session, not just agents, can and did drive real on-device testing
directly via `adb`** when a physical device is connected and unlocked by the user — including
installing freshly-built APKs, granting permissions, posting test notifications, screenshotting,
`uiautomator dump`-based UI-hierarchy inspection for precise tap coordinates, and `dumpsys`
(`activity`, `alarm`, `notification`, `power`, `window`) for state verification. This device (`M06`)
has two recurring characteristics worth knowing for future sessions: a very short
screen-off-to-relock timer (secure PIN re-engages within roughly a minute of screen-off, sometimes
faster), and the already-documented `docs/QA.md` #16 doze quirk — both make fully-scripted,
unattended multi-step UI automation unreliable; the working pattern that emerged is: batch as much
adb-only (non-visual) verification as possible, ask the user to unlock only immediately before a
UI-interaction step, and prefer techniques that don't need the screen to stay unlocked for a
multi-minute wait (e.g. verifying `AlarmManager` scheduling via `dumpsys alarm` rather than physically
waiting for a real-time alarm to fire).

## 15. category-icons-and-assets: Implementation, Review, and an Extensive Live-Device Bug Hunt (Follow-Up Session, PR #15 Open, Not Yet Merged)

Continuing directly from §14 in the same session. `category-icons-and-assets` was next in the
recommended order, but its PLAN.md's app-to-category mapping mechanism was an explicit open question
(`docs/QA.md` #13) blocking real product value. **Resolved this session, asked directly of the user**
(a product decision, not delegated): user-assigned per app via a settings screen, not auto-inferred —
same underlying rationale as §3's importance-detection decision (a hardcoded table only covers apps
someone thought to add ahead of time; a user-assigned screen correctly covers whatever apps a given
user actually has). Recorded as `docs/DECISIONS.md` §21; `docs/QA.md` #13 marked resolved;
`category-icons-and-assets/PLAN.md` updated to drop the open-question framing.

**Implementation** (branch `feature/category-icons-and-assets`, `developer-lite`, 14 commits): adds
`IndicatorCategory` (PHONE/MAIL/SNS/CALENDAR/OTHER), `CategoryMapper`/`DefaultCategoryMapper`,
`CategorySettings` (DataStore, per-app, synchronous cache mirroring `KeywordRegistry`'s pattern), 5
solid-white vector icon assets (classic Material Icons, Apache 2.0, attributed per-file — a shared
`res/drawable/THIRD_PARTY_LICENSES.md` wasn't possible since Android resource-directory filename
rules reject non-resource filenames there), and a new `CategorySettingsScreen`. `FakeSleepActivity`'s
render code swaps the plain dot for a tinted category icon when a slot has one resolved (falls back
to the dot when unassigned). Category for the single MVP slot resolves from the most-recently-posted
pending notification's app; recompute wiring merged into the existing `recomputeIndicatorState()`/
`recomputeIndicatorStateNow()` pass (renamed from `recomputeImportance*`) alongside the
already-shipped importance/blink logic — a shared `InstalledAppPicker.kt` helper was factored out of
`KeywordSettingsScreen` so both settings screens use one app-picker implementation, not two. First
`reviewer-lite` pass: **approved**, no blocking issues (cache correctness, the shared-helper
extraction, the recompute rename/merge, and the render branch — including confirming the icon lookup
is a compile-time `when` expression, not a runtime string lookup — were all verified by direct code
reading; build verified independently).

**Then an extensive live on-device testing round with the user** (same `M06` device, driven directly
via `adb` by the main session, with the user unlocking/interacting on the physical device at each
step) found and fixed **8 additional real bugs**, none caught by either `reviewer-lite` pass (all are
the kind of thing that only surfaces when a real screen, a real status bar, and real installed apps
are involved):

1. **Status bar overlap** (`CategorySettingsScreen.kt`) — the title row and back button rendered
   underneath the status bar (clock/battery), unreadable. Root cause: `enableEdgeToEdge()`
   (`MainActivity.onCreate()`) with no inset-aware padding anywhere in this screen. Fixed:
   `.windowInsetsPadding(WindowInsets.safeDrawing)`.
2. **Same status bar overlap in `KeywordSettingsScreen.kt`** — a pre-existing bug from
   `importance-based-sizing` (§14), sharing the identical top-row layout shape; fixed the same way
   since it was actively being exercised this round too.
3. **Category chip row overflow** — the 5 category chips' (電話/メール/SNS/カレンダー/その他)
   combined Japanese-label width exceeded the screen width, leaving the trailing その他
   (OTHER/unassigned) chip completely unreachable — confirmed via a live screenshot showing only 4 of
   5 chips with no way to reach the 5th. Fixed: `.horizontalScroll(rememberScrollState())`.
4. **App picker missing ordinary user apps (LINE, Gmail) entirely** — root cause #1: this app's
   `minSdk = 31` means Android 11+ package-visibility restrictions apply, and
   `InstalledAppPicker.loadInstalledApps()`'s `queryIntentActivities(ACTION_MAIN/CATEGORY_LAUNCHER)`
   call was silently returning only a small filtered subset of installed apps with no `<queries>`
   declaration in the manifest. Fixed: added a `<queries><intent>` block for exactly
   `ACTION_MAIN`/`CATEGORY_LAUNCHER` (not the broader `QUERY_ALL_PACKAGES` restricted permission,
   respecting this project's minimal-permission philosophy, `docs/DECISIONS.md` §4). Confirmed fixed
   (LINE/Gmail then appeared).
5. **Main switch visually snapping back to OFF after visiting keyword/category settings and
   returning** — root cause: `MainSwitchScreen`'s `enabled` state was `remember { mutableStateOf(false)
   }`, hardcoded, with no sync to reality; since `VirtuaLedApp()` swaps `MainSwitchScreen` out of
   composition when navigating to either settings screen, its remembered state resets to the hardcoded
   default on every return trip — even though `VirtuaLedForegroundService` was still genuinely
   running the whole time (confirmed via `dumpsys activity services`). Fixed: a new
   `isForegroundServiceRunning(context)` helper (queries `ActivityManager.getRunningServices()`,
   filtered to the app's own service — still valid post-API-26 per Android's documented
   backward-compatibility behavior for checking an app's own services, confirmed correct by
   `reviewer-lite`) seeds `enabled`'s initial value instead of a hardcoded `false`.

A **second `reviewer-lite` pass** over fixes 1-5 (5 commits, `a2a39f1`..`b346253`): **approved** —
verified each diff does what its commit message claims, confirmed fix #4's `<queries>` scope exactly
matches the actual query (no overreach), and specifically confirmed the `getRunningServices()`
deprecation reasoning in fix #5 is correct (the method remains reliable for an app checking its own
services, unlike inspecting other apps' services which was restricted starting API 26).

**Live confirmation the core feature actually works**: after fix #4 and a `docs/QA.md`-style
leftover-quiet-hours-configuration false alarm (a stale quiet-hours window from §14's testing was
still active and correctly suppressing all sessions — not a bug, just forgotten test state, resolved
by disabling it), the user assigned MAIL to their Outlook app, sent themselves a test email, and
confirmed via a live screenshot that the tinted mail-envelope icon rendered correctly (not a plain
dot) — the first real end-to-end visual confirmation of this feature's core purpose.

That surfaced **3 more real findings**, fixed and pushed but **not yet reviewed by `reviewer-lite`**
(commits `84f3bb5`, `be71a7b`, `34371f6`, all after the second review pass):

6. **Icon illegibility at the existing 12dp indicator size** — the user pointed out the mail icon was
   "extremely tiny" once actually seen at real size on the device; a plain circle reads fine at 12dp,
   but a detailed icon shape (envelope, phone handset, etc.) doesn't. Fixed: `INDICATOR_DOT_SIZE_DP`
   raised from 12 to 24 (a single shared constant — both the plain-dot fallback and every category
   icon use it, so this is a uniform size bump, not a per-icon change).
7. **No discoverable way to un-assign a category** — functionally possible via the その他 chip
   (writes `category = null`), but the user pointed out (after initially not realizing this
   themselves) that this isn't intuitive UI. Fixed: tapping an already-selected chip now toggles it
   back to unassigned too (standard selectable-chip UX convention), in addition to keeping その他
   available.
8. **`MATCH_DEFAULT_ONLY` silently excluding real launcher apps from the app picker** — after fix #4,
   the user still couldn't find their actual phone app (Rakuten Link, `jp.co.rakuten.mobile.rcs`) in
   the picker; investigated live via `dumpsys package jp.co.rakuten.mobile.rcs`, confirmed it has a
   properly-declared `ACTION_MAIN`/`CATEGORY_LAUNCHER` activity, so it should have been visible. Root
   cause: `InstalledAppPicker.loadInstalledApps()` queried with the `PackageManager.MATCH_DEFAULT_ONLY`
   flag, which additionally requires `CATEGORY_DEFAULT` on the target's intent-filter — a flag meant
   for implicit-intent resolution (e.g. "who can handle a generic action"), not for enumerating
   home-screen-visible apps; many real launcher-icon apps (confirmed: Rakuten Link) declare only
   `CATEGORY_LAUNCHER`, not `CATEGORY_DEFAULT`, and were silently excluded. Fixed: dropped the flag
   entirely (`queryIntentActivities(launcherIntent, 0)`), matching how Android's own home-screen
   launcher enumerates the app drawer. **Not yet confirmed by the user on-device** — this is the
   specific item the user asked to verify in a fresh session (see §17 below).

**Current state**: PR #15 (`feature/category-icons-and-assets` → `master`) is open at commit
`34371f6`, **not merged**. `docs/plans/README.md`'s `category-icons-and-assets` row is still stale
(says "blocked on QA #13" — needs updating once merged, not done yet since the feature isn't
finalized). No new `docs/QA.md` entries were added this round (all 8 findings were fixed directly,
not deferred).

## 16. Freemium Monetization Consultation and a Parallel-Session Git-Sharing Discovery (Follow-Up Session, Consultation-Only — No Code Changes)

This session opened as a documentation/spec consultation only, explicitly run alongside — not
instead of — a separate session doing PLAN implementation in the same repo, per the user's own
framing at the start. No `developer-lite`/`reviewer-lite` agent work happened here; every change
this session made is a `docs/` edit made directly.

**Freemium monetization direction decided** (discussed directly with the user, not delegated):
distribute as a low-price freemium app (a rough ¥100–200 figure was mentioned, not finalized)
rather than pure donationware — superseding §11's donationware framing (given a forward-pointer
note there rather than being rewritten, matching this doc's established correction style). Two
candidate premium features were discussed: per-category indicator color, and displaying the
notification-source app's own launcher icon instead of the fixed category icon set. The user chose
to build only the latter, judging it more valuable to users for the same implementation effort;
per-category color is deprioritized — not implemented, not currently planned (`docs/QA.md` #20
carries a follow-up note).

**Decision**: build the source-app-icon feature now as a fully working feature, gated by one local
`isPremiumUnlocked` DataStore-backed flag defaulting to unlocked (`true`) — no real billing exists
yet, so this lets the feature ship and be exercised/tested on-device before any purchase flow is
built. Recorded as `docs/DECISIONS.md` §22 (extends §7's empty-placeholder pattern to a concretely
working feature). `docs/detailed-spec.md` gained §20 (the feature itself) and §21 (the gating
mechanism); `docs/spec.md` §4/§5 updated to match. The real billing mechanism, exact price, and the
Play Console distribution-model transition real billing would need are all explicitly still
undecided (`docs/QA.md` #21) — **the user set an explicit boundary this session: do not start any
Play Console/distribution-model work, or notification-listener-permission Play-review prep, until
told to.**

**Knock-on decision**: since color no longer needs to vary per icon, the user also decided to
reduce `indicator-color-customization`'s already-shipped (PR #12) UI from an 8-swatch-palette-plus-
HEX-field down to exactly 5 presets with no HEX input — partly because 8+ free-form choices are
now unnecessary for a single shared color, partly to avoid the same wrap/scroll layout risk the
`category-icons-and-assets` chip-row bug (§15, item 3 above) already proved is real on this
project's actual test devices. Recorded as `docs/DECISIONS.md` §23; `docs/detailed-spec.md` §19 and
`docs/plans/other/indicator-color-customization/PLAN.md` §3/§4 updated with the specific code-level
follow-up needed (`ColorPickerSection` in `MainActivity.kt`; remove `parseHexColorOrNull`'s UI call
site). Filed as **[GitHub issue #17](https://github.com/hachi921082-source/VirtuaLED/issues/17)** —
judged small/self-contained enough for the lighter `fix/<slug>` Issue→PR flow
(`~/.claude/shared/dev-workflow.md` §2), not the heavier PLAN-based flow.

**`multi-notification-stacking` interaction resolved by reading real code, not just its `PLAN.md`**:
while spec'ing the source-app-icon feature's interaction with slot-based multi-notification
display, this session discovered `multi-notification-stacking` — "planned but not started" per
§12/§16 of the previous handoff — is now **already implemented** by the parallel session
(`IndicatorSlotBuilder.kt` and related files exist; 9 implementation commits; **PR #16 open**).
Reading `IndicatorSlotBuilder.kt` directly (rather than trusting the plan's prose) settled the
interaction: grouping is strictly per-app (`pending.groupBy { it.packageName }`), so each of the up
to 4 real slots already maps 1:1 to exactly one app — no ambiguity there. Two concrete follow-ups
recorded in `docs/detailed-spec.md` §20: `IndicatorSlotUiState` needs a `packageName` field added
(currently resolved but discarded inside `IndicatorSlotBuilder.build()`), and the 5th+-app "OTHER"
overflow slot has no single representative app, so it must always fall back to the category icon
regardless of whether app-icon mode is enabled.

**Major mid-session discovery: this session and the parallel implementation session share one
working directory, not separate worktrees.** `git worktree list` showed exactly one entry for this
repo. `git reflog` showed the branch moving underneath this session purely from the parallel
session's own activity — `master` → `feature/category-icons-and-assets` (merged as **PR #15**) →
`master` → `feature/multi-notification-stacking` (9 implementation commits, **PR #16** opened) —
interleaved with this session's own doc-only edits, which were sitting uncommitted on top the whole
time without conflict. Concretely, this means `docs/plans/README.md`'s `category-icons-and-assets`
row already read "Done (merged PR #15)" when re-read mid-session (not something this session did),
and `multi-notification-stacking` turned out to already be implemented with PR #16 open — both
**ahead of what §15/§16/§17 of the previous handoff described**, which is now stale on those two
points.

When this sharing was discovered, the user was asked how to handle this session's 6 uncommitted doc
files then sitting on `feature/multi-notification-stacking`; the user chose to commit them there
immediately (one file per commit, per this repo's convention) rather than stash or defer — done: 8
commits total this session (`7cb62b7`..`3f0b08e`), then pushed to
`origin/feature/multi-notification-stacking` as part of this session's wind-down. **Practical
implication for future sessions working in this repo concurrently with another session**: there is
no worktree isolation here — before editing anything, re-run `git status` /
`git branch --show-current` / `git reflog` fresh rather than trusting an earlier read from the same
conversation, since another session can and does change branch/HEAD state mid-conversation.

## 17. `multi-notification-stacking` Merged (PR #16) and a Concurrent-Session Branch Split (Follow-Up Session)

This session opened by reading the previous handoff, resumed PR #15 verification (the user confirmed
on-device that dropping `MATCH_DEFAULT_ONLY` did make Rakuten Link appear in the app picker, and that
the earlier indicator-size fix looked right too), got a final `reviewer-lite` pass on PR #15's 3
previously-unreviewed commits, and merged it (§15's item 8 resolved). Then implemented
`multi-notification-stacking` — the same feature this session's own read of the previous handoff (and,
independently, the parallel freemium-consultation session per §16) both flagged as next — via the usual
branch/`developer-lite`/`reviewer-lite`/PR flow, opened as **PR #16**.

**Live-device testing** (arrows We2 Plus / `M06`, driven directly with the user — real Outlook mail and
Rakuten Link missed-call notifications, not synthetic `adb shell cmd notification post` posts, since
that shell command turned out to always overwrite one single fixed `id=2020 tag=Test` notification
under `com.android.shell` rather than creating distinct ones — a real adb-testing-technique limitation
worth remembering for future sessions) confirmed the 2x2 grid renders multiple real apps in distinct
slots and the count badge shows correct per-app counts (mail=3 including a group-summary notification,
phone=2). Two real findings surfaced and were fixed before merge: (1) the count badge, at its default
unconstrained text size inside a 16dp circle over what was then a 24dp icon, was both illegible itself
and covered most of the icon — fixed by enlarging `INDICATOR_DOT_SIZE_DP` to 32dp, capping the badge
text at `10.sp`, and offsetting the badge outward by half its own diameter so it hangs off the icon's
corner (standard Android badge placement) instead of sitting fully inside it; (2) `docs/QA.md` #21
records a non-blocking finding that `CountBadgeSettings.enabledCached` is read as a plain Kotlin
property inside `SlotIcon` rather than a Compose `State`, so toggling the badge setting mid-session only
takes effect at the next unrelated recomposition (e.g. a pending-notification change, or a session
restart via screen off→on) — same non-blocking class of gap as the pre-existing QA #18.

**Mid-session discovery: a second concurrent Claude Code session (the same one that wrote §16) was
committing directly onto this session's own `feature/multi-notification-stacking` branch**, in the same
shared working directory (confirmed via `git worktree list` showing exactly one entry, matching §16's
own discovery of this from the other side). By the time this was noticed, 7 doc-only commits from that
session (freemium monetization direction, `source-app-icon-indicator-mode` spec, an
`indicator-color-customization` follow-up, and a `docs/HANDOFF.md` rewrite) had already landed on top of
this session's own multi-notification-stacking work and been pushed to
`origin/feature/multi-notification-stacking` — mixing an unrelated future-feature planning effort into
what was supposed to be a focused, already-`reviewer-lite`-approved PR.

**Resolved by splitting history, not by discarding anything**: the user paused the other session first,
then this session split the 7 mixed-in commits by content rather than treating them as one block —
6 pure-planning-doc commits (`docs/DECISIONS.md`, `docs/QA.md`, `docs/detailed-spec.md`, `docs/spec.md`,
`docs/plans/other/indicator-color-customization/PLAN.md`, `docs/plans/README.md`) went to a new branch
`docs/freemium-source-app-icon-planning`, opened as **PR #18** against `master`; the `docs/HANDOFF.md`
rewrite (§16 itself) was cherry-picked straight onto `master` instead, since a general project handoff
doc belongs there, not on either feature branch. `feature/multi-notification-stacking` was then
`git reset --hard` back to its pre-mixing tip, this session's pending icon/badge fix (which had been
sitting as an uncommitted working-tree change through all of this, saved via `git stash` for the
duration of the surgery) was re-committed on top, and the branch was pushed with
`--force-with-lease` (safe here: solo repo, feature branch not `master`, nothing else had pulled the
mixed-in state). A final `reviewer-lite` pass on just the 2 new commits (icon/badge fix, QA #21)
approved cleanly, and **PR #16 was merged** — `gh pr merge --merge --delete-branch` succeeded this time
without being blocked by the Claude Code auto-mode classifier (unlike most previous PRs in this
project). `docs/plans/README.md`'s `multi-notification-stacking` row now reads Done.

**Practical addition to §16's own lesson**: this repo having no worktree isolation between concurrent
sessions is not just a "notice and re-verify" situation — it can mean two sessions' commits land
*inside the same branch*, not just move `master`/`HEAD` around underneath a passive reader. If this
happens again: **identify each stray commit's actual file scope** (`git show --stat`) before deciding
how to split, since docs-only commits usually separate cleanly by file even when interleaved, and
prefer `git reset --hard` to a known-good prior tip + `git stash` for any uncommitted work + a clean
recommit, over trying to interactively rebase away individual commits from the middle of a stack.

**`docs/QA.md` #21 numbering note**: both PR #16 (merged, this session) and PR #18 (freemium, still
open) independently added their own "#21" entry, since neither branch could see the other's number at
the time. PR #16's #21 is now the one in `master`; **whoever reviews/merges PR #18 next needs to
renumber its own "#21" entry** (about the undecided real premium-gating mechanism/price point) to #22
to avoid a duplicate.

## 18. PR #18 Merged, `source-app-icon-indicator-mode` and Issue #17 Shipped, a Generic Live-Refresh Fix, QA #17 Resolved, a Live QA #16 Investigation, and a Long Settings-Screen UI Overhaul (Follow-Up Session)

This session opened by reading the previous handoff and worked through §20's recommended-next-step
list one item at a time, with the user explicitly choosing (after a memory-recorded incident on an
earlier session, `feedback_confirm_scope_before_acting`) to confirm before starting each new item
rather than have the whole list run unattended.

**PR #18 reviewed and merged.** Read the freemium-planning docs directly (not just skimmed) against
`docs/DECISIONS.md` §22/§23 and `docs/detailed-spec.md` §20/§21 — internally consistent, matched
the previous handoff's own description exactly. Confirmed via `git merge-tree` that merging into
`master` would be conflict-free despite `docs/QA.md`/`docs/plans/README.md` having diverged on both
sides since the fork point. Renumbered the branch's duplicate `docs/QA.md` "#21" (3 cross-references
+ the item itself) to "#22" before merging, per the previous handoff's own reminder. **Post-merge
cleanup**: the merge left `docs/QA.md` #21/#22 in the wrong file order (22 landed before 21) and left
a now-stale "whichever PR merges second should renumber" note on #21 — both fixed in a follow-up
commit.

**`source-app-icon-indicator-mode` implemented, reviewed, merged (PR #19).** `developer-lite` wrote
`docs/plans/other/source-app-icon-indicator-mode/PLAN.md` from `docs/detailed-spec.md` §20/§21 (no
`designer-lite` escalation needed — the spec + already-shipped code, e.g. `IndicatorSlotBuilder.kt`,
fully answered the open questions) and implemented it: `PremiumState` (local pre-billing flag,
default unlocked), `AppIconIndicatorSettings` (opt-in toggle, default off), `packageName` added to
`IndicatorSlotUiState`, the OTHER overflow slot structurally excluded from ever getting an app icon,
`PackageManager.getApplicationIcon()` rendering with per-slot fallback to the existing category
icon/dot. `reviewer-lite` approved after direct diff inspection (no billing code present, gating
logic correct, OTHER-slot exclusion double-enforced, clean independent build) and logged one
non-blocking follow-up (`docs/QA.md` #23: the toggle doesn't live-refresh mid-session, same class as
#18/#21 below). Merged via a normal `gh pr merge` with no classifier block.

**[Issue #17](https://github.com/hachi921082-source/VirtuaLED/issues/17) implemented directly, merged
(PR #20).** Small enough that the main session implemented and reviewed it directly rather than via
`developer-lite`/`reviewer-lite` (the lighter `fix/<slug>` flow already covers this): reduced
`ColorPickerSection` to exactly 5 preset swatches, removed the HEX field and `parseHexColorOrNull`
(no remaining call sites), removed the two now-orphaned string resources. Confirmed via a status-check
exchange with the user that "freemium系2項目" in an earlier instruction meant exactly these two items
(`source-app-icon-indicator-mode` + this issue) — matches how the previous handoff itself grouped
them.

**A recurring "settings toggle doesn't live-refresh mid-session" bug fixed generically (PR #21),
`docs/DECISIONS.md` §24 added.** By this point `docs/QA.md` had #18 (`BatteryLevelReceiver`), #21
(`CountBadgeSettings`/`SlotIcon`), and #23 (`AppIconIndicatorSettings`/`SlotIcon`) as three separate
instances of the same-shaped complaint. Investigating together showed two distinct root causes, not
one: (a) a `@Composable` reading a plain Kotlin property instead of a Compose `State` (#21, #23) —
fixed by backing the cache with `mutableStateOf` instead of `@Volatile var`, a drop-in change with no
call-site edits; (b) a callback (`BatteryLevelReceiver.onReceive()`) reading a value captured once at
registration instead of the current value (#18) — fixed by giving `BatteryThresholdSettings` the same
`DndSettings`-style live cache other settings already use, read fresh on every broadcast.
`PremiumState` was deliberately left untouched (no live in-session way to toggle it exists yet, so no
bug to fix there). All three QA items moved to Resolved; `docs/DECISIONS.md` §24 documents both fix
shapes generically so a future feature applies the right one instead of rediscovering it.

**`docs/QA.md` #17 (DND-cache startup race) resolved directly (PR #22).** Also implemented and
reviewed directly rather than via agents, since the fix was small and precisely understood: instead
of `DndSettings.refreshCache()` running as a separate fire-and-forget coroutine in `onCreate()` while
`ScreenStateReceiver` registered synchronously in `onStartCommand()`, registration now happens inside
the same coroutine as the refresh, after it completes — the receiver structurally cannot fire before
the cache is populated, since it isn't registered yet. A boolean guard
(`screenStateReceiverRegistrationStarted`) replaces the old null-check to prevent double-registration,
mirroring the same async-registration-then-field-assignment shape already used (and reviewed) for
`batteryLevelReceiver`.

**`docs/QA.md` #16 (OEM doze quirk) — live investigation, not resolved, new evidence added.** At the
user's request, set up **Wi-Fi adb** for the first time in this project (paired + connected to arrows
We2 Plus over the network, no USB cable) specifically to test the previous handoff's own "needs a
repeat with adb over Wi-Fi (no USB cable in the loop)" ask. The user is not deeply technical about adb
internals, so the whole pairing/reconnect flow and the repro test itself were walked through in plain,
jargon-free language rather than assuming familiarity with terms like "pairing code" or "wakefulness."
Captured logcat across repeated screen-off/screen-on cycles: the device's own inactivity timeout
reliably put it into `WAKEFULNESS_DOZING`, but the full-screen-intent notification woke it from Dozing
and launched `FakeSleepActivity` successfully every single time, with zero reproductions of the
original failure. This is evidence *against* "merely entering Dozing blocks the launch" as a
sufficient explanation on its own, and is consistent with (though doesn't conclusively prove) the
original USB-cable-interference hypothesis from `docs/QA.md` #16's first entry. The battery-only
(no adb at all) test #16 also asks for is still undone. No code changed as a result of this round.

**A long, iterative settings-screen UI overhaul, driven entirely by live on-device feedback across
many rounds, merged as PR #24.** This started from a single concrete report (the newly-shipped
app-icon toggle from PR #19 was structurally present but invisible) and grew, through repeated
screenshot-and-critique cycles with the user, into a near-total rework of `MainSwitchScreen`,
`CategorySettingsScreen`, and `KeywordSettingsScreen`:
- `MainSwitchScreen` gained actual scrolling (it had none — a `Box`-centered `Column` that silently
  clipped both ends once enough rows had accumulated across many past features, hiding both the new
  toggle and the pre-existing category-settings button) with safe-drawing inset padding.
- Every row was reordered to a mockup image the user provided, and unified onto two new shared
  composables (`SettingsSwitchRow`, `SettingsNavigationRow`) so the whole screen reads as one
  consistent label-left/control-right layout instead of a mix of centered blocks and left-aligned
  rows — including giving every row label `weight(1f)` late in the process, after the user
  specifically asked whether narrow-width screens had been considered (they hadn't been).
- DND's label now spells out "(DND)" (the user pointed out "おやすみモード" reads too similarly to
  "サイレントモード," a completely different Android concept) and its default flipped from
  hide-during-DND to show-during-DND, on the reasoning that a silent visual-only indicator doesn't
  conflict with what DND is meant to suppress, and a missed notification is worse for this app's
  purpose than one shown silently — the original `true` default had no recorded rationale at all.
  Quiet-hours dropped its katakana loanword label, and its start/end time buttons are now actually
  disabled while off (previously tappable regardless).
- The blink-interval slider became a −/+ stepper; the color picker marks the selected swatch directly
  instead of a separate preview circle (safe to drop the "might not match a preset" fallback case
  entirely, since this app has never been distributed to anyone — no legacy HEX-set values exist to
  protect).
- The app-icon-indicator row now routes to a new `PremiumInfoScreen` when locked (a disabled
  placeholder purchase button, no billing code — explicitly confirmed with the user that wiring a
  real purchase flow later is fine to defer, matching the project's established pre-billing-seam
  pattern) instead of hiding entirely, so the feature stays discoverable before it's unlocked.
- `CategorySettingsScreen`/`KeywordSettingsScreen` both switched from "flat app list + a separate
  detail panel below it" to an accordion — tapping an app expands its category picker / keyword
  editor directly underneath, closing the previous gap where it wasn't visually clear which app a
  change applied to. Category chips now render the actual indicator icon (required moving
  `IndicatorCategory.drawableRes()` out of `FakeSleepActivity.kt`'s private scope into
  `IndicatorCategory`'s own file so both places could use it) alongside their text label, and the
  chip row switched from horizontal-scroll to `FlowRow` (wraps onto a second line) since horizontal
  scroll wasn't discoverable — confirmed the wrap visibly kicks in on the Sharp AQUOS sense9's
  narrower screen once that device was also connected and given the same build.
- One correction along the way: an early pass in this same overhaul reverted the color-picker label
  from "アイコンの色" back to "インジケーターの色," undoing a wording the user had already settled on
  in an unrelated earlier mockup — caught and fixed once the user flagged it, recorded here as a
  reminder that a user-supplied mockup's exact text is a specification, not a suggestion to weigh
  against "don't rename things that aren't in scope."

**Filed [issue #23](https://github.com/hachi921082-source/VirtuaLED/issues/23), not fixed**: the user
noticed live that burn-in protection's periodic repositioning can push part of the indicator
off-screen. Root-caused by reading the code (not guessed): `BurnInProtector`'s reposition bounds are
computed from a single hardcoded icon size (`INDICATOR_DOT_SIZE_DP`), which doesn't account for the
actual rendered footprint once multiple slots spread into a 2x2 grid, a high-importance icon scales
1.5x, or the count badge hangs off a corner — any of which can make the true bounding box bigger than
what the bounds calculation assumes.

**Two stale `docs/plans/README.md` statuses fixed**: `source-app-icon-indicator-mode` and
`indicator-color-customization` both still read "PR pending" for PRs (#19, #20) that had already been
merged earlier in this same session — caught when the user directly asked "isn't
`source-app-icon-indicator-mode` still outstanding?" after a status summary failed to mention it was
actually done, a reminder to re-verify doc status against `gh pr view` rather than trusting a status
line written earlier in the same session before believing something is still outstanding.

**Workflow notes worth carrying forward**: this session established **Wi-Fi adb** as a working
pattern for this project (pairing code + connect address both come from the device's own "Wireless
debugging" screen and change on every reconnect — always ask the user for the current values fresh
rather than assuming a previously-used address still works; `adb pair` persists across reconnects,
only the connect address rotates). It also surfaced a real gap worth remembering: **a successful
`assembleDebug` does not mean the device is running that build** — this session ran several rounds of
code changes and re-testing without noticing the test device was still running a build from hours
earlier, until the user asked why a default-unlocked feature wasn't visible; always `adb install -r`
the freshly-built APK before asking the user to verify anything on-device, don't assume an earlier
install in the same session is still current after further code changes. Finally, several fixes this
session (issue #17, `docs/QA.md` #17, and the entire UI overhaul) were implemented and reviewed
directly by the main session rather than delegated to `developer-lite`/`reviewer-lite` — reasonable
for narrow, well-understood changes or rapid live-device iteration loops, but the full agent flow
remains the default for anything larger or less thoroughly understood up front.

**Two memory updates recorded**: a new standing rule to proactively tell the user whenever a change
adds a `strings.xml` key, since `localization-additional-languages` is scoped to whatever keys exist
at implementation time and a forgotten key silently falls back to the default locale rather than
erroring; and an explicit instruction, added to the existing "freemium is user-driven" memory, to
never nudge or suggest picking up `docs/QA.md` #22 (the real premium-gating mechanism/price point) —
it is deliberately the last item on this project's roadmap and the user does not want it raised
proactively, only answered if asked about directly.

## 19. Issue #23 Fixed (PR #25), a Notification-Exclusion Feature Scoped and Split into Issues #26/#27, and Issue #26 Implemented and Merged (PR #28) (Follow-Up Session)

This session opened by reading the previous handoff, then implemented **[Issue #23](https://github.com/hachi921082-source/VirtuaLED/issues/23)** (burn-in reposition can push the indicator off-screen) directly in the main session — no `developer-lite`/`reviewer-lite`, per the established precedent for small, well-understood fixes (§18) — since the issue itself already pinned the root cause and fix direction.

**Issue #23 fix** (branch `fix/burn-in-bounds`): `BurnInProtector`'s fixed `slotGroupSizePx: Int` (a single hardcoded icon size, computed once at `FakeSleepActivity.onCreate()`) was replaced with `footprintPx: () -> IndicatorFootprint`, re-evaluated on every reposition jump the same way `screenBoundsPx` already was. `FakeSleepActivity.computeIndicatorFootprintPx()` derives the real current footprint from `slots.value` — column/row count from slot count, `highImportance`'s 1.5x scale, and the count badge's corner overhang (added as a separate `topInsetPx`, since the badge's upward overhang shrinks the *minimum* y a jump can land at, not the maximum). `docs/plans/other/burn-in-protection/PLAN.md`'s implementation note was updated to match. Build verified (`assembleDebug`), merged via PR #25 with no classifier block, issue auto-closed by the PR's `Closes #23`. `docs/RESOLVED-ISSUES.md` got its first-ever entry this session (the file had existed as an empty stub since project start) and `docs/plans/README.md`'s burn-in-protection row was updated from "not yet fixed" to fixed.

A live on-device verification pass was started (Wi-Fi adb to arrows We2 Plus / `M06`: confirmed notification-listener access and `POST_NOTIFICATIONS` already granted, confirmed the foreground service started after the user toggled the in-app switch ON) but was called off mid-setup — the user clarified issue #23 was already considered done and didn't need the extra device pass. No negative finding; simply not completed. Two devices were connected via adb this session: `SH-M26` (sense9, USB) and `M06` (arrows We2 Plus, Wi-Fi, appearing under two transport IDs simultaneously). Neither this session's Bash nor PowerShell had `adb` on `PATH`; the full path (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`) had to be used directly — worth remembering for future sessions in this environment.

**Notification-exclusion feature scoped and split** (discussed directly with the user, not delegated): the user asked whether a per-app "don't notify me for this app" exclusion could be added, ideally selectable straight from an actual received notification. Assessed as two separable halves — (A) a settings-screen app-picker exclusion (small, mirrors `CategorySettings`'s existing pattern) vs. (B) excluding directly from a real received notification (needs a new "seen apps" persisted history mechanism that doesn't exist anywhere yet, plus a new UI surface — closer to `category-icons-and-assets`' scale). Doing both together was judged too large for this project's lightweight `fix/<slug>` Issue→PR flow (`~/.claude/shared/dev-workflow.md` §2). At the user's request, split into two GitHub issues: **[#26](https://github.com/hachi921082-source/VirtuaLED/issues/26)** (the settings-picker version, self-contained) and **[#27](https://github.com/hachi921082-source/VirtuaLED/issues/27)** (the from-a-real-notification version, explicitly deferred, marked as depending on #26, with its "seen apps" persistence/UI design left open rather than guessed at).

**Issue #26 implemented and merged** (branch `fix/app-exclusion-setting`, again directly in the main session, matching #23's precedent): new `AppExclusionSettings` (per-app DataStore boolean, mirrors `CategorySettings`'s persistence pattern, with the same synchronous-cache-plus-`refreshCache()` shape). Gated at the earliest possible point — `NotificationSyncListenerService.onNotificationPosted()` and its `onListenerConnected()` resync — so an excluded app's notification never reaches `PendingNotificationsRepository` at all, unlike category/keyword assignment (which only changes a slot's appearance, not whether it exists). `VirtuaLedForegroundService.onCreate()` refreshes the new cache at startup, matching the majority cache-refresh pattern already used for `CategorySettings`/`KeywordRegistry`/etc. (not the stricter await-before-registering pattern used for `DndSettings`/`BatteryThresholdSettings` — accepted as a known, low-severity race window consistent with `docs/DECISIONS.md` §24's existing tolerance for this class of gap, not fixed proactively). New `ExclusionSettingsScreen` reuses the shared `InstalledAppPicker` (like `CategorySettingsScreen`/`KeywordSettingsScreen`) but renders a direct per-app `Switch` with no accordion, since exclusion is a plain boolean rather than category's multi-choice pick. Wired into `MainSwitchScreen` via a new navigation row. 4 new `strings.xml` keys added (`settings_exclusion_settings_row_label`, `exclusion_settings_title`, `exclusion_settings_back_button`, `exclusion_settings_description`) — flagged per the standing memory rule for `localization-additional-languages`. Build verified, installed on `M06`, merged via PR #28 with no classifier block, issue auto-closed, `docs/RESOLVED-ISSUES.md` updated.

**Not yet done**: the user had not confirmed on-device that excluding an app via the new screen actually suppresses its notifications before asking to commit/PR/merge — the APK was installed on `M06` but no confirmation was reported back. Treat this as an open on-device verification item, not a known defect.

## 20. A Very Long Follow-Up Session: Issues #27/#30/#32/#33/#36/#37/#38/#43 Shipped, App Icon and License Screen Added, Localization Completed (Last `other/`-Tier Item), and an Unresolved Full-Screen-Intent App Ops Investigation In Progress

This session opened by reading the previous handoff and ran for an extremely long time, covering far more ground than a typical follow-up session. Context ran out before the final investigation (see the end of this section) could be resolved — **this was the most important thing for the next session to pick up** — resolved in §21.

**Issue #27 implemented and merged (PR #29)**: "seen apps only" filter added to the existing `ExclusionSettingsScreen` instead of a separate screen (user decision) — new `SeenAppsHistory` (DataStore set of packageNames that have ever notified), recorded from both `onNotificationPosted()` and the `onListenerConnected()` resync, independent of exclusion status. Live-confirmed on `M06`.

**Issue #30 found, filed, and fixed (PR #31)**: live investigation (triggered by a user question about proximity-sensor behavior) found the fake-sleep session only ever started on a genuine `ACTION_SCREEN_OFF` broadcast — a notification arriving while the screen was *already* off never triggered anything until the next manual screen cycle. Fixed by wiring `PendingNotificationsRepository`'s pre-existing but unused `onBecameNonEmpty` hook into `FakeSleepSessionController`, gated on `PowerManager.isInteractive() == false`. Confirmed live multiple times, including the "nobody touches the phone at all" case (screen auto-wakes via the full-screen-intent itself).

**Issue #33: the high-importance blink cue removed entirely (PR #34)**, not just defaulted off — live use found it disorienting combined with burn-in repositioning. High importance is now 1.5x size only. `BlinkSettings` and all blink UI/state deleted. `docs/DECISIONS.md` §15 marked superseded. **Process note**: this was implemented directly on a "やめよう"/"除外して" instruction without filing an issue first; the user pushed back ("勝手な判断じゃないか?"), and the issue was filed retroactively per their request. Recorded as memory `feedback_issue_first_even_when_instruction_is_direct` — a direct instruction does not waive this project's issue-first convention for real feature changes.

**Issue #32 found via code reading and fixed (PR #35)**: distinct from the already-fixed #23, this covered the *gap between* burn-in reposition jumps — a slot's `highImportance` (and therefore its 1.5x footprint) can change mid-interval without the group's position being re-validated. Fixed with `BurnInProtector.onSlotsMayHaveChanged()`, called from `recomputeIndicatorState()`, which re-clamps the *current* offset into the new bounds (not a fresh random jump).

**Off-session work**: the built debug APK was sent to the user via `SendUserFile` and copied into the repo root (gitignored, `*.apk` already excluded) per explicit request — this became a recurring ask throughout the session (`app-debug.apk`, later `VirtuaLED.apk`, kept updated after most merges). A Galaxy A30 (Android 10) install was investigated — `パッケージの解析中に問題が発生しました` traced to `minSdk 31` > the device's API 29; a **test-only** build with `minSdk` temporarily lowered to 29 was produced and sent, then the manifest reverted (master's `minSdk` was never actually changed — this project's official support scope is still only the two devices in `docs/DECISIONS.md` §11). The real blocker on that device turned out to be Samsung's aggressive background-app battery management, not notification-listener onboarding; a plain-language, checkbox-style setup-guide Artifact was published for a non-technical recipient to follow (notification access + battery-optimization exclusion), and it reportedly worked.

**Issue #36: suppress repeated re-notification for already-seen content — went through a real design correction mid-flight.** The first pass only filtered `PeekInteractionController.buildPreviewLines()` (skip already-shown text). Live testing showed this didn't address the actual complaint — the dot/auto-wake kept re-triggering for the same stale content. Corrected (same PR #39, title/description updated before merge): `FakeSleepSessionController.tryStartSession()` now refuses to start a session at all when every pending notification is already-seen, **except** when at least one is high-importance (mirrors the existing "size reflects true pending status regardless of seen-state" rule) — the preview-text fallback became structurally unreachable once this landed and was removed. This session later added a **toggle** for the whole feature (`RepeatSuppressedNotificationSettings`, default ON) after the user pointed out a real opposing need: forgetful users want the repeat reminders. **This toggle's code was left uncommitted** — committed and merged in §21 (issue #47).

**Freemium/Booth distribution licensing consultation** (direct discussion, no code): confirmed all dependencies are Apache 2.0 (no GPL/LGPL conflict with freemium distribution), identified that the per-file XML attribution comments for the 5 category icons aren't visible to anyone downloading a built APK, and that notification-content handling should be disclosed. Led directly to:

**Issue #37: in-app license/credits screen (PR #40)** — new `LicenseScreen.kt`, small unobtrusive text link at the bottom of the main screen (not a full navigation row). Covers icon attribution, library licenses, and a plain-language note on notification-data handling.

**App icon replaced (Issue #38, PR #41)**: the placeholder white circle was replaced. Three concepts were designed and presented in a comparison Artifact (circle/squircle/rounded-square mask previews): "灯" (the dot itself), "夜番" (crescent moon + light, ties to the sleep-gated mechanism), and "灯りの角" (phone silhouette + corner dot, callback to the project's original "NoLED-Revive" name) — the user picked the third, then asked for a less "lonely" background, refined with a warm radial gradient and a soft glow on the dot. Implemented as real `ic_launcher_background.xml`/`ic_launcher_foreground.xml` vector drawables (gradient via `<aapt:attr>`, glow via layered semi-transparent circles — VectorDrawable has no blur).

**`localization-additional-languages` implemented (PR #42) — the last of the original 10-item `other/`-tier list.** Found and corrected a real plan/reality mismatch before implementing: the plan specified default `values/` = English with a `values-ja/` folder for Japanese, but every single feature across the whole project had already shipped its strings in Japanese directly into the default folder. Rather than a risky mass-rewrite, the plan and `docs/detailed-spec.md` §10 were corrected to match reality: `values/` stays Japanese, `values-en/` added (not `values-ja/`). All 64 keys translated into English and Simplified Chinese, verified identical key sets across all three files, confirmed live via Android 13+ per-app locale override (`cmd locale set-app-locales`, no system-wide language change needed) without disrupting the device's own language, then reverted. **A new `strings.xml` key was added later in this same session (`settings_repeat_suppression_label`, for the toggle above) — translated into all three locales at the time it was added**, so this doesn't currently need a follow-up sweep, but future features will still need one per the plan's own "keeping in sync" note.

**Root-level `README.md` added** (Japanese, per explicit request — this project's primary distribution audience) — project purpose, features, supported devices, build instructions, distribution status, pointer to `docs/`.

**Issue #43 found and fixed (PR #45)**: user reported `MainActivity` intermittently appearing instead of the real lock screen after unlocking from a session. Root-caused via manifest reading: `FakeSleepActivity` had no explicit `taskAffinity`, so it defaulted to the same affinity as `MainActivity`; `launchMode="singleTask"` would then reuse `MainActivity`'s existing task (if still alive in recents) instead of a fresh one, and finishing the session revealed `MainActivity` sitting underneath. Fixed with `android:taskAffinity=""`. Build-verified, not yet live-tested, but merged (user-authorized, low-risk single manifest attribute).

**Issue #44 found and implemented, not yet merged (PR #46, open)**: user asked whether the status/navigation bars staying lit during a session risked burn-in — they do (fixed position, never repositioned by `BurnInProtector`, unlike the indicator) and waste battery. Fixed with `WindowInsetsControllerCompat` hiding `systemBars()`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` for edge-swipe recall. Built and installed on-device once, but the session moved on to other work before a live session was actually run to visually confirm the bars disappear — **treat as implemented-but-unverified**, not confirmed-working.

**Unresolved at context-limit: a full-screen-intent App Ops investigation, in progress.** The user reported that after the most recent fixes, notifications stopped appearing on the fake-sleep screen at all. Live investigation found `FSI_REQUESTED_BUT_DENIED` on the posted notification despite the manifest permission showing `granted=true` — `adb shell cmd appops get com.virtualed.app USE_FULL_SCREEN_INTENT` showed an explicit **`deny`** at both the UID and package level. Forcing it to `allow` via `adb shell cmd appops set --uid <uid> USE_FULL_SCREEN_INTENT allow` immediately fixed it (confirmed live — `FakeSleepActivity` launched correctly, flag cleared). This is an OS App Ops state, not a code regression from this session's changes. A full uninstall + clean reinstall showed the App Op at `"default"` mode (not an explicit `deny`) — so this isn't something every fresh install hits automatically. **The user then revealed the test device had received an Android OS update between the last successful test and now** — the leading hypothesis is that the update re-evaluated this already-installed app's full-screen-intent privilege under a newer, stricter policy and reset it to `deny`, and that a *truly* fresh install's `"default"` mode may or may not actually behave as `allow` on this updated OS version (not yet confirmed either way). The user was mid-way through onboarding + switch-on on the freshly-reinstalled app, to test whether `"default"` mode works, when context ran out. **Resolved in the following session — see §21.**

## 21. FSI Investigation Closed (QA #24), Issues #47/#49/#44 Shipped (PRs #48/#50/#46) (Follow-Up Session)

**FSI App Ops investigation (§20) closed — not a code or policy problem.** On `M06` (now Android 16 / API 36), the freshly-reinstalled app with the op at `default` launched `FakeSleepActivity` correctly. Both paths were checked: screen-off-then-arrival (issue #30) and screen-off-with-pending. The indicator was confirmed visually. `cmd appops get` can still print a recent `rejectTime` in `default` mode without anything actually being blocked. Conclusion: the OS update reset the already-installed app's op to `deny` as a one-off. Written up as `docs/QA.md` #24 (resolved), including a "check `cmd appops get` first after an OS update" gotcha.

**Follow-up live verification: the system switch maps directly to the App Op.** Settings > Apps > Special app access > Full-screen notifications ("このアプリからの全画面通知を許可する"):
- OFF → uid `deny`. Reproduced the exact `FSI_REQUESTED_BUT_DENIED` symptom.
- ON → uid `allow`. FSI works again, **no reinstall needed**.

Recorded on issue #49.

**Issue #47 / PR #48 merged — repeat-suppression toggle.** The §20 toggle code that had been sitting uncommitted on `master` got an issue, a branch, per-file commits, and a `docs/DECISIONS.md` §25 addendum explaining why the "no toggle" stance was reversed. The user confirmed it live.

**Issue #49 / PR #50 merged — detect a revoked FSI permission.** The new behavior:
- `PermissionChecker.canUseFullScreenIntent()` (`NotificationManager.canUseFullScreenIntent()` on API 34+, always `true` below) plus `fullScreenIntentSettingsIntent()` (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` + `package:` URI, which lands directly on this app's page).
- Onboarding has a second card and completes only when both listener access and FSI are granted.
- Root routing still gates on listener access only. A later FSI-only revocation is handled by `MainSwitchScreen`, not onboarding.

`MainSwitchScreen` rechecks on every `ON_RESUME`. While FSI is denied, the main switch is disabled and a red warning with an "Open settings" button is shown. If the feature was ON when the revocation is detected, it is switched OFF automatically.

All three behaviors were user decisions, recorded on the issue. `docs/detailed-spec.md` §2/§15 updated. 4 new string keys, translated in all 3 locales at add time. Every flow was live-verified on `M06`, including onboarding with both permissions revoked via adb.

**Issue #44 / PR #46 merged — hide the system bars during fake-sleep.** Before the fix, a screenshot showed the 3-button nav bar drawn in gray during a session. The status bar was already hidden by the theme's `windowFullscreen`, which is why it looked partly done. After the fix, `navigationBars`/`statusBars` are both `visible=false`, and swiping up from the bottom edge shows them transiently before they auto-hide. Pressing Home while the bars are shown returns to the real (PIN-gated) lock screen — correct, since `FakeSleepActivity` only occludes the keyguard. The branch had `master` merged in before testing.

All three PRs were merged **via local `git merge --no-ff` + push**, explicitly authorized by the user. GitHub correctly marked each PR as merged and auto-closed the issues. `docs/RESOLVED-ISSUES.md` was updated for #44/#47/#49. The merged branches were deleted locally and on the remote.

**Testing gotcha found:** `adb shell cmd notification post` parses its args oddly (`-t title TAG TEXT` with multi-word text). Two test posts ended up with the same tag `check`, so the second one had the same key as an already-seen notification. Issue #36's suppression then correctly refused to start a session, which looked like a bug at first. Use a unique single-word TAG and TEXT per test post (e.g. `cmd notification post navswipe4 swipetest4`). Shell test notifications also can't be cancelled via `cmd notification` on this build — the user has to swipe them away.

## 22. Still Open

- **Non-main-screen start paths don't check FSI** (issue #49 scope note): `BootCompletedReceiver`
  (auto-start on boot) and the FGS notification's "オンにする" action (`OnOffActionReceiver`) can
  start the service while FSI is denied. The feature is then switched OFF the next time
  `MainSwitchScreen` is opened. Not decided whether to also gate these paths — ask the user before
  filing/implementing.
- **`docs/QA.md` #16 and #19** (carried over from much earlier sessions, both non-blocking) — still
  not resolved.
  - #16 is the OEM ambient/doze wakefulness quirk on arrows We2 Plus. The battery-only (no adb)
    test is still undone, and a reliable force-stop + relaunch workaround exists.
  - #19 is the quiet-hours "alarm fires while a session is visibly active → force-end" path, which
    is still verified by code review only.
- **This repo has no worktree isolation between concurrent sessions, and stray commits can land
  inside another session's branch, not just move `master`/`HEAD`** (discovered §16, sharpened §17).
  Before any future concurrent-session work, re-verify branch/HEAD state fresh (`git status`,
  `git branch --show-current`, `git log --oneline`, `git reflog`) rather than trusting an earlier
  read in the same conversation. Read §17's "practical addition" paragraph if a mixed-branch
  situation comes up again. The risk is structural, not one-time.
- **Wi-Fi adb pairing/connect addresses rotate on every reconnect** (discovered §18). Always ask
  the user for the device's current "Wireless debugging" screen values fresh. `adb pair` itself
  persists; only the connect address changes. (§21 used a USB connection, `zh2ba2jx5j`.)
- **A successful `assembleDebug` does not mean the connected device is running that build**
  (discovered §18). Always `adb install -r` the freshly-built APK before asking the user to verify
  anything on a real device — every time, not just after the first install of a session.
- **adb is not on `PATH`** in this environment. Use `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
- **PIN entry during manual device testing**: only needed for the very first unlock of a testing
  sequence.
- **Secure-keyguard mechanism** (`docs/DECISIONS.md` §18): confirmed on arrows We2 Plus. A secure-lock
  pass specifically on the Sharp AQUOS sense9 is still outstanding (carried over).
- Package name placeholder (`com.virtualed.app`) and the old `NO-LED` folder cleanup
  (the sibling `NO-LED` folder next to this repo) are both carried over unchanged and still not urgent.
- **Billing/Play Console/distribution-model work remains explicitly off-limits unless asked
  directly** — reaffirmed multiple times across sessions (`docs/QA.md` #22). Do not raise it
  proactively, including in "what's left" summaries.

## 23. Recommended Next Step

No mid-flight work. Everything from §20's open list is resolved, merged, or carried over above.

1. Ask the user whether the non-main-screen start paths (boot, FGS notification action) should also
   gate on FSI (§22, first item). If yes: issue first, then branch/PR/live-verify.
2. `docs/QA.md` #16/#19 remain available for opportunistic follow-up but are not blockers.
3. Any new `strings.xml` key still needs translating into `values-en/` and `values-zh-rCN/` at add
   time (per the localization plan's "keeping in sync" note).
4. Do not raise billing/Play Console work proactively (`docs/QA.md` #22).
