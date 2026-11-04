package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Lyrics

@Immutable
data class StablePlayerState(
    val currentSong: Song? = null,
    val currentMediaItemIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val isShuffleTransitionInProgress: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isLoadingLyrics: Boolean = false,
    val lyrics: Lyrics? = null,
    val isBuffering: Boolean = false,
    /** Stopped at the end of the queue or after an error: playWhenReady says nothing then. */
    val isStalled: Boolean = false
) {
    /** What a play/pause button shows. Stays on pause while the next song loads. */
    val showsPause: Boolean get() = isPlaying || (playWhenReady && !isStalled)
}

/** Stopped at the end of the queue or after an error. playWhenReady can still be true then. */
val Player.isStalled: Boolean
    get() = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED

/**
 * Playing, or loading in order to play. Player.isPlaying is false while the next song loads, so
 * a pause button that goes by that sends play instead when it is tapped right after a skip.
 */
val Player.wantsToPlay: Boolean
    get() = playWhenReady && !isStalled
