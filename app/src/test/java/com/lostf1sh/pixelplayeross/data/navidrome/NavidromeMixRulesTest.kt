package com.lostf1sh.pixelplayeross.data.navidrome

import com.lostf1sh.pixelplayeross.data.navidrome.model.NavidromePlaylist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NavidromeMixRulesTest {
    private val today = LocalDate.of(2026, 11, 26)

    private fun playlist(
        id: String,
        name: String,
        comment: String?,
        owner: String? = "mark",
        created: Long = 0L,
    ) = NavidromePlaylist(id = id, name = name, comment = comment, owner = owner, created = created)

    @Test
    fun `the marker is written and read back`() {
        val comment = NavidromeMixRules.comment(SharedMix.DAILY, today)
        assertEquals("Made for you, refreshed every day. [mix:daily::2026-11-26]", comment)
        assertEquals(MixMarker("daily", "", today), NavidromeMixRules.marker("Daily Mix", comment))
    }

    @Test
    fun `other apps' kinds are mixes too but not PixelPlayer's`() {
        val spotCopy = playlist("1", "Daily Mix 1", "Made for you. [mix:daily:1:2026-11-26]")
        assertTrue(NavidromeMixRules.isMix(spotCopy))
        assertEquals(emptyList<NavidromePlaylist>(), NavidromeMixRules.playlistsFor(SharedMix.DAILY, listOf(spotCopy), "mark"))
    }

    @Test
    fun `the Mac's old Your Mix comment counts as today's Your Mix`() {
        val mac = playlist("7", "Your Mix", "PixelPlayer daily mix 2026-11-26")
        assertEquals(MixMarker("your", "", today), NavidromeMixRules.marker(mac.name, mac.comment))
        assertEquals(listOf(mac), NavidromeMixRules.playlistsFor(SharedMix.YOUR, listOf(mac), "mark"))
        assertTrue(NavidromeMixRules.isFrom(mac, today))
    }

    @Test
    fun `the old comment only counts on a playlist called Your Mix`() {
        assertNull(NavidromeMixRules.marker("Road trip", "PixelPlayer daily mix 2026-11-26"))
    }

    @Test
    fun `a playlist without a marker stays the user's`() {
        val own = playlist("2", "Daily Mix", "my own picks")
        assertFalse(NavidromeMixRules.isMix(own))
        assertNull(NavidromeMixRules.marker("Daily Mix", null))
        assertNull(NavidromeMixRules.marker("Daily Mix", "[mix:daily::2026-13-40]"))
    }

    @Test
    fun `only this account's mixes, oldest first`() {
        val newer = playlist("b", "Daily Mix", "[mix:daily::2026-11-26]", created = 2_000L)
        val older = playlist("a", "Daily Mix", "[mix:daily::2026-11-25]", created = 1_000L)
        val someoneElse = playlist("c", "Daily Mix", "[mix:daily::2026-11-26]", owner = "teun", created = 500L)
        val noOwner = playlist("d", "Daily Mix", "[mix:daily::2026-11-26]", owner = null, created = 100L)
        val found = NavidromeMixRules.playlistsFor(SharedMix.DAILY, listOf(newer, someoneElse, older, noOwner), "Mark")
        assertEquals(listOf(older, newer), found)
        assertFalse(NavidromeMixRules.isFrom(found.first(), today))
        assertTrue(NavidromeMixRules.isFrom(found.last(), today))
    }

    @Test
    fun `a playlist without a creation time goes after the dated ones`() {
        val undated = playlist("a", "Your Mix", "[mix:your::2026-11-26]")
        val dated = playlist("z", "Your Mix", "[mix:your::2026-11-26]", created = 5L)
        assertEquals(listOf(dated, undated), NavidromeMixRules.playlistsFor(SharedMix.YOUR, listOf(undated, dated), "mark"))
    }
}
