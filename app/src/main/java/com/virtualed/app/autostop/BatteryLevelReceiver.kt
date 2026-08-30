package com.virtualed.app.autostop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.virtualed.app.data.BatteryThresholdSettings

/**
 * バッテリー残量がしきい値以下になったことを検出する動的登録 receiver。
 * docs/plans/other/auto-stop-conditions/PLAN-2-battery.md §3 を実装。
 *
 * ACTION_BATTERY_CHANGED は sticky broadcast のため、しきい値以下の状態で登録した場合は
 * 登録直後に即座に onBelowThreshold() が呼ばれる (意図された挙動であり、初回状態ポーリングは不要)。
 *
 * しきい値はコンストラクタで固定値を受け取らず、毎回の onReceive() で
 * BatteryThresholdSettings.thresholdPercentCached を読み直す — 設定画面でスライダーを動かした
 * 変更が、機能を OFF/ON し直さなくても次の broadcast から即座に反映されるようにするため
 * (docs/QA.md #18)。
 */
class BatteryLevelReceiver(
    private val onBelowThreshold: (thresholdPercent: Int) -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val thresholdPercent = BatteryThresholdSettings.thresholdPercentCached
        if (percent <= thresholdPercent) onBelowThreshold(thresholdPercent)
    }
}
