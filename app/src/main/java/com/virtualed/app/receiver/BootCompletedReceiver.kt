package com.virtualed.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virtualed.app.autostop.QuietHoursScheduler
import com.virtualed.app.data.QuietHoursSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末再起動後にクワイエットアワーの AlarmManager 予約を再登録する。
 * docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §5 を実装。
 * このアプリで唯一 RECEIVE_BOOT_COMPLETED を使用する受信者 (同 PLAN スコープ注記参照)。
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            // DataStore 読み出しが例外を投げても pendingResult.finish() を必ず呼ぶ
            // (reviewer-lite 指摘、quiet-hours レビュー — QuietHoursAlarmReceiver と同じ対応)。
            try {
                val window = QuietHoursSettings.getWindow(context)
                QuietHoursScheduler(context).reschedule(window)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
