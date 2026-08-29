package com.virtualed.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.virtualed.app.R

/**
 * feature ON の間、常駐する Foreground Service (specialUse タイプ)。
 *
 * docs/plans/mvp/foreground-service-setup/PLAN.md §2 を実装。
 * 通知検出/画面オフ検出/フェイクスリープ表示は別フィーチャーの担当範囲であり、
 * このクラスはサービスの骨格 (起動/停止ライフサイクルと最小通知) のみを提供する。
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

    // 以下のコラボレーターは onCreate() で初期化される想定 (各担当フィーチャーの PLAN.md 参照):
    // - PendingNotificationsRepository (notification-detection-dot-indicator; 実装済みだがこの
    //   サービスからはまだ参照しない — 参照するのは fake-sleep-overlay 実装時)
    // - FakeSleepSessionController      (fake-sleep-overlay)
    // - overlay タップ/dismiss 配線       (dismiss-and-peek-interaction)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // TODO: コラボレーター初期化 (§6 Cross-Feature Touchpoints 参照)
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
        // TODO: ScreenStateReceiver をここで登録 (fake-sleep-overlay PLAN "Receiver Registration" 参照)
        return START_STICKY
    }

    override fun onDestroy() {
        // TODO: 動的登録した receiver の解除、保持中の処理用 WakeLock の解放 (fake-sleep-overlay §16)、
        // 保留中の full-screen-intent 通知のキャンセルと FakeSleepActivity の終了 (fake-sleep-overlay §6)
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
