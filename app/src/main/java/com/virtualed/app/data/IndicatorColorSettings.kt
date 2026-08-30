package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.indicatorColorSettingsDataStore by preferencesDataStore(name = "indicator_color_settings")

/**
 * ユーザーが選択したインジケーター色 (ARGB Int) を保持する DataStore 永続化値。
 * デフォルトは白 — docs/plans/mvp/fake-sleep-overlay/PLAN.md §5 のハードコード値と同じ。
 * docs/detailed-spec.md §19、docs/plans/other/indicator-color-customization/PLAN.md §2 を実装。
 */
object IndicatorColorSettings {
    const val DEFAULT_COLOR_ARGB = 0xFFFFFFFF.toInt()

    private val COLOR_ARGB_KEY = intPreferencesKey("indicator_color_argb")

    suspend fun getColorArgb(context: Context): Int {
        return context.indicatorColorSettingsDataStore.data.first()[COLOR_ARGB_KEY]
            ?: DEFAULT_COLOR_ARGB
    }

    suspend fun setColorArgb(context: Context, colorArgb: Int) {
        context.indicatorColorSettingsDataStore.edit { prefs -> prefs[COLOR_ARGB_KEY] = colorArgb }
    }
}
// parseHexColorOrNull は HEX 自由入力 UI の廃止に伴い削除済み
// (docs/plans/other/indicator-color-customization/PLAN.md §4、docs/DECISIONS.md §23)。
// getColorArgb() は入力経路によらず永続化済み ARGB Int をそのまま返すため、
// 過去に HEX 入力で保存された値の再検証は不要 — 呼び出し側での再パースも行っていない。
