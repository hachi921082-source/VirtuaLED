package com.virtualed.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ACTION_SCREEN_OFF / ACTION_USER_PRESENT を検出する動的登録 BroadcastReceiver。
 * どちらも暗黙ブロードキャストであり最新 Android では manifest 宣言できないため、
 * 既に起動中の VirtuaLedForegroundService.onStartCommand() 内で動的登録する
 * (docs/plans/mvp/foreground-service-setup/PLAN.md §4)。
 *
 * docs/plans/mvp/fake-sleep-overlay/PLAN.md §3、
 * docs/plans/mvp/dismiss-and-peek-interaction/PLAN.md §6/§7 を実装。
 */
class ScreenStateReceiver(
    private val onScreenOff: () -> Unit,
    private val onUnlock: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
            Intent.ACTION_USER_PRESENT -> onUnlock()
        }
    }
}
