package com.virtualed.app.data

/**
 * 通知キー (PendingNotificationInfo.key) 単位で「フェイクスリープセッション中に一度でも
 * 提示済みか」を保持する、プロセス内のみの一時集合 (DndState/FeatureEnabledState と同じ
 * インメモリのみのパターン、永続化不要)。GitHub issue #36 — 通知欄から消さないまま画面を
 * オン→オフし直した際、同じ通知が peek-preview のテキストへ何度も再登場するのを防ぐ。
 *
 * ドット/アイコン/件数バッジの描画には一切影響しない — 除外対象になるのは
 * PeekInteractionController.buildPreviewLines() が組み立てるテキスト一覧のみ。
 *
 * マーキングは FakeSleepSessionController.endSession() で行う (recomputeIndicatorState() 内では
 * 行わない) — セッション開始直後の再計算時点でマークすると、そのセッションで初めて届いた
 * 通知自身も即座に「既知」扱いになり、一度もプレビューに出せなくなってしまうため。
 */
object NotificationSeenState {
    private val seenKeys = mutableSetOf<String>()

    @Synchronized fun isSeen(key: String): Boolean = key in seenKeys

    @Synchronized fun markSeen(keys: Collection<String>) {
        seenKeys += keys
    }

    // 通知が実際に消えた (onNotificationRemoved) タイミングで呼び出す。無制限な肥大化を防ぎ、
    // 同じ key で再投稿された通知を改めて新規扱いにできるようにする。
    @Synchronized fun forget(key: String) {
        seenKeys -= key
    }
}
