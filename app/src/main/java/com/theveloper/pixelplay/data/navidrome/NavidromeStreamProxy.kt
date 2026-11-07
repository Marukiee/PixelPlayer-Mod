package com.theveloper.pixelplay.data.navidrome

import android.net.Uri
import com.theveloper.pixelplay.data.stream.CloudStreamProxy
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming Navidrome/Subsonic audio.
 *
 * Resolves `navidrome://{songId}` URIs by generating authenticated streaming URLs
 * from the Navidrome server and proxying the audio data to ExoPlayer.
 */
@Singleton
class NavidromeStreamProxy @Inject constructor(
    private val repository: NavidromeRepository,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient) {

    // Dynamically determine allowed hosts from the configured server URL.
    // We allow both HTTP and HTTPS for self-hosted servers.
    override val allowedHostSuffixes: Set<String>
        get() = repository.serverUrl?.toHttpUrlOrNull()?.host?.let { setOf(it) } ?: emptySet()

    // Stream URLs with authentication tokens are valid for a limited time.
    // Set cache expiration to 30 minutes to match typical token validity.
    override val cacheExpirationMs = 30L * 60 * 1000

    override val proxyTag = "NavidromeStreamProxy"
    override val routePath = "/navidrome/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = "navidrome"
    override val routePrefix = "/navidrome"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateNavidromeSongId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            repository.getStreamUrl(id)
        } catch (e: Exception) {
            Timber.w(e, "NavidromeStreamProxy: Failed to resolve stream URL for song $id")
            null
        }
    }

    companion object {
        /**
         * The song's own address for a proxy address that an older version saved with the queue. The proxy takes a
         * new port at every start, so the saved one led nowhere and the first song after a restart failed.
         */
        fun originalUri(saved: String): String? {
            val uri = Uri.parse(saved)
            if (uri.scheme != "http" || uri.host != "127.0.0.1") return null
            val segments = uri.pathSegments
            if (segments.size != 2 || segments[0] != "navidrome") return null
            val id = segments[1].takeIf(CloudStreamSecurity::validateNavidromeSongId) ?: return null
            return "navidrome://$id"
        }
    }

    // Navidrome URIs may use host or path: navidrome://songId or navidrome:///songId
    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveNavidromeUri(uriString: String): String? = resolveUri(uriString)

    /**
     * The server's own stream address for the phone's player, checked the way the proxy checks it. The player reads
     * it directly: through this proxy a core stayed busy for as long as a song played, because the CIO socket writer
     * keeps writing while the player has a full buffer and reads nothing. Made fresh every time: it carries a login
     * token, costs nothing to build, and a kept one would outlive a new login.
     */
    suspend fun directStreamUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return null
        val id = extractIdFromUri(uri)?.let(::parseRouteParam)?.takeIf(::validateId) ?: return null
        val url = resolveStreamUrl(id) ?: return null
        return url.takeIf {
            CloudStreamSecurity.isSafeRemoteStreamUrl(it, allowedHostSuffixes, allowHttpForAllowedHosts = true)
        }
    }

    /**
     * Pre-fetches and caches the real stream URL for a song so the proxy can
     * serve it instantly when ExoPlayer makes its HTTP request.
     */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "navidrome") return
        val songId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateNavidromeSongId(songId)) return
        try {
            getOrFetchStreamUrl(songId)
        } catch (e: Exception) {
            Timber.w(e, "warmUpStreamUrl failed for $songId")
        }
    }
}
