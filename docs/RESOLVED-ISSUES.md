# Resolved Issues

<!-- Log of closed GitHub issues: number, title, labels, PR link. -->

- **[#23](https://github.com/hachi921082-source/VirtuaLED/issues/23)** — Burn-in reposition can push
  the indicator group partly off-screen. No labels. Fixed via [PR #25](https://github.com/hachi921082-source/VirtuaLED/pull/25).
- **[#26](https://github.com/hachi921082-source/VirtuaLED/issues/26)** — Add a per-app exclusion
  setting to fully suppress notifications from the indicator. `enhancement`. Fixed via
  [PR #28](https://github.com/hachi921082-source/VirtuaLED/pull/28).
- **[#27](https://github.com/hachi921082-source/VirtuaLED/issues/27)** — Allow excluding an app
  directly from an actually-received notification. `enhancement`. Fixed via a "seen apps only"
  filter on the existing exclusion settings screen, in
  [PR #29](https://github.com/hachi921082-source/VirtuaLED/pull/29).
- **[#30](https://github.com/hachi921082-source/VirtuaLED/issues/30)** — A notification arriving
  while the screen is already off never triggers the fake-sleep session. `bug`. Fixed via
  [PR #31](https://github.com/hachi921082-source/VirtuaLED/pull/31).
- **[#33](https://github.com/hachi921082-source/VirtuaLED/issues/33)** — Remove the high-importance
  blink cue and its interval setting. `enhancement`. Fixed via
  [PR #34](https://github.com/hachi921082-source/VirtuaLED/pull/34).
- **[#32](https://github.com/hachi921082-source/VirtuaLED/issues/32)** — High-importance 1.5x
  scale-up between burn-in reposition jumps can push the indicator off-screen. `bug`. Fixed via
  [PR #35](https://github.com/hachi921082-source/VirtuaLED/pull/35).
- **[#36](https://github.com/hachi921082-source/VirtuaLED/issues/36)** — Suppress the whole session
  (dot, auto-wake, and preview) when every pending notification is already-seen, except when still
  high-importance. `enhancement`. Fixed via
  [PR #39](https://github.com/hachi921082-source/VirtuaLED/pull/39).
- **[#37](https://github.com/hachi921082-source/VirtuaLED/issues/37)** — Add an in-app
  license/credits screen for redistribution. `enhancement`. Fixed via
  [PR #40](https://github.com/hachi921082-source/VirtuaLED/pull/40).
- **[#38](https://github.com/hachi921082-source/VirtuaLED/issues/38)** — Replace the placeholder
  launcher icon with a real design. `enhancement`. Fixed via
  [PR #41](https://github.com/hachi921082-source/VirtuaLED/pull/41).
- **[#43](https://github.com/hachi921082-source/VirtuaLED/issues/43)** — MainActivity can appear in
  the foreground instead of the real lock screen after unlocking from a fake-sleep session. `bug`.
  Fixed via [PR #45](https://github.com/hachi921082-source/VirtuaLED/pull/45).
- **[#47](https://github.com/hachi921082-source/VirtuaLED/issues/47)** — Make issue #36's
  already-seen session suppression user-toggleable. `enhancement`. Fixed via
  [PR #48](https://github.com/hachi921082-source/VirtuaLED/pull/48).
- **[#49](https://github.com/hachi921082-source/VirtuaLED/issues/49)** — Detect revoked
  full-screen-intent permission and guide the user to re-enable it. `enhancement`. Fixed via
  [PR #50](https://github.com/hachi921082-source/VirtuaLED/pull/50).
- **[#44](https://github.com/hachi921082-source/VirtuaLED/issues/44)** — Hide the status and
  navigation bars during a fake-sleep session (burn-in and battery). `enhancement`. Fixed via
  [PR #46](https://github.com/hachi921082-source/VirtuaLED/pull/46).

- **[#51](https://github.com/hachi921082-source/VirtuaLED/issues/51)** — Prepare repository for
  public release (MIT license, scrub local paths). `documentation`. Fixed via
  [PR #52](https://github.com/hachi921082-source/VirtuaLED/pull/52).