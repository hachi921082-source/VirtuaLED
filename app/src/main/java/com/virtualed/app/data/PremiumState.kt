package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.premiumSettingsDataStore by preferencesDataStore(name = "premium_settings")

/**
 * 課金導入前のローカル・プレミアム解放フラグ。DataStore 永続化 + 同期参照キャッシュ
 * (CountBadgeSettings/DndSettings と同じパターン) を持つが、公開 API はフィールドではなく
 * isPremiumUnlocked() 関数 (docs/detailed-spec.md §21 の記述に厳密に合わせるため)。
 *
 * デフォルト true (解放済み) — 実billingが未実装のため、source-app-icon-indicator-mode を
 * 今すぐ実機で作り込み・検証できるようにする (docs/DECISIONS.md §22)。setUnlocked() は現状
 * どの画面からも呼ばれない — 将来の実billing統合がこの1箇所だけを書き換える差し込み口
 * (docs/detailed-spec.md §21)。
 */
object PremiumState {
    const val DEFAULT_UNLOCKED = true

    private val UNLOCKED_KEY = booleanPreferencesKey("premium_unlocked")

    @Volatile private var unlockedCached: Boolean = DEFAULT_UNLOCKED

    // SlotIcon の描画 (Compose 同期パス) や設定画面の行の表示可否判定から呼ばれる。
    fun isPremiumUnlocked(): Boolean = unlockedCached

    suspend fun getUnlocked(context: Context): Boolean {
        return context.premiumSettingsDataStore.data.first()[UNLOCKED_KEY] ?: DEFAULT_UNLOCKED
    }

    suspend fun setUnlocked(context: Context, unlocked: Boolean) {
        context.premiumSettingsDataStore.edit { prefs -> prefs[UNLOCKED_KEY] = unlocked }
        unlockedCached = unlocked
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、キャッシュを DataStore の値と同期させる。
    suspend fun refreshCache(context: Context) {
        unlockedCached = getUnlocked(context)
    }
}
