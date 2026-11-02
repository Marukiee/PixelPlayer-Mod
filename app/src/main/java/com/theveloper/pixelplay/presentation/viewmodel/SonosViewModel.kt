package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.sonos.SonosController
import com.theveloper.pixelplay.data.sonos.SonosGroup
import com.theveloper.pixelplay.data.sonos.SonosSessionState
import com.theveloper.pixelplay.data.sonos.SonosSourceProblem
import com.theveloper.pixelplay.data.sonos.SonosTrackResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the Settings > Sonos card shows about Navidrome as the source. */
data class SonosSourceInfo(
    val problem: SonosSourceProblem?,
    val server: String?,
    val playable: Int,
    val total: Int
)

@HiltViewModel
class SonosViewModel @Inject constructor(
    private val controller: SonosController,
    private val resolver: SonosTrackResolver,
    private val navidromeRepository: NavidromeRepository,
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

    private val _sourceInfo = MutableStateFlow<SonosSourceInfo?>(null)
    val sourceInfo: StateFlow<SonosSourceInfo?> = _sourceInfo.asStateFlow()

    fun loadSourceInfo() {
        viewModelScope.launch {
            val server = navidromeRepository.serverUrl
            val problem = resolver.sourceProblem()
            if (problem != null) {
                _sourceInfo.value = SonosSourceInfo(problem, server, 0, 0)
                return@launch
            }
            val (playable, total) = withContext(Dispatchers.IO) { resolver.countPlayable() }
            _sourceInfo.value = SonosSourceInfo(resolver.sourceProblem(), server, playable, total)
        }
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { userPreferencesRepository.setSonosEnabled(enabled) }
    fun setSkipUnavailable(skip: Boolean) = viewModelScope.launch { userPreferencesRepository.setSonosSkipUnavailable(skip) }
    fun setVolumeKeys(enabled: Boolean) = viewModelScope.launch { userPreferencesRepository.setSonosVolumeKeys(enabled) }

    fun refresh() = controller.refresh()
    fun connect(groupId: String) = controller.connect(groupId)
    fun switchToPhone() = controller.switchToPhone()
    fun setGroupVolume(volume: Int) = controller.setGroupVolume(volume)
    fun setSpeakerVolume(uuid: String, volume: Int) = controller.setSpeakerVolume(uuid, volume)
    fun setGroupMembers(groupId: String, memberUuids: Set<String>) =
        controller.setGroupMembers(groupId, memberUuids)
}
