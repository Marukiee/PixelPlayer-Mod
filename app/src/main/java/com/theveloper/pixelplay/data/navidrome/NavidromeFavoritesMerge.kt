package com.theveloper.pixelplay.data.navidrome

import com.theveloper.pixelplay.utils.SearchTextNormalizer
import kotlin.math.abs

/** A song in the app's library, as far as matching and liking need it. */
internal data class FavoriteLocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Set when the song is the server's own, so it needs no matching. */
    val navidromeId: String?
)

internal data class FavoriteServerSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

/**
 * Finds the same song on both sides. Local files and the server share no id, only their tags.
 *
 * Going to the server a remaster may still match the original, because one like more is no harm. Coming back it has
 * to be the same title, or a star on a live version would also like the studio version.
 */
internal class FavoriteSongIndex(serverSongs: List<FavoriteServerSong>, localSongs: List<FavoriteLocalSong>) {
    private val serverByTitle = serverSongs.groupBy { fold(it.title) }
    private val serverByLooseTitle = serverSongs.groupBy { loose(it.title) }
    private val localByTitle = localSongs.filter { it.navidromeId == null }.groupBy { fold(it.title) }
    private val localByServerId = localSongs.filter { it.navidromeId != null }.groupBy { it.navidromeId }

    fun serverIdFor(song: FavoriteLocalSong): String? {
        song.navidromeId?.let { return it }
        val title = fold(song.title)
        if (title.isBlank()) return null
        val exact = serverByTitle[title].orEmpty().filter {
            NavidromeSongMatcher.artistMatches(fold(it.artist), fold(song.artist))
        }
        val pool = exact.ifEmpty {
            val looseTitle = loose(song.title)
            if (looseTitle.isBlank()) return@ifEmpty emptyList()
            serverByLooseTitle[looseTitle].orEmpty().filter {
                NavidromeSongMatcher.artistMatches(loose(it.artist), loose(song.artist))
            }
        }
        val album = fold(song.album)
        return pool.filter { fold(it.album) == album }.ifEmpty { pool }
            .minByOrNull { abs(it.durationMs - song.durationMs) }
            ?.id
    }

    /** Every song in the app that is this star: the server's own entry and any local copy of it. */
    fun localsFor(star: FavoriteServerSong): Set<Long> {
        val sameSong = localByTitle[fold(star.title)].orEmpty().filter {
            NavidromeSongMatcher.artistMatches(fold(it.artist), fold(star.artist))
        }
        val album = fold(star.album)
        val copies = sameSong.filter { fold(it.album) == album }.ifEmpty { sameSong }
        return (localByServerId[star.id].orEmpty() + copies).map { it.id }.toSet()
    }

    private fun fold(value: String) = SearchTextNormalizer.fold(value)
    private fun loose(value: String) = NavidromeSongMatcher.normalize(value)
}

/**
 * Decides what a sync changes. [base] holds the songs both sides agreed on after the last sync: without it a song
 * missing on one side could be new on the other or removed here, and guessing wrong loses likes.
 *
 * So a first sync, with an empty base, only ever adds. Later on only a song from the base can be removed, and only
 * because the server dropped its star. An unlike in the app is not worked out here: it arrives as its own event,
 * since a song that left the library (a rescan, a logout) must not count as one.
 */
internal object NavidromeFavoritesMerge {
    /** Below this many a change is small enough to be somebody's choice. */
    private const val SERVER_LOSS_MIN = 20

    data class Plan(
        val toStar: Set<String>,
        val toLike: Set<Long>,
        val toUnlike: Set<Long>,
        val removedOnServer: Set<String>,
        /** Most of the base vanished at once, so the likes were kept and go back to the server. */
        val serverLost: Boolean
    )

    /**
     * @param liked the liked songs in the app that have a match on the server, with that match
     * @param starred what the server has starred right now
     * @param likeTargets for each star, the songs in the app that are that song
     */
    fun plan(
        liked: Map<Long, String>,
        starred: Set<String>,
        base: Set<String>,
        likeTargets: Map<String, Set<Long>>
    ): Plan {
        val likedOnServer = liked.values.toSet()
        val gone = (likedOnServer intersect base) - starred
        // Navidrome derives its ids from file paths, so moving the music renames everything. Stars gone from a new
        // database look the same. Neither is somebody unstarring half their library.
        val serverLost = gone.size >= SERVER_LOSS_MIN && gone.size * 2 > base.size
        val removed = if (serverLost) emptySet() else gone
        return Plan(
            toStar = likedOnServer - starred - removed,
            toLike = starred.flatMap { likeTargets[it].orEmpty() }.toSet() - liked.keys,
            toUnlike = liked.filterValues { it in removed }.keys,
            removedOnServer = removed,
            serverLost = serverLost
        )
    }

    /** The songs liked on both sides once a plan has been carried out. A star that failed is not in [starredNow]. */
    fun nextBase(
        liked: Map<Long, String>,
        starredNow: Set<String>,
        likeTargets: Map<String, Set<Long>>
    ): Set<String> {
        val pulled = likeTargets.filterValues { it.isNotEmpty() }.keys
        return (liked.values.toSet() + pulled) intersect starredNow
    }
}
