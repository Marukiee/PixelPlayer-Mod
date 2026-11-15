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

/**
 * Swaps every server song for its copy on the phone when [library] has one, then leaves one row per recording. A
 * mix picks from the whole library, so it can pick only the server copy; this still plays the local file, which
 * needs no connection. A server song with no local copy stays.
 */
fun List<Song>.preferLocalCopies(library: List<Song>): List<Song> {
    if (none { it.isRemote }) return withoutDuplicateTracks()
    val localByTitle = HashMap<String, MutableList<Song>>()
    for (song in library) {
        if (!song.isRemote) localByTitle.getOrPut(SearchTextNormalizer.fold(song.title)) { mutableListOf() } += song
    }
    return map { song ->
        if (!song.isRemote) song
        else localByTitle[SearchTextNormalizer.fold(song.title)]?.firstOrNull { it.isSameRecordingAs(song) } ?: song
    }.withoutDuplicateTracks()
}

/**
 * Swaps every server song for its copy on the phone from [candidates], keeping the order and the length of the
 * list. Only a server row whose local copy is already in the list, or was swapped in earlier, is dropped, so the
 * queue never holds the same file twice. Other repeats stay, such as a song a playlist holds twice on purpose.
 */
fun List<Song>.withLocalCopiesFrom(candidates: List<Song>): List<Song> {
    if (candidates.isEmpty() || none { it.isRemote }) return this
    val localByTitle = HashMap<String, MutableList<Song>>()
    for (song in candidates) {
        if (!song.isRemote) localByTitle.getOrPut(SearchTextNormalizer.fold(song.title)) { mutableListOf() } += song
    }
    val idsInList = mapTo(HashSet()) { it.id }
    val swappedIn = HashSet<String>()
    return mapNotNull { song ->
        if (!song.isRemote) return@mapNotNull song
        val local = localByTitle[SearchTextNormalizer.fold(song.title)]?.firstOrNull { it.isSameRecordingAs(song) }
        when {
            local == null -> song
            local.id in idsInList || !swappedIn.add(local.id) -> null
            else -> local
        }
    }
}

private fun Song.isSameRecordingAs(other: Song): Boolean {
    val artistMatches = NavidromeSongMatcher.artistMatches(
        SearchTextNormalizer.fold(artist),
        SearchTextNormalizer.fold(other.artist)
    )
    return artistMatches && abs(duration - other.duration) <= SAME_RECORDING_MS
}

private val Song.isRemote: Boolean get() = !contentUriString.startsWith("content://")
