package com.virtualed.app.data

/**
 * 現在 DND (おやすみモード) が有効かどうかを保持するプロセス内シングルトン。
 * NotificationSyncListenerService の onListenerConnected()/onInterruptionFilterChanged() からのみ
 * 更新される (docs/plans/other/dnd-handling/PLAN.md §3)。
 *
 * FeatureEnabledState と異なり onChanged リスナーは持たない — DND 起動時のセッション即終了は
 * NotificationSyncListenerService 側で DndSettings.hideDuringDndCached と合わせて判定し、
 * FakeSleepSessionController.endSession() を直接呼び出す (PLAN §5)。
 */
object DndState {
    private var active: Boolean = false

    @Synchronized fun isActive(): Boolean = active

    @Synchronized fun setActive(value: Boolean) {
        active = value
    }
}
