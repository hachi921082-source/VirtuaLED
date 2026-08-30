package com.virtualed.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.virtualed.app.R
import com.virtualed.app.autostop.BatteryLevelReceiver
import com.virtualed.app.autostop.QuietHoursScheduler
import com.virtualed.app.data.AppIconIndicatorSettings
import com.virtualed.app.data.BatteryThresholdSettings
import com.virtualed.app.data.BlinkSettings
import com.virtualed.app.data.CategorySettings
import com.virtualed.app.data.CountBadgeSettings
import com.virtualed.app.data.DndSettings
import com.virtualed.app.data.FeatureEnabledState
import com.virtualed.app.data.KeywordRegistry
import com.virtualed.app.data.PendingNotificationsRepository
import com.virtualed.app.data.PremiumState
import com.virtualed.app.data.QuietHoursSettings
import com.virtualed.app.overlay.FakeSleepActivityHolder
import com.virtualed.app.overlay.FakeSleepSessionController
import com.virtualed.app.receiver.OnOffActionReceiver
import com.virtualed.app.receiver.ScreenStateReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    // docs/plans/other/auto-stop-conditions/PLAN-2-battery.md §3 — ACTION_BATTERY_CHANGED 用の動的登録
    // receiver。onCreate() で1回だけ登録、onDestroy() で解除する。
    private var batteryLevelReceiver: BatteryLevelReceiver? = null

    // docs/plans/other/dnd-handling/PLAN.md §5 — hideDuringDndCached の起動時読み込み専用。
    // Service にはライフサイクル対応のコルーチンスコープが標準で無いため、自前で保持し onDestroy() で cancel する。
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sessionController = FakeSleepSessionController(this, PendingNotificationsRepository)

        // docs/plans/other/dnd-handling/PLAN.md §5 — onScreenOff() の同期ゲーティング判定に使う
        // キャッシュ値を、サービス起動時に一度だけ DataStore から読み込んでおく。
        serviceScope.launch { DndSettings.refreshCache(applicationContext) }

        // docs/plans/other/importance-based-sizing/PLAN.md §6 — ImportanceResolver の同期評価に
        // 使うキャッシュ値 (登録キーワード全件・点滅間隔) を、同じ理由でサービス起動時に読み込む。
        serviceScope.launch { KeywordRegistry.refreshCache(applicationContext) }
        serviceScope.launch { BlinkSettings.refreshCache(applicationContext) }

        // docs/plans/other/category-icons-and-assets/PLAN.md §4 — CategoryMapper の同期評価に
        // 使うキャッシュ値 (アプリ別割り当てカテゴリ全件) を、同じ理由でサービス起動時に読み込む。
        serviceScope.launch { CategorySettings.refreshCache(applicationContext) }

        // docs/plans/other/multi-notification-stacking/PLAN.md §5 — SlotIcon の件数バッジ描画は
        // Compose の同期パスのため、同じ理由でサービス起動時に読み込む。
        serviceScope.launch { CountBadgeSettings.refreshCache(applicationContext) }

        // docs/plans/other/source-app-icon-indicator-mode/PLAN.md §8 — SlotIcon のアプリアイコン
        // 描画可否判定 (プレミアム解放フラグ + トグル設定) も同じく Compose の同期パスのため、
        // 同じ理由でサービス起動時に読み込む。
        serviceScope.launch { PremiumState.refreshCache(applicationContext) }
        serviceScope.launch { AppIconIndicatorSettings.refreshCache(applicationContext) }

        // docs/plans/other/auto-stop-conditions/PLAN-2-battery.md §3 — しきい値の読み込みが非同期な
        // ため、DataStore からの読み込み完了後に登録する。ACTION_BATTERY_CHANGED は sticky broadcast
        // なので、登録タイミングが多少遅れても登録時点の現在値は取得できる。
        // refreshCache() を待ってから登録することで、既存の「初回は永続化済みの実値が判明してから
        // 登録する」というコールドスタート挙動を維持しつつ、以後は BatteryLevelReceiver が
        // thresholdPercentCached を毎回読み直すため設定変更が即座に反映される (docs/QA.md #18)。
        serviceScope.launch {
            BatteryThresholdSettings.refreshCache(applicationContext)
            val receiver = BatteryLevelReceiver(
                onBelowThreshold = { thresholdPercent ->
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.auto_stop_battery_toast, thresholdPercent),
                        Toast.LENGTH_SHORT,
                    ).show()
                    // アプリ内スイッチ OFF 経路と同等の完全停止 (PLAN-2-battery.md §4)。
                    stopSelf()
                },
            )
            registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryLevelReceiver = receiver
        }

        // docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §5 の追記事項 —
        // 初回インストール後リブートなし/設定未変更のまま FGS が起動されたケースをカバーするため、
        // サービス起動のたびに永続化済みウィンドウで再スケジュールしておく。reschedule() は
        // 冪等 (毎回キャンセルしてから再スケジュール、または無効時はキャンセルのみ) なので、
        // BOOT_COMPLETED や設定変更経由の呼び出しと重複しても無害。
        serviceScope.launch {
            val window = QuietHoursSettings.getWindow(applicationContext)
            QuietHoursScheduler(applicationContext).reschedule(window)
        }

        // docs/plans/mvp/onoff-notification-toggle/PLAN.md §2 — 通知アクション/アプリ内スイッチの
        // どちらからの変更でも、通知の再描画とセッションの即終了を一箇所で処理する。
        FeatureEnabledState.setOnChangedListener { enabled ->
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, buildNotification(enabled))
            if (!enabled && sessionController.sessionActive) {
                sessionController.endSession() // pause: 稼働中のセッションを即終了する
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // アプリ内スイッチ OFF 経路 (docs/detailed-spec.md §18) — 完全停止。
            stopSelf()
            return START_NOT_STICKY
        }

        // ACTION_START はアプリ内スイッチ ON 経路専用 — 呼び出し前に MainActivity 側が
        // FeatureEnabledState.setEnabled(true) 済みのため、初回描画は常に enabled=true でよい。
        val notification = buildNotification(enabled = true)
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
     * sessionController.onScreenOff() に委譲する — pendingRepo.isEmpty() / FeatureEnabledState.isEnabled()
     * のゲーティング判定はそちらに一元化されているため、ここで重複実装しない
     * (docs/plans/mvp/onoff-notification-toggle/PLAN.md §2)。
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
        batteryLevelReceiver?.let { unregisterReceiver(it) }
        batteryLevelReceiver = null
        // アプリ内スイッチ OFF 等でサービスが完全停止する際、保留中の full-screen-intent 通知の
        // キャンセルと FakeSleepActivity の終了も併せて行う (fake-sleep-overlay §6, §7)。
        sessionController.endSession()
        serviceScope.cancel()
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

    // docs/plans/mvp/onoff-notification-toggle/PLAN.md §3 — ON/OFF トグルアクション付き本体。
    // foreground-service-setup PLAN §3 のアクションなし版を置き換える。
    private fun buildNotification(enabled: Boolean): Notification {
        val actionLabel = getString(if (enabled) R.string.fgs_action_turn_off else R.string.fgs_action_turn_on)
        val toggleIntent = Intent(this, OnOffActionReceiver::class.java).setAction(OnOffActionReceiver.ACTION_TOGGLE)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_dot) // 暫定 small-icon アセット
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(if (enabled) R.string.fgs_status_on else R.string.fgs_status_off))
            .addAction(0, actionLabel, pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
