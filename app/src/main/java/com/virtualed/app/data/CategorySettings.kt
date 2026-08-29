package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.virtualed.app.category.IndicatorCategory
import kotlinx.coroutines.flow.first

private val Context.categorySettingsDataStore by preferencesDataStore(name = "category_settings")

private const val KEY_PREFIX = "category_"

/**
 * アプリごとに割り当てられたインジケーターカテゴリを保持する DataStore 永続化値。
 * キーは "category_<packageName>" (String、IndicatorCategory.name を1件) —
 * KeywordRegistry.kt と同じ「キー名プレフィックスで全アプリ分を走査する」パターンを踏襲するが、
 * アプリごとの値がリストではなく単一の enum である点が異なる (Set<String> ではなく
 * stringPreferencesKey を使う)。
 * docs/detailed-spec.md §3、docs/plans/other/category-icons-and-assets/PLAN.md §4 を実装。
 *
 * CategoryMapper (同 PLAN.md §4) は保留通知の変化のたびに同期的にカテゴリを解決する必要があり、
 * KeywordRegistry/DndSettings と同じ「同期参照キャッシュ + refreshCache()」パターンを使う。
 */
object CategorySettings {
    // 未割り当てアプリはこのマップに含めない (割り当て済みエントリのみを保持する) —
    // 不在は「未割り当て」を意味し、CategoryMapper.resolve() が IndicatorCategory.OTHER へ
    // フォールバックする (PLAN.md §3)。
    @Volatile var categoryCache: Map<String, IndicatorCategory> = emptyMap()
        private set

    // 設定画面が現在の割り当てを読み込むために使う。未割り当ての場合は OTHER ではなく null を
    // 返す — 「未設定」と「明示的に OTHER を選択済み」を UI 側で区別できるようにするため。
    suspend fun getCategory(context: Context, packageName: String): IndicatorCategory? {
        val key = stringPreferencesKey(KEY_PREFIX + packageName)
        val name = context.categorySettingsDataStore.data.first()[key] ?: return null
        return runCatching { IndicatorCategory.valueOf(name) }.getOrNull()
    }

    // category = null は割り当て解除を意味する (KeywordRegistry の「空リストならキーごと削除する」
    // 規約と同じ、未割り当てアプリと同じ既定表示に戻す)。
    suspend fun setCategory(context: Context, packageName: String, category: IndicatorCategory?) {
        val key = stringPreferencesKey(KEY_PREFIX + packageName)
        context.categorySettingsDataStore.edit { prefs ->
            if (category == null) prefs.remove(key) else prefs[key] = category.name
        }
        categoryCache = if (category == null) {
            categoryCache - packageName
        } else {
            categoryCache + (packageName to category)
        }
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、全アプリ分のキャッシュを
    // DataStore の値と同期させる (KeywordRegistry.refreshCache() と同じ技法)。
    suspend fun refreshCache(context: Context) {
        val prefs = context.categorySettingsDataStore.data.first()
        val rebuilt = mutableMapOf<String, IndicatorCategory>()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name.startsWith(KEY_PREFIX) && value is String) {
                val category = runCatching { IndicatorCategory.valueOf(value) }.getOrNull()
                if (category != null) rebuilt[name.removePrefix(KEY_PREFIX)] = category
            }
        }
        categoryCache = rebuilt
    }
}
