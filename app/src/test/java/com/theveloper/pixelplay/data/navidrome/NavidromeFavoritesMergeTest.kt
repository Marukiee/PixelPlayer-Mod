package com.theveloper.pixelplay.data.navidrome

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavidromeFavoritesMergeTest {
    private fun targets(vararg stars: String) = stars.associateWith { setOf(it.removePrefix("s").toLong()) }

    @Test
    fun `first sync only adds, on both sides`() {
        val plan = NavidromeFavoritesMerge.plan(
            liked = mapOf(1L to "s1", 2L to "s2"),
            starred = setOf("s2", "s3"),
            base = emptySet(),
            likeTargets = targets("s2", "s3")
        )
        assertEquals(setOf("s1"), plan.toStar)
        assertEquals(setOf(3L), plan.toLike)
        assertTrue(plan.toUnlike.isEmpty())
    }

    @Test
    fun `a star dropped on the server removes the like`() {
        val plan = NavidromeFavoritesMerge.plan(
            liked = mapOf(1L to "s1", 2L to "s2"),
            starred = setOf("s2"),
            base = setOf("s1", "s2"),
            likeTargets = targets("s2")
        )
        assertEquals(setOf(1L), plan.toUnlike)
        assertTrue(plan.toStar.isEmpty())
    }

    @Test
    fun `a song that left the library is not an unlike`() {
        val plan = NavidromeFavoritesMerge.plan(
            liked = mapOf(2L to "s2"),
            starred = setOf("s1", "s2"),
            base = setOf("s1", "s2"),
            likeTargets = mapOf("s1" to emptySet(), "s2" to setOf(2L))
        )
        assertTrue(plan.toStar.isEmpty())
        assertTrue(plan.toLike.isEmpty())
        assertTrue(plan.toUnlike.isEmpty())
    }

    @Test
    fun `most stars vanishing at once keeps the likes and stars them again`() {
        val ids = (1L..30L).toList()
        val plan = NavidromeFavoritesMerge.plan(
            liked = ids.associateWith { "s$it" },
            starred = emptySet(),
            base = ids.map { "s$it" }.toSet(),
            likeTargets = emptyMap()
        )
        assertTrue(plan.serverLost)
        assertTrue(plan.toUnlike.isEmpty())
        assertEquals(30, plan.toStar.size)
    }

    @Test
    fun `a few stars removed by hand are not seen as a lost server`() {
        val ids = (1L..30L).toList()
        val plan = NavidromeFavoritesMerge.plan(
            liked = ids.associateWith { "s$it" },
            starred = ids.drop(3).map { "s$it" }.toSet(),
            base = ids.map { "s$it" }.toSet(),
            likeTargets = targets(*ids.drop(3).map { "s$it" }.toTypedArray())
        )
        assertFalse(plan.serverLost)
        assertEquals(setOf(1L, 2L, 3L), plan.toUnlike)
    }

    @Test
    fun `a star that failed stays out of the base, so it is tried again`() {
        val base = NavidromeFavoritesMerge.nextBase(
            liked = mapOf(1L to "s1", 2L to "s2"),
            starredNow = setOf("s2", "s3"),
            likeTargets = targets("s2", "s3")
        )
        assertEquals(setOf("s2", "s3"), base)
    }
}

class FavoriteSongIndexTest {
    private fun local(id: Long, title: String, artist: String = "Kent", album: String = "Isola", ms: Long = 200_000, navidromeId: String? = null) =
        FavoriteLocalSong(id, title, artist, album, ms, navidromeId)

    private fun server(id: String, title: String, artist: String = "Kent", album: String = "Isola", ms: Long = 200_000) =
        FavoriteServerSong(id, title, artist, album, ms)

    @Test
    fun `the server's own song needs no matching`() {
        val index = FavoriteSongIndex(emptyList(), emptyList())
        assertEquals("abc", index.serverIdFor(local(1, "Anything", navidromeId = "abc")))
    }

    @Test
    fun `accents and case do not matter`() {
        val index = FavoriteSongIndex(listOf(server("s1", "Äventyr i natten")), emptyList())
        assertEquals("s1", index.serverIdFor(local(1, "aventyr i natten", artist = "KENT")))
    }

    @Test
    fun `an exact title beats a loose one`() {
        val index = FavoriteSongIndex(listOf(server("live", "Song (Live)"), server("studio", "Song")), emptyList())
        assertEquals("studio", index.serverIdFor(local(1, "Song")))
    }

    @Test
    fun `going to the server a remaster may match the original`() {
        val index = FavoriteSongIndex(listOf(server("s1", "Song")), emptyList())
        assertEquals("s1", index.serverIdFor(local(1, "Song (Remastered)")))
    }

    @Test
    fun `coming back a star on a live version does not like the studio version`() {
        val studio = local(1, "Song")
        val index = FavoriteSongIndex(emptyList(), listOf(studio))
        assertTrue(index.localsFor(server("live", "Song (Live)")).isEmpty())
    }

    @Test
    fun `the same album is preferred, then the closest length`() {
        val index = FavoriteSongIndex(
            listOf(
                server("other", "Song", album = "Best of", ms = 200_000),
                server("far", "Song", album = "Isola", ms = 400_000),
                server("near", "Song", album = "Isola", ms = 201_000)
            ),
            emptyList()
        )
        assertEquals("near", index.serverIdFor(local(1, "Song")))
    }

    @Test
    fun `a star counts for the server's entry and the local copy`() {
        val file = local(1, "Song")
        val own = local(2, "Song", navidromeId = "s1")
        val index = FavoriteSongIndex(emptyList(), listOf(file, own))
        assertEquals(setOf(1L, 2L), index.localsFor(server("s1", "Song")))
    }

    @Test
    fun `a song without a match stays without one`() {
        val index = FavoriteSongIndex(listOf(server("s1", "Other")), emptyList())
        assertEquals(null, index.serverIdFor(local(1, "Song")))
    }
}
