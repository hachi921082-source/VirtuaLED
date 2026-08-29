package com.virtualed.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.batteryThresholdSettingsDataStore by preferencesDataStore(name = "battery_threshold_settings")

/**
 * 「バッテリー残量が何%以下になったら自動停止するか」を保持する DataStore 永続化値 (1-100, 1%刻み)。
 * デフォルト 20。docs/detailed-spec.md §6、
 * docs/plans/other/auto-stop-conditions/PLAN-2-battery.md §2 を実装。
 */
object BatteryThresholdSettings {
    const val DEFAULT_THRESHOLD_PERCENT = 20
    const val MIN_THRESHOLD_PERCENT = 1
    const val MAX_THRESHOLD_PERCENT = 100

    private val THRESHOLD_PERCENT_KEY = intPreferencesKey("battery_threshold_percent")

    suspend fun getThresholdPercent(context: Context): Int {
        return context.batteryThresholdSettingsDataStore.data.first()[THRESHOLD_PERCENT_KEY]
            ?: DEFAULT_THRESHOLD_PERCENT
    }

    suspend fun setThresholdPercent(context: Context, percent: Int) {
        val clamped = percent.coerceIn(MIN_THRESHOLD_PERCENT, MAX_THRESHOLD_PERCENT)
        context.batteryThresholdSettingsDataStore.edit { prefs -> prefs[THRESHOLD_PERCENT_KEY] = clamped }
    }
}
