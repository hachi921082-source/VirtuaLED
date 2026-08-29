package com.virtualed.app.overlay

import android.os.Handler
import android.os.Looper
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
    private fun buildPreviewLines(): List<String> =
        pendingRepo.snapshot().map { "${it.appLabel}: ${it.title.orEmpty()} ${it.text.orEmpty()}".trim() }

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
            activity.showDot()
        }
    }
}
