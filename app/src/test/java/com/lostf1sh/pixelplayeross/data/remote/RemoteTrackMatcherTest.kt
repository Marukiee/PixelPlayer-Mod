package com.lostf1sh.pixelplayeross.data.remote

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class RemoteTrackMatcherTest {

    // The playing phone keeps the Syncthing folder somewhere else than the controlling phone.
    private val local = listOf(
        LocalCandidate("1", "/storage/emulated/0/Syncthing/Music/Dire Straits/Brothers in Arms/02 Money for Nothing.flac", "Money for Nothing", "Dire Straits", "Brothers in Arms", 504_000),
        LocalCandidate("2", "/storage/emulated/0/Syncthing/Music/Dire Straits/Brothers in Arms/01 So Far Away.flac", "So Far Away", "Dire Straits", "Brothers in Arms", 312_000),
        LocalCandidate("3", "/storage/emulated/0/Syncthing/Music/Sigur Rós/Takk/03 Hoppípolla.flac", "Hoppípolla", "Sigur Rós", "Takk", 268_000),
        // The same file name in two albums.
        LocalCandidate("4", "/storage/emulated/0/Syncthing/Music/Artist A/Album A/01 Intro.mp3", "Intro", "Artist A", "Album A", 60_000),
        LocalCandidate("5", "/storage/emulated/0/Syncthing/Music/Artist B/Album B/01 Intro.mp3", "Intro", "Artist B", "Album B", 95_000)
    )
    private val matcher = RemoteTrackMatcher(local)

    private fun track(path: String?, title: String = "", artist: String = "", duration: Long = 0) = RemoteTrack(
        id = "x",
        pathTail = RemoteTrack.pathTail(path),
        title = title,
        artist = artist,
        album = "",
        durationMs = duration
    )

    @Test
    fun `finds the same file in another root folder`() {
        val found = matcher.match(track("/storage/emulated/0/Music/Dire Straits/Brothers in Arms/02 Money for Nothing.flac"))
        assertThat(found?.id).isEqualTo("1")
    }

    @Test
    fun `tells apart files with the same name by their folders`() {
        assertThat(matcher.match(track("/sdcard/Music/Artist B/Album B/01 Intro.mp3"))?.id).isEqualTo("5")
        assertThat(matcher.match(track("/sdcard/Music/Artist A/Album A/01 Intro.mp3"))?.id).isEqualTo("4")
    }

    @Test
    fun `a moved album is found by folder and file name`() {
        val found = matcher.match(track("/sdcard/Other/Brothers in Arms/01 So Far Away.flac"))
        assertThat(found?.id).isEqualTo("2")
    }

    @Test
    fun `a server song without a file is found by its tags`() {
        val found = matcher.match(track(null, title = "Hoppipolla", artist = "Sigur Ros", duration = 269_500))
        assertThat(found?.id).isEqualTo("3")
    }

    @Test
    fun `tags with a very different length do not match`() {
        assertThat(matcher.match(track(null, title = "Money for Nothing", artist = "Dire Straits", duration = 240_000))).isNull()
    }

    @Test
    fun `a song that is not there is not found`() {
        assertThat(matcher.match(track("/sdcard/Music/Queen/Innuendo/01 Innuendo.flac", "Innuendo", "Queen", 391_000))).isNull()
    }

    @Test
    fun `a file name alone needs about the same length`() {
        assertThat(matcher.match(track("/x/01 Intro.mp3", duration = 95_500))?.id).isEqualTo("5")
        assertThat(matcher.match(track("/x/01 Intro.mp3", duration = 30_000))).isNull()
    }

    @Test
    fun `path tail keeps the last three parts and skips urls`() {
        assertThat(RemoteTrack.pathTail("/storage/emulated/0/Music/A/B/c.flac")).isEqualTo("A/B/c.flac")
        assertThat(RemoteTrack.pathTail("navidrome://123")).isEmpty()
        assertThat(RemoteTrack.pathTail(null)).isEmpty()
    }

    @Test
    fun `a track survives the trip as json`() {
        val sent = RemoteTrack("42", "A/B/c.flac", "Title", "Artist", "Album", 123_000)
        val received = RemoteTrack.fromJson(JSONObject(sent.toJson().toString()))
        assertThat(received).isEqualTo(sent)
    }

    @Test
    fun `state survives the trip as json`() {
        val sent = RemotePlayerState(7L, "42", false, 61_000, 200_000, true, true, 40, 15)
        val received = RemoteProtocol.parseState(JSONObject(RemoteProtocol.state(sent).toString()))
        assertThat(received).isEqualTo(sent)
        val idle = RemotePlayerState(0L, null, false, 0, 0, false, false, null, 0)
        assertThat(RemoteProtocol.parseState(JSONObject(RemoteProtocol.state(idle).toString())).currentId).isNull()
    }

    @Test
    fun `nearest index prefers the copy closest to where it was`() {
        val ids = listOf("a", "b", "a", "c", "a")
        assertThat(RemotePlayerController.nearestIndexOf(ids, "a", 3)).isEqualTo(2)
        assertThat(RemotePlayerController.nearestIndexOf(ids, "a", 4)).isEqualTo(4)
        assertThat(RemotePlayerController.nearestIndexOf(ids, "z", 0)).isEqualTo(-1)
    }
}
