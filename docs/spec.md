# Specification

<!-- Confirmed specification. Built up collaboratively with designer-lite; records only "what is currently decided." -->

## Index

1. Purpose
2. Target Platform
3. Tech Stack
4. Core Features
5. Non-Goals
6. Open Items

## 1. Purpose

VirtuaLED simulates a notification LED on devices that lack one. While the
screen is technically kept on (not truly off), the app shows a black
full-screen overlay with a small indicator icon or dot representing unread
notifications, so at a glance it looks like the screen is off except for the
"LED." This overlay only engages when at least one unread notification is
pending at screen-off, and ends itself the instant none remain — with no
pending notifications, the device sleeps normally, see
`docs/DECISIONS.md` §16. This app is built independently, with functionality similar to a
third-party app the user used to rely on that no longer works on current
Android versions; see `docs/DECISIONS.md` §1 for the authorship background.
During early development the app was known internally as "NoLED-Revive"
before being renamed to "VirtuaLED" — see `docs/DECISIONS.md` §1a.

## 2. Target Platform

- Android 14 (API 34) and Android 15 (API 35) target range; `minSdkVersion` 31 (Android 12)
- Kotlin, Android Studio

### Package/Build Identifier

- Package name: `com.virtualed.app` — **placeholder**, pending the user confirming their
  preferred reverse-domain identifier (e.g. if they own a personal domain they'd rather use
  instead)

### Tested / Supported Devices

- Test coverage is limited to two physical devices: Sharp AQUOS sense9 and FCNT arrows We2 Plus
- Chinese-brand devices (e.g. Xiaomi, Huawei, OPPO, vivo) are out of scope for testing and
  support — see `docs/DECISIONS.md` §11

## 3. Tech Stack

- `NotificationListenerService` — notification detection
- Foreground Service (`specialUse` type) — keeps the feature running without OS task kill
- Full-screen `Activity` with `setShowWhenLocked`/`setTurnScreenOn`, launched via a
  full-screen-intent notification — renders the fake-sleep UI above the lock screen (secure or
  not); see `docs/DECISIONS.md` §18 for why this replaced a raw `WindowManager` overlay window
- `PowerManager` `WakeLock` — narrow role only (guards brief background processing), not
  responsible for keeping the screen lit — see `docs/detailed-spec.md` §16
- Jetpack Compose — settings and onboarding UI

## 4. Core Features

- Indicator display modes: simple dot, or a custom icon selected per notification category
- Indicator color: user-configurable via color picker or HEX input, persisted in settings
- Burn-in protection: indicator periodically repositions on screen
- Importance-based sizing: indicator emphasis (size/blink) driven only by user-registered
  keywords per app — no automatic parsing of notification content
- Auto-stop conditions: proximity sensor cover, low battery, and configured quiet hours
- Do-Not-Disturb (DND) display toggle: user-configurable whether the indicator shows during DND
- Interactions: tap-and-peek (first tap reveals a text preview of pending notifications; a
  second tap or timeout resolves it) plus power-button dismiss; no deep-link tap-to-open — see
  `docs/detailed-spec.md` §8 for the full state model and `docs/DECISIONS.md` §12/§14 for
  rationale
- Localization: Japanese (ja), English (en), Simplified Chinese (zh-rCN)
- Distribution: for now, personal use / limited testing only (e.g. sideloaded or internal testing
  track). Google Play general public release is a deferred future milestone, not the immediate v1
  target — see `docs/DECISIONS.md` §5.
- Notification action button: the Foreground Service's persistent notification includes an
  ON/OFF toggle action, so the feature can be quickly enabled/disabled without opening the app
- Settings screen reserves an empty Donate/Pro placeholder item, not functionally wired

## 5. Non-Goals

- No automatic parsing of notification content (no NLP/content analysis)
- No contacts access
- No forced always-on background permission requests (e.g. battery optimization exclusion is
  opt-in only, never a mandatory onboarding step)
- No home-screen widget (v1)
- No Tasker/Locale plugin support
- No weather display
- No billing/IAP logic (v1) — see the Donate/Pro placeholder item above under Core Features

## 6. Open Items

All 12 initial spec questions in `docs/QA.md` are resolved. One implementation-planning question
remains open (`docs/QA.md` §"Implementation Planning Questions" #13: how apps get mapped to
indicator categories) — deferred, since it only affects the category-icons feature, which is out
of MVP scope. Future questions arising during implementation planning will be appended to
`docs/QA.md`.
