package com.theveloper.pixelplay.data.model

import com.theveloper.pixelplay.data.navidrome.NavidromeSongMatcher
import com.theveloper.pixelplay.utils.SearchTextNormalizer
import kotlin.math.abs

private const val SAME_RECORDING_MS = 3_000L

/**
 * One row per recording. A song can be in the library more than once: as a file and from the server, or in two
 * folders. A local file wins, because it plays without a connection. Same title and artist with a clearly different
 * length is another recording, such as a live version, and stays. The order of the list stays too.
 */
fun List<Song>.withoutDuplicateTracks(): List<Song> {
    val kept = ArrayList<Song>(size)
    val keptByTitle = HashMap<String, MutableList<Int>>()
    for (song in this) {
        val sameTitle = keptByTitle.getOrPut(SearchTextNormalizer.fold(song.title)) { mutableListOf() }
        val duplicateOf = sameTitle.firstOrNull { kept[it].isSameRecordingAs(song) }
        when {
            duplicateOf == null -> {
                sameTitle += kept.size
                kept += song
            }
            kept[duplicateOf].isRemote && !song.isRemote -> kept[duplicateOf] = song
        }
    }
    return kept
}

private fun Song.isSameRecordingAs(other: Song): Boolean {
    val artistMatches = NavidromeSongMatcher.artistMatches(
        SearchTextNormalizer.fold(artist),
        SearchTextNormalizer.fold(other.artist)
    )
    return artistMatches && abs(duration - other.duration) <= SAME_RECORDING_MS
}

private val Song.isRemote: Boolean get() = !contentUriString.startsWith("content://")
