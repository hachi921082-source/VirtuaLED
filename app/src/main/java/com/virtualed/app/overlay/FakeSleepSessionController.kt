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
import com.virtualed.app.category.CategoryMapper
import com.virtualed.app.category.DefaultCategoryMapper
import com.virtualed.app.data.DndSettings
import com.virtualed.app.data.DndState
import com.virtualed.app.data.FeatureEnabledState
import com.virtualed.app.data.KeywordRegistry
import com.virtualed.app.data.NotificationSeenState
import com.virtualed.app.data.PendingNotificationsRepository
import com.virtualed.app.data.QuietHoursState
import com.virtualed.app.data.RepeatSuppressedNotificationSettings
import com.virtualed.app.importance.ImportanceResolver

/**
 * フェイクスリープセッションの状態機械。VirtuaLedForegroundService が1回だけ生成し保持する。
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §2, §4, §5, §6 を実装。
 *
 * MVP スコープ: 画面オフ検出時のゲーティング判定・full-screen-intent 通知の投稿/取消・
 * 処理用の短時間 WakeLock のみ。色カスタマイズ・焼き付き防止の再配置・タップ/ピーク操作は
 * 対象外 (他 PLAN が担当、§7 参照)。重要度判定 (docs/plans/other/importance-based-sizing/)・
 * カテゴリ解決 (docs/plans/other/category-icons-and-assets/)・複数通知の視覚的スタッキング
 * (docs/plans/other/multi-notification-stacking/) は recomputeIndicatorState() で担当する。
 */
class FakeSleepSessionController(
    private val context: Context,
    private val pendingRepo: PendingNotificationsRepository,
    // テスト容易性のための差し替え口。本番では常に DefaultCategoryMapper を使う
    // (docs/plans/other/category-icons-and-assets/PLAN.md §4)。
    private val categoryMapper: CategoryMapper = DefaultCategoryMapper,
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
        // 保留通知の内容が変化するたび (2件目/3件目の到着等も含む) 重要度とカテゴリの両方を
        // 再評価する (docs/plans/other/importance-based-sizing/PLAN.md §6、
        // docs/plans/other/category-icons-and-assets/PLAN.md §5)。
        // GitHub issue #30 — セッション非アクティブ時に画面が既にオフなら、その場で
        // tryStartSession() の同じゲーティング判定を通す。当初は「保留0件→1件以上」の遷移
        // (setOnBecameNonEmptyListener) だけを見ていたが、既に何か保留中の状態でさらに新規が
        // 届く方が実際にはよくあるケースであり、そちらも同じ穴を持っていたため、変化のたびに
        // 毎回チェックするこの形に統合した。画面が点灯中ならここでは何もしない — 通常どおり
        // 次の ACTION_SCREEN_OFF で処理される。
        pendingRepo.setOnChangedListener {
            if (sessionActive) {
                recomputeIndicatorState()
            } else {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pm.isInteractive) tryStartSession()
            }
        }
    }

    fun onScreenOff() = tryStartSession()

    // ACTION_SCREEN_OFF 発生時、および画面が既にオフの状態で新規通知が届いた場合 (issue #30) の
    // 両方から呼ばれる、セッション開始可否のゲーティング判定。
    private fun tryStartSession() {
        // feature OFF 中はゲーティングごと無効化する (docs/plans/mvp/onoff-notification-toggle/PLAN.md §2)。
        if (!FeatureEnabledState.isEnabled()) return
        // DND 中は隠す設定が有効かつ現在 DND 中ならゲーティングごと無効化する (docs/plans/other/dnd-handling/PLAN.md §5)。
        if (DndSettings.hideDuringDndCached && DndState.isActive()) return
        // クワイエットアワー時間帯中はゲーティングごと無効化する
        // (docs/plans/other/auto-stop-conditions/PLAN-3-quiet-hours.md §6)。
        if (QuietHoursState.isActive()) return
        val snapshot = pendingRepo.snapshot()
        if (snapshot.isEmpty()) return // ゲーティング判定 (§1 step 2) — 端末は通常通りスリープする
        // GitHub issue #36 — 保留中の通知が全部既読 (前回までのセッションで既に提示済み) なら、
        // 新規に知らせることが何も無いため、ドットも一切表示せずセッション自体を起動しない。
        // ただし高重要度の通知が1件でもあれば、既読かどうかに関わらず起動する —
        // 「未処理の重要な用件が残っている」事実はサイズ表示 (1.5x) と同様、既読状態とは
        // 無関係に反映し続けるべきもの (このアプリの既存方針)。この抑制自体は設定でOFFにでき、
        // OFF の場合は既読状態を一切見ずに常に起動する (物忘れしやすいユーザー向け、issue #36 の
        // フォローアップ)。
        if (RepeatSuppressedNotificationSettings.enabledCached) {
            val keywordsByApp = { pkg: String -> KeywordRegistry.keywordsCache[pkg] ?: emptyList() }
            val allSeen = snapshot.all { NotificationSeenState.isSeen(it.key) }
            val anyHighImportance = ImportanceResolver.isHighImportance(snapshot, keywordsByApp)
            if (allSeen && !anyHighImportance) return
        }
        startSession()
    }

    private fun startSession() {
        sessionActive = true
        proximityMonitor.start()
        // burnInProtector はこの時点ではまだ存在しない (full-screen-intent 経由で OS が
        // FakeSleepActivity を起動するのはこの後) ため、実際には常に no-op — 実際の起動通知は
        // FakeSleepActivity.onCreate() が直接行う (docs/plans/other/burn-in-protection/PLAN.md §4)。
        FakeSleepActivityHolder.burnInProtector?.onSessionActiveChanged(true)
        // burnInProtector と同じ理由でここでは indicatorUiState もまだ null のことが多く
        // no-op となる — 実際の初回評価は FakeSleepActivity.onCreate() の
        // recomputeIndicatorStateNow() が行う (docs/plans/other/importance-based-sizing/PLAN.md §6)。
        recomputeIndicatorState()
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
        // GitHub issue #36 — このセッションで提示された通知は「既知」として記録する。
        // recomputeIndicatorState() 側ではなくここで行う — セッション開始直後の再計算時点で
        // マークすると、そのセッションで初めて届いた通知自身も即座に「既知」扱いになり、
        // 一度もプレビューに出せなくなってしまうため (次にセッションが始まった時点で初めて
        // 「前回までに提示済みかどうか」を判定できればよい)。
        NotificationSeenState.markSeen(pendingRepo.snapshot().map { it.key })
    }

    // FakeSleepActivity.onCreate() から、indicatorUiState 登録直後の初回評価用に呼び出される
    // 公開エントリーポイント (docs/plans/other/importance-based-sizing/PLAN.md §6、
    // docs/plans/other/category-icons-and-assets/PLAN.md §5)。
    fun recomputeIndicatorStateNow() = recomputeIndicatorState()

    // 保留通知一覧全体の重要度判定 (点滅間隔用、グループ全体で単一値 — PLAN.md §7 の
    // 「点滅はスロット単位ではなくグループ単位」参照) と、アプリ単位のスロット構築
    // (IndicatorSlotBuilder — カテゴリ解決・件数集約・スロット単位の重要度・4件超のオーバー
    // フロー集約を1回のパスでまとめて担う) の両方を行い、結果を
    // FakeSleepActivityHolder.indicatorUiState (存在すれば) へ書き込む。indicatorUiState が
    // まだ存在しない場合 (FakeSleepActivity 未起動) は no-op — 実際の初回評価は
    // recomputeIndicatorStateNow() 経由で FakeSleepActivity.onCreate() が行う。
    private fun recomputeIndicatorState() {
        val uiState = FakeSleepActivityHolder.indicatorUiState ?: return
        val snapshot = pendingRepo.snapshot()
        val keywordsByApp = { pkg: String -> KeywordRegistry.keywordsCache[pkg] ?: emptyList() }
        uiState.slots.value = IndicatorSlotBuilder.build(snapshot, categoryMapper, keywordsByApp)
        // GitHub issue #32 — a slot's footprint (e.g. highImportance's 1.5x scale) may have just
        // changed; re-clamp the group's current position so it can't be left overflowing the
        // screen edge until the next scheduled burn-in reposition jump.
        FakeSleepActivityHolder.burnInProtector?.onSlotsMayHaveChanged()
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
