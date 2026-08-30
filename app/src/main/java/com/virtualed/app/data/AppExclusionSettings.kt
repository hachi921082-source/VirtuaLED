package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.appExclusionSettingsDataStore by preferencesDataStore(name = "app_exclusion_settings")

private const val KEY_PREFIX = "excluded_"

/**
 * アプリごとの「インジケーターから完全に除外する」フラグを保持する DataStore 永続化値。
 * キーは "excluded_<packageName>" (Boolean、true のみを保存し false は削除する — 値が無い =
 * 未除外、CategorySettings.kt と同じ「不在で既定値を表す」規約)。GitHub issue #26 を実装。
 *
 * category/keyword の割り当てとは独立の、より手前の関門: 除外されたアプリの通知は
 * NotificationSyncListenerService が PendingNotificationsRepository へ一切追加しない
 * ため、スロット描画・セッション開始・プレビューテキストのいずれにも影響しない
 * (category/keyword はスロットの見た目を左右するだけで、通知自体を握りつぶすことはない —
 * この違いが本設定を専用の DataStore に分離した理由)。
 *
 * CategorySettings/KeywordRegistry と同じ「同期参照キャッシュ + refreshCache()」パターンを使う —
 * 通知が届くたびに同期的に判定する必要があるため。
 */
object AppExclusionSettings {
    // 除外中の packageName の集合。含まれない = 未除外 (既定)。
    @Volatile var excludedCache: Set<String> = emptySet()
        private set

    suspend fun isExcluded(context: Context, packageName: String): Boolean {
        val key = booleanPreferencesKey(KEY_PREFIX + packageName)
        return context.appExclusionSettingsDataStore.data.first()[key] ?: false
    }

    suspend fun setExcluded(context: Context, packageName: String, excluded: Boolean) {
        val key = booleanPreferencesKey(KEY_PREFIX + packageName)
        context.appExclusionSettingsDataStore.edit { prefs ->
            if (excluded) prefs[key] = true else prefs.remove(key)
        }
        excludedCache = if (excluded) {
            excludedCache + packageName
        } else {
            excludedCache - packageName
        }
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、全アプリ分のキャッシュを
    // DataStore の値と同期させる (CategorySettings.refreshCache() と同じ技法)。
    suspend fun refreshCache(context: Context) {
        val prefs = context.appExclusionSettingsDataStore.data.first()
        val rebuilt = mutableSetOf<String>()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith(KEY_PREFIX) && value == true) {
                rebuilt += name.removePrefix(KEY_PREFIX)
            }
        }
        excludedCache = rebuilt
    }
}
