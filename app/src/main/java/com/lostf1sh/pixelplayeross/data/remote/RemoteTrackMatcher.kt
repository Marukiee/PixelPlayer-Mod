package com.lostf1sh.pixelplayeross.data.remote

import java.text.Normalizer
import kotlin.math.abs

/** A song on the playing phone, as far as finding it goes. */
data class LocalCandidate(
    val id: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

/**
 * Finds the player's own copy of a song the controller sends. Both phones sync the same folder, so the end of the
 * path nearly always finds it; the tags are there for songs that moved, and for server songs without a file.
 */
class RemoteTrackMatcher(candidates: List<LocalCandidate>) {
    private val byTail3 = HashMap<String, MutableList<LocalCandidate>>()
    private val byTail2 = HashMap<String, MutableList<LocalCandidate>>()
    private val byFile = HashMap<String, MutableList<LocalCandidate>>()
    private val byTags = HashMap<String, MutableList<LocalCandidate>>()

    val size = candidates.size

    init {
        for (c in candidates) {
            RemoteTrack.pathTail(c.path, 3).takeIf { it.isNotEmpty() }?.let { byTail3.getOrPut(norm(it)) { ArrayList() } += c }
            RemoteTrack.pathTail(c.path, 2).takeIf { it.isNotEmpty() }?.let { byTail2.getOrPut(norm(it)) { ArrayList() } += c }
            RemoteTrack.pathTail(c.path, 1).takeIf { it.isNotEmpty() }?.let { byFile.getOrPut(norm(it)) { ArrayList() } += c }
            if (c.title.isNotBlank()) byTags.getOrPut(tagKey(c.title, c.artist)) { ArrayList() } += c
        }
    }

    fun match(track: RemoteTrack): LocalCandidate? {
        val tail = track.pathTail
        if (tail.isNotEmpty()) {
            val segments = tail.split('/')
            closest(byTail3[norm(segments.takeLast(3).joinToString("/"))], track, DURATION_ANY)?.let { return it }
            if (segments.size >= 2) {
                closest(byTail2[norm(segments.takeLast(2).joinToString("/"))], track, DURATION_ANY)?.let { return it }
            }
            closest(byFile[norm(segments.last())], track, FILE_TOLERANCE_MS)?.let { return it }
        }
        if (track.title.isBlank()) return null
        val sameTags = byTags[tagKey(track.title, track.artist)] ?: return null
        closest(sameTags, track, TAG_TOLERANCE_MS)?.let { return it }
        // Without a length to compare, the same title and artist is only trusted when there is one such song.
        return if (track.durationMs <= 0 && sameTags.size == 1) sameTags.first() else null
    }

    /** The candidate whose length is nearest to the song's, within [toleranceMs]; any one when the length is unknown. */
    private fun closest(list: List<LocalCandidate>?, track: RemoteTrack, toleranceMs: Long): LocalCandidate? {
        if (list.isNullOrEmpty()) return null
        if (track.durationMs <= 0) return list.first()
        val best = list.minBy { abs(it.durationMs - track.durationMs) }
        val off = abs(best.durationMs - track.durationMs)
        return if (toleranceMs == DURATION_ANY || best.durationMs <= 0 || off <= toleranceMs) best else null
    }

    companion object {
        private const val DURATION_ANY = -1L
        private const val FILE_TOLERANCE_MS = 3_000L
        private const val TAG_TOLERANCE_MS = 4_000L
        private val MARKS = Regex("\\p{Mn}+")
        private val SPACES = Regex("\\s+")

        fun norm(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(SPACES, " ").trim()

        private fun tagKey(title: String, artist: String) = norm(title) + "\u0000" + norm(artist)
    }
}
