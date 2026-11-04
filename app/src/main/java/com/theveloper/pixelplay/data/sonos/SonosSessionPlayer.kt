package com.theveloper.pixelplay.data.sonos

import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The player the media session shows to Android. While a Sonos group plays it reports a remote
 * device with the group volume, so the phone's volume keys, its volume bar and Drip's island
 * work on the speakers, also with the app in the background or the screen off.
 */
@OptIn(UnstableApi::class)
class SonosSessionPlayer(
    val wrapped: Player,
    private val sonos: SonosController
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

    /** Tells the session that the volume or mute changed, or that a Sonos session began or ended. */
    fun onSonosVolumeChanged() {
        val isRemote = remote != null
        if (isRemote != announcedRemote) {
            announcedRemote = isRemote
            val commands = availableCommands
            val info = deviceInfo
            sessionListeners.forEach {
                it.onAvailableCommandsChanged(commands)
                it.onDeviceInfoChanged(info)
            }
        }
        val volume = deviceVolume
        val muted = isDeviceMuted
        sessionListeners.forEach { it.onDeviceVolumeChanged(volume, muted) }
    }

    private val remote: SonosSessionState? get() = sonos.remoteVolume.value

    override fun getDeviceInfo(): DeviceInfo = if (remote != null) REMOTE_DEVICE else super.getDeviceInfo()

    override fun getDeviceVolume(): Int = remote?.let { it.groupVolume ?: 0 } ?: super.getDeviceVolume()

    override fun isDeviceMuted(): Boolean = remote?.isMuted ?: super.isDeviceMuted()

    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (remote != null) sonos.setGroupVolume(volume) else super.setDeviceVolume(volume, flags)
    }

    override fun increaseDeviceVolume(flags: Int) {
        if (remote != null) sonos.adjustGroupVolume(VOLUME_STEP) else super.increaseDeviceVolume(flags)
    }

    override fun decreaseDeviceVolume(flags: Int) {
        if (remote != null) sonos.adjustGroupVolume(-VOLUME_STEP) else super.decreaseDeviceVolume(flags)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        if (remote != null) sonos.setGroupMuted(muted) else super.setDeviceMuted(muted, flags)
    }

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        if (remote == null) return commands
        return commands.buildUpon().addAll(*VOLUME_COMMANDS).build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        (remote != null && command in VOLUME_COMMANDS) || super.isCommandAvailable(command)

    private companion object {
        const val VOLUME_STEP = 2
        val REMOTE_DEVICE: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(100)
            .build()
        val VOLUME_COMMANDS = intArrayOf(
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS
        )
    }
}
