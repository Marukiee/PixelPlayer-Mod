package com.lostf1sh.pixelplayeross.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.lostf1sh.pixelplayeross.data.remote.RemoteDevice
import com.lostf1sh.pixelplayeross.data.remote.RemotePlayerController
import com.lostf1sh.pixelplayeross.data.remote.RemotePlayerHost
import com.lostf1sh.pixelplayeross.data.remote.RemotePlayerService
import com.lostf1sh.pixelplayeross.data.remote.RemotePlayerSettings
import com.lostf1sh.pixelplayeross.data.remote.RemoteSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Both sides of "remote player": this phone playing on another one, and this phone playing for another one. */
@HiltViewModel
class RemotePlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: RemotePlayerController,
    host: RemotePlayerHost,
    private val settings: RemotePlayerSettings
) : ViewModel() {
    val devices: StateFlow<List<RemoteDevice>> = controller.devices
    val isSearching: StateFlow<Boolean> = controller.isSearching
    val session: StateFlow<RemoteSessionState?> = controller.session

    /** "Use as remote player" on this phone. */
    val isEnabled: StateFlow<Boolean> = settings.enabled

    /** The phone that controls this one now. */
    val controllerName: StateFlow<String?> = host.controllerName

    fun watch(on: Boolean) = controller.watch(on)
    fun refresh() = controller.refresh()
    fun connect(device: RemoteDevice) = controller.connect(device.key)
    fun switchToPhone() = controller.switchToPhone()
    fun setVolume(percent: Int) = controller.setVolume(percent)
    fun adjustVolume(up: Boolean) = controller.adjustVolume(up)

    fun setEnabled(on: Boolean) {
        settings.setEnabled(on)
        RemotePlayerService.sync(context, on)
    }
}
