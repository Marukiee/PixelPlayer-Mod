package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.withoutDuplicateTracks
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.SearchTextNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SortOption { ARTIST, ALBUM, TITLE }

data class GenreDetailUiState(
    val genre: Genre? = null,
    val songs: List<Song> = emptyList(),
    /** [songs] in the chosen order: what the list shows and what plays. */
    val sortedSongs: List<Song> = emptyList(),
    val sortOption: SortOption = SortOption.ARTIST,
    val isLoadingGenreName: Boolean = false,
    val isLoadingSongs: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreDetailUiState())
    val uiState: StateFlow<GenreDetailUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.get<String>("genreId")?.let { genreId ->
            loadGenreDetails(java.net.URLDecoder.decode(genreId, "UTF-8"))
        } ?: run {
            _uiState.value = _uiState.value.copy(error = "Genre ID not found")
        }
    }

    private fun loadGenreDetails(genreId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGenreName = true, isLoadingSongs = true, error = null)

            try {
                // First the genre, so the colours are settled before the screen has finished sliding in.
                val genre = withContext(Dispatchers.Default) {
                    musicRepository.getGenres().first().find { it.id.equals(genreId, ignoreCase = true) }
                } ?: Genre(
                    id = genreId,
                    name = genreId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    lightColorHex = "#9E9E9E", onLightColorHex = "#000000",
                    darkColorHex = "#616161", onDarkColorHex = "#FFFFFF"
                )
                _uiState.value = _uiState.value.copy(genre = genre, isLoadingGenreName = false)

                val (songs, sorted) = withContext(Dispatchers.Default) {
                    val songs = musicRepository.getMusicByGenre(genre.name).first().withoutDuplicateTracks()
                    songs to songs.sortedFor(_uiState.value.sortOption)
                }
                _uiState.value = _uiState.value.copy(songs = songs, sortedSongs = sorted, isLoadingSongs = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load genre details: ${e.message}",
                    isLoadingGenreName = false,
                    isLoadingSongs = false
                )
            }
        }
    }

    fun updateSortOption(newSort: SortOption) {
        val current = _uiState.value
        if (current.sortOption == newSort) return
        _uiState.value = current.copy(sortOption = newSort)
        viewModelScope.launch {
            val sorted = withContext(Dispatchers.Default) { current.songs.sortedFor(newSort) }
            _uiState.value = _uiState.value.copy(sortedSongs = sorted)
        }
    }

    private fun List<Song>.sortedFor(sort: SortOption): List<Song> {
        fun fold(value: String) = SearchTextNormalizer.fold(value)
        val inAlbumOrder = compareBy<Song> { fold(it.album) }
            .thenBy { it.discNumber ?: 1 }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { fold(it.title) }
        return when (sort) {
            SortOption.ARTIST -> sortedWith(compareBy<Song> { fold(it.artist) }.then(inAlbumOrder))
            SortOption.ALBUM -> sortedWith(inAlbumOrder)
            SortOption.TITLE -> sortedBy { fold(it.title) }
        }
    }
}
