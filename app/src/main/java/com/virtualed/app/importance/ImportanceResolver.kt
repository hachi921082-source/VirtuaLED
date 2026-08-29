package com.virtualed.app.importance

import com.virtualed.app.data.PendingNotificationInfo
import java.text.Normalizer

/**
 * 大文字/小文字・全角/半角の等価性を吸収した部分一致でキーワード判定を行う。
 * NFKC 正規化は全角英数・カタカナ等を半角相当へ畳み込むため全角/半角の等価性要件を満たし、
 * lowercase() が大文字小文字の等価性を満たす。正規表現は使わず単純な部分文字列一致のみ
 * (docs/detailed-spec.md §5、docs/plans/other/importance-based-sizing/PLAN.md §4)。
 */
private fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()

fun matchesKeyword(haystack: String, keyword: String): Boolean =
    normalize(haystack).contains(normalize(keyword))

/**
 * 永続化を持たない純粋関数のみのオブジェクト。保留中通知一覧と、パッケージ名からキーワード一覧を
 * 引くコールバックを受け取り、いずれか1件でもマッチすれば高重要度と判定する (複数保留通知時は
 * 集約ルール — 単一インジケーター MVP に対する簡略化、PLAN.md スコープ注記参照)。
 */
object ImportanceResolver {
    fun isHighImportance(
        pending: List<PendingNotificationInfo>,
        keywordsByApp: (String) -> List<String>,
    ): Boolean {
        return pending.any { info ->
            val keywords = keywordsByApp(info.packageName)
            if (keywords.isEmpty()) return@any false // 未登録アプリは常に通常重要度
            val haystack = "${info.title.orEmpty()} ${info.text.orEmpty()}"
            keywords.any { matchesKeyword(haystack, it) }
        }
    }
}
