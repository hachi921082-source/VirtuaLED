package com.virtualed.app.data

/**
 * 現在クワイエットアワー時間帯内かどうかを保持するプロセス内シングルトン。
 * DndState.kt と同じ形 (@Synchronized isActive()/setActive()、DataStore 非使用) に揃える。
 * QuietHoursAlarmReceiver の開始/終了トリガーからのみ更新される
 * (docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §4)。
 */
object QuietHoursState {
    private var active: Boolean = false

    @Synchronized fun isActive(): Boolean = active

    @Synchronized fun setActive(value: Boolean) {
        active = value
    }
}
