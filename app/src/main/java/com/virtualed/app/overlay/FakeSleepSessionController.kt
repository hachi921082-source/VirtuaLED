package com.virtualed.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.virtualed.app.R
import com.virtualed.app.autostop.ProximityMonitor
import com.virtualed.app.data.DndSettings
import com.virtualed.app.data.DndState
import com.virtualed.app.data.FeatureEnabledState
import com.virtualed.app.data.PendingNotificationsRepository

/**
 * フェイクスリープセッションの状態機械。VirtuaLedForegroundService が1回だけ生成し保持する。
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §2, §4, §5, §6 を実装。
 *
 * MVP スコープ: 画面オフ検出時のゲーティング判定・full-screen-intent 通知の投稿/取消・
 * 処理用の短時間 WakeLock のみ。カテゴリアイコン・色カスタマイズ・焼き付き防止の再配置・
 * タップ/ピーク操作・複数通知の視覚的スタッキングは対象外 (他 PLAN が担当、§7 参照)。
 */
class FakeSleepSessionController(
    private val context: Context,
    private val pendingRepo: PendingNotificationsRepository,
) {
    companion object {
        const val FAKE_SLEEP_CHANNEL_ID = "virtualed_fake_sleep_channel"
        const val FAKE_SLEEP_NOTIFICATION_ID = 2

        // 安全弁としてのタイムアウト。セッション継続時間を制限する値ではない (§4 参照)。
        private const val SCREEN_OFF_PROCESSING_TIMEOUT_MS = 5000L
        private const val WAKE_LOCK_TAG = "VirtuaLED:ScreenOffProcessing"
    }

    var sessionActive: Boolean = false
        private set

    // isHeld による二重取得ガードのため、フィールドとして保持する (§4)。
    private var wakeLock: PowerManager.WakeLock? = null

    // 近接センサーによる一時的な描画抑止を行う。WakeLock・保留通知トラッキング・
    // セッション状態には影響しない (docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md §3)。
    // burnInProtector の再配置ループもこの被覆状態に連動して一時停止/再開する
    // (docs/plans/other/burn-in-protection/PLAN.md §4、docs/DECISIONS.md §20)。
    private val proximityMonitor = ProximityMonitor(context) { covered ->
        FakeSleepActivityHolder.indicatorUiState?.indicatorVisible?.value = !covered
        FakeSleepActivityHolder.burnInProtector?.onProximityCoveredChanged(covered)
    }

    init {
        FakeSleepActivityHolder.sessionController = this // 自己登録。Service 側はこの1回の
        // コンストラクタ呼び出しでインスタンスを保持するだけでよく、別途の配線呼び出しは不要。
        createNotificationChannel()
        // 保留通知が0件になった時点でセッション中なら即終了する (docs/DECISIONS.md §16)。
        pendingRepo.setOnBecameEmptyListener { if (sessionActive) endSession() }
    }

    fun onScreenOff() {
        // feature OFF 中はゲーティングごと無効化する (docs/plans/mvp/onoff-notification-toggle/PLAN.md §2)。
        if (!FeatureEnabledState.isEnabled()) return
        // DND 中は隠す設定が有効かつ現在 DND 中ならゲーティングごと無効化する (docs/plans/other/dnd-handling/PLAN.md §5)。
        if (DndSettings.hideDuringDndCached && DndState.isActive()) return
        if (pendingRepo.isEmpty()) return // ゲーティング判定 (§1 step 2) — 端末は通常通りスリープする
        startSession()
    }

    private fun startSession() {
        sessionActive = true
        proximityMonitor.start()
        // burnInProtector はこの時点ではまだ存在しない (full-screen-intent 経由で OS が
        // FakeSleepActivity を起動するのはこの後) ため、実際には常に no-op — 実際の起動通知は
        // FakeSleepActivity.onCreate() が直接行う (docs/plans/other/burn-in-protection/PLAN.md §4)。
        FakeSleepActivityHolder.burnInProtector?.onSessionActiveChanged(true)
        withScreenOffProcessingGuard {
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, FakeSleepActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, FAKE_SLEEP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify_dot) // 暫定 small-icon アセット
                .setContentTitle(context.getString(R.string.fake_sleep_notification_title))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreenPendingIntent, /* highPriority = */ true)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(FAKE_SLEEP_NOTIFICATION_ID, notification)
        }
    }

    fun endSession() {
        NotificationManagerCompat.from(context).cancel(FAKE_SLEEP_NOTIFICATION_ID) // 既に消えていても no-op
        FakeSleepActivityHolder.burnInProtector?.onSessionActiveChanged(false)
        FakeSleepActivityHolder.current?.finish()
        proximityMonitor.stop()
        sessionActive = false
    }

    private fun withScreenOffProcessingGuard(block: () -> Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = wakeLock ?: pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG,
        ).also { wakeLock = it }
        if (lock.isHeld) { block(); return } // 二重取得ガード — 既に保持中ならそのまま実行するだけ
        try {
            lock.acquire(SCREEN_OFF_PROCESSING_TIMEOUT_MS)
            block()
        } finally {
            if (lock.isHeld) lock.release()
        }
    }

    // FGS 本体の低重要度チャンネルとは別の、full-screen-intent 用高重要度チャンネル (§5)。
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            FAKE_SLEEP_CHANNEL_ID,
            context.getString(R.string.fake_sleep_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
