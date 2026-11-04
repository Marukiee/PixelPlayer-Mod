package com.theveloper.pixelplay.data.sonos

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
 * Tells Android which speakers the music is on, so the media card in the quick settings names them
 * instead of "This phone". The system takes that name from a routing session, and setting it directly
 * (setRemotePlaybackInfo) needs a system permission. So the app offers the current Sonos group as a
 * route of its own and moves itself onto it.
 */
@Singleton
class SonosOutputRoute @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sonos: SonosController
) {
    private val router by lazy { MediaRouter2.getInstance(context) }
    private val executor by lazy { ContextCompat.getMainExecutor(context) }
    private val discovery = RouteDiscoveryPreference.Builder(listOf(FEATURE), false).build()

    private var provider: SonosRouteProvider? = null
    private var listening = false
    private var transferring = false
    private var stopping = false

    var current: SonosSessionState? = null
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

    fun update(state: SonosSessionState?) {
        current = state
        if (state == null) {
            leaveRoute()
            return
        }
        provider?.publish(state)
        if (!listening) {
            listening = true
            stopping = false
            router.registerRouteCallback(executor, routeCallback, discovery)
            router.registerTransferCallback(executor, transferCallback)
        }
        moveOntoRoute()
    }

    private fun moveOntoRoute() {
        if (!listening || transferring || _controllerId.value != null) return
        val route = router.routes.firstOrNull { FEATURE in it.features } ?: return
        transferring = true
        router.transferTo(route)
    }

    private fun leaveRoute() {
        if (!listening) return
        listening = false
        stopping = true
        transferring = false
        _controllerId.value = null
        router.stop()
        router.unregisterTransferCallback(transferCallback)
        router.unregisterRouteCallback(routeCallback)
        provider?.clear()
    }

    /** Somebody picked another output in Android's own menu, so the speakers stop here too. */
    internal fun onReleased() {
        if (stopping || current == null) return
        sonos.switchToPhone()
    }

    internal fun setVolume(volume: Int) = sonos.setGroupVolume(volume)

    internal fun attach(service: SonosRouteProvider) {
        provider = service
        current?.let(service::publish)
    }

    internal fun detach(service: SonosRouteProvider) {
        if (provider === service) provider = null
    }

    internal companion object {
        const val TAG = "SonosOutputRoute"
        /** Our own feature, so no other app sees this route as something it could cast to. */
        const val FEATURE = "com.theveloper.pixelplay.route.SONOS"
    }
}

@AndroidEntryPoint
class SonosRouteProvider : MediaRoute2ProviderService() {

    @Inject lateinit var output: SonosOutputRoute

    private var clientPackage: String? = null
    private var publishedName: String? = null
    private var publishedVolume: Int? = null

    override fun onCreate() {
        super.onCreate()
        output.attach(this)
    }

    override fun onDestroy() {
        output.detach(this)
        super.onDestroy()
    }

    fun publish(state: SonosSessionState) {
        val name = state.group.displayName
        // Android's output menu takes the slider's value from the route, not from the session.
        if (name != publishedName || state.groupVolume != publishedVolume) {
            publishedName = name
            publishedVolume = state.groupVolume
            notifyRoutes(listOf(routeFor(state)))
        }
        clientPackage?.let { notifySessionUpdated(sessionFor(state, it)) }
    }

    fun clear() {
        publishedName = null
        publishedVolume = null
        notifyRoutes(emptyList())
    }

    private fun routeFor(state: SonosSessionState): MediaRoute2Info =
        MediaRoute2Info.Builder(ROUTE_ID, state.group.displayName)
            .addFeature(SonosOutputRoute.FEATURE)
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(MAX_VOLUME)
            .setVolume(state.groupVolume ?: 0)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setType(MediaRoute2Info.TYPE_REMOTE_SPEAKER) }
            .build()

    private fun sessionFor(state: SonosSessionState, packageName: String): RoutingSessionInfo =
        RoutingSessionInfo.Builder(SESSION_ID, packageName)
            .addSelectedRoute(ROUTE_ID)
            .setName(state.group.displayName)
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(MAX_VOLUME)
            .setVolume(state.groupVolume ?: 0)
            .build()

    override fun onCreateSession(requestId: Long, packageName: String, routeId: String, sessionHints: Bundle?) {
        val state = output.current
        if (state == null) {
            notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE)
            return
        }
        clientPackage = packageName
        notifySessionCreated(requestId, sessionFor(state, packageName))
    }

    override fun onReleaseSession(requestId: Long, sessionId: String) {
        clientPackage = null
        notifySessionReleased(sessionId)
        output.onReleased()
    }

    override fun onSetRouteVolume(requestId: Long, routeId: String, volume: Int) = output.setVolume(volume)

    override fun onSetSessionVolume(requestId: Long, sessionId: String, volume: Int) = output.setVolume(volume)

    // A Sonos group is changed in the app's own panel, not route by route from here.
    override fun onSelectRoute(requestId: Long, sessionId: String, routeId: String) =
        notifyRequestFailed(requestId, REASON_INVALID_COMMAND)

    override fun onDeselectRoute(requestId: Long, sessionId: String, routeId: String) =
        notifyRequestFailed(requestId, REASON_INVALID_COMMAND)

    override fun onTransferToRoute(requestId: Long, sessionId: String, routeId: String) =
        notifyRequestFailed(requestId, REASON_INVALID_COMMAND)

    private companion object {
        const val ROUTE_ID = "sonos"
        const val SESSION_ID = "sonos_session"
        const val MAX_VOLUME = 100
    }
}
