package com.virtualed.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virtualed.app.R

/**
 * プレミアム機能(通知元アプリ自身のアイコン表示)の案内画面 (2026-08-30 追加)。
 * docs/plans/other/source-app-icon-indicator-mode/PLAN.md §7、docs/DECISIONS.md §22。
 *
 * 課金未解放時に MainSwitchScreen のアプリアイコン表示行から遷移する — 「機能自体は隠さず、
 * 存在に気づけるようにする」という方針転換 (以前は行ごと非表示にしていた)。
 *
 * 実際の購入処理 (Google Play Billing Library・Play Console でのアプリ内アイテム登録・
 * 配布方法のテスト公開への移行) はまだ組み込まれていない — 課金の仕組み自体は
 * `docs/QA.md` #22 として意図的に保留中 (`docs/DECISIONS.md` §22)。この画面は「後から購入
 * ボタンにリンクを足すだけで済む」ように置き場所だけ用意したもので、下のボタンは現時点では
 * 無効化されている。
 */
@Composable
fun PremiumInfoScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.premium_info_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.premium_info_back_button))
                }
            }
            Text(
                text = stringResource(R.string.premium_info_description),
                modifier = Modifier.padding(top = 24.dp),
            )
            Button(
                enabled = false,
                onClick = {},
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(text = stringResource(R.string.premium_info_purchase_button_placeholder))
            }
        }
    }
}
