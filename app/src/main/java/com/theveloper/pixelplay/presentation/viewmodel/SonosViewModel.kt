package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.sonos.SonosController
import com.theveloper.pixelplay.data.sonos.SonosGroup
import com.theveloper.pixelplay.data.sonos.SonosSessionState
import com.theveloper.pixelplay.data.sonos.SonosSourceProblem
import com.theveloper.pixelplay.data.sonos.SonosTrackResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** What the Settings > Sonos card shows about Navidrome as the source. */
data class SonosSourceInfo(
    val problem: SonosSourceProblem?,
    /** The Navidrome address the speakers get. */
    val sonosAddress: String?,
    val playable: Int,
    val total: Int,
    /** Whether that address answers from this phone; null while unknown. */
    val reachable: Boolean? = null
)

@HiltViewModel
class SonosViewModel @Inject constructor(
    private val controller: SonosController,
    private val resolver: SonosTrackResolver,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    val groups: StateFlow<List<SonosGroup>> = controller.groups
    val isScanning: StateFlow<Boolean> = controller.isScanning
    val session: StateFlow<SonosSessionState?> = controller.session
    val isEnabled: StateFlow<Boolean> = userPreferencesRepository.sonosEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val skipUnavailable: StateFlow<Boolean> = userPreferencesRepository.sonosSkipUnavailableFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val volumeKeys: StateFlow<Boolean> = userPreferencesRepository.sonosVolumeKeysFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val homeAddress: StateFlow<String> = userPreferencesRepository.sonosHomeAddressFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _sourceInfo = MutableStateFlow<SonosSourceInfo?>(null)
    val sourceInfo: StateFlow<SonosSourceInfo?> = _sourceInfo.asStateFlow()
    private var sourceJob: Job? = null
    private val pingClient by lazy {
        OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()
    }

    fun loadSourceInfo() {
        sourceJob?.cancel()
        sourceJob = viewModelScope.launch {
            val problem = resolver.sourceProblem()
            val address = resolver.sonosBaseUrl()
            if (problem != null) {
                _sourceInfo.value = SonosSourceInfo(problem, address, 0, 0)
                return@launch
            }
            val reachable = async(Dispatchers.IO) { address?.let(::answers) }
            val (playable, total) = withContext(Dispatchers.IO) { resolver.countPlayable() }
            _sourceInfo.value = SonosSourceInfo(null, address, playable, total)
            _sourceInfo.value = _sourceInfo.value?.copy(reachable = reachable.await())
        }
    }

    /** Subsonic answers /rest/ping.view with 200 even without login, so this only tests the address. */
    private fun answers(base: String): Boolean = runCatching {
        pingClient.newCall(Request.Builder().url("$base/rest/ping.view").build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /** Saves the home address; returns false when it is not a valid address. Blank clears it. */
    fun setHomeAddress(input: String): Boolean {
        val normalized = if (input.isBlank()) "" else SonosTrackResolver.normalizeAddress(input) ?: return false
        viewModelScope.launch {
            userPreferencesRepository.setSonosHomeAddress(normalized)
            resolver.clearSession()
            loadSourceInfo()
        }
        return true
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { userPreferencesRepository.setSonosEnabled(enabled) }
    fun setSkipUnavailable(skip: Boolean) = viewModelScope.launch { userPreferencesRepository.setSonosSkipUnavailable(skip) }
    fun setVolumeKeys(enabled: Boolean) = viewModelScope.launch { userPreferencesRepository.setSonosVolumeKeys(enabled) }

    fun refresh() = controller.refresh()
    fun connect(groupId: String) = controller.connect(groupId)
    fun switchToPhone() = controller.switchToPhone()
    fun setGroupVolume(volume: Int) = controller.setGroupVolume(volume)
    fun adjustVolume(delta: Int) = controller.adjustGroupVolume(delta)
    fun setSpeakerVolume(uuid: String, volume: Int) = controller.setSpeakerVolume(uuid, volume)
    fun setGroupMembers(groupId: String, memberUuids: Set<String>) =
        controller.setGroupMembers(groupId, memberUuids)
}
