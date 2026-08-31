# importance-based-sizing — Implementation Plan

Covers: `docs/detailed-spec.md` §5 (Importance-Based Sizing).

**Blink removed**: every reference to blinking/`BlinkSettings`/`blinkIntervalMs` in this document
(originally §5) describes a feature that has since been removed entirely — see
`docs/DECISIONS.md` §15 (superseded) for why. High importance is now conveyed by the 1.5x size
increase alone. Kept here unedited as historical record of the original design; do not treat §5 or
any blink-related pseudocode below as current.

**Scope note**: this plan covers per-app keyword registration, the normalized substring match
rule, the blink-interval setting, and applying size/blink to the overlay indicator. It does not
cover per-icon importance in a multi-notification layout
(`docs/plans/other/multi-notification-stacking/` — this plan applies to the single indicator MVP
already renders); if multiple notifications are pending, this plan treats the indicator as high
importance when **any** currently pending notification matches a registered keyword (an
aggregate-level rule, consistent with the single-indicator simplification MVP already established).

## Index

1. Module Structure
2. Data Structures
3. Keyword Registration UI
4. Match Rule (case/width-normalized substring)
5. Blink Interval Setting
6. Render Integration
7. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.KeywordRegistry` — DataStore-backed, per-app keyword lists.
- `com.virtualed.app.data.BlinkSettings` — DataStore-backed blink interval.
- `com.virtualed.app.importance.ImportanceResolver` — pure function, no persistence.
- `com.virtualed.app.ui.KeywordSettingsScreen.kt` — Compose UI for keyword registration.
- Writes `FakeSleepIndicatorUiState.blinkIntervalMs` and the relevant slot's `highImportance` flag
  (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7) — does not define a new indicator view.

## 2. Data Structures

```kotlin
object KeywordRegistry {
    // DataStore Preferences: one Set<String> entry keyed by "keywords_<packageName>"
    suspend fun getKeywords(context: Context, packageName: String): List<String>
    suspend fun setKeywords(context: Context, packageName: String, keywords: List<String>)
}

object BlinkSettings {
    const val DEFAULT_INTERVAL_MS = 1_000L
    const val MIN_INTERVAL_MS = 500L
    const val MAX_INTERVAL_MS = 3_000L
    // DataStore Preferences: Long entry "blink_interval_ms", default DEFAULT_INTERVAL_MS
    suspend fun getIntervalMs(context: Context): Long
    suspend fun setIntervalMs(context: Context, ms: Long)
}
```

## 3. Keyword Registration UI

Compose screen: pick a monitored app (from the set of apps with at least one notification seen via
`docs/plans/mvp/notification-detection-dot-indicator/`'s `PendingNotificationInfo.packageName`
history, or any installed app), then add/remove free-text keyword strings for that app (e.g.
register "Mom" under a messaging app). Backed by `KeywordRegistry.setKeywords()`. Apps with zero
registered keywords are always normal importance (§4) — no explicit "disabled" toggle needed
beyond an empty list.

## 4. Match Rule (case/width-normalized substring)

```kotlin
private fun normalize(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).lowercase()
    // NFKC folds full-width (zenkaku) alphanumeric/katakana variants to their half-width (hankaku)
    // equivalents, satisfying the zenkaku/hankaku-equivalence requirement; lowercase() satisfies
    // case-insensitivity. No regex — plain substring containment only (docs/detailed-spec.md §5).

fun matchesKeyword(haystack: String, keyword: String): Boolean =
    normalize(haystack).contains(normalize(keyword))
```

```kotlin
object ImportanceResolver {
    fun isHighImportance(pending: List<PendingNotificationInfo>, keywordsByApp: (String) -> List<String>): Boolean {
        return pending.any { info ->
            val keywords = keywordsByApp(info.packageName)
            if (keywords.isEmpty()) return@any false // zero registered keywords => always normal
            val haystack = "${info.title.orEmpty()} ${info.text.orEmpty()}"
            keywords.any { matchesKeyword(haystack, it) }
        }
    }
}
```

Aggregate rule (multiple pending notifications, single MVP indicator): high importance if **any**
pending notification matches — see Scope note above.

## 5. Blink Interval Setting

Settings UI: a slider bound to `BlinkSettings.getIntervalMs()`/`setIntervalMs()`, range
500-3000 ms, default 1000 ms (`docs/detailed-spec.md` §5, `docs/DECISIONS.md` §15).

## 6. Render Integration

Writes `FakeSleepIndicatorUiState` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7):

```kotlin
fun applyImportance(uiState: FakeSleepIndicatorUiState, highImportance: Boolean, intervalMs: Long) {
    uiState.blinkIntervalMs.value = if (highImportance) intervalMs else null
    // Aggregate rule (§4): before docs/plans/other/multi-notification-stacking/ exists there is
    // exactly one slot (the MVP default), so this marks that slot. Once multi-notification-stacking
    // lands, per-slot resolution supersedes this single-slot write (see that plan's §7).
    uiState.slots.value = uiState.slots.value.mapIndexed { index, slot ->
        if (index == 0) slot.copy(highImportance = highImportance) else slot
    }
}
```

Rendering (owned by `docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7's `IndicatorGroup`/`SlotIcon`
composables, not this plan): a slot with `highImportance = true` renders at 1.5x scale — that part
is per-slot. Blinking is not: `blinkIntervalMs` is a single value shared across the whole slot
group (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §7's synchronized-blink decision), so once any
slot is high-importance, *every* currently-shown slot blinks together at that interval, including
slots that are not themselves high-importance.

Re-evaluated whenever `PendingNotificationsRepository`'s contents change while a session is active
(new notification posted/removed) — `FakeSleepSessionController`
(`docs/plans/mvp/fake-sleep-overlay/PLAN.md`) recomputes `ImportanceResolver.isHighImportance()` and
calls `applyImportance()` on each repository change, not just at session start.

## 7. Cross-Feature Touchpoints

- Writes `docs/plans/mvp/fake-sleep-overlay/`'s `FakeSleepIndicatorUiState.blinkIntervalMs` and
  each slot's `highImportance` flag (§7); reads
  `docs/plans/mvp/notification-detection-dot-indicator/`'s `PendingNotificationsRepository`.
- Blink/size effects apply to whichever slot(s) are currently rendered — a plain dot (MVP default)
  or category icons/multiple slots once `docs/plans/other/category-icons-and-assets/` and
  `docs/plans/other/multi-notification-stacking/` are implemented.
- Independent of `docs/plans/other/burn-in-protection/`'s reposition loop: both this plan and that
  one write different `MutableState` fields on the same `FakeSleepIndicatorUiState` instance
  (`blinkIntervalMs`/`slots[].highImportance` here, `indicatorOffset` there) — plain independent
  state, not View properties, so there is nothing for the two to conflict over.
- Does not apply during the peek-preview text state
  (`docs/plans/mvp/dismiss-and-peek-interaction/`) — blink/size only affects the Indicator Shown
  rendering.

## Implementation Deviation Notes

- `IndicatorSlotUiState` (`FakeSleepIndicatorUiState.kt`) omits `category` entirely — that field
  belongs to the not-yet-implemented `docs/plans/other/category-icons-and-assets/`, which will add it
  with a default value (non-breaking) when it lands. Only `count: Int = 1` and
  `highImportance: Boolean = false` are defined here.
- `PendingNotificationsRepository` gained a new `setOnChangedListener`, fired unconditionally from
  `put()`/`remove()`/`replaceAll()` in addition to (not instead of) the existing
  `onBecameEmpty`/`onBecameNonEmpty` callbacks — not sketched in this PLAN.md, needed so
  `FakeSleepSessionController` re-evaluates importance on every pending-notification change, not
  just empty↔non-empty transitions.
- App picker (`KeywordSettingsScreen.kt`) implemented via `PackageManager.queryIntentActivities`
  against `Intent.ACTION_MAIN`/`CATEGORY_LAUNCHER` (the "or any installed app" fallback from §3) —
  this codebase has no notification-history store, so the primary "apps with a notification seen"
  option from §3 is not implemented.
- Blink implemented as a hard on/off toggle via a
  `LaunchedEffect(blinkIntervalMs) { while (isActive) { ... delay ... } }` loop in
  `FakeSleepActivity`'s `IndicatorGroup`, not `Animatable`/alpha interpolation.
- `KeywordSettingsScreen` reachable from `MainSwitchScreen` via a plain
  `showKeywordSettings`-boolean swap in `VirtuaLedApp()` (`MainActivity.kt`), matching the existing
  `OnboardingScreen`↔`MainSwitchScreen` pattern — no navigation library.
