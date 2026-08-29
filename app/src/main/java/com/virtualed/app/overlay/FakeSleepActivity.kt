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
import androidx.compose.ui.unit.dp
import com.virtualed.app.data.PendingNotificationsRepository

/**
 * フェイクスリープ中に表示される全画面 Activity。
 * setShowWhenLocked/setTurnScreenOn により、secure/非secure問わずロック画面の上に表示する
 * (docs/DECISIONS.md §18)。full-screen-intent 通知 (FakeSleepSessionController.startSession())
 * 経由で OS がこの Activity を起動する — Service が直接起動するわけではない。
 *
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §5、
 * docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §3〜§6 を実装。
 *
 * MVP スコープ: 黒背景 + 固定位置・固定色 (白) のドット、および PeekInteractionController による
 * ドット/プレビューテキストのコンテンツ view トグルのみ。複数アイコンの視覚的レイアウト・
 * 座標ベースのヒットテストは対象外 (docs/plans/other/multi-notification-stacking/)。
 */
class FakeSleepActivity : ComponentActivity() {
    // Service 起動時に1回だけ生成されるため、この Activity が存在する時点では常に非 null
    // (docs/plans/mvp/fake-sleep-overlay/PLAN.md §1/§2)。
    private val sessionController: FakeSleepSessionController = FakeSleepActivityHolder.sessionController!!
    private lateinit var peekController: PeekInteractionController

    // null = ドット表示 (INDICATOR_SHOWN)、非 null = プレビューテキスト表示 (PREVIEW_SHOWN)。
    private val previewLinesState = mutableStateOf<List<String>?>(null)

    // other/ 階層の視覚拡張機能 (近接センサーによる一時非表示など) がここ経由で書き込む
    // (docs/DECISIONS.md §20、docs/plans/mvp/fake-sleep-overlay/PLAN.md §7)。
    private val indicatorUiState = FakeSleepIndicatorUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // アイドルタイムアウトによる画面消灯を抑止する。これが無いと OS の screen_off_timeout
        // 経過後に電源ボタン再押下と同一の ACTION_SCREEN_OFF が飛び、セッションが誤終了する。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        peekController = PeekInteractionController(
            activity = this,
            sessionController = sessionController,
            pendingRepo = PendingNotificationsRepository,
        )
        setContent {
            FakeSleepContent(
                previewLinesState = previewLinesState,
                indicatorVisibleState = indicatorUiState.indicatorVisible,
                onTap = { peekController.onTap() },
            )
        }
        FakeSleepActivityHolder.current = this
        FakeSleepActivityHolder.indicatorUiState = indicatorUiState
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
        if (FakeSleepActivityHolder.current === this) FakeSleepActivityHolder.current = null
        if (FakeSleepActivityHolder.indicatorUiState === indicatorUiState) {
            FakeSleepActivityHolder.indicatorUiState = null
        }
        super.onDestroy()
    }
}

@Composable
private fun FakeSleepContent(
    previewLinesState: MutableState<List<String>?>,
    indicatorVisibleState: MutableState<Boolean>,
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
                // 固定位置・固定色 (白) のドット。焼き付き防止の再配置・色カスタマイズは対象外。
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.White, CircleShape),
                )
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
