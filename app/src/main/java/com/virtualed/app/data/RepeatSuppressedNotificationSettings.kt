package com.virtualed.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.repeatSuppressedNotificationSettingsDataStore by
    preferencesDataStore(name = "repeat_suppressed_notification_settings")

/**
 * 「既読の通知は（高重要度でない限り）再度セッションを起動しない」機能 (GitHub issue #36) の
 * ON/OFF 切り替えフラグ。デフォルト true (有効 = 既読は繰り返さない)。
 *
 * issue #36 の実装直後は「誰も両方使いたいとは思わない設定」としてトグル無しの方針だったが、
 * 「物忘れしやすい人には繰り返し知らせてほしい」という本物の対立する需要があるとの指摘を受け、
 * 設定可能にする (docs/DECISIONS.md 参照)。
 *
 * CountBadgeSettings と同じ「同期参照キャッシュ + refreshCache()」パターン —
 * FakeSleepSessionController.tryStartSession() は Compose 外の同期パスのため、suspend な
 * DataStore 読み出しをその場で行えない。
 */
object RepeatSuppressedNotificationSettings {
    const val DEFAULT_ENABLED = true

    private val ENABLED_KEY = booleanPreferencesKey("repeat_suppressed_notification_enabled")

    var enabledCached: Boolean by mutableStateOf(DEFAULT_ENABLED)
        private set

    suspend fun getEnabled(context: Context): Boolean {
        return context.repeatSuppressedNotificationSettingsDataStore.data.first()[ENABLED_KEY]
            ?: DEFAULT_ENABLED
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.repeatSuppressedNotificationSettingsDataStore.edit { prefs -> prefs[ENABLED_KEY] = enabled }
        enabledCached = enabled
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、キャッシュを DataStore の値と同期させる。
    suspend fun refreshCache(context: Context) {
        enabledCached = getEnabled(context)
    }
}
