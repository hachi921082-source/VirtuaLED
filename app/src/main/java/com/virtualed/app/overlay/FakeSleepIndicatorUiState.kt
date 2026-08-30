package com.virtualed.app.overlay

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import com.virtualed.app.category.IndicatorCategory

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

    // インジケータースロットの一覧。初期値は単一スロットの既定値 — 実際のセッション中は
    // FakeSleepSessionController.recomputeIndicatorState() が IndicatorSlotBuilder.build() の
    // 結果 (アプリ単位で最大4件、5件目以降は OTHER へ集約) へ書き換える
    // (docs/plans/other/multi-notification-stacking/PLAN.md §3/§6)。各スロットの highImportance は
    // アプリ単位のキーワードマッチ判定結果を同関数が書き込む
    // (docs/plans/other/importance-based-sizing/PLAN.md §6)。
    val slots: MutableState<List<IndicatorSlotUiState>> = mutableStateOf(listOf(IndicatorSlotUiState()))

    // 高重要度時の点滅間隔 (ms)。null は「点滅なし」(通常時のデフォルト) を表す。ユーザー設定
    // (BlinkSettings) の値を、いずれかのスロットが高重要度と判定されている間だけここへ書き込む
    // (docs/plans/other/importance-based-sizing/PLAN.md §5/§6)。
    val blinkIntervalMs: MutableState<Long?> = mutableStateOf(null)
}

/**
 * インジケータースロット1件分の表示状態。category は null の間プレーンなドット
 * (ic_notify_dot 相当の円) を描画し、非 null なら対応するカテゴリアイコンを描画する
 * (docs/plans/other/category-icons-and-assets/PLAN.md §5)。リスト全体の書き込みは
 * FakeSleepSessionController の再計算パスが IndicatorSlotBuilder.build() 経由で行い、
 * アプリ単位で1スロット (最大4件、5件目以降は count を合算した OTHER オーバーフロー
 * スロット1件へ集約) を持つ (docs/plans/other/multi-notification-stacking/PLAN.md §3)。
 */
data class IndicatorSlotUiState(
    val count: Int = 1,
    val highImportance: Boolean = false,
    val category: IndicatorCategory? = null,
    // このスロットに対応する単一アプリの packageName。null は「単一の代表アプリが存在しない」
    // ことを意味し、5件目以降を集約した OTHER オーバーフロースロットのみが null のまま
    // ビルドされる (実アプリ単位のスロットは必ず非 null)。source-app-icon-indicator-mode の
    // アプリアイコン描画可否は category ではなくこのフィールドで判定する — 未割り当てで
    // category が OTHER に解決される単一アプリも正しくアイコン対象に含めるため
    // (docs/plans/other/source-app-icon-indicator-mode/PLAN.md §2/§5)。
    val packageName: String? = null,
)
