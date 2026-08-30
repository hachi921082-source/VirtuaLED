package com.virtualed.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.countBadgeSettingsDataStore by preferencesDataStore(name = "count_badge_settings")

/**
 * 「通知件数バッジを表示するか」の DataStore 永続化フラグ。デフォルト false (非表示)。
 * docs/detailed-spec.md §9、docs/plans/other/multi-notification-stacking/PLAN.md §5 を実装。
 *
 * DndSettings と同じ「同期参照キャッシュ + refreshCache()」パターン — SlotIcon の描画は
 * Compose 内の同期パスのため、suspend な DataStore 読み出しをその場で行えない。
 * キャッシュ自体は Compose の `State` (`mutableStateOf`) で保持する — SlotIcon がこの値を
 * コンポジション内で読むと Compose のスナップショットシステムに購読され、バックグラウンドの
 * コルーチンから setEnabled()/refreshCache() が値を更新した際に自動で再コンポーズされる
 * (docs/QA.md #21)。 プレーンな読み書き (非 Compose 箇所) の挙動は従来と変わらない。
 */
object CountBadgeSettings {
    const val DEFAULT_ENABLED = false

    private val ENABLED_KEY = booleanPreferencesKey("count_badge_enabled")

    // サービス起動時に一度だけ読み込み、設定画面での保存時に更新される同期参照キャッシュ。
    var enabledCached: Boolean by mutableStateOf(DEFAULT_ENABLED)
        private set

    suspend fun getEnabled(context: Context): Boolean {
        return context.countBadgeSettingsDataStore.data.first()[ENABLED_KEY] ?: DEFAULT_ENABLED
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.countBadgeSettingsDataStore.edit { prefs -> prefs[ENABLED_KEY] = enabled }
        enabledCached = enabled
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、キャッシュを DataStore の値と同期させる。
    suspend fun refreshCache(context: Context) {
        enabledCached = getEnabled(context)
    }
}
