package com.theveloper.pixelplay.data.sonos

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.database.NavidromeSongEntity
import com.theveloper.pixelplay.data.navidrome.NavidromeSongMatcher
import org.junit.Test

class SonosParsingTest {

    @Test
    fun envelope_escapesDidlOnceMoreSoTheSpeakerGetsTheOriginalBack() {
        val url = "http://10.0.0.5:4533/rest/stream.view?u=mark&t=abc&s=def&id=42"
        val didl = SonosTrackResolver.buildDidl(
            itemId = "pixelplay-42",
            title = "Känn ingen sorg för mig Göteborg",
            artist = "Håkan Hellström",
            album = "Känn ingen sorg & mer",
            artUri = null,
            uri = url,
            mimeType = "audio/flac",
            durationMs = 221_000
        )
        val envelope = SonosSoapClient.buildEnvelope(
            SonosService.AV_TRANSPORT,
            "AddURIToQueue",
            listOf("InstanceID" to 0, "EnqueuedURI" to url, "EnqueuedURIMetaData" to didl)
        )
        // Layer 1: the SOAP body parses and gives back the exact URL and DIDL text.
        val doc = SonosSoapClient.parseXml(envelope)
        assertThat(doc.getElementsByTagName("EnqueuedURI").item(0).textContent).isEqualTo(url)
        val metadata = doc.getElementsByTagName("EnqueuedURIMetaData").item(0).textContent
        assertThat(metadata).isEqualTo(didl)
        // Layer 2: the DIDL itself parses and holds the URL and the & in the album.
        val didlDoc = SonosSoapClient.parseXml(metadata)
        assertThat(didlDoc.getElementsByTagName("res").item(0).textContent).isEqualTo(url)
        assertThat(didlDoc.getElementsByTagNameNS("*", "album").item(0).textContent).isEqualTo("Känn ingen sorg & mer")
        assertThat(didlDoc.getElementsByTagName("res").item(0).attributes.getNamedItem("duration").nodeValue)
            .isEqualTo("0:03:41")
    }

    @Test
    fun parseResponse_readsActionResult() {
        val xml = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
            <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <Track>3</Track><TrackDuration>0:04:02</TrackDuration><TrackURI>http://x/stream?id=1&amp;u=m</TrackURI>
            <RelTime>0:01:05</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>"""
        val result = SonosSoapClient.parseResponse(xml, "GetPositionInfo")
        assertThat(result["Track"]).isEqualTo("3")
        assertThat(result["TrackURI"]).isEqualTo("http://x/stream?id=1&u=m")
        assertThat(SonosSoapClient.hmsToMs(result["RelTime"])).isEqualTo(65_000)
    }

    @Test
    fun parseFaultCode_readsUpnpError() {
        val xml = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>
            <faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>
            <UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>701</errorCode></UPnPError>
            </detail></s:Fault></s:Body></s:Envelope>"""
        assertThat(SonosSoapClient.parseFaultCode(xml)).isEqualTo(701)
    }

    @Test
    fun hms_roundTrips() {
        assertThat(SonosSoapClient.msToHms(3_725_400)).isEqualTo("1:02:05")
        assertThat(SonosSoapClient.hmsToMs("NOT_IMPLEMENTED")).isEqualTo(0)
        assertThat(SonosSoapClient.hmsToMs("0:00:07.250")).isEqualTo(7_000)
    }

    @Test
    fun parseZoneGroupState_skipsHiddenMembersAndPutsCoordinatorFirst() {
        val xml = """<ZoneGroupState><ZoneGroups>
            <ZoneGroup Coordinator="RINCON_B" ID="RINCON_B:12">
              <ZoneGroupMember UUID="RINCON_A" Location="http://192.168.1.20:1400/xml/device_description.xml" ZoneName="Kitchen"/>
              <ZoneGroupMember UUID="RINCON_B" Location="http://192.168.1.21:1400/xml/device_description.xml" ZoneName="Living room">
                <Satellite UUID="RINCON_S" Location="http://192.168.1.22:1400/xml/device_description.xml" ZoneName="Living room" Invisible="1"/>
              </ZoneGroupMember>
              <ZoneGroupMember UUID="RINCON_SUB" Location="http://192.168.1.23:1400/xml/device_description.xml" ZoneName="Living room" Invisible="1"/>
            </ZoneGroup>
            <ZoneGroup Coordinator="RINCON_C" ID="RINCON_C:3">
              <ZoneGroupMember UUID="RINCON_C" Location="http://192.168.1.24:1400/xml/device_description.xml" ZoneName="Bedroom"/>
            </ZoneGroup>
            </ZoneGroups><VanishedDevices/></ZoneGroupState>"""
        val groups = SonosDiscovery.parseZoneGroupState(xml)
        assertThat(groups.map { it.displayName }).containsExactly("Bedroom", "Living room + Kitchen").inOrder()
        val living = groups[1]
        assertThat(living.coordinator.host).isEqualTo("192.168.1.21:1400")
        assertThat(living.members.map { it.uuid }).containsExactly("RINCON_B", "RINCON_A").inOrder()
        assertThat(living.queueUri).isEqualTo("x-rincon-queue:RINCON_B#0")
    }

    @Test
    fun parseLocationHost_readsSsdpAnswer() {
        val answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age = 1800\r\n" +
            "LOCATION: http://192.168.1.21:1400/xml/device_description.xml\r\nST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n"
        assertThat(SonosDiscovery.parseLocationHost(answer)).isEqualTo("192.168.1.21:1400")
    }

    @Test
    fun tailscaleHosts_areRecognised() {
        assertThat(SonosTrackResolver.isTailscaleHost("100.101.5.9")).isTrue()
        assertThat(SonosTrackResolver.isTailscaleHost("nas.tail1234.ts.net")).isTrue()
        assertThat(SonosTrackResolver.isTailscaleHost("100.20.5.9")).isFalse()
        assertThat(SonosTrackResolver.isTailscaleHost("192.168.1.10")).isFalse()
    }

    @Test
    fun matchSong_findsTheRightVersion() {
        val songs = listOf(
            entity("1", "Känn ingen sorg för mig Göteborg", "Håkan Hellström", "Känn ingen sorg för mig Göteborg", 221_000),
            entity("2", "Känn ingen sorg för mig Göteborg", "Håkan Hellström", "Live at Ullevi", 260_000),
            entity("3", "Ängel", "Kent", "Isola", 245_000)
        )
        val byTitle = songs.groupBy { NavidromeSongMatcher.normalize(it.title) }
        val studio = SonosTrackResolver.matchSong(
            "Kann ingen sorg for mig Goteborg", "Hakan Hellstrom", "Känn ingen sorg för mig Göteborg", 221_500, byTitle
        )
        assertThat(studio?.navidromeId).isEqualTo("1")
        val live = SonosTrackResolver.matchSong(
            "Känn ingen sorg för mig Göteborg (Live)", "Håkan Hellström", "Live at Ullevi", 259_000, byTitle
        )
        assertThat(live?.navidromeId).isEqualTo("2")
        assertThat(SonosTrackResolver.matchSong("Ängel", "Someone else", "", 0, byTitle)).isEqualTo(songs[2])
        assertThat(SonosTrackResolver.matchSong("Unknown song", "Kent", "", 0, byTitle)).isNull()
    }

    @Test
    fun nearestIndexOf_prefersTheClosestCopy() {
        val queue = listOf("a", "b", "a", "c").map { SonosTrack(it, "u-$it", "") }
        assertThat(SonosController.nearestIndexOf(queue, "a", 3)).isEqualTo(2)
        assertThat(SonosController.nearestIndexOf(queue, "a", 0)).isEqualTo(0)
        assertThat(SonosController.nearestIndexOf(queue, "z", 1)).isEqualTo(-1)
    }

    @Test
    fun sameStream_comparesTheSubsonicIdOnly() {
        val queued = "http://10.0.0.5:4533/rest/stream.view?u=mark&t=abc&s=def&v=1.16.1&c=PixelPlayer&id=al-42"
        val reordered = "http://10.0.0.5:4533/rest/stream.view?id=al%2D42&c=PixelPlayer&u=mark&t=abc&s=def&v=1.16.1"
        val other = "http://10.0.0.5:4533/rest/stream.view?u=mark&t=xyz&s=uvw&id=al-43"
        assertThat(SonosController.subsonicId(queued)).isEqualTo("al-42")
        assertThat(SonosController.sameStream(queued, reordered)).isTrue()
        assertThat(SonosController.sameStream(queued, other)).isFalse()
        // Unknown shapes never count as a mismatch.
        assertThat(SonosController.sameStream(queued, "x-sonos-http:track.mp3")).isTrue()
        assertThat(SonosController.sameStream(queued, "")).isTrue()
    }

    @Test
    fun homeAddress_isCleanedUpAndSwappedIntoSonosUrls() {
        assertThat(SonosTrackResolver.normalizeAddress("192.168.1.157:4533/")).isEqualTo("http://192.168.1.157:4533")
        assertThat(SonosTrackResolver.normalizeAddress(" https://music.home/navidrome ")).isEqualTo("https://music.home/navidrome")
        assertThat(SonosTrackResolver.normalizeAddress("")).isNull()
        assertThat(SonosTrackResolver.normalizeAddress("http://")).isNull()

        val login = "http://100.86.14.39:4533"
        val home = "http://192.168.1.157:4533"
        val stream = "$login/rest/stream.view?u=mark&t=abc&s=def&id=42"
        assertThat(SonosTrackResolver.rebase(stream, login, home))
            .isEqualTo("$home/rest/stream.view?u=mark&t=abc&s=def&id=42")
        // A longer host that only starts the same is left alone.
        assertThat(SonosTrackResolver.rebase("http://100.86.14.39:45330/rest/x", login, home))
            .isEqualTo("http://100.86.14.39:45330/rest/x")
    }

    @Test
    fun coverAddress_mustBeHttps() {
        assertThat(SonosTrackResolver.normalizeCoverAddress("cover.example.com/")).isEqualTo("https://cover.example.com")
        assertThat(SonosTrackResolver.normalizeCoverAddress(" https://cover.example.com ")).isEqualTo("https://cover.example.com")
        assertThat(SonosTrackResolver.normalizeCoverAddress("http://cover.example.com")).isNull()
        assertThat(SonosTrackResolver.normalizeCoverAddress("")).isNull()

        val login = "http://100.86.14.39:4533"
        assertThat(SonosTrackResolver.rebase("$login/rest/getCoverArt.view?u=mark&id=42", login, "https://cover.example.com"))
            .isEqualTo("https://cover.example.com/rest/getCoverArt.view?u=mark&id=42")
    }

    @Test
    fun groupVolume_loudRoomMovesFasterAndQuietRoomStaysOn() {
        val base = mapOf("a" to 50, "b" to 10)
        assertThat(SonosController.groupVolumeTargets(base, 20)).isEqualTo(mapOf("a" to 33, "b" to 7))
        assertThat(SonosController.groupVolumeTargets(base, 30)).isEqualTo(base)
        assertThat(SonosController.groupVolumeTargets(base, 0)).isEqualTo(mapOf("a" to 0, "b" to 0))
        // The loud room stops at 100, the quiet one catches up.
        assertThat(SonosController.groupVolumeTargets(base, 70)).isEqualTo(mapOf("a" to 100, "b" to 40))
        assertThat(SonosController.groupVolumeTargets(base, 100)).isEqualTo(mapOf("a" to 100, "b" to 100))
        assertThat(SonosController.groupVolumeTargets(mapOf("a" to 0, "b" to 0), 12)).isEqualTo(mapOf("a" to 12, "b" to 12))
    }

    @Test
    fun speakerOffset_narrowsDownFromWholeSeconds() {
        // The speaker really is 640 ms ahead; it reports whole seconds, polled every 1150 ms.
        val estimate = SpeakerOffsetEstimate()
        var result: Long? = null
        var polls = 0
        var phone = 10_000L
        while (result == null && polls < 20) {
            val speaker = phone + 640
            result = estimate.add(speaker / 1000 * 1000, phone)
            phone += 1_150
            polls++
        }
        assertThat(polls).isAtMost(8)
        assertThat(result!!).isIn(com.google.common.collect.Range.closed(640L - 250, 640L + 250))
        // A jump of a clock starts over instead of giving nonsense.
        assertThat(estimate.add(90_000, 10_000)).isNull()
    }

    private fun entity(id: String, title: String, artist: String, album: String, durationMs: Long) = NavidromeSongEntity(
        id = id,
        navidromeId = id,
        playlistId = "__library__",
        title = title,
        artist = artist,
        artistId = null,
        album = album,
        albumId = null,
        coverArtId = null,
        duration = durationMs,
        trackNumber = 1,
        discNumber = 1,
        year = 0,
        genre = null,
        bitRate = null,
        mimeType = "audio/flac",
        suffix = "flac",
        path = "",
        dateAdded = 0
    )
}
