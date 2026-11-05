package com.theveloper.pixelplay.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SongDuplicatesTest {
    private fun song(id: String, title: String = "Sommaren i City", artist: String = "Kent", ms: Long = 200_000, uri: String = "content://media/$id") =
        Song(
            id = id, title = title, artist = artist, artistId = 1, album = "A", albumId = 1, path = "", contentUriString = uri,
            albumArtUriString = null, duration = ms, mimeType = null, bitrate = null, sampleRate = null
        )

    private fun List<Song>.ids() = map { it.id }

    @Test
    fun `the file wins from the server's copy, wherever it stands`() {
        val songs = listOf(song("server", uri = "navidrome://x", ms = 200_400), song("other", title = "Else"), song("file"))
        assertEquals(listOf("file", "other"), songs.withoutDuplicateTracks().ids())
    }

    @Test
    fun `two files of one song become one row`() {
        assertEquals(listOf("a"), listOf(song("a"), song("b", ms = 201_000)).withoutDuplicateTracks().ids())
    }

    @Test
    fun `accents and case do not make it another song`() {
        val songs = listOf(song("a", title = "Äventyr i natten"), song("b", title = "aventyr I NATTEN", artist = "KENT"))
        assertEquals(listOf("a"), songs.withoutDuplicateTracks().ids())
    }

    @Test
    fun `a clearly different length is another recording`() {
        assertEquals(listOf("a", "live"), listOf(song("a"), song("live", ms = 260_000)).withoutDuplicateTracks().ids())
    }

    @Test
    fun `the same title by someone else stays`() {
        assertEquals(listOf("a", "b"), listOf(song("a"), song("b", artist = "Abba")).withoutDuplicateTracks().ids())
    }
}
