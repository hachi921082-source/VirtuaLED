package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.quietHoursSettingsDataStore by preferencesDataStore(name = "quiet_hours_settings")

/**
 * 「クワイエットアワー」(毎日の時間帯でフェイクスリープを完全抑止する) の DataStore 永続化設定。
 * docs/detailed-spec.md §6、docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §2 を実装。
 *
 * BatteryThresholdSettings と同じくフィールドごとに個別キーを用いる (JSON シリアライズ用の
 * ライブラリはこのプロジェクトに存在しないため)。デフォルトは enabled=false
 * (他の auto-stop 条件と同様、控えめなデフォルトとして無効化状態から始める)。
 */
object QuietHoursSettings {
    data class Window(
        val enabled: Boolean,
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
    )

    const val DEFAULT_ENABLED = false
    const val DEFAULT_START_HOUR = 1
    const val DEFAULT_START_MINUTE = 0
    const val DEFAULT_END_HOUR = 7
    const val DEFAULT_END_MINUTE = 0

    val DEFAULT_WINDOW = Window(
        enabled = DEFAULT_ENABLED,
        startHour = DEFAULT_START_HOUR,
        startMinute = DEFAULT_START_MINUTE,
        endHour = DEFAULT_END_HOUR,
        endMinute = DEFAULT_END_MINUTE,
    )

    private val ENABLED_KEY = booleanPreferencesKey("quiet_hours_enabled")
    private val START_HOUR_KEY = intPreferencesKey("quiet_hours_start_hour")
    private val START_MINUTE_KEY = intPreferencesKey("quiet_hours_start_minute")
    private val END_HOUR_KEY = intPreferencesKey("quiet_hours_end_hour")
    private val END_MINUTE_KEY = intPreferencesKey("quiet_hours_end_minute")

    suspend fun getWindow(context: Context): Window {
        val prefs = context.quietHoursSettingsDataStore.data.first()
        return Window(
            enabled = prefs[ENABLED_KEY] ?: DEFAULT_ENABLED,
            startHour = prefs[START_HOUR_KEY] ?: DEFAULT_START_HOUR,
            startMinute = prefs[START_MINUTE_KEY] ?: DEFAULT_START_MINUTE,
            endHour = prefs[END_HOUR_KEY] ?: DEFAULT_END_HOUR,
            endMinute = prefs[END_MINUTE_KEY] ?: DEFAULT_END_MINUTE,
        )
    }

    suspend fun setWindow(context: Context, window: Window) {
        context.quietHoursSettingsDataStore.edit { prefs ->
            prefs[ENABLED_KEY] = window.enabled
            prefs[START_HOUR_KEY] = window.startHour
            prefs[START_MINUTE_KEY] = window.startMinute
            prefs[END_HOUR_KEY] = window.endHour
            prefs[END_MINUTE_KEY] = window.endMinute
        }
    }
}
