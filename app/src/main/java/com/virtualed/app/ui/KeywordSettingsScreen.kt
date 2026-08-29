package com.virtualed.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** アプリ選択リスト1件分 (パッケージ名 + 表示ラベル)。 */
data class InstalledAppInfo(val packageName: String, val label: String)

/**
 * アプリ別キーワード登録画面 (docs/plans/other/importance-based-sizing/PLAN.md §1/§3)。
 * ナビゲーションライブラリを使わず、OnboardingScreen ↔ MainSwitchScreen と同じ boolean トグルで
 * MainSwitchScreen と行き来する (VirtuaLedApp() 側の呼び出し元を参照)。
 *
 * このアプリは「現在保留中の通知」しか保持しない (PendingNotificationsRepository、通知履歴は
 * 対象外) ため、アプリ選択は PackageManager 経由でランチャー起動可能な全インストール済みアプリ
 * から行う (PLAN.md §3 の「or any installed app」フォールバック)。
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
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
            LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(top = 4.dp)) {
                items(installedApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPackage = app.packageName }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(text = app.label)
                    }
                }
            }

            val selected = selectedPackage
            if (selected == null) {
                Text(
                    text = stringResource(R.string.keyword_settings_no_app_selected),
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text(
                    text = selected,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text(text = stringResource(R.string.keyword_settings_add_keyword_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val keyword = newKeyword.trim()
                            if (keyword.isNotEmpty()) {
                                val updated = keywords + keyword
                                keywords = updated
                                newKeyword = ""
                                coroutineScope.launch { KeywordRegistry.setKeywords(context, selected, updated) }
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.keyword_settings_add_button))
                    }
                }
                if (keywords.isEmpty()) {
                    Text(
                        text = stringResource(R.string.keyword_settings_empty_keywords),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        keywords.forEach { keyword ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = keyword)
                                TextButton(
                                    onClick = {
                                        val updated = keywords - keyword
                                        keywords = updated
                                        coroutineScope.launch {
                                            KeywordRegistry.setKeywords(context, selected, updated)
                                        }
                                    },
                                ) {
                                    Text(text = stringResource(R.string.keyword_settings_remove_button))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ランチャー起動可能な全インストール済みアプリを、表示ラベルの昇順で取得する
 * (docs/plans/other/importance-based-sizing/PLAN.md §3 の「or any installed app」フォールバック)。
 */
private fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfos
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .map { appInfo: ApplicationInfo ->
            InstalledAppInfo(appInfo.packageName, pm.getApplicationLabel(appInfo).toString())
        }
        .sortedBy { it.label.lowercase() }
}
