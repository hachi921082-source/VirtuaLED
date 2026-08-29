package com.virtualed.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.virtualed.app.R
import com.virtualed.app.data.PendingNotificationsRepository
import com.virtualed.app.overlay.FakeSleepActivityHolder
import com.virtualed.app.overlay.FakeSleepSessionController
import com.virtualed.app.receiver.ScreenStateReceiver

/**
 * feature ON の間、常駐する Foreground Service (specialUse タイプ)。
 *
 * docs/plans/mvp/foreground-service-setup/PLAN.md §2 を実装。
 * 通知検出は別フィーチャーの担当範囲 (NotificationSyncListenerService) だが、
 * 画面オフ検出/フェイクスリープ表示のトリガーはこのサービスがホストする
 * (docs/plans/mvp/fake-sleep-overlay/PLAN.md §3, §7)。
 */
class VirtuaLedForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "virtualed_fgs_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.virtualed.app.action.START"

        // ACTION_STOP はアプリ内スイッチ OFF 経路専用。通知アクションタップによる OFF 経路
        // (docs/plans/mvp/onoff-notification-toggle/PLAN.md) はこの ACTION_STOP を使わない。
        const val ACTION_STOP = "com.virtualed.app.action.STOP"
    }

    // fake-sleep-overlay PLAN §2 が定義するセッション状態機械。onCreate() で1回だけ生成し保持する。
    private lateinit var sessionController: FakeSleepSessionController

    // ACTION_SCREEN_OFF/ACTION_USER_PRESENT 用の動的登録 receiver。
    // onStartCommand() で1回だけ登録、onDestroy() で解除する。
    private var screenStateReceiver: ScreenStateReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sessionController = FakeSleepSessionController(this, PendingNotificationsRepository)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // アプリ内スイッチ OFF 経路 (docs/detailed-spec.md §18) — 完全停止。
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(enabled = true) // onoff-notification-toggle で拡張
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (screenStateReceiver == null) {
            // START_STICKY による再配信/onStartCommand の複数回呼び出しに対する二重登録ガード。
            val receiver = ScreenStateReceiver(
                onScreenOff = { handleScreenOff() },
                onUnlock = { handleUnlock() },
            )
            registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
            )
            screenStateReceiver = receiver
        }
        return START_STICKY
    }

    /**
     * docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §6。セッション中に届く2回目の
     * ACTION_SCREEN_OFF は電源ボタン再押下(=フルディスミス)を意味する。通常の画面オフ検出は
     * sessionController.onScreenOff() に委譲する — pendingRepo.isEmpty() のゲーティング判定を
     * ここで重複実装しない (将来の onoff-notification-toggle がこの共有メソッドへ
     * FeatureEnabledState ガードを追加する予定のため、重複させると OFF 中でもセッションが
     * 開始してしまう)。
     */
    private fun handleScreenOff() {
        if (sessionController.sessionActive) {
            FakeSleepActivityHolder.current?.onPowerButtonPressed() ?: sessionController.endSession()
            return
        }
        sessionController.onScreenOff()
    }

    // docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §7 — 認証成功によるアンロック検出時の自動ディスミス。
    private fun handleUnlock() {
        if (sessionController.sessionActive) sessionController.endSession()
    }

    override fun onDestroy() {
        screenStateReceiver?.let { unregisterReceiver(it) }
        screenStateReceiver = null
        // アプリ内スイッチ OFF 等でサービスが完全停止する際、保留中の full-screen-intent 通知の
        // キャンセルと FakeSleepActivity の終了も併せて行う (fake-sleep-overlay §6, §7)。
        sessionController.endSession()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null // バインドされないサービス

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW, // 常駐ステータス通知のため音/ヘッズアップ表示は不要
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(enabled: Boolean): Notification {
        // MVP 最小構成: タイトル + 簡易ステータスのみ、アクションボタンなし。
        // onoff-notification-toggle PLAN で ON/OFF アクション付きの本体に置き換えられる。
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_dot) // 暫定 small-icon アセット
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(if (enabled) R.string.fgs_status_on else R.string.fgs_status_off))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
