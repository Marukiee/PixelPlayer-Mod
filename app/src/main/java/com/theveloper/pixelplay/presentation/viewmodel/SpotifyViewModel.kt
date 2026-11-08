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
        /** From the Podcasts filter: no songs asked for. */
        val podcastsOnly: Boolean
        data class Loading(override val query: String, override val podcastsOnly: Boolean = false) : Search
        data class Found(override val query: String, val results: SpotifyResults, override val podcastsOnly: Boolean = false) : Search
        data class Failed(
            override val query: String,
            val notLoggedIn: Boolean,
            val status: Int? = null,
            val reason: String? = null,
            override val podcastsOnly: Boolean = false
        ) : Search
    }

    val account: StateFlow<SpotifyAuth.Account> = auth.account
    val preferences: StateFlow<SpotifyAuth.Preferences> = auth.preferences

    fun setShowButton(on: Boolean) = auth.setShowButton(on)
    fun setPodcasts(on: Boolean) {
        auth.setPodcasts(on)
        // Results without or with podcasts belong to the other setting.
        _search.value = null
    }
    fun setAutoSearch(on: Boolean) = auth.setAutoSearch(on)

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

    /**
     * On a tap of the button, or once the typing has stopped where that was asked for (the Podcasts filter, or
     * searching by itself in the settings): an app in development mode may not ask much.
     */
    fun search(query: String, podcastsOnly: Boolean = false) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        searchJob?.cancel()
        _search.value = Search.Loading(cleaned, podcastsOnly)
        searchJob = viewModelScope.launch {
            val podcasts = podcastsOnly || auth.preferences.value.podcasts
            _search.value = api.search(cleaned, podcasts = podcasts, songs = !podcastsOnly).fold(
                onSuccess = { Search.Found(cleaned, it, podcastsOnly) },
                onFailure = {
                    val refused = it as? SpotifyApi.Refused
                    Search.Failed(
                        cleaned,
                        notLoggedIn = it is SpotifyApi.NotLoggedIn,
                        status = refused?.status,
                        reason = refused?.reason,
                        podcastsOnly = podcastsOnly
                    )
                }
            )
        }
    }
}
