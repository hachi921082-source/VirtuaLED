package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.appIconIndicatorSettingsDataStore by
    preferencesDataStore(name = "app_icon_indicator_settings")

/**
 * 「通知元アプリ自身のアイコンを表示するか」の DataStore 永続化フラグ。デフォルト false
 * (プレミアム解放後も opt-in — 色変更よりも見た目の変化が大きいため、docs/detailed-spec.md §20)。
 * CountBadgeSettings と同じ「同期参照キャッシュ + refreshCache()」パターン — SlotIcon の描画は
 * Compose 内の同期パスのため、suspend な DataStore 読み出しをその場で行えない。
 *
 * この値はユーザーの「意向」のみを保持する。実際に描画へ反映されるかは PremiumState.
 * isPremiumUnlocked() との組み合わせで描画側 (SlotIcon) が判定する — プレミアムがロックされても
 * この永続化値自体は書き換えない (docs/detailed-spec.md §20 の
 * "regardless of this setting's stored value" に対応)。
 */
object AppIconIndicatorSettings {
    const val DEFAULT_ENABLED = false

    private val ENABLED_KEY = booleanPreferencesKey("app_icon_indicator_enabled")

    // サービス起動時に一度だけ読み込み、設定画面での保存時に更新される同期参照キャッシュ。
    @Volatile var enabledCached: Boolean = DEFAULT_ENABLED
        private set

    suspend fun getEnabled(context: Context): Boolean {
        return context.appIconIndicatorSettingsDataStore.data.first()[ENABLED_KEY] ?: DEFAULT_ENABLED
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.appIconIndicatorSettingsDataStore.edit { prefs -> prefs[ENABLED_KEY] = enabled }
        enabledCached = enabled
    }

    // VirtuaLedForegroundService.onCreate() から呼び出し、キャッシュを DataStore の値と同期させる。
    suspend fun refreshCache(context: Context) {
        enabledCached = getEnabled(context)
    }
}
