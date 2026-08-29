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
 * (indicatorOffset)、インジケーター色 (tintColor)、重要度に基づくスロット一覧と点滅間隔
 * (slots/blinkIntervalMs) を持つ。
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

    // インジケータースロットの一覧。現時点では常に単一スロットの既定値 (MVP は単一ドットのみ) —
    // 複数スロット化は docs/plans/other/multi-notification-stacking/ が担当。各スロットの
    // highImportance は ImportanceResolver の判定結果を FakeSleepSessionController が書き込む
    // (docs/plans/other/importance-based-sizing/PLAN.md §6)。
    val slots: MutableState<List<IndicatorSlotUiState>> = mutableStateOf(listOf(IndicatorSlotUiState()))

    // 高重要度時の点滅間隔 (ms)。null は「点滅なし」(通常時のデフォルト) を表す。ユーザー設定
    // (BlinkSettings) の値を、いずれかのスロットが高重要度と判定されている間だけここへ書き込む
    // (docs/plans/other/importance-based-sizing/PLAN.md §5/§6)。
    val blinkIntervalMs: MutableState<Long?> = mutableStateOf(null)
}

/**
 * インジケータースロット1件分の表示状態。docs/plans/mvp/fake-sleep-overlay/PLAN.md §7 のスケッチは
 * category: IndicatorCategory? も持つが、IndicatorCategory はまだ未実装の
 * docs/plans/other/category-icons-and-assets/ が所有する型のため、本フィーチャーでは
 * count/highImportance のみを定義する (category はデフォルト値付きで後から非破壊的に追加できる、
 * docs/plans/other/importance-based-sizing/PLAN.md 実装上の乖離ノート参照)。
 */
data class IndicatorSlotUiState(
    val count: Int = 1,
    val highImportance: Boolean = false,
)
