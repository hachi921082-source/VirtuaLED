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
    // スロットグループの現在の実描画バウンディングボックス (px)。スロット数 (2x2 グリッド)・
    // 高重要度の1.5倍スケール・件数バッジの角はみ出しでサイズが変動するため、screenBoundsPx と
    // 同様にジャンプの都度呼び出し、キャッシュしない (固定 Int だった旧実装は multi-notification-
    // stacking/importance-based-sizing/multi-notification-stacking のバッジ機能がいずれも
    // 単一ドット時のサイズのまま反映されず、実際のグループがそれより大きくなった場合に画面外へ
    // はみ出す不具合の原因だった — GitHub issue #23)。このクラスは非 Compose なプレーンクラスで
    // あり Density を持たないため、dp→px 変換自体は呼び出し側 (FakeSleepActivity) が行う。
    private val footprintPx: () -> IndicatorFootprint,
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
        val footprint = footprintPx()
        // topInsetPx: 件数バッジは各アイコンの右上に半径分はみ出す (SlotIcon)。上段スロットの
        // バッジは group の上端よりさらに上へはみ出すため、y の下限を 0 からこの分だけ上げて
        // グループ自体をその分下げないと、バッジが画面上端の外側に出てしまう。
        val minY = footprint.topInsetPx
        val maxX = (bounds.width - footprint.sizePx.width).coerceAtLeast(0)
        val maxY = (bounds.height - footprint.sizePx.height).coerceAtLeast(minY)
        indicatorUiState.indicatorOffset.value = IntOffset(
            Random.nextInt(0, maxX + 1),
            Random.nextInt(minY, maxY + 1),
        )
    }
}

/**
 * BurnInProtector がジャンプ先の上下限を計算するための、スロットグループの現在の実描画
 * バウンディングボックス。sizePx はグループの幅/高さ (件数バッジの右方向はみ出しを含む)、
 * topInsetPx は件数バッジの上方向はみ出し分 — 幅と違って「グループの左上を基準にどれだけ
 * 上にはみ出るか」という下限側の制約のため、サイズではなく別フィールドとして持つ
 * (GitHub issue #23)。
 */
data class IndicatorFootprint(val sizePx: IntSize, val topInsetPx: Int)
