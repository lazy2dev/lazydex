package app.lazydex.data.tracker.sync

import java.util.Locale

/**
 * Utility for matching titles across remote tracker sources and local library items.
 * Normalizes titles by stripping punctuation, symbols, whitespace, and case.
 */
object TitleMatcher {

    fun normalize(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Determines if any candidate title matches any target title.
     * Checks exact normalized equality, and prefix/containment match for strings of length >= 6.
     */
    fun isMatch(
        candidateTitles: List<String?>,
        targetTitles: List<String?>
    ): Boolean {
        val candidateNorms = candidateTitles
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        val targetNorms = targetTitles
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        for (c in candidateNorms) {
            for (t in targetNorms) {
                if (c == t) return true
                if (c.length >= 6 && t.length >= 6) {
                    if (c.startsWith(t) || t.startsWith(c)) return true
                }
            }
        }
        return false
    }
}

