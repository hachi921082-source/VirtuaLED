package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.keywordRegistryDataStore by preferencesDataStore(name = "keyword_registry")

private const val KEY_PREFIX = "keywords_"

/**
 * アプリごとの登録キーワード一覧を保持する DataStore 永続化値。
 * キーは "keywords_<packageName>" (Set<String>) 1件ずつ (JSON 等のシリアライズライブラリは
 * 使わず、キー名プレフィックスで全アプリ分を走査する — app/build.gradle.kts に既存の依存が
 * 無く、この用途には不要なため)。
 * docs/detailed-spec.md §5、docs/plans/other/importance-based-sizing/PLAN.md §2/§3 を実装。
 *
 * ImportanceResolver は保留通知の変化のたびに同期的に評価する必要があり、DndSettings と同じ
 * 「同期参照キャッシュ + refreshCache()」パターンを踏襲するが、値がアプリ別マップである点が
 * 異なる — setKeywords() ではその1件分のみをキャッシュへ差し替え、refreshCache() でのみ
 * DataStore の Preferences 全体を読み直して全アプリ分を再構築する。
 */
object KeywordRegistry {
    // サービス起動時に一度だけ全件読み込み、以後は設定画面での保存時に該当1件のみ更新される
    // 同期参照キャッシュ。
    @Volatile var keywordsCache: Map<String, List<String>> = emptyMap()
        private set

    suspend fun getKeywords(context: Context, packageName: String): List<String> {
        val key = stringSetPreferencesKey(KEY_PREFIX + packageName)
        return context.keywordRegistryDataStore.data.first()[key]?.toList() ?: emptyList()
    }

    suspend fun setKeywords(context: Context, packageName: String, keywords: List<String>) {
        val key = stringSetPreferencesKey(KEY_PREFIX + packageName)
        context.keywordRegistryDataStore.edit { prefs ->
            // 空リストはエントリごと削除する — 未登録アプリと同じ「常に通常重要度」扱いになる
            // (docs/plans/other/importance-based-sizing/PLAN.md §3)。
            if (keywords.isEmpty()) prefs.remove(key) else prefs[key] = keywords.toSet()
        }
        keywordsCache = if (keywords.isEmpty()) {
            keywordsCache - packageName
        } else {
            keywordsCache + (packageName to keywords)
        }
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、全アプリ分のキャッシュを
    // DataStore の値と同期させる。
    suspend fun refreshCache(context: Context) {
        val prefs = context.keywordRegistryDataStore.data.first()
        val rebuilt = mutableMapOf<String, List<String>>()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith(KEY_PREFIX) && value is Set<*>) {
                @Suppress("UNCHECKED_CAST")
                rebuilt[name.removePrefix(KEY_PREFIX)] = (value as Set<String>).toList()
            }
        }
        keywordsCache = rebuilt
    }
}
