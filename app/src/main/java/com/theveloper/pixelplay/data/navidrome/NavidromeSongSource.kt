package com.theveloper.pixelplay.data.navidrome

import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.NavidromeDao
import javax.inject.Inject

/** The songs on both sides, as [FavoriteSongIndex] needs them to find the same song. */
class NavidromeSongSource @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val navidromeDao: NavidromeDao,
    private val musicDao: MusicDao
) {
    internal suspend fun localSongs(): List<FavoriteLocalSong> = musicDao.getSongsForFavoritesSync().map {
        FavoriteLocalSong(
            id = it.id,
            title = it.title,
            artist = it.artist,
            album = it.album,
            durationMs = it.durationMs,
            navidromeId = it.contentUri.takeIf { uri -> uri.startsWith(NAVIDROME_URI) }?.removePrefix(NAVIDROME_URI)
        )
    }

    internal suspend fun serverSongs(): List<FavoriteServerSong> {
        if (navidromeDao.getLibrarySongCount() == 0) navidromeRepository.syncLibrarySongs()
        return navidromeDao.getAllNavidromeSongsList().distinctBy { it.navidromeId }.map {
            FavoriteServerSong(it.navidromeId, it.title, it.artist, it.album, it.duration)
        }
    }

    /** A song from one of the server's playlists goes by the row it is stored in, not by the server's id. */
    internal suspend fun serverIdsByRow(): Map<String, String> =
        navidromeDao.getAllNavidromeSongsList().associate { it.id to it.navidromeId }

    private companion object {
        const val NAVIDROME_URI = "navidrome://"
    }
}
