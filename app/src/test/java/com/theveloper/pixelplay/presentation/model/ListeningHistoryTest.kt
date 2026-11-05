package com.theveloper.pixelplay.presentation.model

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackHistoryEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ListeningHistoryTest {
    private fun song(id: String) = Song(
        id = id, title = id, artist = "A", artistId = 1, album = "A", albumId = 1, path = "", contentUriString = "",
        albumArtUriString = null, duration = 1000, mimeType = null, bitrate = null, sampleRate = null
    )

    private fun at(day: Int, hour: Int) =
        LocalDate.of(2026, 11, day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val songs = listOf("a", "b").associateWith { song(it) }

    private fun build(vararg entries: Pair<String, Long>) = buildHistoryDays(
        entries.map { PlaybackHistoryEntry(it.first, it.second) },
        songs,
        ZoneOffset.UTC
    )

    @Test
    fun `newest day first and the last played song first`() {
        val days = build("a" to at(1, 10), "b" to at(2, 9), "a" to at(2, 12))
        assertEquals(listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)), days.map { it.date })
        assertEquals(listOf("a", "b"), days[0].plays.map { it.song.id })
    }

    @Test
    fun `a song played twice on a day is one row with a count and the last time`() {
        val day = build("a" to at(1, 10), "a" to at(1, 14)).single()
        val play = day.plays.single()
        assertEquals(2, play.playCount)
        assertEquals(at(1, 14), play.lastPlayedAt)
    }

    @Test
    fun `a song stays on every day it was played`() {
        val days = build("a" to at(1, 10), "a" to at(2, 10))
        assertEquals(listOf(1, 1), days.map { it.plays.size })
    }

    @Test
    fun `songs no longer in the library are left out, and so is a day with nothing left`() {
        assertEquals(emptyList<HistoryDay>(), build("gone" to at(1, 10)))
    }
}
