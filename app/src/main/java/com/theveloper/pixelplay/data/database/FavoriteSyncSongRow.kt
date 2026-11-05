package com.theveloper.pixelplay.data.database

/** The little of a song that matching it with a server needs, so a whole library stays light to load. */
data class FavoriteSyncSongRow(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String
)
