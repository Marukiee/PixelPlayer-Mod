package com.theveloper.pixelplay.data.spotify

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.math.min

/**
 * What the phone plays for a Spotify song: silence as long as the song. The speakers play the song itself; the phone
 * keeps the queue, the notification and the time going, as it does for every other song on the Sonos. Made on the fly,
 * so nothing is held in memory or written to disk.
 */
@OptIn(UnstableApi::class)
class SpotifyStandInDataSource : BaseDataSource(/* isNetwork = */ false) {
    private var uri: Uri? = null
    private var header = ByteArray(0)
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val dataBytes = SilentWav.dataBytes(SpotifyIds.durationMs(dataSpec.uri))
        header = SilentWav.header(dataBytes)
        val total = header.size + dataBytes
        if (dataSpec.position > total) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) min(dataSpec.length, total - position) else total - position
        uri = dataSpec.uri
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = min(length.toLong(), remaining).toInt()
        for (i in 0 until count) {
            val at = position + i
            buffer[offset + i] = if (at < header.size) header[at.toInt()] else 0
        }
        position += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (uri != null) {
            uri = null
            transferEnded()
        }
    }
}

/** Hands Spotify addresses to [SpotifyStandInDataSource] and everything else to the normal data source. */
@OptIn(UnstableApi::class)
class StandInAwareDataSource(private val upstream: DataSource) : DataSource {
    private val standIn = SpotifyStandInDataSource()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
        standIn.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == SpotifyIds.SCHEME) standIn else upstream
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = StandInAwareDataSource(upstream.createDataSource())
    }
}

/** A WAV file of silence: 16 bit mono at 8 kHz, the least an audio track takes without trouble. */
internal object SilentWav {
    const val SAMPLE_RATE = 8_000
    private const val BYTES_PER_SAMPLE = 2
    const val HEADER_SIZE = 44

    fun dataBytes(durationMs: Long): Long = durationMs.coerceAtLeast(0L) * SAMPLE_RATE / 1_000 * BYTES_PER_SAMPLE

    fun header(dataBytes: Long): ByteArray {
        val out = ByteArray(HEADER_SIZE)
        fun text(at: Int, value: String) = value.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
        fun int32(at: Int, value: Long) = repeat(4) { out[at + it] = (value shr (8 * it)).toByte() }
        fun int16(at: Int, value: Int) = repeat(2) { out[at + it] = (value shr (8 * it)).toByte() }
        text(0, "RIFF")
        int32(4, 36 + dataBytes)
        text(8, "WAVE")
        text(12, "fmt ")
        int32(16, 16)
        int16(20, 1)
        int16(22, 1)
        int32(24, SAMPLE_RATE.toLong())
        int32(28, (SAMPLE_RATE * BYTES_PER_SAMPLE).toLong())
        int16(32, BYTES_PER_SAMPLE)
        int16(34, 16)
        text(36, "data")
        int32(40, dataBytes)
        return out
    }
}
