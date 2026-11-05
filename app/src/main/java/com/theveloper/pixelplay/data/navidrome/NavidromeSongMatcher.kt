package com.theveloper.pixelplay.data.navidrome

import com.theveloper.pixelplay.utils.SearchTextNormalizer

/**
 * Matches a local song to its counterpart on a Navidrome/Subsonic server by metadata, so we can
 * star it or stream it. Shared by the favorites-sync worker and Sonos playback (both need the
 * server's song id for a locally-played track).
 *
 * Matching is deliberately lenient: titles/artists that differ only cosmetically (accents,
 * punctuation, "(Remastered)", feat. credits, order/joiners) still match.
 */
object NavidromeSongMatcher {

    fun titleMatches(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    fun artistMatches(candidate: String, want: String): Boolean {
        if (candidate.isBlank() || want.isBlank()) return false
        return candidate == want || candidate.contains(want) || want.contains(candidate)
    }

    /** Lowercase, strip accents, drop bracketed/`feat.` suffixes and punctuation, collapse spaces. */
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        // Shared with search so letters like ø and æ fold too instead of being dropped below.
        var s = SearchTextNormalizer.stripDiacritics(value)
        s = s.replace(Regex("[\\(\\[\\{].*?[\\)\\]\\}]"), " ")
        s = s.replace(Regex("\\b(feat|ft|featuring)\\b.*"), " ")
        s = s.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return s
    }
}
