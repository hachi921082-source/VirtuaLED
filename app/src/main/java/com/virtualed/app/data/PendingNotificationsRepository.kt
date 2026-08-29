package com.virtualed.app.data

/**
 * プロセス全体で共有される「現在保留中の通知は何か」の単一情報源。
 * NotificationSyncListenerService と VirtuaLedForegroundService は同一プロセスで動作するため、
 * プロセス間 IPC は不要でシンプルな Kotlin object シングルトンで足りる。
 *
 * MVP simplification: 呼び出し側には isEmpty()/snapshot() のみを公開し、
 * アプリ別アイコン・件数バッジ等のスタッキング表現は持たない
 * (docs/plans/other/multi-notification-stacking/ に分離、対象外)。
 */
object PendingNotificationsRepository {
    private val notifications = mutableMapOf<String, PendingNotificationInfo>()
    private var onBecameEmpty: (() -> Unit)? = null
    private var onBecameNonEmpty: (() -> Unit)? = null

    // 空↔非空の「遷移」に限らず、内容が変化するたび毎回発火する汎用リスナー。
    // importance-based-sizing が保留通知の2件目/3件目到着時にも重要度を再評価するために使う
    // (docs/plans/other/importance-based-sizing/PLAN.md §6) — onBecameEmpty/onBecameNonEmpty は
    // 既存のセッション終了ロジック用途のまま変更しない。
    private var onChanged: (() -> Unit)? = null

    @Synchronized fun isEmpty(): Boolean = notifications.isEmpty()
    @Synchronized fun snapshot(): List<PendingNotificationInfo> = notifications.values.toList()

    @Synchronized fun put(info: PendingNotificationInfo) {
        val wasEmpty = notifications.isEmpty()
        notifications[info.key] = info
        if (wasEmpty) onBecameNonEmpty?.invoke()
        onChanged?.invoke()
    }

    @Synchronized fun remove(key: String) {
        if (notifications.remove(key) != null && notifications.isEmpty()) onBecameEmpty?.invoke()
        onChanged?.invoke()
    }

    // resync 専用 (onListenerConnected() から呼ばれる、§5)
    @Synchronized fun replaceAll(infos: List<PendingNotificationInfo>) {
        val wasEmpty = notifications.isEmpty()
        notifications.clear()
        infos.forEach { notifications[it.key] = it }
        when {
            notifications.isEmpty() && !wasEmpty -> onBecameEmpty?.invoke()
            notifications.isNotEmpty() && wasEmpty -> onBecameNonEmpty?.invoke()
        }
        onChanged?.invoke()
    }

    fun setOnBecameEmptyListener(cb: () -> Unit) { onBecameEmpty = cb }
    fun setOnBecameNonEmptyListener(cb: () -> Unit) { onBecameNonEmpty = cb }
    fun setOnChangedListener(cb: () -> Unit) { onChanged = cb }
}
