package com.lostf1sh.pixelplayeross.data.sonos

import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.remote.RemotePlayerController
import com.lostf1sh.pixelplayeross.data.remote.RemoteSessionState
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.roundToInt

/**
 * The player the media session shows to Android. While a Sonos group plays it reports a remote
 * device with the group volume, so the phone's volume keys, its volume bar and Drip's island
 * work on the speakers, also with the app in the background or the screen off. The same for another phone that plays
 * as remote player, in its own volume steps.
 */
@OptIn(UnstableApi::class)
class SonosSessionPlayer(
    val wrapped: Player,
    private val sonos: SonosController,
    private val output: SonosOutputRoute,
    private val remotePlayer: RemotePlayerController
) : ForwardingPlayer(wrapped) {

    private val sessionListeners = CopyOnWriteArraySet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        sessionListeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        sessionListeners -= listener
    }

    private var announcedRemote = false
    private var announcedRoute: String? = null
    private var announcedSteps: Int? = null

    /** Tells the session that the volume or mute changed, or that a Sonos session or its route began or ended. */
    fun onSonosVolumeChanged() {
        if (isElsewhere != announcedRemote || output.controllerId.value != announcedRoute || phone?.volumeSteps != announcedSteps) {
            announceDevice()
        }
        val volume = deviceVolume
        val muted = isDeviceMuted
        sessionListeners.forEach { it.onDeviceVolumeChanged(volume, muted) }
    }

    /**
     * Also needed right after this player takes over in the session: a new player has announced nothing yet,
     * while the session still shows what the old one told it. Without this it stays on the speakers after a disconnect.
     */
    fun announceDevice() {
        announcedRemote = isElsewhere
        announcedRoute = output.controllerId.value
        announcedSteps = phone?.volumeSteps
        val commands = availableCommands
        val info = deviceInfo
        sessionListeners.forEach {
            it.onAvailableCommandsChanged(commands)
            it.onDeviceInfoChanged(info)
        }
    }

    private val remote: SonosSessionState? get() = sonos.remoteVolume.value

    /** Another phone playing as remote player, once it has said how loud it is. */
    private val phone: RemoteSessionState? get() = remotePlayer.session.value?.takeIf { !it.isConnecting && it.volume != null }

    private val isElsewhere: Boolean get() = remote != null || phone != null

    private fun phoneSteps(state: RemoteSessionState) = state.volumeSteps.takeIf { it > 0 } ?: 15

    override fun getDeviceInfo(): DeviceInfo {
        phone?.let { state ->
            return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(phoneSteps(state))
                .build()
        }
        return if (remote != null) {
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(100)
                .setRoutingControllerId(output.controllerId.value)
                .build()
        } else {
            super.getDeviceInfo()
        }
    }

    override fun getDeviceVolume(): Int {
        phone?.let { state -> return ((state.volume ?: 0) * phoneSteps(state) / 100f).roundToInt() }
        return remote?.let { it.groupVolume ?: 0 } ?: super.getDeviceVolume()
    }

    override fun isDeviceMuted(): Boolean = if (phone != null) false else remote?.isMuted ?: super.isDeviceMuted()

    override fun setDeviceVolume(volume: Int, flags: Int) {
        val state = phone
        when {
            state != null -> remotePlayer.setVolume((volume * 100f / phoneSteps(state)).roundToInt())
            remote != null -> sonos.setGroupVolume(volume)
            else -> super.setDeviceVolume(volume, flags)
        }
    }

    override fun increaseDeviceVolume(flags: Int) {
        when {
            phone != null -> remotePlayer.adjustVolume(up = true)
            remote != null -> sonos.adjustGroupVolume(VOLUME_STEP)
            else -> super.increaseDeviceVolume(flags)
        }
    }

    override fun decreaseDeviceVolume(flags: Int) {
        when {
            phone != null -> remotePlayer.adjustVolume(up = false)
            remote != null -> sonos.adjustGroupVolume(-VOLUME_STEP)
            else -> super.decreaseDeviceVolume(flags)
        }
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        when {
            phone != null -> if (muted) remotePlayer.setVolume(0)
            remote != null -> sonos.setGroupMuted(muted)
            else -> super.setDeviceMuted(muted, flags)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        if (!isElsewhere) return commands
        return commands.buildUpon().addAll(*VOLUME_COMMANDS).build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        (isElsewhere && command in VOLUME_COMMANDS) || super.isCommandAvailable(command)

    private companion object {
        const val VOLUME_STEP = 2
        val VOLUME_COMMANDS = intArrayOf(
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS
        )
    }
}
