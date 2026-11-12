package com.theveloper.pixelplay.data.sonos

import android.os.Looper
import android.os.Handler
import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.media.RoutingSessionInfo
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offers the Sonos rooms in Android's own output menu, the cast button in the media card and in the island of
 * phones like Oppo's, and names the group there while the music is on it. Every room or group is a route of the app's
 * own; picking one there starts the music in that room, picking the phone brings it back. The name in the media card
 * comes from a routing session, and setting it directly (setRemotePlaybackInfo) needs a system permission, so the app
 * moves itself onto the route of the group that plays.
 */
@Singleton
class SonosOutputRoute @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sonos: SonosController
) {
    private val router by lazy { MediaRouter2.getInstance(context) }
    private val executor by lazy { ContextCompat.getMainExecutor(context) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val discovery = RouteDiscoveryPreference.Builder(listOf(FEATURE), false).build()

    private var provider: SonosRouteProvider? = null
    private var listening = false
    private var transferring = false
    private var stopping = false

    var current: SonosSessionState? = null
        private set

    /** The rooms and groups to offer; empty while Sonos is off. */
    var rooms: List<SonosGroup> = emptyList()
        private set

    private val _controllerId = MutableStateFlow<String?>(null)

    /** The session's id at the system, which the media session passes on so Android can match the two. */
    val controllerId: StateFlow<String?> = _controllerId.asStateFlow()

    private val routeCallback = object : MediaRouter2.RouteCallback() {
        override fun onRoutesUpdated(routes: List<MediaRoute2Info>) = moveOntoRoute()
    }

    private val transferCallback = object : MediaRouter2.TransferCallback() {
        override fun onTransfer(old: MediaRouter2.RoutingController, new: MediaRouter2.RoutingController) {
            transferring = false
            _controllerId.value = new.id.takeIf { new.selectedRoutes.any { route -> FEATURE in route.features } }
        }

        override fun onTransferFailure(route: MediaRoute2Info) {
            transferring = false
            Timber.tag(TAG).w("Android refused the route")
        }

        override fun onStop(controller: MediaRouter2.RoutingController) {
            _controllerId.value = null
        }
    }

    /** The rooms Android's output menu offers. Kept up while Sonos is on, also when the music plays on the phone. */
    fun updateRooms(groups: List<SonosGroup>, enabled: Boolean) {
        rooms = if (enabled) groups else emptyList()
        provider?.publish(rooms, current)
        if (rooms.isNotEmpty()) listen() else if (current == null) unlisten()
    }

    fun update(state: SonosSessionState?) {
        val wasPlaying = current != null
        current = state
        provider?.publish(rooms, state)
        if (state == null) {
            if (wasPlaying) leaveSession()
            if (rooms.isEmpty()) unlisten()
            return
        }
        listen()
        moveOntoRoute()
    }

    private fun listen() {
        if (listening) return
        listening = true
        // The output menu only shows an app's own routes while the app asks for them.
        router.registerRouteCallback(executor, routeCallback, discovery)
        router.registerTransferCallback(executor, transferCallback)
    }

    private fun unlisten() {
        if (!listening) return
        listening = false
        router.unregisterTransferCallback(transferCallback)
        router.unregisterRouteCallback(routeCallback)
    }

    /** Onto the route of the group that plays, so the media card names it. Once on it, the session follows by itself. */
    private fun moveOntoRoute() {
        val state = current ?: return
        if (!listening || transferring || _controllerId.value != null) return
        val routeId = SonosRouteProvider.routeIdFor(state.group)
        val route = router.routes.firstOrNull { FEATURE in it.features && it.id.endsWith(":$routeId") } ?: return
        stopping = false
        transferring = true
        router.transferTo(route)
        releaseTransferWait()
    }

    /** The music stopped on the speakers: off the route, but the rooms stay in the menu. */
    private fun leaveSession() {
        stopping = true
        transferring = false
        _controllerId.value = null
        if (listening) router.stop()
    }

    /**
     * A room was picked in Android's own menu: play there. The system moves the app onto the route itself, so the
     * app does not move itself again while that happens.
     */
    internal fun playOn(group: SonosGroup) {
        stopping = false
        transferring = true
        releaseTransferWait()
        if (current?.group?.coordinatorUuid != group.coordinatorUuid) sonos.connect(group.id)
    }

    /** A move that never reports back must not block the next one. */
    private fun releaseTransferWait() {
        mainHandler.postDelayed({ transferring = false }, TRANSFER_WAIT_MS)
    }

    /** Android's menu is open, or a router asked for routes: a fresh look for rooms, at most once a minute. */
    internal fun onSystemLooking() = sonos.warmUp()

    /** Somebody picked the phone or another output in Android's own menu, so the speakers stop here too. */
    internal fun onReleased() {
        if (stopping || current == null) return
        sonos.switchToPhone()
    }

    internal fun setVolume(volume: Int) = sonos.setGroupVolume(volume)

    internal fun attach(service: SonosRouteProvider) {
        provider = service
        service.publish(rooms, current)
    }

    internal fun detach(service: SonosRouteProvider) {
        if (provider === service) provider = null
    }

    internal companion object {
        const val TAG = "SonosOutputRoute"
        /** Our own feature, so no other app sees these routes as something it could cast to. */
        const val FEATURE = "com.theveloper.pixelplay.route.SONOS"
        const val TRANSFER_WAIT_MS = 5_000L
    }
}

@AndroidEntryPoint
class SonosRouteProvider : MediaRoute2ProviderService() {

    @Inject lateinit var output: SonosOutputRoute

    private var clientPackage: String? = null
    private var publishedRoutes: List<MediaRoute2Info>? = null
    /** The room picked in Android's menu, shown as selected until the speakers report in. */
    private var pickedRouteId: String? = null

    override fun onCreate() {
        super.onCreate()
        output.attach(this)
    }

    override fun onDestroy() {
        output.detach(this)
        super.onDestroy()
    }

    fun publish(rooms: List<SonosGroup>, state: SonosSessionState?) {
        val groups = buildList {
            addAll(rooms)
            // The group that plays, also if the last search for rooms missed it.
            if (state != null && none { it.coordinatorUuid == state.group.coordinatorUuid }) add(state.group)
        }
        if (state != null && pickedRouteId == routeIdFor(state.group)) pickedRouteId = null
        val routes = groups.map { group -> routeFor(group, state?.takeIf { it.group.coordinatorUuid == group.coordinatorUuid }) }
        // Android's output menu takes the slider's value from the route, not from the session.
        if (routes != publishedRoutes) {
            publishedRoutes = routes
            notifyRoutes(routes)
        }
        val packageName = clientPackage ?: return
        sessionFor(packageName, state)?.let(::notifySessionUpdated)
    }

    private fun routeFor(group: SonosGroup, playing: SonosSessionState?): MediaRoute2Info =
        MediaRoute2Info.Builder(routeIdFor(group), group.displayName)
            .addFeature(SonosOutputRoute.FEATURE)
            .apply {
                if (playing != null) {
                    setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
                    setVolumeMax(MAX_VOLUME)
                    setVolume(playing.groupVolume ?: 0)
                } else {
                    setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_FIXED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setType(if (group.members.size > 1) MediaRoute2Info.TYPE_GROUP else MediaRoute2Info.TYPE_REMOTE_SPEAKER)
                }
            }
            .build()

    /** The session on the speakers: the room that plays, or was just picked, with the other rooms to move to. */
    private fun sessionFor(packageName: String, state: SonosSessionState?): RoutingSessionInfo? {
        val routes = publishedRoutes.orEmpty()
        val selectedId = pickedRouteId ?: state?.group?.let(::routeIdFor) ?: return null
        val selected = routes.firstOrNull { it.originalIdOrId() == selectedId }
        return RoutingSessionInfo.Builder(SESSION_ID, packageName)
            .addSelectedRoute(selectedId)
            .apply { routes.map { it.originalIdOrId() }.filter { it != selectedId }.forEach(::addTransferableRoute) }
            .setName(selected?.name ?: state?.group?.displayName ?: "")
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(MAX_VOLUME)
            .setVolume(state?.groupVolume ?: 0)
            .build()
    }

    private fun MediaRoute2Info.originalIdOrId(): String = id.substringAfterLast(':')

    private fun groupFor(routeId: String): SonosGroup? =
        (output.rooms + listOfNotNull(output.current?.group)).firstOrNull { routeIdFor(it) == routeId }

    override fun onCreateSession(requestId: Long, packageName: String, routeId: String, sessionHints: Bundle?) {
        val group = groupFor(routeId)
        if (group == null) {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
            return
        }
        clientPackage = packageName
        // Picked in Android's menu while the music was elsewhere: start it in that room.
        if (output.current?.group?.coordinatorUuid != group.coordinatorUuid) {
            pickedRouteId = routeId
            output.playOn(group)
        }
        val session = sessionFor(packageName, output.current)
        if (session == null) notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE) else notifySessionCreated(requestId, session)
    }

    override fun onTransferToRoute(requestId: Long, sessionId: String, routeId: String) {
        val group = groupFor(routeId)
        val packageName = clientPackage
        if (group == null || packageName == null) {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
            return
        }
        pickedRouteId = routeId
        output.playOn(group)
        sessionFor(packageName, output.current)?.let(::notifySessionUpdated)
    }

    override fun onReleaseSession(requestId: Long, sessionId: String) {
        clientPackage = null
        pickedRouteId = null
        notifySessionReleased(sessionId)
        output.onReleased()
    }

    override fun onDiscoveryPreferenceChanged(preference: RouteDiscoveryPreference) {
        if (SonosOutputRoute.FEATURE in preference.preferredFeatures) output.onSystemLooking()
    }

    override fun onSetRouteVolume(requestId: Long, routeId: String, volume: Int) = output.setVolume(volume)

    override fun onSetSessionVolume(requestId: Long, sessionId: String, volume: Int) = output.setVolume(volume)

    // Rooms are grouped in the app's own panel, not route by route from here.
    override fun onSelectRoute(requestId: Long, sessionId: String, routeId: String) =
        notifyRequestFailed(requestId, REASON_INVALID_COMMAND)

    override fun onDeselectRoute(requestId: Long, sessionId: String, routeId: String) =
        notifyRequestFailed(requestId, REASON_INVALID_COMMAND)

    internal companion object {
        const val SESSION_ID = "sonos_session"
        const val MAX_VOLUME = 100

        fun routeIdFor(group: SonosGroup): String = "room_${group.coordinatorUuid}"
    }
}
