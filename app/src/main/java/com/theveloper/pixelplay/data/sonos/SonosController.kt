package com.theveloper.pixelplay.data.sonos

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class SonosSessionState(
    val group: SonosGroup,
    val isConnecting: Boolean = true,
    val isPlaying: Boolean = false,
    val groupVolume: Int? = null,
    val speakerVolumes: Map<String, Int> = emptyMap()
)

/**
 * Plays the app's queue on a Sonos group.
 *
 * The phone stays in charge: its player keeps running muted (see SonosAudioGate), so the
 * queue, notification, lock screen and swipes work as usual. Every user action on the phone
 * is sent to the speakers, which stream the songs from Navidrome. The Sonos leads on timing:
 * the muted phone is moved to the speaker's position and follows changes made in the Sonos app.
 * The Sonos queue holds the upcoming songs, so the music keeps going when the phone is off.
 */
@Singleton
@OptIn(UnstableApi::class)
class SonosController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: DualPlayerEngine,
    private val resolver: SonosTrackResolver,
    userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val soap = SonosSoapClient()
    private val discovery = SonosDiscovery(context, soap)
    private val commandMutex = Mutex()

    private val _groups = MutableStateFlow<List<SonosGroup>>(emptyList())
    val groups: StateFlow<List<SonosGroup>> = _groups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _session = MutableStateFlow<SonosSessionState?>(null)
    val session: StateFlow<SonosSessionState?> = _session.asStateFlow()

    private var skipUnavailableSongs = true
    private var active: ActiveSession? = null
    private var observedPlayer: Player? = null
    private var volumeJob: Job? = null
    private var lastVolumeChangeAt = 0L

    private class ActiveSession(var group: SonosGroup) {
        /** Mirror of the Sonos queue: track number = index + 1. */
        val queue = ArrayList<SonosTrack>()
        var transportState = ""
        var positionMs = 0L
        var lastTrackCommandAt = 0L
        var lastPlayPauseCommandAt = 0L
        var lastResetAt = 0L
        var lastLocalAutoTransitionAt = 0L
        /** Local player events before this moment were caused by us following the Sonos. */
        var followUntil = 0L
        var skipStreak = 0
        /** The phone is skipping a song that is not on Navidrome; the Sonos must not restart. */
        var skipping = false
        var pendingJump = false
        var pollJob: Job? = null
        var syncJob: Job? = null
        var appendJob: Job? = null
    }

    private data class LocalState(
        val upcomingIds: List<String>,
        val currentMediaId: String,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val isPlaying: Boolean
    )

    init {
        scope.launch {
            userPreferencesRepository.sonosSkipUnavailableFlow.collect { skipUnavailableSongs = it }
        }
        engine.addPlayerSwapListener { newPlayer ->
            if (active != null) attachTo(newPlayer)
        }
    }

    // ─── Discovery and groups ─────────────────────────────────────────────

    fun refresh() {
        if (_isScanning.value) return
        scope.launch {
            _isScanning.value = true
            try {
                applyGroups(discovery.discoverGroups())
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun applyGroups(groups: List<SonosGroup>) {
        _groups.value = groups
        val s = active ?: return
        val updated = groups.firstOrNull { it.coordinatorUuid == s.group.coordinatorUuid }
        if (updated != null) {
            s.group = updated
            _session.update { it?.copy(group = updated) }
        } else if (groups.isNotEmpty()) {
            // Our speaker joined another group in the Sonos app.
            endSession(R.string.sonos_msg_taken_over, s.group.coordinator.name, pauseLocal = true)
        }
    }

    /** Puts exactly [memberUuids] (plus the coordinator) in the group, like the Sonos app's Group screen. */
    fun setGroupMembers(groupId: String, memberUuids: Set<String>) {
        val group = _groups.value.firstOrNull { it.id == groupId } ?: return
        val everyone = _groups.value.flatMap { it.members }
        val coordinator = group.coordinator
        scope.launch {
            commandMutex.withLock {
                runCatching {
                    for (speaker in everyone) {
                        if (speaker.uuid == coordinator.uuid) continue
                        val inGroup = group.members.any { it.uuid == speaker.uuid }
                        val wanted = speaker.uuid in memberUuids
                        if (wanted && !inGroup) {
                            soap.call(
                                speaker.host, SonosService.AV_TRANSPORT, "SetAVTransportURI",
                                "InstanceID" to 0,
                                "CurrentURI" to "x-rincon:${coordinator.uuid}",
                                "CurrentURIMetaData" to ""
                            )
                        } else if (!wanted && inGroup) {
                            soap.call(
                                speaker.host, SonosService.AV_TRANSPORT, "BecomeCoordinatorOfStandaloneGroup",
                                "InstanceID" to 0
                            )
                        }
                    }
                }.onFailure(::report)
            }
            delay(700)
            runCatching { discovery.readGroups(coordinator.host) }.onSuccess(::applyGroups)
            active?.let { refreshVolumes(it) }
        }
    }

    // ─── Session ──────────────────────────────────────────────────────────

    fun connect(groupId: String) {
        val group = _groups.value.firstOrNull { it.id == groupId } ?: return
        val previous = active
        if (previous?.group?.coordinatorUuid == group.coordinatorUuid) return
        scope.launch {
            if (previous != null) {
                stopJobs(previous)
                active = null
                commandMutex.withLock { runCatching { avt(previous, "Pause") } }
            }
            _session.value = SonosSessionState(group)
            val problem = resolver.prepare()
            if (problem != null) {
                _session.value = null
                if (previous != null) restorePhone(pauseLocal = true)
                toast(problemMessage(problem))
                return@launch
            }
            val s = ActiveSession(group)
            active = s
            engine.setRemoteOutputActive(true)
            attachTo(engine.masterPlayer)
            val ok = withContext(NonCancellable) {
                commandMutex.withLock {
                    runCatching {
                        avt(s, "SetPlayMode", "NewPlayMode" to "NORMAL")
                        syncNow(s, jump = true, forceReset = true)
                    }.onFailure { report(it) }.isSuccess
                }
            }
            if (!ok) {
                endSession(null, null, pauseLocal = false)
                return@launch
            }
            _session.update { it?.copy(isConnecting = false) }
            refreshVolumes(s)
            startPolling(s)
        }
    }

    /** Back to the phone: the Sonos pauses and the phone plays on from the same spot. */
    fun switchToPhone() {
        val s = active ?: return
        scope.launch {
            stopJobs(s)
            commandMutex.withLock { runCatching { avt(s, "Pause") } }
            endSession(null, null, pauseLocal = false)
        }
    }

    private fun endSession(messageRes: Int?, arg: String?, pauseLocal: Boolean) {
        val s = active ?: return
        stopJobs(s)
        active = null
        detach()
        restorePhone(pauseLocal)
        resolver.clearSession()
        _session.value = null
        if (messageRes != null) toast(context.getString(messageRes, arg ?: ""))
    }

    private fun restorePhone(pauseLocal: Boolean) {
        if (pauseLocal) {
            engine.masterPlayer.pause()
            // Let the pause reach the audio sink before the sound comes back.
            scope.launch {
                delay(300)
                if (active == null) engine.setRemoteOutputActive(false)
            }
        } else {
            engine.setRemoteOutputActive(false)
        }
    }

    private fun stopJobs(s: ActiveSession) {
        s.pollJob?.cancel()
        s.syncJob?.cancel()
        s.appendJob?.cancel()
    }

    // ─── Volume ───────────────────────────────────────────────────────────

    fun setGroupVolume(volume: Int) {
        val s = active ?: return
        val v = volume.coerceIn(0, 100)
        _session.update { state ->
            // The rooms follow the group slider right away; the next refresh brings the exact values.
            val delta = v - (state?.groupVolume ?: v)
            state?.copy(
                groupVolume = v,
                speakerVolumes = state.speakerVolumes.mapValues { (it.value + delta).coerceIn(0, 100) }
            )
        }
        sendVolume {
            soap.call(
                s.group.coordinator.host, SonosService.GROUP_RENDERING_CONTROL, "SetGroupVolume",
                "InstanceID" to 0, "DesiredVolume" to v
            )
        }
    }

    fun setSpeakerVolume(uuid: String, volume: Int) {
        val s = active ?: return
        val speaker = s.group.members.firstOrNull { it.uuid == uuid } ?: return
        val v = volume.coerceIn(0, 100)
        _session.update { it?.copy(speakerVolumes = it.speakerVolumes + (uuid to v)) }
        sendVolume {
            soap.call(
                speaker.host, SonosService.RENDERING_CONTROL, "SetVolume",
                "InstanceID" to 0, "Channel" to "Master", "DesiredVolume" to v
            )
        }
    }

    /** For the phone's volume keys. Returns false when no Sonos session is active. */
    fun adjustGroupVolume(delta: Int): Boolean {
        val state = _session.value ?: return false
        if (state.isConnecting) return true
        setGroupVolume((state.groupVolume ?: 20) + delta)
        return true
    }

    private fun sendVolume(block: suspend () -> Unit) {
        lastVolumeChangeAt = SystemClock.elapsedRealtime()
        volumeJob?.cancel()
        volumeJob = scope.launch {
            delay(90)
            runCatching { block() }.onFailure(::report)
        }
    }

    private suspend fun refreshVolumes(s: ActiveSession) {
        if (SystemClock.elapsedRealtime() - lastVolumeChangeAt < 2_000) return
        runCatching {
            val group = soap.call(
                s.group.coordinator.host, SonosService.GROUP_RENDERING_CONTROL, "GetGroupVolume",
                "InstanceID" to 0
            )["CurrentVolume"]?.toIntOrNull()
            val speakers = if (s.group.members.size > 1) {
                s.group.members.associate { speaker ->
                    speaker.uuid to (soap.call(
                        speaker.host, SonosService.RENDERING_CONTROL, "GetVolume",
                        "InstanceID" to 0, "Channel" to "Master"
                    )["CurrentVolume"]?.toIntOrNull() ?: 0)
                }
            } else {
                emptyMap()
            }
            // Drop the answer if the user moved a volume while it was on its way.
            val stale = SystemClock.elapsedRealtime() - lastVolumeChangeAt < 2_000
            if (active === s && !stale) _session.update { it?.copy(groupVolume = group, speakerVolumes = speakers) }
        }
    }

    // ─── Following the phone ──────────────────────────────────────────────

    private val localListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val s = active ?: return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                s.lastLocalAutoTransitionAt = now()
                return
            }
            if (isFollowing(s)) return
            if (s.skipping) {
                s.skipping = false
                scheduleSync(s, jump = false)
                return
            }
            scheduleSync(s, jump = true)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val s = active ?: return
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) scheduleSync(s, jump = false)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val s = active ?: return
            if (isFollowing(s) || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
            s.lastPlayPauseCommandAt = now()
            // A pending sync sends the play state itself once the queue is right.
            if (s.syncJob?.isActive == true) return
            command(s) { if (playWhenReady) avt(s, "Play", "Speed" to "1") else pauseSonos(s) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val s = active ?: return
            if (isFollowing(s) || reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) return
            s.lastTrackCommandAt = now()
            command(s) { seekSonos(s, newPosition.positionMs) }
        }
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

    private fun isFollowing(s: ActiveSession) = now() < s.followUntil

    private inline fun followLocal(s: ActiveSession, block: (Player) -> Unit) {
        s.followUntil = now() + FOLLOW_GRACE_MS
        block(engine.masterPlayer)
    }

    private fun command(s: ActiveSession, block: suspend () -> Unit) {
        scope.launch {
            commandMutex.withLock {
                if (active === s) withContext(NonCancellable) { runCatching { block() }.onFailure(::report) }
            }
        }
    }

    private fun scheduleSync(s: ActiveSession, jump: Boolean) {
        s.pendingJump = s.pendingJump || jump
        s.syncJob?.cancel()
        s.syncJob = scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            withContext(NonCancellable) {
                commandMutex.withLock {
                    if (active !== s) return@withLock
                    val wantJump = s.pendingJump
                    s.pendingJump = false
                    runCatching { syncNow(s, wantJump, forceReset = false) }.onFailure(::report)
                }
            }
        }
    }

    private fun readLocal(): LocalState? {
        val player = engine.masterPlayer
        val item = player.currentMediaItem ?: return null
        val timeline = player.currentTimeline
        val ids = ArrayList<String>()
        val window = Timeline.Window()
        var index = player.currentMediaItemIndex
        while (index != C.INDEX_UNSET && index < timeline.windowCount && ids.size <= MAX_UPCOMING) {
            ids += timeline.getWindow(index, window).mediaItem.mediaId
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        return LocalState(ids, item.mediaId, player.currentPosition, player.playWhenReady, player.isPlaying)
    }

    /**
     * Makes the Sonos play what the phone plays. Reuses what is already on the Sonos queue
     * where possible, so editing the upcoming songs never interrupts the current one.
     */
    private suspend fun syncNow(s: ActiveSession, jump: Boolean, forceReset: Boolean) {
        s.appendJob?.cancel()
        val local = readLocal() ?: return
        val resolved = resolver.resolve(local.upcomingIds)
        val current = resolved[local.currentMediaId]
        if (current == null) {
            handleUnplayable(s)
            return
        }
        s.skipStreak = 0
        val upcoming = local.upcomingIds.drop(1).mapNotNull { resolved[it] }

        if (!forceReset && s.queue.isNotEmpty()) {
            val media = avt(s, "GetMediaInfo")["CurrentURI"].orEmpty()
            val position = avt(s, "GetPositionInfo")
            var index = (position["Track"]?.toIntOrNull() ?: 0) - 1
            if (media == s.group.queueUri && index in s.queue.indices) {
                if (s.queue[index].mediaId != current.mediaId) {
                    val found = nearestIndexOf(s.queue, current.mediaId, index)
                    if (found >= 0) {
                        s.lastTrackCommandAt = now()
                        avt(s, "Seek", "Unit" to "TRACK_NR", "Target" to found + 1)
                        index = found
                        if (local.positionMs > 1_500) seekSonos(s, local.positionMs)
                    } else {
                        index = -1
                    }
                } else if (jump && abs(SonosSoapClient.hmsToMs(position["RelTime"]) - local.positionMs) > 2_500) {
                    s.lastTrackCommandAt = now()
                    seekSonos(s, local.positionMs)
                }
                if (index >= 0) {
                    applyPlayState(s, local.playWhenReady)
                    replaceUpcoming(s, index, upcoming)
                    return
                }
            }
        }
        fullReset(s, current, upcoming, local)
    }

    private suspend fun fullReset(s: ActiveSession, current: SonosTrack, upcoming: List<SonosTrack>, local: LocalState) {
        s.lastResetAt = now()
        s.lastTrackCommandAt = now()
        avt(s, "RemoveAllTracksFromQueue")
        s.queue.clear()
        addToQueue(s, current)
        avt(s, "SetAVTransportURI", "CurrentURI" to s.group.queueUri, "CurrentURIMetaData" to "")
        avt(s, "Seek", "Unit" to "TRACK_NR", "Target" to 1)
        val resumeAt = local.positionMs.takeIf { it > 2_000 }
        var seeked = resumeAt == null || runCatching { seekSonos(s, resumeAt) }.isSuccess
        applyPlayState(s, local.playWhenReady)
        // Some speakers only accept a time seek once the track is loaded.
        if (!seeked && resumeAt != null && local.playWhenReady) {
            delay(600)
            seeked = runCatching { seekSonos(s, resumeAt) }.isSuccess
        }
        appendTracks(s, upcoming)
    }

    private suspend fun replaceUpcoming(s: ActiveSession, index: Int, wanted: List<SonosTrack>) {
        val existing = s.queue.subList(index + 1, s.queue.size).map { it.mediaId }
        var same = 0
        while (same < existing.size && same < wanted.size && existing[same] == wanted[same].mediaId) same++
        if (same == existing.size && same == wanted.size) return
        if (same < existing.size) {
            avt(
                s, "RemoveTrackRangeFromQueue",
                "UpdateID" to 0,
                "StartingIndex" to index + 2 + same,
                "NumberOfTracks" to existing.size - same
            )
            s.queue.subList(index + 1 + same, s.queue.size).clear()
        }
        appendTracks(s, wanted.drop(same))
    }

    /** Adds the next two songs right away and the rest in the background. */
    private suspend fun appendTracks(s: ActiveSession, tracks: List<SonosTrack>) {
        tracks.take(2).forEach { addToQueue(s, it) }
        val rest = tracks.drop(2)
        if (rest.isEmpty()) return
        s.appendJob = scope.launch {
            for (track in rest) {
                if (!isActive || active !== s) break
                val ok = commandMutex.withLock {
                    withContext(NonCancellable) { runCatching { addToQueue(s, track) }.onFailure(::report).isSuccess }
                }
                if (!ok) break
            }
        }
    }

    private suspend fun addToQueue(s: ActiveSession, track: SonosTrack) {
        avt(
            s, "AddURIToQueue",
            "EnqueuedURI" to track.uri,
            "EnqueuedURIMetaData" to track.metadata,
            "DesiredFirstTrackNumberEnqueued" to 0,
            "EnqueueAsNext" to 0
        )
        s.queue += track
    }

    private suspend fun applyPlayState(s: ActiveSession, playing: Boolean) {
        val state = avt(s, "GetTransportInfo")["CurrentTransportState"].orEmpty()
        s.lastPlayPauseCommandAt = now()
        if (playing && state != "PLAYING") avt(s, "Play", "Speed" to "1")
        if (!playing && state == "PLAYING") pauseSonos(s)
    }

    private suspend fun handleUnplayable(s: ActiveSession) {
        val player = engine.masterPlayer
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        if (skipUnavailableSongs && s.skipStreak < MAX_SKIP_STREAK && player.hasNextMediaItem()) {
            if (s.skipStreak == 0) toast(context.getString(R.string.sonos_msg_skipped, title))
            s.skipStreak++
            // The resulting transition triggers the next sync, without restarting the Sonos.
            s.skipping = true
            player.seekToNextMediaItem()
            s.skipping = false
        } else {
            s.skipStreak = 0
            followLocal(s) { it.pause() }
            runCatching { pauseSonos(s) }
            toast(context.getString(R.string.sonos_msg_stopped, title))
        }
    }

    private suspend fun pauseSonos(s: ActiveSession) {
        // Pausing an already stopped speaker returns UPnP 701; that is fine.
        runCatching { avt(s, "Pause") }.onFailure { if ((it as? SonosException)?.upnpCode != 701) throw it }
    }

    private suspend fun seekSonos(s: ActiveSession, positionMs: Long) {
        avt(s, "Seek", "Unit" to "REL_TIME", "Target" to SonosSoapClient.msToHms(positionMs))
    }

    // ─── Following the Sonos ──────────────────────────────────────────────

    private fun startPolling(s: ActiveSession) {
        s.pollJob = scope.launch {
            var tick = 0
            var failures = 0
            while (isActive && active === s) {
                val ok = commandMutex.withLock {
                    if (active !== s) return@withLock true
                    runCatching { pollOnce(s, tick) }
                        .onFailure { Timber.tag(TAG).w(it, "Sonos poll failed") }
                        .isSuccess
                }
                failures = if (ok) 0 else failures + 1
                if (failures >= MAX_POLL_FAILURES) {
                    endSession(R.string.sonos_msg_lost, s.group.coordinator.name, pauseLocal = true)
                    break
                }
                tick++
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun pollOnce(s: ActiveSession, tick: Int) {
        val transport = avt(s, "GetTransportInfo")["CurrentTransportState"].orEmpty()
        val position = avt(s, "GetPositionInfo")
        val index = (position["Track"]?.toIntOrNull() ?: 0) - 1
        val trackUri = position["TrackURI"].orEmpty()
        val relMs = SonosSoapClient.hmsToMs(position["RelTime"])
        s.transportState = transport
        s.positionMs = relMs
        _session.update { it?.copy(isPlaying = transport == "PLAYING" || transport == "TRANSITIONING") }

        // Only once our queue is on the speaker can someone else take it over.
        val settled = s.lastResetAt > 0 && now() - s.lastResetAt > TAKEOVER_GRACE_MS
        // A track outside our queue usually means the TV or another app took over: check right away.
        if (settled && (tick % 2 == 0 || index !in s.queue.indices)) {
            val media = avt(s, "GetMediaInfo")["CurrentURI"].orEmpty()
            if (media != s.group.queueUri) {
                endSession(R.string.sonos_msg_taken_over, s.group.coordinator.name, pauseLocal = true)
                return
            }
        }
        if (tick % 3 == 0) refreshVolumes(s)
        if (tick % 15 == 14) runCatching { discovery.readGroups(s.group.coordinator.host) }.onSuccess(::applyGroups)
        if (active !== s) return

        if (index !in s.queue.indices) return
        val busy = now() - s.lastTrackCommandAt < TRACK_COMMAND_GRACE_MS || s.syncJob?.isActive == true
        if (settled && !busy && !sameStream(s.queue[index].uri, trackUri)) {
            // The queue was edited in the Sonos app, so our copy is off: rebuild it from the phone.
            s.queue.clear()
            scheduleSync(s, jump = false)
            return
        }
        val local = readLocal() ?: return
        val sonosId = s.queue[index].mediaId
        if (local.currentMediaId != sonosId) {
            if (busy) return
            val localIndex = s.queue.indexOfFirst { it.mediaId == local.currentMediaId }
            val autoAdvancedRecently = now() - s.lastLocalAutoTransitionAt < AUTO_ADVANCE_GRACE_MS
            when {
                // The phone finished the song a moment before the speakers did.
                autoAdvancedRecently && localIndex == index + 1 && transport == "PLAYING" -> Unit
                // The phone moved on by itself (end of queue, repeat): bring the Sonos along.
                autoAdvancedRecently -> scheduleSync(s, jump = true)
                // Changed on the Sonos side (Sonos app, buttons on the speaker): follow it.
                else -> followTrack(s, sonosId, relMs)
            }
            return
        }

        val playPauseRecent = now() - s.lastPlayPauseCommandAt < PLAY_PAUSE_GRACE_MS
        if (!playPauseRecent) {
            if (transport == "PLAYING" && !local.playWhenReady) followLocal(s) { it.play() }
            if ((transport == "PAUSED_PLAYBACK" || transport == "STOPPED") && local.playWhenReady) {
                followLocal(s) { it.pause() }
            }
        }
        if (transport == "PLAYING" && local.isPlaying && !busy && abs(relMs - local.positionMs) > DRIFT_LIMIT_MS) {
            followLocal(s) { it.seekTo(relMs) }
        }
    }

    private fun followTrack(s: ActiveSession, mediaId: String, positionMs: Long) {
        val player = engine.masterPlayer
        val current = player.currentMediaItemIndex
        val target = (0 until player.mediaItemCount)
            .filter { player.getMediaItemAt(it).mediaId == mediaId }
            .minByOrNull { abs(it - current) } ?: return
        followLocal(s) { it.seekTo(target, positionMs) }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private suspend fun avt(s: ActiveSession, action: String, vararg args: Pair<String, Any>): Map<String, String> =
        soap.call(s.group.coordinator.host, SonosService.AV_TRANSPORT, action, "InstanceID" to 0, *args)

    private fun report(error: Throwable) {
        // A newer volume or sync request replaced this one; that is not a failure.
        if (error is CancellationException) return
        Timber.tag(TAG).w(error, "Sonos command failed")
        val detail = (error as? SonosException)?.upnpCode?.let { "UPnP $it" } ?: error.message.orEmpty()
        toast(context.getString(R.string.sonos_msg_failed, detail))
    }

    private fun problemMessage(problem: SonosSourceProblem): String = context.getString(
        when (problem) {
            SonosSourceProblem.NOT_LOGGED_IN -> R.string.sonos_problem_not_logged_in
            SonosSourceProblem.TAILSCALE_ADDRESS -> R.string.sonos_problem_tailscale
            SonosSourceProblem.NO_SONGS -> R.string.sonos_problem_no_songs
        }
    )

    private fun toast(text: String) {
        scope.launch { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        private const val TAG = "SonosController"
        private const val MAX_UPCOMING = 100
        private const val MAX_SKIP_STREAK = 25
        private const val MAX_POLL_FAILURES = 8
        private const val POLL_INTERVAL_MS = 1_000L
        private const val SYNC_DEBOUNCE_MS = 250L
        private const val FOLLOW_GRACE_MS = 1_200L
        private const val TRACK_COMMAND_GRACE_MS = 4_000L
        private const val PLAY_PAUSE_GRACE_MS = 3_000L
        private const val AUTO_ADVANCE_GRACE_MS = 7_000L
        private const val TAKEOVER_GRACE_MS = 6_000L
        private const val DRIFT_LIMIT_MS = 2_500L

        /**
         * Whether the speaker plays the stream we queued. Compares the Subsonic song id only,
         * because a speaker may hand the URL back with other encoding or parameter order.
         * When either id is unknown it assumes a match rather than rebuilding for nothing.
         */
        internal fun sameStream(queued: String, reported: String): Boolean {
            if (reported.isBlank()) return true
            val a = subsonicId(queued) ?: return true
            val b = subsonicId(reported) ?: return true
            return a == b
        }

        internal fun subsonicId(uri: String): String? {
            val query = uri.substringAfter('?', "").substringBefore('#')
            if (query.isEmpty()) return null
            return query.split('&').firstNotNullOfOrNull { part ->
                val (key, value) = part.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                if (key == "id" || key == "amp;id") java.net.URLDecoder.decode(value, "UTF-8").ifBlank { null } else null
            }
        }

        /** Index of [mediaId] in [queue] closest to [around], or -1. */
        internal fun nearestIndexOf(queue: List<SonosTrack>, mediaId: String, around: Int): Int =
            queue.indices.filter { queue[it].mediaId == mediaId }.minByOrNull { abs(it - around) } ?: -1
    }
}
