package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.blinkSettingsDataStore by preferencesDataStore(name = "blink_settings")

/**
 * 高重要度インジケーターの点滅間隔 (ms) を保持する DataStore 永続化値 (500-3000, デフォルト 1000)。
 * docs/detailed-spec.md §5、docs/plans/other/importance-based-sizing/PLAN.md §2/§5 を実装。
 *
 * ImportanceResolver は保留通知の変化のたびに同期的に評価する必要があり (DataStore は suspend
 * 読み出ししかできない)、DndSettings と同じ「同期参照キャッシュ + refreshCache()」パターンを踏襲する。
 */
object BlinkSettings {
    const val DEFAULT_INTERVAL_MS = 1_000L
    const val MIN_INTERVAL_MS = 500L
    const val MAX_INTERVAL_MS = 3_000L

    private val INTERVAL_MS_KEY = longPreferencesKey("blink_interval_ms")

    // サービス起動時に一度だけ読み込み、設定画面での保存時に更新される同期参照キャッシュ。
    @Volatile var intervalMsCached: Long = DEFAULT_INTERVAL_MS
        private set

    suspend fun getIntervalMs(context: Context): Long {
        return context.blinkSettingsDataStore.data.first()[INTERVAL_MS_KEY] ?: DEFAULT_INTERVAL_MS
    }

    suspend fun setIntervalMs(context: Context, ms: Long) {
        val clamped = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        context.blinkSettingsDataStore.edit { prefs -> prefs[INTERVAL_MS_KEY] = clamped }
        intervalMsCached = clamped
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、キャッシュを DataStore の値と同期させる。
    suspend fun refreshCache(context: Context) {
        intervalMsCached = getIntervalMs(context)
    }
}
