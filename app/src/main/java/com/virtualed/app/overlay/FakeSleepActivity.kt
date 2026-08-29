package com.virtualed.app.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * フェイクスリープ中に表示される全画面 Activity。
 * setShowWhenLocked/setTurnScreenOn により、secure/非secure問わずロック画面の上に表示する
 * (docs/DECISIONS.md §18)。full-screen-intent 通知 (FakeSleepSessionController.startSession())
 * 経由で OS がこの Activity を起動する — Service が直接起動するわけではない。
 *
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §5 を実装。
 *
 * MVP スコープ: 黒背景 + 固定位置・固定色 (白) のドットのみ。カテゴリアイコン描画・
 * タップ/電源ボタンのハンドリングはここでは行わない
 * (docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md が本 Activity のコンテンツ view に
 * click listener を追加する形で担当、§7 参照)。
 */
class FakeSleepActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            FakeSleepContent()
        }
        FakeSleepActivityHolder.current = this
    }

    override fun onDestroy() {
        if (FakeSleepActivityHolder.current === this) FakeSleepActivityHolder.current = null
        super.onDestroy()
    }
}

@Composable
private fun FakeSleepContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // 固定位置・固定色 (白) のドット。焼き付き防止の再配置・色カスタマイズは対象外 (§7)。
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.White, CircleShape),
        )
    }
}
