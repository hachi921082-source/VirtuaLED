# source-app-icon-indicator-mode — Implementation Plan

Covers: `docs/detailed-spec.md` §20 (Source-App Icon Indicator Mode) and §21 (Premium Feature
Gating (Pre-Billing)). Background/rationale: `docs/DECISIONS.md` §22 (first freemium premium
feature, local pre-billing gate).

## Index

1. Module Structure
2. Data Structures
3. Premium Gating (`PremiumState`)
4. App-Icon Toggle Setting (`AppIconIndicatorSettings`)
5. `packageName` Plumbing Through `IndicatorSlotBuilder`
6. Icon Resolution and Rendering (`SlotIcon`)
7. Settings UI
8. Service Startup Wiring
9. Cross-Feature Touchpoints

## 1. Module Structure

- `com.virtualed.app.data.PremiumState` — new. DataStore-backed cached singleton, mirrors the
  existing `CountBadgeSettings`/`DndSettings` "cached field + DataStore + `refreshCache()`"
  pattern, but exposes its sync read as a function (`isPremiumUnlocked(): Boolean`) rather than a
  public cached field, per `docs/detailed-spec.md` §21's exact wording. Sole flag consumed by §20;
  no other feature reads it yet.
- `com.virtualed.app.data.AppIconIndicatorSettings` — new. DataStore-backed cached boolean toggle
  ("show app's own icon"), structurally identical to `CountBadgeSettings`.
- `com.virtualed.app.overlay.FakeSleepIndicatorUiState.kt` — `IndicatorSlotUiState` gains a
  `packageName: String?` field (default `null`).
- `com.virtualed.app.overlay.IndicatorSlotBuilder` — `build()` writes `packageName = pkg` for each
  real per-app slot; the OTHER overflow slot leaves it `null` (see §5/§9).
- `com.virtualed.app.overlay.FakeSleepActivity.kt` (`SlotIcon`) — new render branch: resolve and
  draw the source app's own launcher icon when enabled, with the existing category-icon/dot
  fallback else or on resolution failure.
- `com.virtualed.app.service.VirtuaLedForegroundService` — `onCreate()` gains two more
  `refreshCache()` launches, following the existing list of per-setting cache warmups.
- `com.virtualed.app.MainActivity.kt` (`MainSwitchScreen`) — one new toggle row, gated on
  `PremiumState.isPremiumUnlocked()`.
- `app/src/main/res/values/strings.xml` — one new string resource for the toggle label.

## 2. Data Structures

```kotlin
data class IndicatorSlotUiState(
    val count: Int = 1,
    val highImportance: Boolean = false,
    val category: IndicatorCategory? = null,
    val packageName: String? = null, // new — null means "no single representative app" (OTHER overflow slot)
)
```

`packageName` is the authoritative signal for "does this slot map to exactly one real app" — not
`category`. A single un-mapped app can legitimately resolve to `IndicatorCategory.OTHER` via
`CategoryMapper` and must still be eligible for its own app icon; only the *overflow* slot (which
represents 2+ combined apps) has no single icon to show, and is the only slot ever built with
`packageName = null`. See §5/§9.

## 3. Premium Gating (`PremiumState`)

```kotlin
// com.virtualed.app.data.PremiumState
private val Context.premiumSettingsDataStore by preferencesDataStore(name = "premium_settings")

object PremiumState {
    const val DEFAULT_UNLOCKED = true // docs/DECISIONS.md §22 — no real billing yet, defaults unlocked

    private val UNLOCKED_KEY = booleanPreferencesKey("premium_unlocked")

    @Volatile private var unlockedCached: Boolean = DEFAULT_UNLOCKED

    // Sync accessor — the only entry point consumers use (§20's render path, §7's settings-row
    // gate). Matches docs/detailed-spec.md §21's literal "exposing isPremiumUnlocked(): Boolean".
    fun isPremiumUnlocked(): Boolean = unlockedCached

    suspend fun getUnlocked(context: Context): Boolean =
        context.premiumSettingsDataStore.data.first()[UNLOCKED_KEY] ?: DEFAULT_UNLOCKED

    suspend fun setUnlocked(context: Context, unlocked: Boolean) {
        context.premiumSettingsDataStore.edit { prefs -> prefs[UNLOCKED_KEY] = unlocked }
        unlockedCached = unlocked
    }

    suspend fun refreshCache(context: Context) {
        unlockedCached = getUnlocked(context)
    }
}
```

No UI writes `setUnlocked()` in this feature — it exists only so a future real-billing integration
has a single place to flip the flag (per §21's explicit forward-compat note), and so the persisted
default can be verified/tested. Not reachable from any screen right now.

## 4. App-Icon Toggle Setting (`AppIconIndicatorSettings`)

Structurally identical to `CountBadgeSettings` (`app/src/main/java/com/virtualed/app/data/CountBadgeSettings.kt`):

```kotlin
object AppIconIndicatorSettings {
    const val DEFAULT_ENABLED = false // opt-in even once unlocked — docs/detailed-spec.md §20

    private val ENABLED_KEY = booleanPreferencesKey("app_icon_indicator_enabled")

    @Volatile var enabledCached: Boolean = DEFAULT_ENABLED
        private set

    suspend fun getEnabled(context: Context): Boolean
    suspend fun setEnabled(context: Context, enabled: Boolean)
    suspend fun refreshCache(context: Context)
}
```

This stores the user's *preference* only. Whether that preference actually takes visual effect is
decided at render time (§6) by additionally checking `PremiumState.isPremiumUnlocked()` — the
stored preference is never force-written to `false` just because premium is locked, matching
§20's "regardless of this setting's stored value" wording (the persisted value survives a future
lock/unlock cycle unchanged).

## 5. `packageName` Plumbing Through `IndicatorSlotBuilder`

```kotlin
object IndicatorSlotBuilder {
    const val MAX_SLOTS = 4

    fun build(
        pending: List<PendingNotificationInfo>,
        categoryMapper: CategoryMapper,
        keywordsByApp: (String) -> List<String>,
    ): List<IndicatorSlotUiState> {
        val perApp = pending.groupBy { it.packageName }.map { (pkg, list) ->
            IndicatorSlotUiState(
                category = categoryMapper.resolve(pkg),
                count = list.size,
                highImportance = /* unchanged */,
                packageName = pkg, // new — this group is exactly one app
            )
        }
        if (perApp.size <= MAX_SLOTS) return perApp
        val kept = perApp.take(MAX_SLOTS - 1)
        val overflow = perApp.drop(MAX_SLOTS - 1)
        return kept + IndicatorSlotUiState(
            category = IndicatorCategory.OTHER,
            count = overflow.sumOf { it.count },
            highImportance = overflow.any { it.highImportance },
            // packageName intentionally omitted (stays null) — no single representative app
        )
    }
}
```

## 6. Icon Resolution and Rendering (`SlotIcon`)

`FakeSleepActivity.kt`'s `SlotIcon` composable gains a new highest-priority render branch, checked
before the existing category-icon/dot branches:

```kotlin
@Composable
private fun rememberAppIconBitmap(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    // remember() keyed on packageName so this doesn't re-run on every blink toggle recomposition —
    // only when the resolved slot's app actually changes.
    return remember(packageName) {
        if (packageName.isNullOrEmpty()) {
            null
        } else {
            runCatching { context.packageManager.getApplicationIcon(packageName) }
                .getOrNull()
                ?.toBitmap()
                ?.asImageBitmap()
        }
    }
}

@Composable
private fun SlotIcon(slot: IndicatorSlotUiState, tintColor: Color, modifier: Modifier) {
    val dotSizeDp = if (slot.highImportance) INDICATOR_DOT_SIZE_DP * 1.5f else INDICATOR_DOT_SIZE_DP.toFloat()
    val category = slot.category
    val appIconEnabled = PremiumState.isPremiumUnlocked() && AppIconIndicatorSettings.enabledCached
    val appIconBitmap = if (appIconEnabled) rememberAppIconBitmap(slot.packageName) else null

    Box(modifier = modifier) {
        when {
            appIconBitmap != null -> {
                // Real multi-color app icon — no tintColor filter (docs/detailed-spec.md §19's
                // note added for this mode).
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(dotSizeDp.dp),
                )
            }
            category != null -> {
                // unchanged existing category-icon branch (tinted)
            }
            else -> {
                // unchanged existing plain-dot fallback branch (tinted)
            }
        }
        // unchanged count-badge overlay
    }
}
```

Fallback is automatic and per-slot: `appIconBitmap` is `null` whenever the toggle/premium gate is
off, `slot.packageName` is `null` (OTHER overflow slot, §5), or `getApplicationIcon()` throws
(`NameNotFoundException` from an uninstalled app, or any other resolution failure caught by
`runCatching`) — in every one of those cases control falls through to the pre-existing
category-icon/dot branches, never a blank or broken render.

`toBitmap()` is `androidx.core.graphics.drawable.toBitmap()` (already available — `core-ktx` is an
existing dependency, `app/build.gradle.kts`). No new Gradle dependency needed for drawable→Compose
conversion (no Coil/Accompanist in this project).

## 7. Settings UI

`MainSwitchScreen` (`MainActivity.kt`) gains one row, following the existing inline-toggle-row
convention (same shape as the "show during DND" / "show count badge" rows):

```kotlin
var appIconIndicatorEnabled by remember { mutableStateOf(AppIconIndicatorSettings.DEFAULT_ENABLED) }
LaunchedEffect(Unit) {
    appIconIndicatorEnabled = AppIconIndicatorSettings.getEnabled(context)
}

if (PremiumState.isPremiumUnlocked()) {
    Row(
        modifier = Modifier.padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.settings_show_app_icon))
        Switch(
            checked = appIconIndicatorEnabled,
            onCheckedChange = { checked ->
                appIconIndicatorEnabled = checked
                coroutineScope.launch {
                    AppIconIndicatorSettings.setEnabled(context, checked)
                }
            },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
```

Gating choice: **hide the row entirely** when `!PremiumState.isPremiumUnlocked()`, rather than show
it disabled — simplest option consistent with §21's scope (no paywall UI exists yet to explain
*why* it's disabled, so a disabled-but-visible row would be confusing with nothing to tap). Since
`PremiumState.DEFAULT_UNLOCKED = true`, the row is visible unconditionally today; this only matters
once a future real-billing integration can actually set the flag to `false`.

## 8. Service Startup Wiring

`VirtuaLedForegroundService.onCreate()` gains two more cache warmups, alongside the existing list
(`DndSettings`, `KeywordRegistry`, `BlinkSettings`, `CategorySettings`, `CountBadgeSettings`):

```kotlin
serviceScope.launch { PremiumState.refreshCache(applicationContext) }
serviceScope.launch { AppIconIndicatorSettings.refreshCache(applicationContext) }
```

Both flags are read synchronously from `SlotIcon`'s Compose render path (§6), same reason the
existing settings warm their caches here — a suspend DataStore read cannot happen inline in that
render path.

## 9. Cross-Feature Touchpoints

- **`multi-notification-stacking`** (`docs/plans/other/multi-notification-stacking/PLAN.md`):
  grouping is strictly per-app (`pending.groupBy { it.packageName }`), so each of up to 4 real
  slots already maps 1:1 to one app — no ambiguity. The 5th+ apps' combined OTHER overflow slot has
  no single representative app and is therefore hard-coded to never carry a `packageName` (§5),
  which transitively means it can never render an app icon regardless of the toggle state — this is
  enforced structurally (the field is absent), not by a runtime special case in the render branch.
- **`importance-based-sizing`**: the 1.5x high-importance scale (`dotSizeDp`) is computed once per
  slot before the render branch selection in §6 and reused by the app-icon branch identically to
  the category-icon branch — high-importance app icons enlarge the same way high-importance
  category icons already do. `docs/detailed-spec.md` §20 doesn't call this out explicitly, but
  nothing in it suggests app-icon mode should be exempt from sizing.
- **`category-icons-and-assets`**: this mode replaces the category *icon* rendering, not the
  underlying per-app category assignment — `CategoryMapper.resolve(pkg)` is still called and
  `slot.category` is still populated exactly as before (used for the fallback branch and for any
  other consumer), matching `docs/detailed-spec.md` §20's explicit "does not remove or change the
  underlying per-app category assignment" note.
- **`indicator-color-customization`**: `tintColor` is applied to the category-icon and plain-dot
  branches only, unchanged. The new app-icon branch never receives a `colorFilter` argument
  (`docs/detailed-spec.md` §19's note).
- **Deliberately not built**: per-category tint color (deprioritized alternative, `docs/QA.md`
  #20) and any Play Billing Library / purchase-verification code (`docs/detailed-spec.md` §21,
  explicit scope boundary) — out of scope for this plan.
