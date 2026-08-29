package com.virtualed.app.overlay

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * フェイクスリープ中の視覚的インジケーター（ドット/プレビュー）表示状態を保持する
 * 状態ホルダー。FakeSleepActivity 自体はロック画面ライフサイクル関心事
 * (setShowWhenLocked/setTurnScreenOn/ウィンドウフラグ) に留め、other/ 階層の視覚拡張
 * 機能が素直に書き込める先として分離している (docs/DECISIONS.md §20、
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §7)。
 *
 * 現時点では近接センサーによる一時非表示 (indicatorVisible) のみを持つ。
 * slots/tintColor/blinkIntervalMs/indicatorOffset は、それぞれを書き込む other/ 階層の
 * 各機能 (multi-notification-stacking, indicator-color-customization,
 * importance-based-sizing, burn-in-protection) の実装時に追加される。
 */
class FakeSleepIndicatorUiState {
    // false の間はドット/プレビューいずれの描画も抑止する (近接センサー被覆時のみ)。
    // セッション状態・保留通知トラッキング・WakeLock ガードには一切影響しない
    // (docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md §3)。
    val indicatorVisible: MutableState<Boolean> = mutableStateOf(true)
}
