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
16. Still Open
17. Recommended Next Step

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

## 16. Still Open

- **PR #15 (`category-icons-and-assets`) is open, not merged** (§15) — this is the immediate blocker
  before anything else `other/`-tier proceeds. Specifically open: whether commit `34371f6` (dropping
  `MATCH_DEFAULT_ONLY`) actually makes Rakuten Link (`jp.co.rakuten.mobile.rcs`) appear in the app
  picker on-device — the user asked to verify this in a fresh session rather than continuing in this
  one. See §17 for the exact resume steps.
- **`docs/plans/README.md`'s `category-icons-and-assets` row is stale** (§15) — still reads "blocked
  on QA #13," which is now resolved (`docs/DECISIONS.md` §21). Update once PR #15 actually merges,
  not before (the row should reflect merged reality, matching every other `other/`-tier row's
  convention).
- **`other/`-tier build order** (§12): the remaining recommended order after `category-icons-and-assets`
  is `multi-notification-stacking` → `localization-additional-languages`. The first 8 of 10 features
  are now done (`dnd-handling`, `auto-stop-conditions` ×3, `burn-in-protection`,
  `indicator-color-customization`, `importance-based-sizing`, quiet-hours) — `category-icons-and-assets`
  is the 9th, in progress. `multi-notification-stacking` is intentionally last-but-one since it's more
  meaningful to build/test once `category-icons-and-assets` (per-slot icons) already exists — which
  will be true once PR #15 merges. `localization-additional-languages` is last by its own plan's
  design (tracks whatever `strings.xml` keys exist at the time it's implemented).
- **`docs/QA.md` #17** — a narrow, non-blocking DND-cache-refresh race on service start (§13). Not a
  blocker.
- **`docs/QA.md` #18** — the battery-threshold slider doesn't live-refresh while the service is
  already running (§13). Not a blocker, unresolved design question.
- **`docs/QA.md` #19** — quiet-hours' real-time alarm-fires-while-session-active path is verified by
  code review only, not an actual timed on-device test (§14). Not a blocker.
- **`FakeSleepIndicatorUiState` is now fully implemented** — all 5 originally-sketched properties
  (`indicatorVisible`, `indicatorOffset`, `tintColor`, `slots`, `blinkIntervalMs`) exist and are wired,
  as of `importance-based-sizing`/`category-icons-and-assets` (§14/§15). No longer a partial surface —
  this line in earlier handoffs is now resolved, kept here only as a closure note.
- **This session's device-testing pattern is now established practice** (§14's closing paragraph) —
  worth reading before doing more live `adb`-driven testing with the user: batch non-visual `adb`
  verification, ask for unlock only right before a UI-interaction step, avoid multi-minute real-time
  waits where a `dumpsys`-based check can substitute.
- **Package name placeholder**: `com.virtualed.app`, still unconfirmed by the user — not urgent, not
  a blocker (§5/§6).
- **Old `NO-LED` folder cleanup**: `c:\Users\m9112\vscode2\NO-LED` — status not re-checked in several
  sessions; carried over.
- **App icon**: interim Material Symbols plan stands, final art deferred (carried over).
- `docs/QA.md` #16 — the OEM ambient/doze wakefulness quirk (`WAKEFULNESS_DOZING`) on arrows We2
  Plus (§11); not reproduced again in §14/§15's extensive testing this session, but root cause still
  not isolated. Not a blocker.
- Secure-keyguard mechanism (`docs/DECISIONS.md` §18): confirmed on arrows We2 Plus (§8, §11); a
  confirmed secure-lock pass specifically on the Sharp AQUOS sense9 is still outstanding, not a
  blocker (carried over unchanged for several sessions now).

## 17. Recommended Next Step

**MVP is fully complete** (§11), the `other/`-tier pre-implementation cross-consistency review and
architecture fix are done (§12), and 8 of 10 `other/`-tier features are implemented, reviewed, and
merged (§13/§14): `dnd-handling` (PR #8), `auto-stop-conditions`/battery (PR #9),
`auto-stop-conditions`/proximity (PR #10), `burn-in-protection` (PR #11),
`indicator-color-customization` (PR #12), `importance-based-sizing` (PR #13),
`auto-stop-conditions`/quiet-hours (PR #14). `category-icons-and-assets` (the 9th) is implemented,
reviewed, and extensively live-tested, but **PR #15 is still open, not merged** (§15) — this is
where the next session should pick up.

1. **Resume PR #15 verification** (§15, item 8 in that section's bug list). The immediate task the
   user asked to defer to a fresh session: confirm commit `34371f6` (dropping `MATCH_DEFAULT_ONLY`
   from `InstalledAppPicker.loadInstalledApps()`'s `queryIntentActivities()` call) actually makes
   Rakuten Link (`jp.co.rakuten.mobile.rcs`) appear in the app picker on the `M06` test device. The
   fix is already built, committed (`34371f6`), pushed, and was installed on the device at the end of
   the previous session — a fresh session just needs the user to open the category (or keyword)
   settings screen and confirm Rakuten Link is now visible in the list. If confirmed working: proceed
   to step 2. If still missing: investigate further before merging (read `InstalledAppPicker.kt`'s
   current query logic fresh, and consider whether some other resolution constraint is still filtering
   it out — the diagnostic technique that worked well this session was `adb shell dumpsys package
   <pkg>` to inspect the target app's actual declared intent-filters directly, rather than guessing).
2. **Get a final `reviewer-lite` pass** over the 3 not-yet-reviewed commits (`84f3bb5` size increase,
   `be71a7b` chip toggle-off, `34371f6` `MATCH_DEFAULT_ONLY` fix) — mirroring the two-round review
   pattern already used earlier in this same PR (§15) and for quiet-hours (§14). Give the reviewer the
   full context of *why* each fix exists (user-reported issues during live testing, not self-initiated
   changes) so it can judge correctness rather than re-relitigating the UX decisions themselves.
3. **Merge PR #15** once reviewed (ask the user how — `gh pr merge` first per the established
   preference, §8/§9/§10, falling back to local `git merge --no-ff` + `push` only if blocked), then
   update `docs/plans/README.md`'s `category-icons-and-assets` row (currently stale, still says
   "blocked on QA #13") to "Done," matching the convention every other merged `other/`-tier row
   already follows in that file.
4. **Continue down the build order**: `multi-notification-stacking` → `localization-additional-languages`
   (§12/§16). `multi-notification-stacking` becomes meaningful to build/test only once
   `category-icons-and-assets` is actually merged (per-slot category icons need to exist first) —
   this is now true once step 3 completes.
5. Resolve the package name placeholder before any public release is pursued (not urgent now, §5/§6).
6. `docs/QA.md` #17, #18, #19 (all non-blocking) are worth a design decision at some point but don't
   block further `other/`-tier implementation.
7. When a `gh pr merge` gets blocked, apply the workflow preference from §8/§9/§10: ask via
   `AskUserQuestion` with local `git merge` + `git push` as the offered option, rather than retrying
   `gh pr merge` itself.
8. If more live on-device testing is needed with the user, read §14's closing paragraph first (the
   established pattern for working efficiently around this device's short re-lock timer and the
   `docs/QA.md` #16 doze quirk).
