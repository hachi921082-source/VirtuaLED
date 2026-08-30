package com.virtualed.app.category

import com.virtualed.app.data.CategorySettings

/**
 * パッケージ名からカテゴリを解決するインターフェース。呼び出し側 (描画時のアイコン選択、
 * FakeSleepSessionController の再計算パス) が解決器の内部実装 (DataStore/キャッシュ) に
 * 依存しないようにする。
 * docs/plans/other/category-icons-and-assets/PLAN.md §4 を実装
 * (docs/QA.md #13、docs/DECISIONS.md §21 でユーザー割り当て方式に決定済み)。
 */
interface CategoryMapper {
    fun resolve(packageName: String): IndicatorCategory
}

/**
 * CategorySettings.categoryCache を参照する既定実装。未割り当てアプリは OTHER に
 * フォールバックする (PLAN.md §3)。ImportanceResolver 同様、保留通知の変化のたびに
 * 同期評価する必要があるため、同期参照キャッシュを直接読む (DataStore への非同期アクセスはしない)。
 */
object DefaultCategoryMapper : CategoryMapper {
    override fun resolve(packageName: String): IndicatorCategory =
        CategorySettings.categoryCache[packageName] ?: IndicatorCategory.OTHER
}
