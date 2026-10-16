package app.loop.core.domain.food

import app.loop.core.model.Food
import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy matching of dish names (from Gemini or typed search) to the food table, on the name and
 * every alias. Handles spelling variants common in Indian food names ("daal"/"dal",
 * "paneer butter masala"/"butter paneer masala") with token overlap plus edit distance.
 */
object FoodMatcher {
    /** Below this score a dish counts as "not in the table". */
    const val MATCH_THRESHOLD = 0.72

    data class Match(val food: Food, val score: Double)

    fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        // Common transliteration variants collapse to one spelling.
        .replace("aa", "a").replace("ee", "i").replace("oo", "u")
        .replace("dh", "d").replace("bh", "b").replace("kh", "k").replace("gh", "g")

    /** Best match at or above [MATCH_THRESHOLD], or null. */
    fun best(query: String, foods: List<Food>): Match? =
        rank(query, foods, limit = 1).firstOrNull()?.takeIf { it.score >= MATCH_THRESHOLD }

    /** Foods ordered by similarity, for search-as-you-type. */
    fun rank(query: String, foods: List<Food>, limit: Int = 20): List<Match> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return foods.asSequence()
            .map { food -> Match(food, (listOf(food.name) + food.aliases).maxOf { similarity(q, normalize(it)) }) }
            .filter { it.score > 0.55 }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    internal fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val ta = a.split(' ').toSet()
        val tb = b.split(' ').toSet()
        val tokens = ta.intersect(tb).size.toDouble() / max(ta.size, tb.size)
        val sameWords = if (ta == tb) 1.0 else 0.0
        val edit = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        // Prefix typing ("panee") should rank the dish highly while searching.
        val prefix = if (b.startsWith(a) && a.length >= 3) 0.85 + 0.15 * a.length / b.length else 0.0
        val contains = if (a.length >= 4 && (b.contains(a) || a.contains(b))) 0.75 else 0.0
        return maxOf(sameWords, edit, prefix, contains, 0.5 * tokens + 0.5 * edit, tokens * 0.9)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }
}
