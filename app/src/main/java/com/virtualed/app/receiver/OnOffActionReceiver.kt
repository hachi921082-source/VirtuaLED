package com.virtualed.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virtualed.app.data.FeatureEnabledState

/**
 * FGS 通知の ON/OFF アクションボタンの PendingIntent 送信先。
 * docs/plans/mvp/onoff-notification-toggle/PLAN.md §4 を実装。
 */
class OnOffActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE = "com.virtualed.app.action.TOGGLE_FEATURE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        FeatureEnabledState.setEnabled(!FeatureEnabledState.isEnabled())
        // 意図的に stopSelf() も ACTION_STOP 送信も行わない — アクションをタップし続けられるよう
        // service と通知は生存させたままにする (§18)。
    }
}
