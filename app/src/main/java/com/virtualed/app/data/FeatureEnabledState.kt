package com.virtualed.app.data

/**
 * feature 全体の ON/OFF フラグを保持するプロセス内シングルトン。DataStore 等への永続化は
 * MVP スコープ外 — プロセスが kill されて FGS が START_STICKY で再起動した場合、
 * デフォルト値 true にリセットされる (docs/DECISIONS.md §8、
 * docs/plans/mvp/onoff-notification-toggle/PLAN.md §1)。
 *
 * アプリ内スイッチ (MainActivity) と通知アクション (OnOffActionReceiver) の双方から
 * setEnabled() が呼ばれる。VirtuaLedForegroundService は onCreate() でリスナーを登録し、
 * 変更を通知の再描画・フェイクスリープセッションの即終了に反映する (PLAN §2)。
 */
object FeatureEnabledState {
    private var enabled: Boolean = true
    private var onChanged: ((Boolean) -> Unit)? = null

    @Synchronized fun isEnabled(): Boolean = enabled

    @Synchronized fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        onChanged?.invoke(value)
    }

    fun setOnChangedListener(cb: (Boolean) -> Unit) { onChanged = cb }
}
