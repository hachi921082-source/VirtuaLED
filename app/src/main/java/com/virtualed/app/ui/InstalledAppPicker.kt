package com.virtualed.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** アプリ選択リスト1件分 (パッケージ名 + 表示ラベル)。 */
data class InstalledAppInfo(val packageName: String, val label: String)

/**
 * ランチャー起動可能な全インストール済みアプリを、表示ラベルの昇順で取得する
 * (docs/plans/other/importance-based-sizing/PLAN.md §3 の「or any installed app」フォールバック)。
 * KeywordSettingsScreen (アプリ別キーワード登録) と CategorySettingsScreen (アプリ別カテゴリ割り当て)
 * の双方が同じ「現在保留中の通知しか保持しない = 通知履歴からの選択はできない」制約を持つため、
 * このアプリ選択ロジックを共有する (docs/plans/other/category-icons-and-assets/PLAN.md §7)。
 */
fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
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
