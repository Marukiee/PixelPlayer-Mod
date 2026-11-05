package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.presentation.model.HistoryDay
import com.theveloper.pixelplay.presentation.model.buildHistoryDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ListeningHistoryViewModel @Inject constructor(
    playbackStatsRepository: PlaybackStatsRepository,
    listeningStatsTracker: ListeningStatsTracker,
    musicRepository: MusicRepository
) : ViewModel() {
    /** Null until loaded. The stored plays plus what was played since, which may not have been written yet. */
    val days: StateFlow<List<HistoryDay>?> = combine(
        flow { emit(playbackStatsRepository.loadAllPlaybackHistory()) },
        listeningStatsTracker.playbackHistory
    ) { stored, recent ->
        (recent + stored).distinctBy { it.songId to it.timestamp }
    }.map { entries ->
        val songs = entries.map { it.songId }.distinct()
            .chunked(SONG_QUERY_SIZE)
            .flatMap { musicRepository.getSongsByIds(it).first() }
        buildHistoryDays(entries, songs.associateBy { it.id })
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        /** Older Android versions allow 999 values in one query. */
        const val SONG_QUERY_SIZE = 900
    }
}
