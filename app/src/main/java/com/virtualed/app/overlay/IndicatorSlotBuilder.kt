package com.virtualed.app.overlay

import com.virtualed.app.category.CategoryMapper
import com.virtualed.app.category.IndicatorCategory
import com.virtualed.app.data.PendingNotificationInfo
import com.virtualed.app.importance.matchesKeyword

/**
 * 保留通知一覧をアプリ単位でグループ化し、最大 MAX_SLOTS 個の表示スロットへ集約する。
 * 5件目以降のアプリは1つの OTHER オーバーフロースロットへまとめる (件数は合算)。
 * docs/plans/other/multi-notification-stacking/PLAN.md §3 を実装。
 *
 * 永続化を持たない純粋関数のみのオブジェクト — ImportanceResolver/CategoryMapper と同様、
 * 保留通知の変化のたびに同期評価する (FakeSleepSessionController.recomputeIndicatorState())。
 */
object IndicatorSlotBuilder {
    const val MAX_SLOTS = 4

    fun build(
        pending: List<PendingNotificationInfo>,
        categoryMapper: CategoryMapper,
        keywordsByApp: (String) -> List<String>,
    ): List<IndicatorSlotUiState> {
        val perApp = pending.groupBy { it.packageName }.map { (pkg, list) ->
            IndicatorSlotUiState(
                category = categoryMapper.resolve(pkg),
                count = list.size,
                // 重要度はスロット単位 (= アプリ単位) で判定する。ImportanceResolver.isHighImportance()
                // は複数保留通知全体を1つの Boolean へ集約する既存 API (PLAN.md §3 の実装注記参照) の
                // ため、ここではその実装が使う matchesKeyword() を直接アプリごとに呼び出す
                // (ImportanceResolver 自体へのシグネチャ変更は行わない)。
                highImportance = list.any { info ->
                    val keywords = keywordsByApp(pkg)
                    keywords.isNotEmpty() &&
                        keywords.any { matchesKeyword("${info.title.orEmpty()} ${info.text.orEmpty()}", it) }
                },
            )
        }
        if (perApp.size <= MAX_SLOTS) return perApp
        val kept = perApp.take(MAX_SLOTS - 1)
        val overflow = perApp.drop(MAX_SLOTS - 1)
        return kept + IndicatorSlotUiState(
            category = IndicatorCategory.OTHER,
            count = overflow.sumOf { it.count },
            highImportance = overflow.any { it.highImportance },
        )
    }
}
