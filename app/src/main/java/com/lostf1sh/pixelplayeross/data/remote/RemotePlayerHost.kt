package com.lostf1sh.pixelplayeross.data.remote

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.database.SongEntity
import com.lostf1sh.pixelplayeross.data.database.SourceType
import com.lostf1sh.pixelplayeross.data.database.toSong
import com.lostf1sh.pixelplayeross.data.service.MusicService
import com.lostf1sh.pixelplayeross.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The playing side: carries out what the controlling phone asks, on this phone's own player and files, and reports
 * back where it is. When the line drops the music simply plays on, like on an mp3 player.
 */
@Singleton
@OptIn(UnstableApi::class)
class RemotePlayerHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = context.getSystemService(AudioManager::class.java)

    private val _controllerName = MutableStateFlow<String?>(null)

    /** The phone that controls this one right now, or null. */
    val controllerName: StateFlow<String?> = _controllerName.asStateFlow()

    private var link: RemoteLink? = null
    private var linkJob: Job? = null
    private var tickJob: Job? = null
    private var stateJob: Job? = null
    private var volumeCheckJob: Job? = null
    private var lastRequestedVolume = -1
    private var player: MediaController? = null

    /** The controller's queue as far as it is on this phone. */
    private var queueId = 0L
    private var placed: List<Placed> = emptyList()

    /** One song this host put in the player: the player's media id, the controller's id, and where it was in the queue it sent. */
    private data class Placed(val mediaId: String, val remoteId: String, val sentIndex: Int)

    private var matcher: RemoteTrackMatcher? = null
    private var entities: Map<String, SongEntity> = emptyMap()
    private var matcherBuiltAt = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED
                )
            ) {
                scheduleState()
            }
        }
    }

    /** A new controller dialled in; any earlier one is let go. May be called from any thread. */
    fun attach(newLink: RemoteLink) {
        scope.launch {
            val old = link
            link = newLink
            if (old != null) {
                old.send(RemoteProtocol.bye())
                old.close()
            }
            // A phone that only looks around is not in control yet; its hello says when it takes over.
            _controllerName.value = null
            linkJob?.cancel()
            tickJob?.cancel()
            linkJob = launch {
                newLink.messages.collect { message ->
                    runCatching { handle(newLink, message) }.onFailure { Timber.tag(TAG).w(it, "Could not carry out %s", message.optString("t")) }
                }
                if (link === newLink) {
                    link = null
                    _controllerName.value = null
                    tickJob?.cancel()
                }
            }
            tickJob = launch {
                while (isActive && link === newLink) {
                    delay(TICK_MS)
                    if (SystemClock.uptimeMillis() - newLink.lastReceivedAt > SILENT_LIMIT_MS) {
                        Timber.tag(TAG).i("Controller went quiet, closing the line")
                        newLink.close()
                        break
                    }
                    sendState()
                }
            }
        }
    }

    /** The remote player was turned off: lets the controller know and stops listening to it. */
    fun detachAll() {
        scope.launch {
            link?.let {
                it.send(RemoteProtocol.bye())
                delay(150)
                it.close()
            }
            link = null
            _controllerName.value = null
        }
    }

    private suspend fun handle(from: RemoteLink, message: JSONObject) {
        if (link !== from) return
        when (message.optString("t")) {
            RemoteProtocol.HELLO -> {
                if (message.optBoolean("session")) {
                    _controllerName.value = message.optString("name").takeIf { it.isNotBlank() } ?: from.peerName
                }
                from.send(RemoteProtocol.hello(RemoteTransport.ownName(context), ROLE))
                ensurePlayer()
                sendState()
            }
            RemoteProtocol.PING -> sendState()
            RemoteProtocol.QUEUE -> {
                if (_controllerName.value == null) _controllerName.value = from.peerName
                applyQueue(message)
            }
            RemoteProtocol.JUMP -> jump(message)
            RemoteProtocol.PLAY -> ensurePlayer()?.let { p ->
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(p.currentMediaItemIndex, 0)
                p.play()
            }
            RemoteProtocol.PAUSE -> ensurePlayer()?.pause()
            RemoteProtocol.SEEK -> ensurePlayer()?.seekTo(message.optLong("pos").coerceAtLeast(0))
            RemoteProtocol.VOLUME -> setVolume(message.optInt("v", -1))
            RemoteProtocol.BYE -> from.close()
        }
        if (message.optString("t") in COMMANDS) sendState()
    }

    private suspend fun applyQueue(message: JSONObject) {
        val qid = message.optLong("qid")
        val array = message.optJSONArray("items") ?: return
        val items = List(array.length()) { RemoteTrack.fromJson(array.getJSONObject(it)) }
        val index = message.optInt("index").coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val positionMs = message.optLong("pos").coerceAtLeast(0)
        val play = message.optBoolean("play")
        val repeat = message.optInt("repeat", Player.REPEAT_MODE_OFF)
        val songs = resolve(items)
        val p = ensurePlayer() ?: return
        val missing = items.indices.filter { songs[it] == null }.map { items[it].id }
        val foundIndices = items.indices.filter { songs[it] != null }
        if (foundIndices.isEmpty()) {
            queueId = qid
            placed = emptyList()
            p.pause()
            link?.send(RemoteProtocol.queued(qid, missing, 0))
            return
        }
        // The wanted song, or the first one after it that is here; the song before it when none follows.
        val start = foundIndices.firstOrNull { it >= index } ?: foundIndices.last()
        // Nothing left to play after the wanted song: wait on the last one there is instead of playing back.
        val playNow = play && start >= index
        val startInFound = foundIndices.indexOf(start)
        val newPlaced = foundIndices.map { Placed(songs[it]!!.id.toString(), items[it].id, it) }
        val mediaItems = foundIndices.map { MediaItemBuilder.build(songs[it]!!.toSong()) }

        // The controller decides the order.
        if (p.shuffleModeEnabled) p.shuffleModeEnabled = false
        if (p.repeatMode != repeat) p.repeatMode = repeat

        val playingNow = currentPlacedIndex(p)?.let { placed[it] }
        val keepsCurrent = playingNow != null && start == index && playingNow.mediaId == newPlaced[startInFound].mediaId &&
            p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED
        if (keepsCurrent) {
            // Only the songs around the current one changed: swap those, the song plays on without a hitch.
            val current = p.currentMediaItemIndex
            if (current + 1 < p.mediaItemCount) p.removeMediaItems(current + 1, p.mediaItemCount)
            if (current > 0) p.removeMediaItems(0, current)
            val after = mediaItems.subList(startInFound + 1, mediaItems.size)
            val before = mediaItems.subList(0, startInFound)
            if (after.isNotEmpty()) p.addMediaItems(after)
            if (before.isNotEmpty()) p.addMediaItems(0, before)
            if (abs(p.currentPosition - positionMs) > KEEP_POSITION_TOLERANCE_MS) p.seekTo(positionMs)
        } else {
            p.setMediaItems(mediaItems, startInFound, if (start == index) positionMs else 0L)
            p.prepare()
        }
        p.playWhenReady = playNow
        queueId = qid
        placed = newPlaced
        link?.send(RemoteProtocol.queued(qid, missing, foundIndices.size))
        sendState()
    }

    private suspend fun jump(message: JSONObject) {
        val p = ensurePlayer() ?: return
        if (message.optLong("qid") != queueId) return
        val id = message.optString("id")
        val sentIndex = message.optInt("index")
        val target = placed.indices.filter { placed[it].remoteId == id }.minByOrNull { abs(placed[it].sentIndex - sentIndex) } ?: return
        // The queue may have been edited on this phone since: find the song where it is now.
        val playerIndex = if (target < p.mediaItemCount && p.getMediaItemAt(target).mediaId == placed[target].mediaId) {
            target
        } else {
            (0 until p.mediaItemCount).filter { p.getMediaItemAt(it).mediaId == placed[target].mediaId }
                .minByOrNull { abs(it - target) } ?: return
        }
        p.seekTo(playerIndex, message.optLong("pos").coerceAtLeast(0))
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        p.playWhenReady = message.optBoolean("play", p.playWhenReady)
    }

    /** This phone's copy of each song, or null where it has none. Builds the index once and again when a song is missing. */
    private suspend fun resolve(items: List<RemoteTrack>): List<SongEntity?> = withContext(Dispatchers.IO) {
        fun lookup(m: RemoteTrackMatcher) = items.map { track -> m.match(track)?.let { entities[it.id] } }
        var m = matcher
        if (m == null || SystemClock.elapsedRealtime() - matcherBuiltAt > MATCHER_MAX_AGE_MS) m = buildMatcher()
        var result = lookup(m)
        if (result.any { it == null } && SystemClock.elapsedRealtime() - matcherBuiltAt > MATCHER_REFRESH_MS) {
            m = buildMatcher()
            result = lookup(m)
        }
        result
    }

    private suspend fun buildMatcher(): RemoteTrackMatcher {
        val local = musicDao.getAllSongsList().filter { it.sourceType == SourceType.LOCAL }
        entities = local.associateBy { it.id.toString() }
        val m = RemoteTrackMatcher(local.map {
            LocalCandidate(it.id.toString(), it.filePath, it.title, it.artistName, it.albumName, it.duration)
        })
        matcher = m
        matcherBuiltAt = SystemClock.elapsedRealtime()
        Timber.tag(TAG).d("Indexed %d local songs", m.size)
        return m
    }

    private fun currentPlacedIndex(p: Player): Int? {
        val mediaId = p.currentMediaItem?.mediaId ?: return null
        val index = p.currentMediaItemIndex
        if (placed.getOrNull(index)?.mediaId == mediaId) return index
        return placed.indices.filter { placed[it].mediaId == mediaId }.minByOrNull { abs(it - index) }
    }

    private fun setVolume(percent: Int) {
        if (percent < 0) return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = (percent.coerceIn(0, 100) * max / 100f).roundToInt()
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
            .onFailure { Timber.tag(TAG).w(it, "Could not set the volume") }
        // With headphones or a cable, Android holds the volume at a safe level until someone confirms its warning on
        // this phone. Then the controller should say so instead of the slider just springing back. Only the last
        // value of a drag counts, or going up and back down would look like being held.
        lastRequestedVolume = index
        volumeCheckJob?.cancel()
        volumeCheckJob = scope.launch {
            delay(VOLUME_CHECK_MS)
            if (index == lastRequestedVolume && audio.getStreamVolume(AudioManager.STREAM_MUSIC) < index) {
                link?.send(RemoteProtocol.volumeHeld())
            }
        }
    }

    private fun volumePercent(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()
    }

    private fun scheduleState() {
        if (link == null) return
        stateJob?.cancel()
        stateJob = scope.launch {
            delay(STATE_DEBOUNCE_MS)
            sendState()
        }
    }

    private fun sendState() {
        val l = link ?: return
        val p = player
        val remoteId = p?.let { currentPlacedIndex(it) }?.let { placed[it].remoteId }
        val state = RemotePlayerState(
            queueId = queueId,
            currentId = remoteId,
            foreign = p?.currentMediaItem != null && placed.isNotEmpty() && remoteId == null,
            positionMs = p?.currentPosition ?: 0L,
            durationMs = p?.duration?.takeIf { it > 0 } ?: 0L,
            playWhenReady = p?.playWhenReady == true,
            isPlaying = p?.isPlaying == true,
            volumePercent = volumePercent(),
            volumeSteps = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        )
        l.send(RemoteProtocol.state(state))
    }

    /** A controller to this app's own media session, so the music plays with the usual notification and queue. */
    private suspend fun ensurePlayer(): MediaController? {
        player?.let { if (it.isConnected) return it }
        player = null
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val controller = suspendCancellableCoroutine<MediaController?> { cont ->
            future.addListener({
                cont.resume(runCatching { future.get() }.onFailure { Timber.tag(TAG).w(it, "No media session") }.getOrNull())
            }, ContextCompat.getMainExecutor(context))
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        } ?: return null
        controller.addListener(listener)
        player = controller
        return controller
    }

    private companion object {
        const val TAG = "RemotePlayerHost"
        const val ROLE = "player"
        const val TICK_MS = 1_000L
        const val STATE_DEBOUNCE_MS = 80L
        /** The controller pings every two seconds; this much silence means it is gone. */
        const val SILENT_LIMIT_MS = 12_000L
        const val KEEP_POSITION_TOLERANCE_MS = 3_000L
        const val VOLUME_CHECK_MS = 250L
        const val MATCHER_MAX_AGE_MS = 10 * 60_000L
        const val MATCHER_REFRESH_MS = 30_000L
        val COMMANDS = setOf(RemoteProtocol.JUMP, RemoteProtocol.PLAY, RemoteProtocol.PAUSE, RemoteProtocol.SEEK, RemoteProtocol.VOLUME)
    }
}
