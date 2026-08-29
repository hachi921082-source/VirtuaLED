package com.virtualed.app.overlay

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.virtualed.app.data.PendingNotificationsRepository
import kotlin.math.roundToInt

// スロットグループのドットサイズ (dp)。焼き付き防止のバウンディングボックス計算 (px 換算) と
// 実際の描画サイズの両方で共有する (docs/plans/other/burn-in-protection/PLAN.md §3)。
private const val INDICATOR_DOT_SIZE_DP = 12

/**
 * フェイクスリープ中に表示される全画面 Activity。
 * setShowWhenLocked/setTurnScreenOn により、secure/非secure問わずロック画面の上に表示する
 * (docs/DECISIONS.md §18)。full-screen-intent 通知 (FakeSleepSessionController.startSession())
 * 経由で OS がこの Activity を起動する — Service が直接起動するわけではない。
 *
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §5、
 * docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §3〜§6 を実装。
 *
 * MVP スコープ: 黒背景 + 固定色 (白) のドット、および PeekInteractionController による
 * ドット/プレビューテキストのコンテンツ view トグルのみ。位置は BurnInProtector が一定間隔で
 * ランダムに再配置する (docs/plans/other/burn-in-protection/PLAN.md)。複数アイコンの視覚的
 * レイアウト・座標ベースのヒットテストは対象外 (docs/plans/other/multi-notification-stacking/)。
 */
class FakeSleepActivity : ComponentActivity() {
    // Service 起動時に1回だけ生成されるため、この Activity が存在する時点では常に非 null
    // (docs/plans/mvp/fake-sleep-overlay/PLAN.md §1/§2)。
    private val sessionController: FakeSleepSessionController = FakeSleepActivityHolder.sessionController!!
    private lateinit var peekController: PeekInteractionController
    private lateinit var burnInProtector: BurnInProtector

    // null = ドット表示 (INDICATOR_SHOWN)、非 null = プレビューテキスト表示 (PREVIEW_SHOWN)。
    private val previewLinesState = mutableStateOf<List<String>?>(null)

    // other/ 階層の視覚拡張機能 (近接センサーによる一時非表示・焼き付き防止の再配置など) が
    // ここ経由で書き込む (docs/DECISIONS.md §20、docs/plans/mvp/fake-sleep-overlay/PLAN.md §7)。
    private val indicatorUiState = FakeSleepIndicatorUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // アイドルタイムアウトによる画面消灯を抑止する。これが無いと OS の screen_off_timeout
        // 経過後に電源ボタン再押下と同一の ACTION_SCREEN_OFF が飛び、セッションが誤終了する。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        burnInProtector = BurnInProtector(
            indicatorUiState = indicatorUiState,
            // 回転/構成変更対策のため、都度 WindowManager から読み直す (キャッシュしない)。
            screenBoundsPx = {
                val bounds = windowManager.currentWindowMetrics.bounds
                IntSize(bounds.width(), bounds.height())
            },
            // dp→px 変換はここ (Compose 外・非 Composable なコンテキスト) で行う — BurnInProtector
            // 自体は Density を持たないプレーンクラスのため (docs/plans/other/burn-in-protection/
            // PLAN.md §3 からの実装上の乖離、詳細は同 PLAN.md 参照)。
            slotGroupSizePx = (INDICATOR_DOT_SIZE_DP * resources.displayMetrics.density).roundToInt(),
        )
        // Activity インスタンスとセッションは 1:1 (PeekInteractionController と同様) —
        // startSession() 自体はこの Activity 生成前に呼ばれ、その時点では burnInProtector が
        // まだ存在しないため、ここで直接初期状態を通知する
        // (docs/plans/other/burn-in-protection/PLAN.md §4 からの実装上の乖離)。
        burnInProtector.onSessionActiveChanged(true)
        peekController = PeekInteractionController(
            activity = this,
            sessionController = sessionController,
            pendingRepo = PendingNotificationsRepository,
            onUiStateChanged = { state -> burnInProtector.onUiStateChanged(state) },
        )
        setContent {
            FakeSleepContent(
                previewLinesState = previewLinesState,
                indicatorVisibleState = indicatorUiState.indicatorVisible,
                indicatorOffsetState = indicatorUiState.indicatorOffset,
                onTap = { peekController.onTap() },
            )
        }
        FakeSleepActivityHolder.current = this
        FakeSleepActivityHolder.indicatorUiState = indicatorUiState
        FakeSleepActivityHolder.burnInProtector = burnInProtector
    }

    /** ドットからプレビューテキストへ、コンテンツ view を切り替える (同一 Activity インスタンス内)。 */
    fun showPreviewText(lines: List<String>) {
        previewLinesState.value = lines
    }

    /** プレビューテキストからドットへ、コンテンツ view を戻す。 */
    fun showDot() {
        previewLinesState.value = null
    }

    /** 電源ボタン再押下によるフルディスミス (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §6)。 */
    fun onPowerButtonPressed() {
        peekController.cancelPreviewTimeout()
        sessionController.endSession()
    }

    override fun onDestroy() {
        // endSession() 済みでも stale なコールバックが発火しないようにするバックストップ (§5)。
        peekController.cancelPreviewTimeout()
        // endSession() 側でも呼ばれるが、Activity が先に破棄される経路に備えたバックストップ
        // (docs/plans/other/burn-in-protection/PLAN.md §4)。stop() は running ガードで冪等。
        burnInProtector.onSessionActiveChanged(false)
        if (FakeSleepActivityHolder.current === this) FakeSleepActivityHolder.current = null
        if (FakeSleepActivityHolder.indicatorUiState === indicatorUiState) {
            FakeSleepActivityHolder.indicatorUiState = null
        }
        if (FakeSleepActivityHolder.burnInProtector === burnInProtector) {
            FakeSleepActivityHolder.burnInProtector = null
        }
        super.onDestroy()
    }
}

@Composable
private fun FakeSleepContent(
    previewLinesState: MutableState<List<String>?>,
    indicatorVisibleState: MutableState<Boolean>,
    indicatorOffsetState: MutableState<IntOffset>,
    onTap: () -> Unit,
) {
    val previewLines = previewLinesState.value
    val indicatorVisible = indicatorVisibleState.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        // 近接センサー被覆中は indicatorVisible が false になり、ドット/プレビューいずれの
        // 描画も抑止する。previewLinesState 自体は変更しないため、被覆解除時は直前の
        // 表示状態がそのまま復元される (docs/plans/other/auto-stop-conditions/PLAN-1-proximity.md §3)。
        if (indicatorVisible) {
            if (previewLines == null) {
                IndicatorGroup(offsetState = indicatorOffsetState)
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    previewLines.forEach { line -> Text(text = line, color = Color.White) }
                }
            }
        }
    }
}

/**
 * インジケータースロットグループの位置決めコンテナ。画面左上を基準とする絶対座標系で、
 * offsetState (BurnInProtector が書き換える) の値だけドットをずらして描画する
 * (docs/plans/other/burn-in-protection/PLAN.md §3)。複数スロットのグリッドレイアウトは対象外
 * (docs/plans/other/multi-notification-stacking/) — MVP と同じ単一ドット (固定色・白) を
 * オフセット付きで描画するのみ。
 */
@Composable
private fun IndicatorGroup(offsetState: MutableState<IntOffset>) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { offsetState.value }
                .size(INDICATOR_DOT_SIZE_DP.dp)
                .background(Color.White, CircleShape),
        )
    }
}
