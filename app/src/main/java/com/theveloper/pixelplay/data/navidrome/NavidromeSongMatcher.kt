package com.theveloper.pixelplay.data.navidrome

import com.theveloper.pixelplay.data.network.navidrome.NavidromeApiService
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

    /** Returns the server song id for a local track, or null if no confident match exists. */
    suspend fun resolveNavidromeId(
        api: NavidromeApiService,
        title: String,
        artist: String,
        album: String
    ): String? {
        val wantTitle = normalize(title)
        if (wantTitle.isBlank()) return null
        val wantArtist = normalize(artist)
        val wantAlbum = normalize(album)

        var candidates = api.searchSongs(query = title, count = 50).getOrNull().orEmpty()
        if (candidates.isEmpty() && artist.isNotBlank()) {
            candidates = api.searchSongs(query = "$title $artist", count = 50).getOrNull().orEmpty()
        }
        if (candidates.isEmpty()) return null

        val titleMatches = candidates.filter { titleMatches(normalize(it.optString("title")), wantTitle) }
        if (titleMatches.isEmpty()) return null

        val best = titleMatches.firstOrNull { c ->
            artistMatches(normalize(c.optString("artist")), wantArtist) &&
                titleMatches(normalize(c.optString("album")), wantAlbum)
        } ?: titleMatches.firstOrNull { c ->
            artistMatches(normalize(c.optString("artist")), wantArtist)
        } ?: titleMatches.singleOrNull()

        return best?.optString("id")?.takeIf { it.isNotBlank() }
    }

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
