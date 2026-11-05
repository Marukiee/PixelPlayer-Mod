package com.theveloper.pixelplay.data.navidrome

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NavidromeScrobbleRulesTest {
    private val minute = 60_000L

    @Test
    fun `half a song counts, less does not`() {
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 2 * minute, durationMs = 4 * minute)).isTrue()
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 2 * minute - 1, durationMs = 4 * minute)).isFalse()
    }

    @Test
    fun `skipping after a few seconds does not count`() {
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 6_000, durationMs = 3 * minute)).isFalse()
    }

    @Test
    fun `four minutes is enough for a long song`() {
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 4 * minute, durationMs = 60 * minute)).isTrue()
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 4 * minute - 1, durationMs = 60 * minute)).isFalse()
    }

    @Test
    fun `a short song counts like any other`() {
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 10_000, durationMs = 20_000)).isTrue()
    }

    @Test
    fun `an unknown length asks for half a minute`() {
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 30_000, durationMs = 0)).isTrue()
        assertThat(NavidromeScrobbleRules.counts(listenedMs = 29_999, durationMs = 0)).isFalse()
    }

    @Test
    fun `the server's own songs need no matching`() {
        assertThat(NavidromeScrobbleRules.serverIdOfServerSong("navidrome_abc", emptyMap())).isEqualTo("abc")
        assertThat(NavidromeScrobbleRules.serverIdOfServerSong("123", emptyMap())).isNull()
    }

    @Test
    fun `a song from a playlist goes by its stored row`() {
        val rows = mapOf("list7_abc" to "abc")
        assertThat(NavidromeScrobbleRules.serverIdOfServerSong("navidrome_list7_abc", rows)).isEqualTo("abc")
    }

    @Test
    fun `songs from other sources stay out`() {
        assertThat(NavidromeScrobbleRules.canBeOnServer("navidrome_abc")).isTrue()
        assertThat(NavidromeScrobbleRules.canBeOnServer("123")).isTrue()
        assertThat(NavidromeScrobbleRules.canBeOnServer("-123")).isTrue()
        assertThat(NavidromeScrobbleRules.canBeOnServer("telegram_123")).isFalse()
    }

    @Test
    fun `a listen survives being written down`() {
        val scrobble = PendingScrobble("navidrome_a:b", 1_700_000_000_000)
        assertThat(PendingScrobble.decode(scrobble.encode())).isEqualTo(scrobble)
    }

    @Test
    fun `a broken entry is ignored`() {
        assertThat(PendingScrobble.decode("nonsense")).isNull()
        assertThat(PendingScrobble.decode("12:")).isNull()
        assertThat(PendingScrobble.decode(":abc")).isNull()
    }

    @Test
    fun `no connection or a failed login keeps the listen`() {
        assertThat(NavidromeScrobbleRules.keepAfter(Exception("timeout"))).isTrue()
        assertThat(NavidromeScrobbleRules.keepAfter(Exception("HTTP 502: Bad Gateway"))).isTrue()
        assertThat(NavidromeScrobbleRules.keepAfter(Exception("API Error 40: Wrong username or password"))).isTrue()
        assertThat(NavidromeScrobbleRules.keepAfter(Exception("API Error 50: Not authorized"))).isTrue()
    }

    @Test
    fun `a refused listen does not hold up the rest`() {
        assertThat(NavidromeScrobbleRules.keepAfter(Exception("API Error 70: Song not found"))).isFalse()
        assertThat(NavidromeScrobbleRules.keepAfter(Exception("API Error 0: Internal error"))).isFalse()
    }

    @Test
    fun `too many waiting drops the oldest`() {
        val pending = (1L..(NavidromeScrobbleRules.MOST_PENDING + 2L)).map { PendingScrobble("1", it) }.toSet()
        val trimmed = NavidromeScrobbleRules.trimmed(pending)
        assertThat(trimmed).hasSize(NavidromeScrobbleRules.MOST_PENDING)
        assertThat(trimmed.minOf { it.startedAtMs }).isEqualTo(3L)
    }
}
