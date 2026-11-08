package com.theveloper.pixelplay.presentation.viewmodel

import kotlinx.coroutines.flow.first
import kotlin.math.abs
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.search.RecentSearchSongs
import com.theveloper.pixelplay.data.model.withoutDuplicateTracks
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val recentSongs: RecentSearchSongs,
    private val playbackStats: PlaybackStatsRepository,
) {
    /** The songs tapped in Search, for the recent searches. */
    val recentSearchSongs = recentSongs.entries

    fun addRecentSearchSong(song: Song, query: String? = null) = recentSongs.add(song, query)

    fun removeRecentSearchSong(songId: String) = recentSongs.remove(songId)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        /** A tapped search result starts within moments of the search being kept. */
        const val BACKFILL_BEFORE_MS = 3_000L
        const val BACKFILL_AFTER_MS = 15_000L
    }

    private data class SearchRequest(
        val query: String,
        val requestId: Long,
    )

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request ->
                    val normalizedQuery = request.query

                    if (normalizedQuery.isBlank()) {
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    try {
                        val currentFilter = _selectedSearchFilter.value
                        musicRepository.searchAll(normalizedQuery, currentFilter).collect { rawResults ->
                            // A song that is on the phone and on the server shows once, as the local file.
                            val keptSongIds = rawResults.filterIsInstance<SearchResultItem.SongItem>()
                                .map { it.song }
                                .withoutDuplicateTracks()
                                .mapTo(HashSet()) { it.id }
                            val resultsList = rawResults.filter { it !is SearchResultItem.SongItem || it.song.id in keptSongIds }
                            // Sort: prioritize Song/Album matches over Artist/Playlist matches
                            val sortedResults = resultsList.sortedWith(
                                compareBy { result ->
                                    when (result) {
                                        is SearchResultItem.SongItem -> 0
                                        is SearchResultItem.AlbumItem -> 1
                                        is SearchResultItem.ArtistItem -> 2
                                        is SearchResultItem.PlaylistItem -> 3
                                    }
                                }
                            )

                            if (request.requestId != latestSearchRequestId.get()) {
                                return@collect
                            }

                            val immutableResults = sortedResults.toImmutableList()
                            if (_searchResults.value != immutableResults) {
                                _searchResults.value = immutableResults
                            }
                        }
                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search for query: $normalizedQuery")
                            _searchResults.value = persistentListOf()
                        }
                    }
                }
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
                if (!recentSongs.backfilled) backfillRecentSongs(history)
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    /**
     * Once: the searches from before songs were kept get the song that started playing right after them, from the
     * listening history, so they show with a cover like a song tapped today.
     */
    private suspend fun backfillRecentSongs(history: List<SearchHistoryItem>) = withContext(Dispatchers.IO) {
        val starts = runCatching { playbackStats.loadPlaybackStarts() }.getOrDefault(emptyList())
        val guesses = history.mapNotNull { item ->
            val hit = starts
                .filter { it.timestamp in (item.timestamp - BACKFILL_BEFORE_MS)..(item.timestamp + BACKFILL_AFTER_MS) }
                .minByOrNull { abs(it.timestamp - item.timestamp) } ?: return@mapNotNull null
            val song = runCatching { musicRepository.getSong(hit.songId).first() }.getOrNull() ?: return@mapNotNull null
            RecentSearchSongs.Entry(song, item.timestamp, item.query)
        }
        recentSongs.addGuessed(guesses)
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()

        val requestId = latestSearchRequestId.incrementAndGet()

        if (normalizedQuery.isBlank()) {
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId))
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
                recentSongs.clear()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }
}
