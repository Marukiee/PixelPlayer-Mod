package com.theveloper.pixelplay.data.spotify

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.sonos.SonosService
import com.theveloper.pixelplay.data.sonos.SonosSoapClient
import com.theveloper.pixelplay.data.sonos.SonosTrackResolver
import org.junit.Test

class SpotifyPartsTest {

    @Test
    fun pkceChallenge_matchesTheExampleOfTheStandard() {
        // RFC 7636, appendix B.
        assertThat(Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
            .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    }

    @Test
    fun silence_isAPlainWavOfTheRightLength() {
        val dataBytes = SilentWav.dataBytes(213_000)
        assertThat(dataBytes).isEqualTo(213L * 8_000 * 2)
        val header = SilentWav.header(dataBytes)
        assertThat(header.size).isEqualTo(SilentWav.HEADER_SIZE)
        assertThat(String(header, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(String(header, 8, 4, Charsets.US_ASCII)).isEqualTo("WAVE")
        assertThat(String(header, 36, 4, Charsets.US_ASCII)).isEqualTo("data")
        fun int32(at: Int) = (0 until 4).sumOf { (header[at + it].toLong() and 0xFF) shl (8 * it) }
        assertThat(int32(24)).isEqualTo(8_000L)
        assertThat(int32(40)).isEqualTo(dataBytes)
        assertThat(int32(4)).isEqualTo(36 + dataBytes)
    }

    @Test
    fun songIds_stayInARangeOfTheirOwn() {
        val id = SpotifyIds.songId(SpotifyItem(SpotifyItem.TRACK, "4uLU6hMCjMI75M1A2tKUQC"))
        val episode = SpotifyIds.songId(SpotifyItem(SpotifyItem.EPISODE, "4uLU6hMCjMI75M1A2tKUQC"))
        assertThat(SpotifyIds.isSpotifySongId(episode)).isTrue()
        assertThat(episode).isNotEqualTo(id)
        assertThat(SpotifyIds.isSpotifySongId(id)).isTrue()
        assertThat(id.toLong()).isLessThan(-20_000_000_000_000L + 1)
        // A Navidrome song lies between 9 and 9.002 trillion below zero.
        assertThat(SpotifyIds.isSpotifySongId((-9_000_001_234_567L).toString())).isFalse()
        assertThat(SpotifyIds.isSpotifySongId("1000000304")).isFalse()
    }

    @Test
    fun sonosGetsTheSameSpotifyItemThatPlayedOnMarksSpeakers() {
        val track = SonosTrackResolver.spotifyTrack("-20000123", SpotifyItem(SpotifyItem.TRACK, "4uLU6hMCjMI75M1A2tKUQC"))
        val episode = SonosTrackResolver.spotifyTrack("-20000124", SpotifyItem(SpotifyItem.EPISODE, "512ojhOuo1ktJprKbVcKyQ"))
        assertThat(episode.uri).isEqualTo("spotify%3aepisode%3a512ojhOuo1ktJprKbVcKyQ")
        assertThat(episode.metadata).contains("<item id=\"00032020spotify%3aepisode%3a512ojhOuo1ktJprKbVcKyQ\"")
        assertThat(track.uri).isEqualTo("spotify%3atrack%3a4uLU6hMCjMI75M1A2tKUQC")
        assertThat(track.metadata).contains("<item id=\"00032020spotify%3atrack%3a4uLU6hMCjMI75M1A2tKUQC\" parentID=\"-1\"")
        assertThat(track.metadata).contains("SA_RINCON2311_X_#Svc2311-0-Token")
        assertThat(track.metadata).contains("object.item.audioItem.musicTrack")
        val envelope = SonosSoapClient.buildEnvelope(
            SonosService.AV_TRANSPORT,
            "AddURIToQueue",
            listOf("InstanceID" to 0, "EnqueuedURI" to track.uri, "EnqueuedURIMetaData" to track.metadata)
        )
        assertThat(envelope).contains("<EnqueuedURI>spotify%3atrack%3a4uLU6hMCjMI75M1A2tKUQC</EnqueuedURI>")
        assertThat(envelope).contains("&lt;DIDL-Lite")
    }
}
