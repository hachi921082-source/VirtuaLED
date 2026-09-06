package com.virtualed.app.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.virtualed.app.data.AppExclusionSettings
import com.virtualed.app.data.DndSettings
import com.virtualed.app.data.DndState
import com.virtualed.app.data.NotificationSeenState
import com.virtualed.app.data.PendingNotificationInfo
import com.virtualed.app.data.PendingNotificationsRepository
import com.virtualed.app.data.SeenAppsHistory
import com.virtualed.app.overlay.FakeSleepActivityHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * システムの通知リスナーとしてバインドされ、通知の投稿/削除を
 * PendingNotificationsRepository に反映するサービス。
 *
 * docs/detailed-spec.md §12 (Notification Removal Sync) を実装。
 * docs/plans/other/dnd-handling/PLAN.md §3 により、DND (おやすみモード) 状態の検出も本サービスが
 * 兼務する — 別のリスナーサービスを追加で定義しない (同 PLAN のスコープ注記参照)。
 */
class NotificationSyncListenerService : NotificationListenerService() {

    // issue #27 — SeenAppsHistory.record() (suspend) を呼ぶためのスコープ。
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // §4 — 自アプリの FGS 通知を除外
        // issue #27 — 除外の有無に関わらず「通知してきたアプリ」として記録する
        // (ExclusionSettingsScreen の絞り込み表示のデータソース)。
        serviceScope.launch { SeenAppsHistory.record(applicationContext, sbn.packageName) }
        // issue #26 — 除外設定されたアプリの通知はここで完全に握りつぶす。
        // PendingNotificationsRepository へ一切追加しないため、スロット描画・セッション開始・
        // プレビューテキストのいずれにも影響しない (category/keyword の除外とは異なり、
        // 通知そのものをここで止める)。
        if (sbn.packageName in AppExclusionSettings.excludedCache) return
        PendingNotificationsRepository.put(sbn.toPendingInfo(this))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        PendingNotificationsRepository.remove(sbn.key)
        // issue #36 — 通知が実際に消えたので「既知」記録も一緒に片付ける。無制限な肥大化を防ぎ、
        // 同じ key で再投稿された場合に改めて新規扱いにできるようにする。
        NotificationSeenState.forget(sbn.key)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val activeOthers = activeNotifications.filter { it.packageName != packageName }
        // issue #27 — 再接続時点でアクティブな通知の送信元アプリも記録する
        // (バインドが切れていた間に届いた通知の送信元を取りこぼさないため)。
        activeOthers.forEach { sbn ->
            serviceScope.launch { SeenAppsHistory.record(applicationContext, sbn.packageName) }
        }
        // §5 resync — 初回バインド時、および OS/プロセスキルからのリスナー再接続時に
        // 保留中通知の集合をアクティブな通知一覧から再構築する (空とは仮定しない)
        val active = activeOthers
            .filter { it.packageName !in AppExclusionSettings.excludedCache }
            .map { it.toPendingInfo(this) }
        PendingNotificationsRepository.replaceAll(active)

        // docs/plans/other/dnd-handling/PLAN.md §3 — 再接続時点の DND 状態で初期化する。
        DndState.setActive(currentInterruptionFilter != INTERRUPTION_FILTER_ALL)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // docs/plans/other/dnd-handling/PLAN.md §3, §5。
    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        val dndNowActive = interruptionFilter != INTERRUPTION_FILTER_ALL
        DndState.setActive(dndNowActive)

        // docs/plans/mvp/onoff-notification-toggle/PLAN.md §2 と同じ非対称パターン — DND が
        // 今まさに有効化され、かつ「DND 中は隠す」設定が有効な場合のみ、稼働中のセッションを
        // 即終了する。DND が解除されても再開はしない (次の画面オフ時に通常通り再評価される)。
        val sessionController = FakeSleepActivityHolder.sessionController
        if (DndSettings.hideDuringDndCached && dndNowActive && sessionController?.sessionActive == true) {
            sessionController.endSession()
        }
    }
}

private fun StatusBarNotification.toPendingInfo(context: Context): PendingNotificationInfo {
    val label = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)
    return PendingNotificationInfo(
        key = key,
        packageName = packageName,
        appLabel = label,
        title = notification.extras.getString(Notification.EXTRA_TITLE),
        text = notification.extras.getString(Notification.EXTRA_TEXT),
        postTime = postTime,
    )
}
