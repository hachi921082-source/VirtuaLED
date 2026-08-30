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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virtualed.app.R
import com.virtualed.app.data.AppExclusionSettings
import kotlinx.coroutines.launch

/**
 * アプリ別「インジケーターから除外」設定画面 (GitHub issue #26)。
 * CategorySettingsScreen/KeywordSettingsScreen と同じ boolean トグル方式 (ナビゲーション
 * ライブラリ不使用) で MainSwitchScreen と行き来する (MainActivity.kt の
 * showExclusionSettings 参照)。アプリ選択は同じ理由で InstalledAppPicker.kt の
 * loadInstalledApps() を共用する。
 *
 * category/keyword はスロットの「1件」を選ぶ多肢選択のためアコーディオンで展開するが、
 * こちらは各アプリ「除外する/しない」の単純な二値なので、アプリ行に直接スイッチを置く
 * (展開操作を挟まない)。選択は「保存」ボタンを持たず、切り替えと同時に
 * AppExclusionSettings.setExcluded() へ書き込む (この画面以外の設定項目と同じ
 * write-through 方式)。
 */
@Composable
fun ExclusionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps(context)
    }

    // 個々のアプリ行の DataStore 読み込みを待たずに一覧を出せるよう、除外中のアプリ全件を
    // 一括で読み込む (AppExclusionSettings.excludedCache はサービス起動時のみ更新されるため、
    // ここでは設定画面専用に都度読み直す)。
    var excludedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        excludedPackages = AppExclusionSettings.excludedCache
    }

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
                    text = stringResource(R.string.exclusion_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.exclusion_settings_back_button))
                }
            }
            Text(
                text = stringResource(R.string.exclusion_settings_description),
                modifier = Modifier.padding(top = 12.dp),
            )
            LazyColumn(modifier = Modifier.padding(top = 4.dp)) {
                items(installedApps) { app ->
                    val isExcluded = app.packageName in excludedPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = app.label, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isExcluded,
                            onCheckedChange = { checked ->
                                excludedPackages = if (checked) {
                                    excludedPackages + app.packageName
                                } else {
                                    excludedPackages - app.packageName
                                }
                                coroutineScope.launch {
                                    AppExclusionSettings.setExcluded(context, app.packageName, checked)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
