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

/**
 * HEX 文字列 (#RRGGBB / RRGGBB / #AARRGGBB / AARRGGBB) を ARGB Int へ変換する。
 * 解析に失敗した場合は null を返す — 呼び出し側は永続化を行わず、入力エラーを表示する
 * (docs/plans/other/indicator-color-customization/PLAN.md §4)。
 */
fun parseHexColorOrNull(input: String): Int? =
    runCatching {
        android.graphics.Color.parseColor(if (input.startsWith("#")) input else "#$input")
    }.getOrNull()
