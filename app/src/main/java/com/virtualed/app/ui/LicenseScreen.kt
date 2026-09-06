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
import androidx.compose.foundation.lazy.LazyColumn
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
 * Booth 等の配布先で第三者へ配布する際に開示が必要な内容をまとめた画面
 * (アイコン出典・使用ライブラリのライセンス・通知内容の取り扱い)。
 * CategorySettingsScreen 等と同じ boolean トグル方式で MainSwitchScreen と行き来する
 * (MainActivity.kt の showLicenseScreen 参照)。ソースコード上のコメント (各 ic_notify_*.xml)
 * だけでは配布パッケージの利用者に見えないため、アプリ内に同内容を複製する。
 */
@Composable
fun LicenseScreen(onBack: () -> Unit) {
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
                    text = stringResource(R.string.license_screen_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.license_screen_back_button))
                }
            }
            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                item {
                    LicenseSection(
                        heading = stringResource(R.string.license_icons_heading),
                        body = stringResource(R.string.license_icons_body),
                    )
                }
                item {
                    LicenseSection(
                        heading = stringResource(R.string.license_libraries_heading),
                        body = stringResource(R.string.license_libraries_body),
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                item {
                    LicenseSection(
                        heading = stringResource(R.string.license_notification_heading),
                        body = stringResource(R.string.license_notification_body),
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseSection(heading: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = heading, style = MaterialTheme.typography.titleSmall)
        Text(text = body, modifier = Modifier.padding(top = 6.dp))
    }
}
