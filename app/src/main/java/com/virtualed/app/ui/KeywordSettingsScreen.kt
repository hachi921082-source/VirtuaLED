package com.virtualed.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virtualed.app.R
import com.virtualed.app.data.KeywordRegistry
import kotlinx.coroutines.launch

/**
 * アプリ別キーワード登録画面 (docs/plans/other/importance-based-sizing/PLAN.md §1/§3)。
 * ナビゲーションライブラリを使わず、OnboardingScreen ↔ MainSwitchScreen と同じ boolean トグルで
 * MainSwitchScreen と行き来する (VirtuaLedApp() 側の呼び出し元を参照)。
 *
 * このアプリは「現在保留中の通知」しか保持しない (PendingNotificationsRepository、通知履歴は
 * 対象外) ため、アプリ選択は PackageManager 経由でランチャー起動可能な全インストール済みアプリ
 * から行う (PLAN.md §3 の「or any installed app」フォールバック)。InstalledAppInfo/
 * loadInstalledApps() は InstalledAppPicker.kt へ共通化済み (docs/plans/other/
 * category-icons-and-assets/PLAN.md §7、CategorySettingsScreen と共用)。
 */
@Composable
fun KeywordSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps(context)
    }

    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var keywords by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(selectedPackage) {
        val pkg = selectedPackage
        keywords = if (pkg != null) KeywordRegistry.getKeywords(context, pkg) else emptyList()
    }

    var newKeyword by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // CategorySettingsScreen と同じ理由でステータスバー/ナビゲーションバー分の余白を確保する
        // (enableEdgeToEdge() によりコンテンツが先頭行から時計/バッテリー表示と重なっていた —
        // category-icons-and-assets のブランチでの実機テストで発見)。
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
                    text = stringResource(R.string.keyword_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.keyword_settings_back_button))
                }
            }
            Text(
                text = stringResource(R.string.keyword_settings_pick_app_label),
                modifier = Modifier.padding(top = 12.dp),
            )
            // 2026-08-30: 選んだアプリの直下にキーワード編集を展開する形へ変更 (以前はリストの外に
            // 別枠で表示しており、選択したアプリとの視覚的なつながりが分かりにくいとの指摘 —
            // CategorySettingsScreen と同じ理由・同じアコーディオン方式)。
            LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(top = 4.dp)) {
                items(installedApps) { app ->
                    val isExpanded = selectedPackage == app.packageName
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPackage = if (isExpanded) null else app.packageName
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(text = app.label)
                        }
                        if (isExpanded) {
                            KeywordEditor(
                                keywords = keywords,
                                newKeyword = newKeyword,
                                onNewKeywordChanged = { newKeyword = it },
                                onAddKeyword = {
                                    val keyword = newKeyword.trim()
                                    if (keyword.isNotEmpty()) {
                                        val updated = keywords + keyword
                                        keywords = updated
                                        newKeyword = ""
                                        coroutineScope.launch {
                                            KeywordRegistry.setKeywords(context, app.packageName, updated)
                                        }
                                    }
                                },
                                onRemoveKeyword = { keyword ->
                                    val updated = keywords - keyword
                                    keywords = updated
                                    coroutineScope.launch {
                                        KeywordRegistry.setKeywords(context, app.packageName, updated)
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 展開されたアプリ行の直下に表示するキーワード入力欄+登録済みキーワード一覧
 * (2026-08-30 追加 — 以前は KeywordSettingsScreen 本体に直接書かれていた)。
 */
@Composable
private fun KeywordEditor(
    keywords: List<String>,
    newKeyword: String,
    onNewKeywordChanged: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedTextField(
                value = newKeyword,
                onValueChange = onNewKeywordChanged,
                label = { Text(text = stringResource(R.string.keyword_settings_add_keyword_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAddKeyword, modifier = Modifier.padding(start = 8.dp)) {
                Text(text = stringResource(R.string.keyword_settings_add_button))
            }
        }
        if (keywords.isEmpty()) {
            Text(
                text = stringResource(R.string.keyword_settings_empty_keywords),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                keywords.forEach { keyword ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = keyword)
                        TextButton(onClick = { onRemoveKeyword(keyword) }) {
                            Text(text = stringResource(R.string.keyword_settings_remove_button))
                        }
                    }
                }
            }
        }
    }
}
