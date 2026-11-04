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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

data class SonosSessionState(
    val group: SonosGroup,
    val isConnecting: Boolean = true,
    val isPlaying: Boolean = false,
    val groupVolume: Int? = null,
    val speakerVolumes: Map<String, Int> = emptyMap(),
    val isMuted: Boolean = false
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
/**
 * How far the speaker is ahead of the phone. The speaker only reports whole seconds, so one
 * reading says the true difference lies somewhere in a window of a second. Readings taken at
 * different moments within that second overlap in an ever smaller window, which gives the
 * difference to within a few hundred milliseconds after a handful of polls.
 */
internal class SpeakerOffsetEstimate {
    private var low = Long.MIN_VALUE
    private var high = Long.MAX_VALUE

    /** Takes one reading; returns the difference once it is known well enough, otherwise null. */
    fun add(speakerWholeSecondsMs: Long, phoneMs: Long): Long? {
        val sample = speakerWholeSecondsMs - phoneMs
        val newLow = maxOf(low, sample)
        val newHigh = minOf(high, sample + WINDOW_MS)
        // No overlap: one of the two clocks jumped, so what we had says nothing any more.
        if (newLow > newHigh) {
            low = sample
            high = sample + WINDOW_MS
        } else {
            low = newLow
            high = newHigh
        }
        return if (high - low <= MAX_WIDTH_MS) (low + high) / 2 else null
    }

    fun reset() {
        low = Long.MIN_VALUE
        high = Long.MAX_VALUE
    }

    private companion object {
        const val WINDOW_MS = 999L
        const val MAX_WIDTH_MS = 400L
    }
}

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
    private val prefs = context.getSharedPreferences("sonos", Context.MODE_PRIVATE)

    private val _groups = MutableStateFlow<List<SonosGroup>>(emptyList())
    val groups: StateFlow<List<SonosGroup>> = _groups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _session = MutableStateFlow<SonosSessionState?>(null)
    val session: StateFlow<SonosSessionState?> = _session.asStateFlow()

    private val volumeKeysEnabled = MutableStateFlow(true)

    /** The session, when the phone's volume keys should work on it. See [SonosSessionPlayer]. */
    val remoteVolume: StateFlow<SonosSessionState?> = _session
        .combine(volumeKeysEnabled) { state, keys -> state?.takeIf { keys && !it.isConnecting } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private var skipUnavailableSongs = true
    private var sonosEnabled = true
    private var lastRefreshAt = 0L
    private var active: ActiveSession? = null
    private var observedPlayer: Player? = null
    private var volumeJob: Job? = null
    private var pendingVolume: (suspend () -> Unit)? = null
    private var lastVolumeChangeAt = 0L
    /** Each room's volume at the start of the current group volume change. */
    private var volumeShares: Map<String, Int>? = null

    private class ActiveSession(var group: SonosGroup) {
        /** Mirror of the Sonos queue: track number = index + 1. */
        val queue = ArrayList<SonosTrack>()
        var transportState = ""
        var positionMs = 0L
        var lastTrackCommandAt = 0L
        var lastPlayPauseCommandAt = 0L
        var lastResetAt = 0L
        var lastLocalAutoTransitionAt = 0L
        /** We are moving the phone to follow the Sonos right now. The player reports that during the call. */
        var following = false
        /** Bumped by every sync request, so a sync that waited its turn can see a newer one behind it. */
        var syncGeneration = 0L
        var lastSkipAt = 0L
        /** The speaker went quiet during a run of skips and starts again on the song they end on. */
        var quietForSkips = false
        var skipStreak = 0
        /** The phone is skipping a song that is not on Navidrome; the Sonos must not restart. */
        var skipping = false
        /** The speaker could not seek while paused; put it on the phone's spot when it starts playing. */
        var alignOnPlay = false
        var pendingJump = false
        /** Picked up again after the app was closed: the phone follows the speaker until it has caught up. */
        var resumedUntil = 0L
        val speakerOffset = SpeakerOffsetEstimate()
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
        // Rooms from the last search show up right away; a fresh search runs in the background.
        _groups.value = discovery.cachedGroups()
        scope.launch { resumeSavedSession() }
        scope.launch {
            userPreferencesRepository.sonosSkipUnavailableFlow.collect { skipUnavailableSongs = it }
        }
        scope.launch {
            userPreferencesRepository.sonosVolumeKeysFlow.collect { volumeKeysEnabled.value = it }
        }
        scope.launch {
            userPreferencesRepository.sonosEnabledFlow.collect { enabled ->
                sonosEnabled = enabled
                if (enabled) warmUp()
            }
        }
        engine.addPlayerSwapListener { newPlayer ->
            if (active != null) attachTo(newPlayer)
        }
    }

    // ─── Discovery and groups ─────────────────────────────────────────────

    /** Searches in the background when the last search is older than a minute, so Connect opens with rooms. */
    fun warmUp() {
        if (!sonosEnabled || now() - lastRefreshAt < WARM_UP_INTERVAL_MS) return
        refresh()
    }

    fun refresh() {
        if (_isScanning.value) return
        lastRefreshAt = now()
        scope.launch {
            _isScanning.value = true
            try {
                val found = discovery.discoverGroups()
                // An empty answer on a flaky network should not wipe the rooms we know.
                if (found.isNotEmpty() || _groups.value.isEmpty()) applyGroups(found)
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun applyGroups(groups: List<SonosGroup>) {
        _groups.value = groups
        if (groups.isNotEmpty()) discovery.saveGroups(groups)
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

    /** Every room of the group plays on its own again. */
    fun ungroup(groupId: String) = setGroupMembers(groupId, emptySet())

    /** Adds the rooms of [groupId] to the group that is playing now. */
    fun addToActiveGroup(groupId: String) {
        val s = active ?: return
        val playing = _groups.value.firstOrNull { it.coordinatorUuid == s.group.coordinatorUuid } ?: return
        val extra = _groups.value.firstOrNull { it.id == groupId } ?: return
        setGroupMembers(playing.id, (playing.members + extra.members).map { it.uuid }.toSet())
    }

    // ─── Session ──────────────────────────────────────────────────────────

    private fun saveSession(s: ActiveSession) {
        prefs.edit().putString(
            KEY_SESSION,
            JSONObject()
                .put("coordinator", s.group.coordinatorUuid)
                .put("at", System.currentTimeMillis())
                .put("queue", JSONArray(s.queue.map { it.mediaId }))
                .toString()
        ).apply()
    }

    private fun clearSavedSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    /**
     * The app was closed or killed while the speakers kept going: picks the session up again,
     * and the phone follows the speaker from there. Starts by muting, because the queue the
     * app restores may start playing before the speakers have answered.
     */
    private suspend fun resumeSavedSession() {
        val saved = prefs.getString(KEY_SESSION, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        engine.setRemoteOutputActive(true)
        val group = _groups.value.firstOrNull { it.coordinatorUuid == saved.optString("coordinator") }
        val nrTracks = group?.let { speakerQueueLength(it, saved.optLong("at")) }
        if (group == null || nrTracks == null || resolver.prepare() != null) {
            clearSavedSession()
            if (active == null) engine.setRemoteOutputActive(false)
            return
        }
        val ids = saved.optJSONArray("queue")?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty()
        val resolved = resolver.resolve(ids)
        // Something else was connected to in the meantime.
        if (active != null) return
        val s = ActiveSession(group)
        s.queue += ids.mapNotNull { resolved[it] }.take(nrTracks)
        s.lastResetAt = 1L
        s.resumedUntil = now() + RESUME_GRACE_MS
        active = s
        _session.value = SonosSessionState(group, isConnecting = false)
        attachTo(engine.masterPlayer)
        refreshVolumes(s)
        startPolling(s)
    }

    /** How many songs are on the speaker's queue, or null when the speaker is not ours any more. */
    private suspend fun speakerQueueLength(group: SonosGroup, savedAt: Long): Int? = runCatching {
        val host = group.coordinator.host
        val media = soap.callQuick(host, SonosService.AV_TRANSPORT, "GetMediaInfo", "InstanceID" to 0)
        if (media["CurrentURI"] != group.queueUri) return null
        val state = soap.callQuick(host, SonosService.AV_TRANSPORT, "GetTransportInfo", "InstanceID" to 0)["CurrentTransportState"]
        val recent = System.currentTimeMillis() - savedAt < RESUME_PAUSED_WITHIN_MS
        val goesOn = state == "PLAYING" || state == "TRANSITIONING" || (state == "PAUSED_PLAYBACK" && recent)
        if (goesOn) media["NrTracks"]?.toIntOrNull() else null
    }.getOrNull()

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
        clearSavedSession()
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

    /**
     * Sets the group volume like the Sonos app: every room keeps its share, so a loud room
     * moves faster than a quiet one. The shares are taken at the start of a drag or a run of
     * key presses, so going down and back up again ends where it started.
     */
    fun setGroupVolume(volume: Int) {
        val s = active ?: return
        val state = _session.value ?: return
        val v = volume.coerceIn(0, 100)
        if (state.isMuted) setGroupMuted(false)
        val members = s.group.members
        if (members.size < 2 || members.any { it.uuid !in state.speakerVolumes }) {
            _session.update { it?.copy(groupVolume = v) }
            sendVolume {
                soap.call(
                    s.group.coordinator.host, SonosService.GROUP_RENDERING_CONTROL, "SetGroupVolume",
                    "InstanceID" to 0, "DesiredVolume" to v
                )
            }
            return
        }
        val base = volumeShares?.takeIf {
            now() - lastVolumeChangeAt < VOLUME_GESTURE_MS && it.keys == members.map { m -> m.uuid }.toSet()
        } ?: members.associate { it.uuid to state.speakerVolumes.getValue(it.uuid) }
        volumeShares = base
        val targets = groupVolumeTargets(base, v)
        _session.update { it?.copy(groupVolume = v, speakerVolumes = it.speakerVolumes + targets) }
        sendVolume {
            coroutineScope {
                members.map { speaker ->
                    async {
                        soap.call(
                            speaker.host, SonosService.RENDERING_CONTROL, "SetVolume",
                            "InstanceID" to 0, "Channel" to "Master", "DesiredVolume" to targets.getValue(speaker.uuid)
                        )
                    }
                }.awaitAll()
            }
        }
    }

    fun setGroupMuted(muted: Boolean) {
        val s = active ?: return
        lastVolumeChangeAt = now()
        _session.update { it?.copy(isMuted = muted) }
        scope.launch {
            runCatching {
                soap.call(
                    s.group.coordinator.host, SonosService.GROUP_RENDERING_CONTROL, "SetGroupMute",
                    "InstanceID" to 0, "DesiredMute" to if (muted) 1 else 0
                )
            }.onFailure(::report)
        }
    }

    fun setSpeakerVolume(uuid: String, volume: Int) {
        val s = active ?: return
        val speaker = s.group.members.firstOrNull { it.uuid == uuid } ?: return
        val v = volume.coerceIn(0, 100)
        // A room set by hand gets a new share for the next group change.
        volumeShares = null
        _session.update { state ->
            val speakers = state?.speakerVolumes.orEmpty() + (uuid to v)
            state?.copy(speakerVolumes = speakers, groupVolume = speakers.values.average().roundToInt())
        }
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

    /**
     * Sends only the newest volume, one at a time: a call that is already on its way cannot be
     * taken back, so sending the next one alongside it could let the older value win.
     */
    private fun sendVolume(block: suspend () -> Unit) {
        lastVolumeChangeAt = SystemClock.elapsedRealtime()
        pendingVolume = block
        if (volumeJob?.isActive == true) return
        volumeJob = scope.launch {
            delay(90)
            while (true) {
                val next = pendingVolume ?: break
                pendingVolume = null
                runCatching { next() }.onFailure(::report)
            }
        }
    }

    private suspend fun refreshVolumes(s: ActiveSession) {
        if (SystemClock.elapsedRealtime() - lastVolumeChangeAt < 2_000) return
        runCatching {
            val group = soap.call(
                s.group.coordinator.host, SonosService.GROUP_RENDERING_CONTROL, "GetGroupVolume",
                "InstanceID" to 0
            )["CurrentVolume"]?.toIntOrNull()
            val muted = runCatching {
                soap.call(
                    s.group.coordinator.host, SonosService.GROUP_RENDERING_CONTROL, "GetGroupMute",
                    "InstanceID" to 0
                )["CurrentMute"] == "1"
            }.getOrDefault(false)
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
            if (active === s && !stale) {
                _session.update { it?.copy(groupVolume = group, speakerVolumes = speakers, isMuted = muted) }
            }
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
            val inRun = now() - s.lastSkipAt < SKIP_RUN_MS
            s.lastSkipAt = now()
            if (inRun && !s.quietForSkips && engine.masterPlayer.playWhenReady) {
                // The speaker should not start every song that is being skipped past.
                s.quietForSkips = true
                s.lastPlayPauseCommandAt = now()
                command(s) { pauseSonos(s) }
            }
            if (inRun) s.lastPlayPauseCommandAt = now()
            scheduleSync(s, jump = true, waitMs = if (inRun) SKIP_RUN_DEBOUNCE_MS else SYNC_DEBOUNCE_MS)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val s = active ?: return
            if (isFollowing(s)) return
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) scheduleSync(s, jump = false)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val s = active ?: return
            if (isFollowing(s) || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
            s.lastPlayPauseCommandAt = now()
            // Starting waits for the queue to be right, or the old song plays for a moment. A sync that
            // is already running may have passed the play state, so this asks for one more after it.
            if (playWhenReady && s.syncJob?.isActive == true) {
                scheduleSync(s, jump = false)
                return
            }
            // A pause goes out right away, also in the middle of a sync: the order of the lock keeps it last.
            command(s) { applyPlayState(s) }
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

    private fun isFollowing(s: ActiveSession) = s.following || now() < s.resumedUntil

    private inline fun followLocal(s: ActiveSession, block: (Player) -> Unit) {
        s.following = true
        try {
            block(engine.masterPlayer)
        } finally {
            s.following = false
        }
    }

    private fun command(s: ActiveSession, block: suspend () -> Unit) {
        scope.launch {
            commandMutex.withLock {
                if (active === s) withContext(NonCancellable) { runCatching { block() }.onFailure(::report) }
            }
        }
    }

    private fun scheduleSync(s: ActiveSession, jump: Boolean, waitMs: Long = SYNC_DEBOUNCE_MS) {
        s.pendingJump = s.pendingJump || jump
        s.syncJob?.cancel()
        val generation = ++s.syncGeneration
        s.syncJob = scope.launch {
            delay(waitMs)
            withContext(NonCancellable) {
                commandMutex.withLock {
                    // A newer request behind this one reads the phone when it is its turn.
                    if (active !== s || generation != s.syncGeneration) return@withLock
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
        val generation = s.syncGeneration
        val local = readLocal() ?: return
        val resolved = resolver.resolve(local.upcomingIds)
        // The phone moved on during the lookup; the sync that caused is next in line.
        if (generation != s.syncGeneration) return
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
                    applyPlayState(s)
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
        val hadOtherSource = releaseOtherSource(s)
        avt(s, "RemoveAllTracksFromQueue")
        s.queue.clear()
        addToQueue(s, current)
        // A fresh queue starts at its first track, so no TRACK_NR seek is needed.
        avt(s, "SetAVTransportURI", "CurrentURI" to s.group.queueUri, "CurrentURIMetaData" to "")
        if (hadOtherSource) holdSpeaker(s)
        // Jump ahead by the time the speaker needs to start; the exact alignment follows once it plays.
        val phoneAt = readLocal()?.positionMs ?: local.positionMs
        // A quick call: if the speaker is slow to answer, alignWithPhone fixes the spot once it plays.
        val preSeeked = phoneAt > 1_500 && runCatching {
            soap.callQuick(
                s.group.coordinator.host, SonosService.AV_TRANSPORT, "Seek",
                "InstanceID" to 0, "Unit" to "REL_TIME",
                "Target" to SonosSoapClient.msToHms(phoneAt + if (local.playWhenReady) START_LEAD_MS else 0L)
            )
        }.isSuccess
        s.quietForSkips = false
        if (engine.masterPlayer.playWhenReady) {
            s.lastPlayPauseCommandAt = now()
            avt(s, "Play", "Speed" to "1")
            alignWithPhone(s)
        } else {
            // Some speakers refuse a time seek before playing; align on the next play then.
            s.alignOnPlay = phoneAt > 1_500 && !preSeeked
        }
        appendTracks(s, upcoming)
    }

    /**
     * Spotify Connect grabs a speaker back half a second after losing it, also when it was already
     * on pause, unless it got a Pause command first (measured on real speakers).
     * Returns whether something else was on the speaker.
     */
    private suspend fun releaseOtherSource(s: ActiveSession): Boolean {
        val media = avt(s, "GetMediaInfo")["CurrentURI"].orEmpty()
        if (media.isEmpty() || media == s.group.queueUri) return false
        runCatching { avt(s, "Pause") }
        delay(RELEASE_SOURCE_MS)
        return true
    }

    /**
     * Makes sure the other source really let go before we start: a Play or Seek that lands on it
     * would resume Spotify or hang. If it did come back, it gets paused and replaced once more.
     */
    private suspend fun holdSpeaker(s: ActiveSession) {
        repeat(HOLD_ATTEMPTS) {
            var retaken = false
            for (check in 1..HOLD_CHECKS) {
                delay(HOLD_CHECK_MS)
                if (avt(s, "GetMediaInfo")["CurrentURI"] != s.group.queueUri) {
                    retaken = true
                    break
                }
            }
            if (!retaken) return
            runCatching { avt(s, "Pause") }
            delay(RELEASE_SOURCE_MS)
            avt(s, "SetAVTransportURI", "CurrentURI" to s.group.queueUri, "CurrentURIMetaData" to "")
        }
    }

    /**
     * Waits until the speaker really plays, then puts it where the phone is now. The phone kept
     * playing (muted) while the Sonos buffered, so the music continues from the same spot.
     */
    private suspend fun alignWithPhone(s: ActiveSession) {
        s.alignOnPlay = false
        val player = engine.masterPlayer
        val mediaId = player.currentMediaItem?.mediaId
        val deadline = now() + PLAY_START_TIMEOUT_MS
        while (now() < deadline) {
            // Paused or skipped while the speaker was starting: do not hold up what comes next.
            if (!player.playWhenReady) {
                s.alignOnPlay = true
                return
            }
            if (player.currentMediaItem?.mediaId != mediaId) return
            if (avt(s, "GetTransportInfo")["CurrentTransportState"] == "PLAYING") break
            delay(120)
        }
        val phoneAt = readLocal()?.positionMs ?: return
        if (phoneAt < 1_500) return
        val sonosAt = SonosSoapClient.hmsToMs(avt(s, "GetPositionInfo")["RelTime"])
        if (abs(sonosAt - phoneAt) < ALIGN_TOLERANCE_MS) return
        runCatching { seekSonos(s, phoneAt + SEEK_LEAD_MS) }
        s.lastTrackCommandAt = now()
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
            saveSession(s)
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
        saveSession(s)
    }

    /** Puts the speaker in the play state the phone is in now, not in the one it had when a sync started. */
    private suspend fun applyPlayState(s: ActiveSession) {
        val state = avt(s, "GetTransportInfo")["CurrentTransportState"].orEmpty()
        s.lastPlayPauseCommandAt = now()
        s.quietForSkips = false
        when (playStateCommand(engine.masterPlayer.playWhenReady, state)) {
            "Play" -> {
                avt(s, "Play", "Speed" to "1")
                if (s.alignOnPlay) alignWithPhone(s)
            }
            "Pause" -> pauseSonos(s)
        }
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
        // Read right away: every further call to the speaker would make the comparison less exact.
        val phoneAtSample = engine.masterPlayer.currentPosition
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
        if (local.currentMediaId == sonosId) s.resumedUntil = 0L
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
        if (transport != "PLAYING" || !local.isPlaying || busy) {
            s.speakerOffset.reset()
            return
        }
        // The phone keeps the speaker's time closely, because the lyrics and the progress bar run on it.
        val offset = s.speakerOffset.add(relMs, phoneAtSample)
        if (abs(relMs - phoneAtSample) > DRIFT_LIMIT_MS) {
            followLocal(s) { it.seekTo(relMs) }
            s.speakerOffset.reset()
        } else if (offset != null && abs(offset) > SYNC_LIMIT_MS) {
            followLocal(s) { it.seekTo(it.currentPosition + offset) }
            s.speakerOffset.reset()
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
        /** Not a whole second, so each poll lands at another moment in the speaker's second; see [SpeakerOffsetEstimate]. */
        private const val POLL_INTERVAL_MS = 1_130L
        private const val SYNC_DEBOUNCE_MS = 250L
        /** Skips closer together than this are one run. */
        private const val SKIP_RUN_MS = 1_500L
        /** How long the speaker stays quiet after the last skip of a run. */
        private const val SKIP_RUN_DEBOUNCE_MS = 800L
        private const val TRACK_COMMAND_GRACE_MS = 4_000L
        private const val PLAY_PAUSE_GRACE_MS = 3_000L
        private const val AUTO_ADVANCE_GRACE_MS = 7_000L
        private const val TAKEOVER_GRACE_MS = 6_000L
        private const val DRIFT_LIMIT_MS = 2_500L
        /** A lyric line that is this far off is just noticeable. */
        private const val SYNC_LIMIT_MS = 250L
        private const val WARM_UP_INTERVAL_MS = 60_000L
        private const val KEY_SESSION = "session"
        private const val RESUME_GRACE_MS = 20_000L
        /** A speaker that is on pause only counts as still in use for this long. */
        private const val RESUME_PAUSED_WITHIN_MS = 12 * 60 * 60 * 1000L
        private const val PLAY_START_TIMEOUT_MS = 4_000L
        /** How long a speaker needs from Play to sound (measured: 0,5 to 0,6 s); the pre-seek aims that far ahead. */
        private const val START_LEAD_MS = 550L
        private const val RELEASE_SOURCE_MS = 300L
        /** Spotify came back after 0,5 to 0,6 s in every measurement, so watch a bit longer than that. */
        private const val HOLD_CHECK_MS = 150L
        private const val HOLD_CHECKS = 5
        private const val HOLD_ATTEMPTS = 3
        private const val SEEK_LEAD_MS = 250L
        private const val ALIGN_TOLERANCE_MS = 1_200L
        /** Volume changes closer together than this count as one drag or run of key presses. */
        private const val VOLUME_GESTURE_MS = 1_500L

        /**
         * What the speaker needs to match the phone, or null when it already does. A speaker that
         * is starting reports TRANSITIONING: it counts as playing, and takes a Pause at once.
         */
        internal fun playStateCommand(wantPlaying: Boolean, transport: String): String? {
            val going = transport == "PLAYING" || transport == "TRANSITIONING"
            return when {
                wantPlaying && !going -> "Play"
                !wantPlaying && going -> "Pause"
                else -> null
            }
        }

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

        /**
         * Spreads a group volume over the rooms in proportion to [base], so the average lands on
         * [groupVolume]. A room that hits 100 stops there and the others keep rising; rooms that
         * were all silent move together.
         */
        internal fun groupVolumeTargets(base: Map<String, Int>, groupVolume: Int): Map<String, Int> {
            val target = groupVolume.coerceIn(0, 100).toDouble()
            val quietest = base.values.filter { it > 0 }.minOrNull() ?: return base.mapValues { target.roundToInt() }
            fun spread(factor: Double) = base.mapValues { (_, v) -> (v * factor).coerceIn(0.0, 100.0) }
            var low = 0.0
            var high = 100.0 / quietest
            repeat(40) {
                val mid = (low + high) / 2
                if (spread(mid).values.average() < target) low = mid else high = mid
            }
            return spread(high).mapValues { it.value.roundToInt() }
        }

        /** Index of [mediaId] in [queue] closest to [around], or -1. */
        internal fun nearestIndexOf(queue: List<SonosTrack>, mediaId: String, around: Int): Int =
            queue.indices.filter { queue[it].mediaId == mediaId }.minByOrNull { abs(it - around) } ?: -1
    }
}
