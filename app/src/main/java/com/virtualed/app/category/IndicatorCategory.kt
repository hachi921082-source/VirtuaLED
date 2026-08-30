package com.virtualed.app.category

import com.virtualed.app.R

/**
 * インジケーターに表示するカテゴリ種別。docs/detailed-spec.md §3、
 * docs/plans/other/category-icons-and-assets/PLAN.md §1 を実装。
 * assetName はアセット命名を文書化するための String であり、実際の描画時ドローアブル解決は
 * drawableRes() 側のコンパイル時 when 分岐で行う (ランタイムの文字列名解決はしない、
 * PLAN.md §5 参照)。
 */
enum class IndicatorCategory(val assetName: String) {
    PHONE("ic_notify_phone"),
    MAIL("ic_notify_mail"),
    SNS("ic_notify_sns"),
    CALENDAR("ic_notify_calendar"),
    OTHER("ic_notify_other"),
}

/**
 * カテゴリに対応するドローアブルリソース。元は FakeSleepActivity.kt 内の private 拡張関数
 * だったが、2026-08-30 に CategorySettingsScreen でも実際のアイコン画像を表示したいとの
 * 要望に伴い、両方から使える IndicatorCategory 自身のファイルへ移動した。
 */
fun IndicatorCategory.drawableRes(): Int = when (this) {
    IndicatorCategory.PHONE -> R.drawable.ic_notify_phone
    IndicatorCategory.MAIL -> R.drawable.ic_notify_mail
    IndicatorCategory.SNS -> R.drawable.ic_notify_sns
    IndicatorCategory.CALENDAR -> R.drawable.ic_notify_calendar
    IndicatorCategory.OTHER -> R.drawable.ic_notify_other
}
