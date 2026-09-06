package com.virtualed.app.overlay

import android.os.Handler
import android.os.Looper
import com.virtualed.app.data.NotificationSeenState
import com.virtualed.app.data.PendingNotificationsRepository

/**
 * FakeSleepActivity のコンテンツ view タップ数に応じた Indicator Shown/Preview Shown の状態機械。
 * 座標ベースのヒットテストは一切行わない — タップは常に「回数」としてのみ扱う
 * (docs/DECISIONS.md §12, §14)。
 *
 * docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §2〜§5 を実装。
 */
enum class OverlayUiState { INDICATOR_SHOWN, PREVIEW_SHOWN }

class PeekInteractionController(
    private val activity: FakeSleepActivity, // トグル対象のコンテンツ view を所有する
    private val sessionController: FakeSleepSessionController,
    private val pendingRepo: PendingNotificationsRepository,
    // uiState 遷移 (INDICATOR_SHOWN <-> PREVIEW_SHOWN) の度に呼ばれる。BurnInProtector が
    // プレビューテキスト表示中は再配置ループを一時停止するために使う
    // (docs/plans/other/burn-in-protection/PLAN.md §4)。既定は no-op。
    private val onUiStateChanged: (OverlayUiState) -> Unit = {},
) {
    companion object {
        // detailed-spec.md §8 が示す 3-5 秒レンジ内の固定値。MVP では設定 UI を持たない (§5)。
        private const val PREVIEW_TIMEOUT_MS = 4000L
    }

    // セッション(=Activity インスタンス)と 1:1 なので常に INDICATOR_SHOWN から始まる (§2)。
    private var uiState: OverlayUiState = OverlayUiState.INDICATOR_SHOWN
    private var previewTimeoutHandler: Handler? = null

    fun onTap() {
        when (uiState) {
            OverlayUiState.INDICATOR_SHOWN -> {
                uiState = OverlayUiState.PREVIEW_SHOWN
                onUiStateChanged(uiState)
                activity.showPreviewText(buildPreviewLines())
                startPreviewTimeout()
            }
            OverlayUiState.PREVIEW_SHOWN -> {
                cancelPreviewTimeout()
                sessionController.endSession() // フルディスミス — フェイクスリープを抜け実ロック画面へ
            }
        }
    }

    fun cancelPreviewTimeout() {
        previewTimeoutHandler?.removeCallbacksAndMessages(null)
    }

    // タップされたアイコンに関わらず、保留中の全通知を集約した同一リストを表示する (§4/§9)。
    // GitHub issue #36 — 前回までのセッションで既に提示済みの通知は除外し、今回新たに増えた分
    // だけを見せる (通知欄から消さずに画面をオン→オフし直しても、同じ内容が毎回蒸し返されない
    // ようにするため)。ただし新規分が0件になってしまう場合は、空のプレビューを見せるよりは
    // 保留中の全件を見せた方が有用なので、その時だけ従来通りの全件表示にフォールバックする。
    private fun buildPreviewLines(): List<String> {
        val snapshot = pendingRepo.snapshot()
        val newOnly = snapshot.filterNot { NotificationSeenState.isSeen(it.key) }
        val lines = if (newOnly.isEmpty()) snapshot else newOnly
        return lines.map { "${it.appLabel}: ${it.title.orEmpty()} ${it.text.orEmpty()}".trim() }
    }

    private fun startPreviewTimeout() {
        previewTimeoutHandler = Handler(Looper.getMainLooper()).also {
            it.postDelayed({ onPreviewTimeout() }, PREVIEW_TIMEOUT_MS)
        }
    }

    private fun onPreviewTimeout() {
        if (pendingRepo.isEmpty()) {
            sessionController.endSession() // 表示するものがない — ディスミスアニメーション等は不要
        } else {
            uiState = OverlayUiState.INDICATOR_SHOWN
            onUiStateChanged(uiState)
            activity.showDot()
        }
    }
}
