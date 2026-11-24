package com.lostf1sh.pixelplayeross.data.remote

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import com.lostf1sh.pixelplayeross.data.service.player.SonosAudioGate
import com.lostf1sh.pixelplayeross.data.sonos.SonosController
import com.lostf1sh.pixelplayeross.data.spotify.SpotifyIds
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** A phone that answered as remote player. */
data class RemoteDevice(val endpoint: RemoteEndpoint, val name: String) {
    val key: String get() = endpoint.key
}

data class RemoteSessionState(
    val device: RemoteDevice,
    val isConnecting: Boolean = true,
    /** The line dropped; the other phone plays on and this one keeps trying to get back. */
    val isReconnecting: Boolean = false,
    val isPlaying: Boolean = false,
    /** The other phone's media volume, 0 to 100. */
    val volume: Int? = null,
    /** How many steps that volume has; 0 while unknown. */
    val volumeSteps: Int = 0
)

/**
 * Plays the app's queue on another phone running PixelPlayer as remote player, the way [SonosController] does on a
 * Sonos: this phone's player keeps running muted, so the player, mini player, queue, lists, notification and lock
 * screen all work as usual, and every change is sent across. The other phone plays its own copy of each file and
 * leads on timing; this phone follows its position.
 */
@Singleton
@OptIn(UnstableApi::class)
class RemotePlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: DualPlayerEngine,
    private val settings: RemotePlayerSettings,
    private val sonos: dagger.Lazy<SonosController>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _devices = MutableStateFlow<List<RemoteDevice>>(emptyList())

    /** Remote players in reach, found while the connect panel is open. */
    val devices: StateFlow<List<RemoteDevice>> = _devices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _session = MutableStateFlow<RemoteSessionState?>(null)
    val session: StateFlow<RemoteSessionState?> = _session.asStateFlow()

    /** Lines opened while looking, kept so a tap connects at once. */
    private val standby = HashMap<String, Pair<RemoteLink, Job>>()
    private var watchJob: Job? = null
    private var watchers = 0
    private var searchGeneration = 0
    private var standbyCloseJob: Job? = null
    private var observedPlayer: Player? = null
    private var active: Active? = null
    private var volumeJob: Job? = null
    private var lastVolumeSentAt = 0L
    private var lastVolumeHeldToastAt = 0L

    private class Active(val device: RemoteDevice) {
        var link: RemoteLink? = null
        var readJob: Job? = null
        var pingJob: Job? = null
        var reconnectJob: Job? = null
        var syncJob: Job? = null
        var syncGeneration = 0L
        var pendingJump = false
        /** The last queue sent: its id, the song ids in order, and the ones the other phone does not have. */
        var queueId = 0L
        var sentIds: List<String> = emptyList()
        var missing: Set<String> = emptySet()
        var acked = false
        /** Where in [sentIds] the other phone was last. */
        var remoteIndex = 0
        var following = false
        var lastTrackCommandAt = 0L
        var lastPlayPauseCommandAt = 0L
        var lastSeekAt = 0L
        var lastLocalAutoTransitionAt = 0L
        /** Something was changed here while the line was down: on the way back this phone leads. */
        var changedWhileAway = false
        var awaitingFirstState = false
        var skipStreak = 0
        var toldMissing: String? = null
        var refillCheckedAt = -1
    }

    private data class LocalWindow(
        val items: List<MediaItem>,
        val currentIndex: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val repeatMode: Int
    ) {
        val ids: List<String> get() = items.map { it.mediaId }
    }

    init {
        engine.addPlayerSwapListener { newPlayer -> if (active != null) attachTo(newPlayer) }
        scope.launch { resumeSavedSession() }
    }

    // ─── Looking for remote players ───────────────────────────────────────

    /**
     * Looks for remote players while the connect panel or the remote player settings are open. Counted, so one of
     * them closing does not stop the search the other still shows.
     */
    fun watch(on: Boolean) {
        watchers = (watchers + if (on) 1 else -1).coerceAtLeast(0)
        if (watchers > 0) {
            standbyCloseJob?.cancel()
            if (watchJob?.isActive == true) return
            watchJob = scope.launch {
                while (isActive) {
                    searchOnce()
                    delay(SEARCH_INTERVAL_MS)
                }
            }
        } else {
            watchJob?.cancel()
            watchJob = null
            _isSearching.value = false
            standbyCloseJob?.cancel()
            standbyCloseJob = scope.launch {
                delay(STANDBY_KEEP_MS)
                closeStandby()
            }
        }
    }

    fun refresh() {
        if (watchJob?.isActive == true) {
            watchJob?.cancel()
            watchJob = scope.launch {
                while (isActive) {
                    searchOnce()
                    delay(SEARCH_INTERVAL_MS)
                }
            }
        } else {
            scope.launch { searchOnce() }
        }
    }

    private suspend fun searchOnce() {
        // A search that is let go for a fresh one must not switch the indicator off under the new one.
        val generation = ++searchGeneration
        _isSearching.value = true
        try {
            val last = settings.lastDeviceKey
            val candidates = (RemoteTransport.tcpCandidates(context) + RemoteTransport.pairedCandidates(context))
                .distinctBy { it.key }
                .sortedBy { if (it.key == last) 0 else 1 }
            val found = ArrayList<RemoteDevice>()
            for (endpoint in candidates) {
                // Never knock on the phone that plays: the knock would take its line over.
                val playing = active?.device
                if (playing?.key == endpoint.key) {
                    found += playing
                    continue
                }
                val device = probe(endpoint) ?: continue
                found += device
                _devices.value = (found + _devices.value).distinctBy { it.key }
            }
            _devices.value = found
        } finally {
            if (generation == searchGeneration) _isSearching.value = false
        }
    }

    private suspend fun probe(endpoint: RemoteEndpoint): RemoteDevice? {
        standby[endpoint.key]?.let { (link, _) ->
            if (link.isOpen) return RemoteDevice(endpoint, nameFor(endpoint, null))
            dropStandby(endpoint.key)
        }
        val (link, hello) = openWithHello(endpoint, PROBE_TIMEOUT_MS, session = false) ?: return null
        // The player reports its state every second; nobody reads it until a tap, so it is let go. The pings keep the
        // line open, or the player would take it for gone.
        val drain = scope.launch {
            launch {
                while (isActive && link.isOpen) {
                    delay(PING_MS)
                    link.send(RemoteProtocol.ping())
                }
            }
            link.messages.collect { }
            dropStandby(endpoint.key)
        }
        standby[endpoint.key] = link to drain
        return RemoteDevice(endpoint, nameFor(endpoint, hello))
    }

    private fun nameFor(endpoint: RemoteEndpoint, hello: JSONObject?): String = when (endpoint) {
        // The name the phone has in this phone's Bluetooth list, which is the one Mark knows it by.
        is RemoteEndpoint.Bluetooth -> endpoint.name
        is RemoteEndpoint.Tcp -> hello?.optString("name")?.takeIf { it.isNotBlank() } ?: endpoint.name
    }

    /** Opens a line and waits for the other side to say it is a PixelPlayer remote player. */
    private suspend fun openWithHello(endpoint: RemoteEndpoint, timeoutMs: Long, session: Boolean): Pair<RemoteLink, JSONObject>? {
        val link = runCatching { RemoteTransport.open(context, endpoint, timeoutMs) }
            .onFailure { Timber.tag(TAG).d("No remote player at %s: %s", endpoint.name, it.message) }
            .getOrNull() ?: return null
        link.send(hello(session))
        val reply = withTimeoutOrNull(HELLO_TIMEOUT_MS) {
            link.messages.first { it.optString("t") == RemoteProtocol.HELLO }
        }
        if (reply == null || reply.optInt("v") < 1) {
            link.close()
            return null
        }
        return link to reply
    }

    private fun hello(session: Boolean) =
        RemoteProtocol.hello(RemoteTransport.ownName(context), ROLE).put("session", session)

    private fun takeStandby(key: String): RemoteLink? {
        val (link, drain) = standby.remove(key) ?: return null
        drain.cancel()
        return link.takeIf { it.isOpen }
    }

    private fun dropStandby(key: String) {
        standby.remove(key)?.let { (link, drain) ->
            drain.cancel()
            link.close()
        }
    }

    private fun closeStandby() {
        standby.keys.toList().forEach(::dropStandby)
    }

    // ─── Session ──────────────────────────────────────────────────────────

    fun connect(key: String) {
        val device = _devices.value.firstOrNull { it.key == key } ?: return
        if (active?.device?.key == key) return
        scope.launch {
            active?.let { endSession(it, null, pauseLocal = false, pauseRemote = true) }
            val a = Active(device)
            active = a
            _session.value = RemoteSessionState(device)
            val link = takeStandby(key) ?: openWithHello(device.endpoint, CONNECT_TIMEOUT_MS, session = false)?.first
            if (active !== a) {
                link?.close()
                return@launch
            }
            if (link == null) {
                active = null
                _session.value = null
                toast(context.getString(R.string.remote_msg_unreachable, device.name))
                return@launch
            }
            settings.lastDeviceKey = key
            settings.saveSession(device.endpoint)
            engine.setRemoteOutputActive(true, SonosAudioGate.OWNER_REMOTE_PLAYER)
            // One place plays at a time. Only now, so a phone out of reach leaves the Sonos playing.
            if (sonos.get().session.value != null) sonos.get().switchToPhone()
            attachTo(engine.masterPlayer)
            startLink(a, link)
            link.send(hello(session = true))
            syncNow(a, jump = true, forceQueue = true)
            _session.update { it?.copy(isConnecting = false, isPlaying = engine.masterPlayer.playWhenReady) }
        }
    }

    /** Back to this phone: the other phone pauses and this one plays on from the same spot. */
    fun switchToPhone() {
        val a = active ?: return
        endSession(a, null, pauseLocal = false, pauseRemote = true)
    }

    private fun endSession(a: Active, message: String?, pauseLocal: Boolean, pauseRemote: Boolean) {
        if (active !== a) return
        active = null
        a.readJob?.cancel()
        a.pingJob?.cancel()
        a.reconnectJob?.cancel()
        a.syncJob?.cancel()
        a.link?.let { link ->
            if (pauseRemote) link.send(RemoteProtocol.pause())
            link.send(RemoteProtocol.bye())
            // Lets the last messages out before the line closes.
            scope.launch {
                delay(300)
                link.close()
            }
        }
        a.link = null
        settings.clearSession()
        detach()
        if (pauseLocal) {
            engine.masterPlayer.pause()
            scope.launch {
                delay(300)
                if (active == null) engine.setRemoteOutputActive(false, SonosAudioGate.OWNER_REMOTE_PLAYER)
            }
        } else {
            engine.setRemoteOutputActive(false, SonosAudioGate.OWNER_REMOTE_PLAYER)
        }
        _session.value = null
        if (message != null) toast(message)
    }

    private fun startLink(a: Active, link: RemoteLink) {
        a.link = link
        a.readJob?.cancel()
        a.pingJob?.cancel()
        a.readJob = scope.launch {
            link.messages.collect { message ->
                runCatching { handle(a, message) }.onFailure { Timber.tag(TAG).w(it, "Could not handle %s", message.optString("t")) }
            }
            if (active === a && a.link === link) onLinkLost(a)
        }
        a.pingJob = scope.launch {
            while (isActive && a.link === link) {
                delay(PING_MS)
                link.send(RemoteProtocol.ping())
                if (SystemClock.uptimeMillis() - link.lastReceivedAt > SILENT_LIMIT_MS) {
                    Timber.tag(TAG).i("Remote player went quiet")
                    link.close()
                    break
                }
            }
        }
    }

    /** The line dropped: the other phone plays on, this one keeps following its own copy and tries to get back. */
    private fun onLinkLost(a: Active) {
        a.link = null
        a.pingJob?.cancel()
        _session.update { it?.copy(isReconnecting = true) }
        a.reconnectJob?.cancel()
        a.reconnectJob = scope.launch {
            var attempt = 0
            while (isActive && active === a) {
                val opened = openWithHello(a.device.endpoint, RECONNECT_TIMEOUT_MS, session = true)
                if (active !== a) {
                    opened?.first?.close()
                    return@launch
                }
                if (opened != null) {
                    a.awaitingFirstState = true
                    startLink(a, opened.first)
                    _session.update { it?.copy(isReconnecting = false) }
                    return@launch
                }
                attempt++
                delay(if (attempt < FAST_RECONNECTS) RECONNECT_FAST_MS else RECONNECT_SLOW_MS)
            }
        }
    }

    /**
     * The app was closed while the other phone played on: picks the session up again. Mutes first, because the queue
     * the app restores may start before the other phone has answered.
     */
    private suspend fun resumeSavedSession() {
        val endpoint = settings.savedSession(RESUME_MAX_AGE_MS) ?: return
        engine.setRemoteOutputActive(true, SonosAudioGate.OWNER_REMOTE_PLAYER)
        val opened = openWithHello(endpoint, RESUME_TIMEOUT_MS, session = true)
        val state = opened?.let { (link, _) ->
            withTimeoutOrNull(HELLO_TIMEOUT_MS) { link.messages.first { it.optString("t") == RemoteProtocol.STATE } }
                ?.let(RemoteProtocol::parseState)
        }
        val remoteId = state?.currentId
        val player = engine.masterPlayer
        // The app puts its last queue back a moment after it starts.
        fun findLocal(): Int? = remoteId?.let { id -> (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id } }
        var resumeAt = findLocal()
        var checks = 0
        while (remoteId != null && resumeAt == null && checks++ < RESUME_QUEUE_CHECKS) {
            delay(RESUME_QUEUE_CHECK_MS)
            resumeAt = findLocal()
        }
        if (opened == null || state == null || state.foreign || resumeAt == null || active != null) {
            opened?.first?.close()
            if (active == null) {
                settings.clearSession()
                // The music was on the other phone: it must not suddenly come out of this one.
                if (player.playWhenReady) player.pause()
                delay(300)
                if (active == null) engine.setRemoteOutputActive(false, SonosAudioGate.OWNER_REMOTE_PLAYER)
            }
            return
        }
        val device = RemoteDevice(endpoint, nameFor(endpoint, opened.second))
        val a = Active(device)
        a.queueId = state.queueId
        a.acked = true
        active = a
        _session.value = RemoteSessionState(device, isConnecting = false, isPlaying = state.playWhenReady, volume = state.volumePercent)
        attachTo(player)
        startLink(a, opened.first)
        followLocal(a) { it.seekTo(resumeAt, state.positionMs) }
        onState(a, state)
    }

    // ─── Messages from the remote player ──────────────────────────────────

    private fun handle(a: Active, message: JSONObject) {
        if (active !== a) return
        when (message.optString("t")) {
            RemoteProtocol.STATE -> onState(a, RemoteProtocol.parseState(message))
            RemoteProtocol.QUEUED -> onQueued(a, message)
            RemoteProtocol.VOLUME_HELD -> if (now() - lastVolumeHeldToastAt > VOLUME_HELD_TOAST_MS) {
                lastVolumeHeldToastAt = now()
                toast(context.getString(R.string.remote_msg_volume_held, a.device.name))
            }
            RemoteProtocol.BYE -> endSession(
                a, context.getString(R.string.remote_msg_stopped, a.device.name), pauseLocal = true, pauseRemote = false
            )
        }
    }

    private fun onQueued(a: Active, message: JSONObject) {
        if (message.optLong("qid") != a.queueId) return
        val missingArray = message.optJSONArray("missing")
        val missing = HashSet<String>()
        if (missingArray != null) for (i in 0 until missingArray.length()) missing += missingArray.optString(i)
        a.missing = missing
        a.acked = true
        val player = engine.masterPlayer
        val current = player.currentMediaItem ?: return
        if (message.optInt("found") == 0) {
            followLocal(a) { it.pause() }
            toast(context.getString(R.string.remote_msg_none_found, a.device.name))
            return
        }
        if (current.mediaId in a.missing) tellMissing(a, current)
    }

    private fun onState(a: Active, state: RemotePlayerState) {
        val sinceVolume = now() - lastVolumeSentAt
        _session.update {
            it?.copy(
                isPlaying = state.playWhenReady,
                // Right after a drag the other phone may still report the old value; the slider keeps the finger's.
                volume = if (sinceVolume < VOLUME_SETTLE_MS || volumeJob?.isActive == true) it.volume else state.volumePercent ?: it.volume,
                volumeSteps = state.volumeSteps.takeIf { s -> s > 0 } ?: it.volumeSteps
            )
        }
        if (a.awaitingFirstState) {
            a.awaitingFirstState = false
            if (a.changedWhileAway || state.queueId != a.queueId) {
                a.changedWhileAway = false
                scheduleSync(a, jump = true, forceQueue = true, waitMs = 0)
                return
            }
        }
        if (state.foreign) {
            endSession(a, context.getString(R.string.remote_msg_taken_over, a.device.name), pauseLocal = true, pauseRemote = false)
            return
        }
        // Our newest queue has not arrived there yet.
        if (state.queueId != a.queueId) return
        val remoteId = state.currentId ?: return
        val now = now()
        val busy = now - a.lastTrackCommandAt < TRACK_COMMAND_GRACE_MS || a.syncJob?.isActive == true
        val player = engine.masterPlayer
        val localId = player.currentMediaItem?.mediaId
        if (localId != remoteId) {
            if (busy) return
            val local = readWindow()
            val autoAdvancedRecently = now - a.lastLocalAutoTransitionAt < AUTO_ADVANCE_GRACE_MS
            // This phone finished the song a moment before the other one did.
            val oneAhead = local != null && local.currentIndex > 0 && local.ids[local.currentIndex - 1] == remoteId
            if (autoAdvancedRecently && oneAhead && state.isPlaying) return
            followTrack(a, remoteId, state.positionMs)
            return
        }
        a.skipStreak = 0
        val sentIndex = nearestIndexOf(a.sentIds, remoteId, a.remoteIndex)
        if (sentIndex >= 0) {
            a.remoteIndex = sentIndex
            // The other phone plays on by itself; before its queue runs short it gets the next songs.
            if (sentIndex != a.refillCheckedAt && a.sentIds.size - sentIndex < REFILL_AT && !busy) {
                a.refillCheckedAt = sentIndex
                scheduleSync(a, jump = false)
            }
        }

        if (now - a.lastPlayPauseCommandAt > PLAY_PAUSE_GRACE_MS && state.playWhenReady != player.playWhenReady) {
            followLocal(a) {
                if (state.playWhenReady) {
                    if (it.playbackState == Player.STATE_IDLE) it.prepare()
                    it.play()
                } else {
                    it.pause()
                }
            }
        }
        if (busy || now - a.lastSeekAt < SEEK_GRACE_MS) return
        // The lyrics and the progress bar run on this phone's copy, so it keeps close to the other phone's time.
        val playingBoth = state.isPlaying && player.isPlaying
        val target = state.positionMs + if (state.isPlaying) LINK_LATENCY_MS else 0L
        // Both stand still and the pause has settled: the other phone's spot is exact, so this one takes it closely.
        val pausedBoth = !state.playWhenReady && !player.playWhenReady
        val limit = when {
            playingBoth -> DRIFT_LIMIT_MS
            pausedBoth && now - a.lastPlayPauseCommandAt > PLAY_PAUSE_GRACE_MS -> PAUSED_ALIGN_LIMIT_MS
            else -> PAUSED_DRIFT_LIMIT_MS
        }
        if (abs(target - player.currentPosition) > limit) followLocal(a) { it.seekTo(target) }
    }

    private fun tellMissing(a: Active, item: MediaItem) {
        if (a.toldMissing == item.mediaId) return
        a.toldMissing = item.mediaId
        val title = item.mediaMetadata.title?.toString().orEmpty()
        toast(context.getString(R.string.remote_msg_skipped, title, a.device.name))
    }

    private fun followTrack(a: Active, mediaId: String, positionMs: Long) {
        val player = engine.masterPlayer
        val current = player.currentMediaItemIndex
        val target = (0 until player.mediaItemCount)
            .filter { player.getMediaItemAt(it).mediaId == mediaId }
            .minByOrNull { abs(it - current) } ?: return
        followLocal(a) { it.seekTo(target, positionMs) }
    }

    // ─── Following this phone ─────────────────────────────────────────────

    private val localListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val a = active ?: return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                a.lastLocalAutoTransitionAt = now()
                return
            }
            if (a.following) return
            userChange(a)
            scheduleSync(a, jump = true)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val a = active ?: return
            if (a.following || reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            userChange(a)
            scheduleSync(a, jump = false)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            val a = active ?: return
            if (a.following) return
            userChange(a)
            scheduleSync(a, jump = false)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val a = active ?: return
            if (a.following) return
            userChange(a)
            scheduleSync(a, jump = false, forceQueue = true)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val a = active ?: return
            if (a.following || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
            userChange(a)
            a.lastPlayPauseCommandAt = now()
            _session.update { it?.copy(isPlaying = playWhenReady) }
            // A queue on its way carries the play state already.
            if (playWhenReady && a.syncJob?.isActive == true) return
            a.link?.send(if (playWhenReady) RemoteProtocol.play() else RemoteProtocol.pause())
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            val a = active ?: return
            if (a.following || reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) return
            userChange(a)
            a.lastSeekAt = now()
            a.link?.send(RemoteProtocol.seek(newPosition.positionMs))
        }
    }

    private fun userChange(a: Active) {
        if (a.link == null) a.changedWhileAway = true
    }

    private fun attachTo(player: Player) {
        if (observedPlayer === player) return
        observedPlayer?.removeListener(localListener)
        player.addListener(localListener)
        observedPlayer = player
    }

    private fun detach() {
        observedPlayer?.removeListener(localListener)
        observedPlayer = null
    }

    private inline fun followLocal(a: Active, block: (Player) -> Unit) {
        a.following = true
        try {
            block(engine.masterPlayer)
        } finally {
            a.following = false
        }
    }

    private fun scheduleSync(a: Active, jump: Boolean, forceQueue: Boolean = false, waitMs: Long = SYNC_DEBOUNCE_MS) {
        a.pendingJump = a.pendingJump || jump
        a.syncJob?.cancel()
        val generation = ++a.syncGeneration
        a.syncJob = scope.launch {
            delay(waitMs)
            if (active !== a || generation != a.syncGeneration) return@launch
            val wantJump = a.pendingJump
            a.pendingJump = false
            runCatching { syncNow(a, wantJump, forceQueue) }.onFailure { Timber.tag(TAG).w(it, "Sync failed") }
        }
    }

    /**
     * Makes the other phone play what this one plays. Moves within the queue it already has where that queue still
     * holds the same songs ahead; otherwise sends a new one, which it swaps in around the current song without a hitch.
     */
    private fun syncNow(a: Active, jump: Boolean, forceQueue: Boolean) {
        val link = a.link ?: return
        val local = readWindow() ?: return
        val current = local.items[local.currentIndex]
        if (isSpotify(current) || (a.acked && current.mediaId in a.missing && current.mediaId in a.sentIds)) {
            handleMissingCurrent(a, current)
            return
        }
        if (!forceQueue && a.acked && a.sentIds.isNotEmpty()) {
            val at = nearestIndexOf(a.sentIds, current.mediaId, a.remoteIndex)
            if (at >= 0 && remoteHasSameAhead(a.sentIds, at, local)) {
                if (jump) {
                    a.lastTrackCommandAt = now()
                    a.remoteIndex = at
                    link.send(RemoteProtocol.jump(a.queueId, current.mediaId, at, local.positionMs, local.playWhenReady))
                }
                return
            }
        }
        sendQueue(a, link, local)
    }

    private fun sendQueue(a: Active, link: RemoteLink, local: LocalWindow) {
        val tracks = local.items.filterNot(::isSpotify).map(::trackOf)
        val ids = tracks.map { it.id }
        val currentId = local.items[local.currentIndex].mediaId
        val index = nearestIndexOf(ids, currentId, local.currentIndex).coerceAtLeast(0)
        a.queueId = maxOf(a.queueId + 1, System.currentTimeMillis())
        a.sentIds = ids
        a.acked = false
        a.remoteIndex = index
        a.lastTrackCommandAt = now()
        a.skipStreak = 0
        link.send(
            RemoteProtocol.queue(a.queueId, tracks, index, local.positionMs, local.playWhenReady)
                .put("repeat", local.repeatMode)
        )
    }

    /** True when the other phone has the same songs ahead of [at] as this one, far enough to play on without us. */
    private fun remoteHasSameAhead(sent: List<String>, at: Int, local: LocalWindow): Boolean {
        val remoteAhead = sent.subList(at, sent.size)
        val localAhead = local.items.subList(local.currentIndex, local.items.size).filterNot(::isSpotify).map { it.mediaId }
        val shared = minOf(remoteAhead.size, localAhead.size)
        if (remoteAhead.subList(0, shared) != localAhead.subList(0, shared)) return false
        // The queue here ends within the window: the other phone must end at the same song.
        if (localAhead.size < WINDOW_AHEAD) return remoteAhead.size == localAhead.size
        return remoteAhead.size >= MIN_AHEAD
    }

    /** This phone moved to a song the other phone does not have: skip on, like Sonos does with songs it cannot play. */
    private fun handleMissingCurrent(a: Active, current: MediaItem) {
        val player = engine.masterPlayer
        tellMissing(a, current)
        if (a.skipStreak < MAX_SKIP_STREAK && player.hasNextMediaItem()) {
            a.skipStreak++
            // Counts as a skip of the user's, so the next song goes through the same check.
            player.seekToNextMediaItem()
        } else {
            a.skipStreak = 0
            followLocal(a) { it.pause() }
            a.link?.send(RemoteProtocol.pause())
        }
    }

    private fun readWindow(): LocalWindow? {
        val player = engine.masterPlayer
        val currentIndex = player.currentMediaItemIndex
        if (player.currentMediaItem == null || currentIndex == C.INDEX_UNSET) return null
        val timeline = player.currentTimeline
        if (currentIndex >= timeline.windowCount) return null
        val shuffle = player.shuffleModeEnabled
        val window = Timeline.Window()
        val before = ArrayList<MediaItem>()
        var index = timeline.getPreviousWindowIndex(currentIndex, Player.REPEAT_MODE_OFF, shuffle)
        while (index != C.INDEX_UNSET && before.size < WINDOW_BEHIND) {
            before.add(0, timeline.getWindow(index, window).mediaItem)
            index = timeline.getPreviousWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        val ahead = ArrayList<MediaItem>()
        index = currentIndex
        while (index != C.INDEX_UNSET && ahead.size <= WINDOW_AHEAD) {
            ahead += timeline.getWindow(index, window).mediaItem
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        // Repeating everything only works over there when everything is over there.
        val wholeQueue = before.size + ahead.size >= timeline.windowCount
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ALL && !wholeQueue) Player.REPEAT_MODE_OFF else player.repeatMode
        return LocalWindow(before + ahead, before.size, player.currentPosition, player.playWhenReady, repeat)
    }

    private fun trackOf(item: MediaItem): RemoteTrack {
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        return RemoteTrack(
            id = item.mediaId,
            pathTail = RemoteTrack.pathTail(extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM) ?: metadata.albumTitle?.toString().orEmpty(),
            durationMs = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION)?.takeIf { it > 0 } ?: metadata.durationMs ?: 0L
        )
    }

    private fun isSpotify(item: MediaItem) = SpotifyIds.item(item.localConfiguration?.uri) != null

    // ─── Volume ───────────────────────────────────────────────────────────

    fun setVolume(percent: Int) {
        val a = active ?: return
        val v = percent.coerceIn(0, 100)
        _session.update { it?.copy(volume = v) }
        // A drag gives a value every frame; the radio gets one every so often and always the last.
        volumeJob?.cancel()
        val wait = (VOLUME_SEND_INTERVAL_MS - (now() - lastVolumeSentAt)).coerceAtLeast(0)
        volumeJob = scope.launch {
            delay(wait)
            lastVolumeSentAt = now()
            a.link?.send(RemoteProtocol.volume(v))
        }
    }

    /** For the volume keys: one step of the other phone's volume. False when nothing plays elsewhere. */
    fun adjustVolume(up: Boolean): Boolean {
        val state = _session.value ?: return false
        val steps = state.volumeSteps.takeIf { it > 0 } ?: DEFAULT_STEPS
        val step = Math.round((state.volume ?: 50) * steps / 100f) + if (up) 1 else -1
        setVolume((step.coerceIn(0, steps) * 100f / steps).roundToIntSafe())
        return true
    }

    private fun Float.roundToIntSafe(): Int = Math.round(this)

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        private const val TAG = "RemotePlayer"
        private const val ROLE = "controller"
        private const val PROBE_TIMEOUT_MS = 6_000L
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val RECONNECT_TIMEOUT_MS = 6_000L
        private const val RESUME_TIMEOUT_MS = 6_000L
        private const val HELLO_TIMEOUT_MS = 3_000L
        private const val SEARCH_INTERVAL_MS = 20_000L
        private const val STANDBY_KEEP_MS = 20_000L
        private const val PING_MS = 2_000L
        /** About four missed pings. */
        private const val SILENT_LIMIT_MS = 8_000L
        private const val FAST_RECONNECTS = 20
        private const val RECONNECT_FAST_MS = 3_000L
        private const val RECONNECT_SLOW_MS = 10_000L
        private const val RESUME_MAX_AGE_MS = 12 * 60 * 60 * 1000L
        private const val RESUME_QUEUE_CHECKS = 12
        private const val RESUME_QUEUE_CHECK_MS = 500L
        private const val SYNC_DEBOUNCE_MS = 150L
        private const val TRACK_COMMAND_GRACE_MS = 2_500L
        private const val PLAY_PAUSE_GRACE_MS = 2_000L
        private const val SEEK_GRACE_MS = 1_500L
        private const val AUTO_ADVANCE_GRACE_MS = 5_000L
        private const val DRIFT_LIMIT_MS = 600L
        private const val PAUSED_DRIFT_LIMIT_MS = 1_500L
        private const val PAUSED_ALIGN_LIMIT_MS = 200L
        /** About half a round trip over Bluetooth. */
        private const val LINK_LATENCY_MS = 40L
        private const val WINDOW_BEHIND = 20
        private const val WINDOW_AHEAD = 300
        /** With fewer songs ahead than this the other phone gets a fresh queue. */
        private const val MIN_AHEAD = 100
        private const val MAX_SKIP_STREAK = 25
        /** Fewer songs than this left over there, and it gets the next ones. */
        private const val REFILL_AT = 50
        private const val VOLUME_SEND_INTERVAL_MS = 80L
        private const val VOLUME_SETTLE_MS = 1_500L
        private const val VOLUME_HELD_TOAST_MS = 30_000L
        private const val DEFAULT_STEPS = 15

        internal fun nearestIndexOf(ids: List<String>, id: String, around: Int): Int {
            var best = -1
            for (i in ids.indices) {
                if (ids[i] == id && (best < 0 || abs(i - around) < abs(best - around))) best = i
            }
            return best
        }
    }
}
