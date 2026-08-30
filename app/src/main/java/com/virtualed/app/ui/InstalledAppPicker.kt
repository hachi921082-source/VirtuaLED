package com.virtualed.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

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
    // MATCH_DEFAULT_ONLY は「暗黙的インテントに応答できるか」の判定用フラグで、対象の
    // intent-filter に CATEGORY_DEFAULT が無いと除外してしまう。ホーム画面アイコンを持つアプリの
    // 多くは CATEGORY_LAUNCHER のみを宣言し CATEGORY_DEFAULT は宣言しない (例: 楽天Link
    // jp.co.rakuten.mobile.rcs) ため、このフラグを付けると実在するランチャーアプリが一覧から
    // 抜け落ちる — 実機テストで発見。実際の Android ホームランチャーがアプリ一覧を列挙する際と
    // 同じく、このフラグ無しで問い合わせる。
    val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
    return resolveInfos
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .map { appInfo: ApplicationInfo ->
            InstalledAppInfo(appInfo.packageName, pm.getApplicationLabel(appInfo).toString())
        }
        .sortedBy { it.label.lowercase() }
}
