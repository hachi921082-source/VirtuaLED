package com.virtualed.app.overlay

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

/**
 * フェイクスリープ中の視覚的インジケーター（ドット/プレビュー）表示状態を保持する
 * 状態ホルダー。FakeSleepActivity 自体はロック画面ライフサイクル関心事
 * (setShowWhenLocked/setTurnScreenOn/ウィンドウフラグ) に留め、other/ 階層の視覚拡張
 * 機能が素直に書き込める先として分離している (docs/DECISIONS.md §20、
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §7)。
 *
 * 現時点では近接センサーによる一時非表示 (indicatorVisible)、焼き付き防止の再配置
 * (indicatorOffset)、インジケーター色 (tintColor) を持つ。slots/blinkIntervalMs は、
 * それぞれを書き込む other/ 階層の各機能 (multi-notification-stacking,
 * importance-based-sizing) の実装時に追加される。
 */
class FakeSleepIndicatorUiState {
    // false の間はドット/プレビューいずれの描画も抑止する (近接センサー被覆時のみ)。
    // セッション状態・保留通知トラッキング・WakeLock ガードには一切影響しない
    // (docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md §3)。
    val indicatorVisible: MutableState<Boolean> = mutableStateOf(true)

    // インジケータースロットグループ全体のアンカーオフセット。BurnInProtector が一定間隔で
    // ランダムな値へ書き換える。IntOffset.Zero がデフォルト位置 (画面左上基準)
    // (docs/plans/other/burn-in-protection/PLAN.md §3)。
    val indicatorOffset: MutableState<IntOffset> = mutableStateOf(IntOffset.Zero)

    // ドット/各スロットアイコンの着色。ユーザーが設定画面で選択した永続化色
    // (IndicatorColorSettings) をセッション開始時に読み込んで書き込む。デフォルトは白
    // (docs/plans/other/indicator-color-customization/PLAN.md §2/§5)。
    val tintColor: MutableState<Color> = mutableStateOf(Color.White)
}
