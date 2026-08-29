package com.virtualed.app.autostop

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.virtualed.app.data.QuietHoursSettings
import com.virtualed.app.data.QuietHoursState
import com.virtualed.app.receiver.QuietHoursAlarmReceiver
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * クワイエットアワー開始/終了の2本の AlarmManager トリガーをスケジュール/キャンセルする。
 * docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §3 を実装。
 *
 * setAndAllowWhileIdle() による inexact スケジューリング — SCHEDULE_EXACT_ALARM 権限は不要
 * (docs/DECISIONS.md §10)。Doze 中でも一定の遅延で発火する。
 */
class QuietHoursScheduler(private val context: Context) {
    companion object {
        const val ACTION_QUIET_HOURS_START = "com.virtualed.app.action.QUIET_HOURS_START"
        const val ACTION_QUIET_HOURS_END = "com.virtualed.app.action.QUIET_HOURS_END"
    }

    // 常に両トリガーをキャンセルしてから再スケジュールする — 無効化時に古い予約が
    // 残り続けることを防ぐ (PLAN §3)。無効化時は QuietHoursState も即座にクリアする
    // (キャンセル済みの終了アラームを待たない)。
    fun reschedule(window: QuietHoursSettings.Window) {
        cancel(ACTION_QUIET_HOURS_START)
        cancel(ACTION_QUIET_HOURS_END)
        if (!window.enabled) {
            QuietHoursState.setActive(false)
            return
        }
        scheduleNextOccurrence(window.startHour, window.startMinute, ACTION_QUIET_HOURS_START)
        scheduleNextOccurrence(window.endHour, window.endMinute, ACTION_QUIET_HOURS_END)
        // 次回発生の予約だけでは不十分 — reschedule() 呼び出し時点で既にウィンドウ内 (再起動直後や
        // 設定編集直後など) の場合、次の開始アラームが発火するまで isActive() が古い値のまま
        // 取り残される。現在時刻を都度チェックして即座に同期する (実機テストで発見)。
        QuietHoursState.setActive(
            isWithinWindow(LocalTime.now(), window.startHour, window.startMinute, window.endHour, window.endMinute),
        )
    }

    fun scheduleNextOccurrence(hour: Int, minute: Int, action: String) {
        val triggerAtMillis = nextOccurrenceMillis(hour, minute)
        val pendingIntent = pendingIntentFor(action)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun cancel(action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntentFor(action))
    }

    private fun pendingIntentFor(action: String): PendingIntent {
        val intent = Intent(context, QuietHoursAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // 端末のローカルタイムゾーンで hour:minute の次回発生時刻を求める。今日のその時刻が既に
    // 過去 (または現在時刻と一致) であれば翌日のその時刻を返す (minSdk 31 は java.time を
    // ネイティブサポートするため Calendar ではなく ZonedDateTime を使う)。
    private fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    // now が [start, end) の範囲内かを判定する純粋関数。start > end の場合は日をまたぐウィンドウ
    // (例: 23:43 -> 翌07:00) とみなし、start 以降 or end 未満のいずれかで在圏中とする。
    // start == end はゼロ長ウィンドウとして常に false (在圏なし) を返す。
    private fun isWithinWindow(
        now: LocalTime,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ): Boolean {
        val start = LocalTime.of(startHour, startMinute)
        val end = LocalTime.of(endHour, endMinute)
        return if (start == end) {
            false
        } else if (start < end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }
}
