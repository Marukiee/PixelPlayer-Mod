package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.sonos.SonosController
import com.theveloper.pixelplay.data.spotify.SpotifyApi
import com.theveloper.pixelplay.data.spotify.SpotifyAuth
import com.theveloper.pixelplay.data.spotify.SpotifyResults
import com.theveloper.pixelplay.data.spotify.SpotifyShow
import com.theveloper.pixelplay.data.spotify.SpotifyEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Spotify login for the settings, and searching Spotify from Search. */
@HiltViewModel
class SpotifyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: SpotifyAuth,
    private val api: SpotifyApi,
    sonosController: SonosController
) : ViewModel() {

    sealed interface Search {
        val query: String
        data class Loading(override val query: String) : Search
        data class Found(override val query: String, val results: SpotifyResults) : Search
        data class Failed(override val query: String, val notLoggedIn: Boolean) : Search
    }

    val account: StateFlow<SpotifyAuth.Account> = auth.account

    /** Spotify songs only play on a Sonos: the speaker fetches them, the phone has no sound for them. */
    val sonosActive: StateFlow<Boolean> = sonosController.session
        .map { it != null && !it.isConnecting }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The podcast that is open, with its episodes once they are in; null episodes while loading. */
    data class OpenShow(val show: SpotifyShow, val episodes: List<SpotifyEpisode>?, val failed: Boolean = false)

    private val _openShow = MutableStateFlow<OpenShow?>(null)
    val openShow: StateFlow<OpenShow?> = _openShow.asStateFlow()
    private var showJob: Job? = null

    fun openShow(show: SpotifyShow) {
        showJob?.cancel()
        _openShow.value = OpenShow(show, episodes = null)
        showJob = viewModelScope.launch {
            _openShow.value = api.showEpisodes(show).fold(
                onSuccess = { OpenShow(show, it) },
                onFailure = { OpenShow(show, emptyList(), failed = true) }
            )
        }
    }

    fun closeShow() {
        showJob?.cancel()
        _openShow.value = null
    }

    private val _search = MutableStateFlow<Search?>(null)
    val search: StateFlow<Search?> = _search.asStateFlow()
    private var searchJob: Job? = null

    fun setClientId(clientId: String) = auth.setClientId(clientId)

    /** Opens Spotify's login page. False without a Client ID. */
    fun login(clientId: String): Boolean {
        auth.setClientId(clientId)
        val intent = auth.loginIntent() ?: return false
        // Without a browser there is nothing to log in with; say so instead of crashing.
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, R.string.spotify_login_failed, Toast.LENGTH_LONG).show()
        }
        return true
    }

    fun logout() {
        auth.logout()
        _search.value = null
    }

    /** Only on a tap of the button, never while typing: an app in development mode may not ask much. */
    fun search(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        searchJob?.cancel()
        _search.value = Search.Loading(cleaned)
        searchJob = viewModelScope.launch {
            _search.value = api.search(cleaned).fold(
                onSuccess = { Search.Found(cleaned, it) },
                onFailure = { Search.Failed(cleaned, notLoggedIn = it is SpotifyApi.NotLoggedIn) }
            )
        }
    }
}
