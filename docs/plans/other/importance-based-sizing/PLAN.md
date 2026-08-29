# importance-based-sizing — Implementation Plan

Covers: `docs/detailed-spec.md` §5 (Importance-Based Sizing).

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
- Extends `OverlayIndicatorView` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md`) with a
  size/blink render mode.

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

Added to `OverlayIndicatorView` (`docs/plans/mvp/fake-sleep-overlay/PLAN.md` §5):

```kotlin
fun setImportance(highImportance: Boolean, blinkIntervalMs: Long) {
    if (highImportance) {
        scaleX = 1.5f; scaleY = 1.5f
        startBlink(blinkIntervalMs)
    } else {
        scaleX = 1f; scaleY = 1f
        stopBlink()
    }
}

private var blinkAnimator: ObjectAnimator? = null

private fun startBlink(intervalMs: Long) {
    stopBlink()
    blinkAnimator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f, 1f).apply {
        duration = intervalMs
        repeatCount = ValueAnimator.INFINITE
        start()
    }
}

private fun stopBlink() {
    blinkAnimator?.cancel()
    blinkAnimator = null
    alpha = 1f
}
```

Re-evaluated whenever `PendingNotificationsRepository`'s contents change while a session is active
(new notification posted/removed) — `FakeSleepSessionController`
(`docs/plans/mvp/fake-sleep-overlay/PLAN.md`) recomputes `ImportanceResolver.isHighImportance()` and
calls `setImportance()` on each repository change, not just at session start.

## 7. Cross-Feature Touchpoints

- Extends `docs/plans/mvp/fake-sleep-overlay/`'s `OverlayIndicatorView`; reads
  `docs/plans/mvp/notification-detection-dot-indicator/`'s `PendingNotificationsRepository`.
- Blink/size effects apply to whichever indicator is currently rendered — plain dot (MVP) or a
  category icon (`docs/plans/other/category-icons-and-assets/`), and interact with
  `docs/plans/other/burn-in-protection/`'s independent position-jump loop (both act on the same
  `View`, but on different properties — scale/alpha vs. x/y — so no conflict).
- Does not apply during the peek-preview text state
  (`docs/plans/mvp/dismiss-and-peek-interaction/`) — blink/size only affects the Indicator Shown
  rendering.
