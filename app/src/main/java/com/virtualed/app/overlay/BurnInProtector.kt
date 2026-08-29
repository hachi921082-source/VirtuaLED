package com.virtualed.app.overlay

import android.os.Handler
import android.os.Looper
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

/**
 * フェイクスリープ中のインジケータースロットグループ全体を一定間隔でランダムな位置へ瞬間移動させ、
 * OLED パネルの焼き付きを軽減する。docs/plans/other/burn-in-protection/PLAN.md を実装。
 *
 * WindowManager/View 層は存在しないため、移動は FakeSleepIndicatorUiState.indicatorOffset への
 * 書き込みのみで完結する (docs/plans/other/burn-in-protection/PLAN.md §1)。
 *
 * セッションアクティブ状態・OverlayUiState・近接センサー被覆状態の3条件が全て揃った時のみ
 * ループを稼働させる。各条件は独立に onXxxChanged() で通知され、recompute() が単一のゲートとして
 * start()/stop() を判断する — 各トリガーが個別に start()/stop() を呼ぶ設計は採らない
 * (docs/DECISIONS.md §20)。
 */
class BurnInProtector(
    private val indicatorUiState: FakeSleepIndicatorUiState,
    // FakeSleepActivity から供給される。回転/構成変更対策のため、ジャンプの都度呼び出し、
    // キャッシュしない (docs/plans/other/burn-in-protection/PLAN.md §3)。
    private val screenBoundsPx: () -> IntSize,
    // スロットグループのバウンディングボックス (px)。MVP は単一ドットのみなので、その辺の長さを
    // dp→px 変換した値を呼び出し側 (FakeSleepActivity) が渡す — このクラスは非 Compose な
    // プレーンクラスであり Density を持たないため、変換自体はここでは行わない。
    private val slotGroupSizePx: Int,
) {
    companion object {
        const val REPOSITION_INTERVAL_MS = 4_000L // 固定値、設定 UI は持たない (docs/DECISIONS.md §13)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private var sessionActive = false
    private var uiState: OverlayUiState = OverlayUiState.INDICATOR_SHOWN
    private var proximityCovered = false

    fun onSessionActiveChanged(active: Boolean) {
        sessionActive = active
        recompute()
    }

    fun onUiStateChanged(state: OverlayUiState) {
        uiState = state
        recompute()
    }

    fun onProximityCoveredChanged(covered: Boolean) {
        proximityCovered = covered
        recompute()
    }

    // 3条件を1箇所でまとめて評価する唯一のゲート (docs/DECISIONS.md §20)。
    private fun recompute() {
        val shouldRun = sessionActive && uiState == OverlayUiState.INDICATOR_SHOWN && !proximityCovered
        if (shouldRun) start() else stop()
    }

    private fun start() {
        if (running) return
        running = true
        scheduleNext()
    }

    private fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun scheduleNext() {
        handler.postDelayed({
            jumpToRandomOffset() // 瞬間移動 — Animatable 等による補間は行わない (§3)
            if (running) scheduleNext()
        }, REPOSITION_INTERVAL_MS)
    }

    private fun jumpToRandomOffset() {
        val bounds = screenBoundsPx()
        val maxX = (bounds.width - slotGroupSizePx).coerceAtLeast(0)
        val maxY = (bounds.height - slotGroupSizePx).coerceAtLeast(0)
        indicatorUiState.indicatorOffset.value = IntOffset(
            Random.nextInt(0, maxX + 1),
            Random.nextInt(0, maxY + 1),
        )
    }
}
