package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.withoutDuplicateTracks
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.toArtist
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.ArtistPlaybackSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Data for the redesigned Home sections that the player and stats view models don't already provide. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicDao: MusicDao,
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository
) : ViewModel() {

    data class HomeArtist(
        val name: String,
        val artistId: Long?,
        val imageModel: String?,
        val totalDurationMs: Long
    )

    private val _rediscoverSongs = MutableStateFlow<List<Song>>(emptyList())
    /** Liked songs you haven't heard in a while; the pick changes once a day, not on every visit. */
    val rediscoverSongs: StateFlow<List<Song>> = _rediscoverSongs.asStateFlow()

    private val _topArtists = MutableStateFlow<List<HomeArtist>>(emptyList())
    val topArtists: StateFlow<List<HomeArtist>> = _topArtists.asStateFlow()

    private var resolvedArtistSummaries: List<ArtistPlaybackSummary>? = null

    init {
        loadRediscover()
    }

    private fun loadRediscover() {
        viewModelScope.launch {
            _rediscoverSongs.value = withContext(Dispatchers.IO) {
                val playedBefore = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(REDISCOVER_MIN_AGE_DAYS)
                musicDao.getFavoriteSongsNotPlayedSince(playedBefore)
                    .shuffled(Random(LocalDate.now().toEpochDay()))
                    .map { it.toSong() }
                    .withoutDuplicateTracks()
                    .take(REDISCOVER_POOL_SIZE)
            }
        }
    }

    /** Resolves stats artist names (which can be combined, like "Douwe Bob, Sera") to library artists. */
    fun updateTopArtists(summaries: List<ArtistPlaybackSummary>) {
        val top = summaries.take(TOP_ARTISTS_COUNT)
        if (top == resolvedArtistSummaries) return
        resolvedArtistSummaries = top
        viewModelScope.launch {
            _topArtists.value = withContext(Dispatchers.IO) {
                top.map { summary ->
                    val entity = musicDao.findArtistByName(summary.artist)
                        ?: firstArtistName(summary.artist)?.let { musicDao.findArtistByName(it) }
                    val artist = entity?.toArtist()
                    HomeArtist(
                        name = summary.artist,
                        artistId = artist?.id,
                        imageModel = artist?.customImageUri?.takeIf { it.isNotBlank() }
                            ?: artist?.imageUrl?.takeIf { it.isNotBlank() },
                        totalDurationMs = summary.totalDurationMs
                    )
                }
            }
            // Photos are normally fetched when an artist page opens; fetch the missing ones here.
            val missing = _topArtists.value.filter { it.imageModel == null && it.artistId != null }
            if (missing.isEmpty()) return@launch
            val found = withContext(Dispatchers.IO) {
                missing.map { artist ->
                    async {
                        artist.name to runCatching {
                            artistImageRepository.getEffectiveArtistImageUrl(artist.artistId!!, firstArtistName(artist.name) ?: artist.name)
                        }.getOrNull()
                    }
                }.awaitAll().toMap()
            }
            if (resolvedArtistSummaries != top) return@launch
            _topArtists.value = _topArtists.value.map { artist ->
                found[artist.name]?.takeIf { it.isNotBlank() }?.let { artist.copy(imageModel = it) } ?: artist
            }
        }
    }

    suspend fun songsForGenre(genre: String): List<Song> =
        withContext(Dispatchers.IO) { musicRepository.getMusicByGenre(genre).first().withoutDuplicateTracks() }

    suspend fun songsForArtist(artistId: Long): List<Song> =
        withContext(Dispatchers.IO) { musicRepository.getSongsForArtist(artistId).first() }

    private fun firstArtistName(combined: String): String? =
        combined.split(ARTIST_SEPARATORS)
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != combined.trim() }

    private companion object {
        const val REDISCOVER_MIN_AGE_DAYS = 60L
        const val REDISCOVER_POOL_SIZE = 50
        const val TOP_ARTISTS_COUNT = 8
        val ARTIST_SEPARATORS = Regex("""\s*(?:,|;|/|&| feat\.? | ft\.? | x )\s*""", RegexOption.IGNORE_CASE)
    }
}
