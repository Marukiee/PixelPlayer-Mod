package com.theveloper.pixelplay.presentation.model

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A song on one day: played [playCount] times, the last time at [lastPlayedAt]. */
data class HistoryPlay(
    val song: Song,
    val lastPlayedAt: Long,
    val playCount: Int
)

data class HistoryDay(
    val date: LocalDate,
    val plays: List<HistoryPlay>
)

/** Newest day first, and within a day the song played last first. Songs no longer in the library are left out. */
fun buildHistoryDays(
    entries: List<PlaybackHistoryEntry>,
    songsById: Map<String, Song>,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<HistoryDay> {
    return entries
        .filter { it.songId in songsById }
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, dayEntries) ->
            val plays = dayEntries.groupBy { it.songId }.map { (songId, songEntries) ->
                HistoryPlay(
                    song = songsById.getValue(songId),
                    lastPlayedAt = songEntries.maxOf { it.timestamp },
                    playCount = songEntries.size
                )
            }
            HistoryDay(date, plays.sortedByDescending { it.lastPlayedAt })
        }
}
