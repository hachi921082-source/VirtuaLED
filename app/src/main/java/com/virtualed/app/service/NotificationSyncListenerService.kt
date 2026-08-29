package com.virtualed.app.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.virtualed.app.data.PendingNotificationInfo
import com.virtualed.app.data.PendingNotificationsRepository

/**
 * システムの通知リスナーとしてバインドされ、通知の投稿/削除を
 * PendingNotificationsRepository に反映するサービス。
 *
 * docs/detailed-spec.md §12 (Notification Removal Sync) を実装。
 */
class NotificationSyncListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // §4 — 自アプリの FGS 通知を除外
        PendingNotificationsRepository.put(sbn.toPendingInfo(this))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        PendingNotificationsRepository.remove(sbn.key)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // §5 resync — 初回バインド時、および OS/プロセスキルからのリスナー再接続時に
        // 保留中通知の集合をアクティブな通知一覧から再構築する (空とは仮定しない)
        val active = activeNotifications
            .filter { it.packageName != packageName }
            .map { it.toPendingInfo(this) }
        PendingNotificationsRepository.replaceAll(active)
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
