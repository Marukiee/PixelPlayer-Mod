package com.theveloper.pixelplay.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * Global switch for "the music plays on Sonos, the phone only follows along".
 *
 * While active, the local player keeps running so the queue, notification, lock screen and
 * swipes keep working, but its output is silenced at the audio sink. Volume changes from
 * crossfade or ReplayGain cannot undo that, because they only reach [MutingAudioSink.setVolume].
 */
object SonosAudioGate {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Volatile
    var isMuted: Boolean = false
        private set

    fun setActive(active: Boolean) {
        isMuted = active
        _active.value = active
    }
}

/** Forces the AudioTrack volume to zero while [SonosAudioGate] is active. Works in every output mode. */
@OptIn(UnstableApi::class)
class MutingAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private var requestedVolume = 1f
    private var appliedMuted = false

    override fun setVolume(volume: Float) {
        requestedVolume = volume
        applyVolume()
    }

    override fun play() {
        applyVolume()
        super.play()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        // The gate flips on the main thread; the sink may only be touched on the playback thread.
        if (appliedMuted != SonosAudioGate.isMuted) applyVolume()
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    private fun applyVolume() {
        appliedMuted = SonosAudioGate.isMuted
        super.setVolume(if (appliedMuted) 0f else requestedVolume)
    }
}
