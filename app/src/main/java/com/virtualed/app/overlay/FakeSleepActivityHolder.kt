package com.virtualed.app.overlay

/**
 * プロセス全体で唯一の「現在レジューム中の FakeSleepActivity」参照と、
 * Service が1回だけ生成する FakeSleepSessionController インスタンスへの参照を保持する。
 * 両者は同一プロセス内で完結するため、単純な Kotlin object シングルトンで十分 (IPC 不要)。
 * FakeSleepSessionController (Service 側) がこの参照経由で Activity を finish() でき、
 * FakeSleepActivity のタップ/電源ボタン処理 (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md
 * が実装、本フィーチャーのスコープ外) がこの参照経由で同じ controller インスタンスへ
 * コールバックできる。
 *
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §1 を実装。
 */
object FakeSleepActivityHolder {
    var current: FakeSleepActivity? = null
    var sessionController: FakeSleepSessionController? = null // Service 起動時に1回だけ設定される
    // FakeSleepActivity 生成時に current と共に1回だけ設定される (docs/DECISIONS.md §20、
    // docs/plans/mvp/fake-sleep-overlay/PLAN.md §7)。other/ 階層の視覚拡張機能はここ経由で
    // 描画状態へ書き込む。
    var indicatorUiState: FakeSleepIndicatorUiState? = null

    // indicatorUiState と同じタイミング (FakeSleepActivity 生成時) で1回だけ設定される。
    // screenBoundsPx が Activity の WindowManager に依存するため indicatorUiState と同様に
    // Activity インスタンスごとに生成し直す (docs/plans/other/burn-in-protection/PLAN.md §5)。
    // FakeSleepSessionController はこの参照経由で焼き付き防止ループの起動条件を通知する。
    var burnInProtector: BurnInProtector? = null
}
