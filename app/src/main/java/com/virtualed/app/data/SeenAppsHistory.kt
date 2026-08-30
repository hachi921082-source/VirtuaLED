package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.seenAppsHistoryDataStore by preferencesDataStore(name = "seen_apps_history")

private const val KEY_PREFIX = "seen_"

/**
 * 過去に一度でも通知してきたアプリの packageName 集合を保持する DataStore 永続化値。
 * AppExclusionSettings.kt と同じ「キー不在 = 未記録」規約・同期キャッシュパターンを使う。
 * GitHub issue #27 (ExclusionSettingsScreen の「通知してきたアプリのみ」絞り込み表示の
 * データソース) を実装。
 *
 * 除外設定の有無に関わらず記録する — 既に除外済みのアプリも一覧に残り続け、後から除外を
 * 解除できるようにするため (NotificationSyncListenerService.onNotificationPosted() 参照)。
 */
object SeenAppsHistory {
    @Volatile var seenCache: Set<String> = emptySet()
        private set

    suspend fun record(context: Context, packageName: String) {
        if (packageName in seenCache) return // 既知なら DataStore 書き込みを省略する
        val key = booleanPreferencesKey(KEY_PREFIX + packageName)
        context.seenAppsHistoryDataStore.edit { prefs -> prefs[key] = true }
        seenCache = seenCache + packageName
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、DataStore の値とキャッシュを同期させる
    // (AppExclusionSettings.refreshCache() と同じ技法)。
    suspend fun refreshCache(context: Context) {
        val prefs = context.seenAppsHistoryDataStore.data.first()
        val rebuilt = mutableSetOf<String>()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith(KEY_PREFIX) && value == true) {
                rebuilt += name.removePrefix(KEY_PREFIX)
            }
        }
        seenCache = rebuilt
    }
}
