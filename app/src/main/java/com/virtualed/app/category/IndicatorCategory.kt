package com.virtualed.app.category

/**
 * インジケーターに表示するカテゴリ種別。docs/detailed-spec.md §3、
 * docs/plans/other/category-icons-and-assets/PLAN.md §1 を実装。
 * assetName はアセット命名を文書化するための String であり、実際の描画時ドローアブル解決は
 * FakeSleepActivity.kt 側のコンパイル時 when 分岐で行う (ランタイムの文字列名解決はしない、
 * PLAN.md §5 参照)。
 */
enum class IndicatorCategory(val assetName: String) {
    PHONE("ic_notify_phone"),
    MAIL("ic_notify_mail"),
    SNS("ic_notify_sns"),
    CALENDAR("ic_notify_calendar"),
    OTHER("ic_notify_other"),
}
