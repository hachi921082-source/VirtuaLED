package com.virtualed.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virtualed.app.autostop.QuietHoursScheduler
import com.virtualed.app.data.QuietHoursSettings
import com.virtualed.app.overlay.FakeSleepActivityHolder
import com.virtualed.app.data.QuietHoursState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * クワイエットアワー開始/終了トリガーの発火を処理する。
 * docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §4 を実装。
 *
 * setAndAllowWhileIdle() は one-shot のため、発火するたびに自分自身を翌日分として
 * 再アームする必要がある。
 *
 * Implementation Deviation: PLAN.md §4 の pseudocode は runBlocking { QuietHoursSettings.getWindow() }
 * で同期的に読み出す案だったが、onReceive() はメインスレッドで実行されるため DataStore 読み出しを
 * ブロッキングするのは ANR リスクがある (§5 の BootCompletedReceiver と同じ goAsync() パターンに揃える)。
 */
class QuietHoursAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // active フラグの更新・稼働中セッションの即終了は DataStore 読み出しに依存しないため、
        // ここで同期的に即座に行う (goAsync() の完了を待たない)。
        when (intent.action) {
            QuietHoursScheduler.ACTION_QUIET_HOURS_START -> {
                QuietHoursState.setActive(true)
                val sessionController = FakeSleepActivityHolder.sessionController
                if (sessionController?.sessionActive == true) sessionController.endSession()
            }
            QuietHoursScheduler.ACTION_QUIET_HOURS_END -> QuietHoursState.setActive(false)
        }

        // 翌日分の再アームだけが DataStore の非同期読み出しに依存する (runBlocking は使わない —
        // BroadcastReceiver.onReceive() はメインスレッドで実行されるため、PLAN.md §4 の
        // runBlocking pseudocode ではなく BootCompletedReceiver (§5) と同じ goAsync() パターンに
        // 揃える)。
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            // DataStore 読み出しが例外を投げても pendingResult.finish() を必ず呼ぶ —
            // 呼ばれないまま放置すると OS に「応答しない receiver」として扱われうる
            // (reviewer-lite 指摘、quiet-hours レビュー)。
            try {
                val window = QuietHoursSettings.getWindow(context)
                val scheduler = QuietHoursScheduler(context)
                val action = intent.action!!
                if (action == QuietHoursScheduler.ACTION_QUIET_HOURS_START) {
                    scheduler.scheduleNextOccurrence(window.startHour, window.startMinute, action)
                } else {
                    scheduler.scheduleNextOccurrence(window.endHour, window.endMinute, action)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
