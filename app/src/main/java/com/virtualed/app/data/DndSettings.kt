package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dndSettingsDataStore by preferencesDataStore(name = "dnd_settings")

/**
 * 「DND (応答不可モード) 中はインジケーターを隠すか」の DataStore 永続化フラグ。
 * docs/detailed-spec.md §7、docs/plans/other/dnd-handling/PLAN.md §2 を実装。
 *
 * デフォルトは false (隠さない、常に表示する) — 2026-08-30 に true から反転。この表示は
 * 音も振動も伴わない完全に無音の視覚表示のため、DND が本来防ぎたい「うるさい割り込み」とは
 * 性質が異なり、見落とし防止を優先する方が本来の用途に合うと判断 (元の true 側には明記された
 * 根拠が無かった)。
 *
 * 設定画面のスイッチ (docs/plans/other/dnd-handling/PLAN.md §4) は保存の都度
 * hideDuringDndCached を更新する — VirtuaLedForegroundService.onScreenOff() の
 * ゲーティング判定は同期処理のため、suspend な DataStore 読み出しをその場で行えない (PLAN §5)。
 */
object DndSettings {
    const val DEFAULT_HIDE_DURING_DND = false

    private val HIDE_DURING_DND_KEY = booleanPreferencesKey("hide_during_dnd")

    // サービス起動時に一度だけ読み込み、設定画面での保存時に更新される同期参照キャッシュ (PLAN §5)。
    @Volatile var hideDuringDndCached: Boolean = DEFAULT_HIDE_DURING_DND
        private set

    suspend fun getHideDuringDnd(context: Context): Boolean {
        return context.dndSettingsDataStore.data.first()[HIDE_DURING_DND_KEY] ?: DEFAULT_HIDE_DURING_DND
    }

    suspend fun setHideDuringDnd(context: Context, hide: Boolean) {
        context.dndSettingsDataStore.edit { prefs -> prefs[HIDE_DURING_DND_KEY] = hide }
        hideDuringDndCached = hide
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、キャッシュを DataStore の値と同期させる。
    suspend fun refreshCache(context: Context) {
        hideDuringDndCached = getHideDuringDnd(context)
    }
}
