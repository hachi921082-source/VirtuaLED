package com.virtualed.app.permission

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * 権限グラント状態を確認する無状態ヘルパー。
 *
 * docs/plans/mvp/permission-onboarding/PLAN.md §2 を実装。
 * MVP スコープは通知リスナーアクセスのみ (同 PLAN のスコープ注記参照) —
 * `USE_FULL_SCREEN_INTENT` は自動許可済みのため onboarding 不要、
 * `POST_NOTIFICATIONS` は通常のランタイム許可ダイアログで足りるためここでは扱わない。
 */
object PermissionChecker {

    // 通知リスナーアクセスは通常のランタイム許可ダイアログでは要求できず、
    // ユーザーが Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS 経由で個別に許可する必要がある
    // (docs/detailed-spec.md §15)。
    fun hasNotificationListenerAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
