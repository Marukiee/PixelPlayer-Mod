package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.sonos.SonosController
import com.theveloper.pixelplay.data.spotify.SpotifyAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** How each place music can play from stands, for the status lines on "Play from". */
@HiltViewModel
class SourcesViewModel @Inject constructor(
    navidromeRepository: NavidromeRepository,
    userPreferencesRepository: UserPreferencesRepository,
    sonosController: SonosController,
    spotifyAuth: SpotifyAuth
) : ViewModel() {

    data class Status(
        val navidromeUser: String? = null,
        val sonosOn: Boolean = false,
        /** The room or group the phone plays on now, once it is connected. */
        val sonosRoom: String? = null,
        val spotifyLoggedIn: Boolean = false,
        val spotifyName: String? = null,
        val spotifyHasClientId: Boolean = false
    )

    val status: StateFlow<Status> = combine(
        navidromeRepository.isLoggedInFlow,
        userPreferencesRepository.sonosEnabledFlow,
        sonosController.session,
        spotifyAuth.account
    ) { navidromeIn, sonosOn, session, spotify ->
        Status(
            navidromeUser = if (navidromeIn) navidromeRepository.username.orEmpty() else null,
            sonosOn = sonosOn,
            sonosRoom = session?.takeIf { !it.isConnecting }?.group?.let { group ->
                val extra = group.members.size - 1
                if (extra > 0) "${group.coordinator.name} +$extra" else group.coordinator.name
            },
            spotifyLoggedIn = spotify.loggedIn,
            spotifyName = spotify.displayName,
            spotifyHasClientId = spotify.clientId.isNotBlank()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Status())
}
