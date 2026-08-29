package com.virtualed.app.data

/**
 * 保留中(未読)通知1件分のスナップショット情報。
 * NotificationListenerService から取得した StatusBarNotification を
 * このプロセス内の他コンポーネント向けに変換した不変データ。
 */
data class PendingNotificationInfo(
    val key: String,          // StatusBarNotification.key — 投稿された通知ごとに一意
    val packageName: String,
    val appLabel: String,     // PackageManager.getApplicationLabel() で解決、peek プレビュー用
    val title: String?,       // Notification.EXTRA_TITLE
    val text: String?,        // Notification.EXTRA_TEXT
    val postTime: Long,
)
