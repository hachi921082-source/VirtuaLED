package com.virtualed.app.permission

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 権限グラント状態を確認する無状態ヘルパー。
 *
 * docs/plans/mvp/permission-onboarding/PLAN.md §2 を実装。
 * 当初の MVP スコープは通知リスナーアクセスのみで、`USE_FULL_SCREEN_INTENT` はインストール時に
 * 自動許可されるため確認不要としていた。しかし OS アップデートでインストール済みアプリの許可が
 * 取り消される事例を実機で確認したため (docs/QA.md #24)、全画面通知の可否も確認対象に加えた
 * (GitHub issue #49)。
 * `POST_NOTIFICATIONS` は通常のランタイム許可ダイアログで足りるためここでは扱わない。
 */
object PermissionChecker {

    // 通知リスナーアクセスは通常のランタイム許可ダイアログでは要求できず、
    // ユーザーが Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS 経由で個別に許可する必要がある
    // (docs/detailed-spec.md §15)。
    fun hasNotificationListenerAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    // Android 14 (API 34) 以降、全画面通知の許可はユーザーが設定画面で取り消せる (OS アップデート
    // 時に取り消されることもある)。API 33 以下はインストール時許可のみで取り消し手段が無いため常に true。
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return notificationManager.canUseFullScreenIntent()
    }

    // このアプリ専用の「全画面通知」許可画面を開く Intent。canUseFullScreenIntent() が false を
    // 返しうるのは API 34 以降のみのため、呼び出し側もその場合に限って使う。
    fun fullScreenIntentSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}"),
        )
}
