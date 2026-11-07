package com.theveloper.pixelplay.data.spotify

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs in to Spotify with the Client ID of the app Mark makes himself on developer.spotify.com, with PKCE, so there is
 * no secret in the app. Keeps the tokens encrypted and out of every backup, and never logs them.
 */
@Singleton
class SpotifyAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    data class Account(val clientId: String, val loggedIn: Boolean, val displayName: String?)

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "$TAG: no encrypted preferences, falling back to plain")
        context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
    }

    private val mutex = Mutex()
    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account> = _account.asStateFlow()

    /** Only for the emulator tests: a file next to `sonos_hosts.txt` points both bases at a stub. */
    private val testBase: String? = runCatching {
        File(context.getExternalFilesDir(null), "spotify_test_base.txt").takeIf { it.exists() }?.readText()?.trim()
    }.getOrNull()?.takeIf { it.startsWith("http://127.0.0.1:") }

    val accountsBase: String get() = testBase ?: "https://accounts.spotify.com"
    val apiBase: String get() = testBase ?: "https://api.spotify.com"

    fun setClientId(clientId: String) {
        val cleaned = clientId.trim()
        if (cleaned == prefs.getString(KEY_CLIENT_ID, "")) return
        prefs.edit {
            putString(KEY_CLIENT_ID, cleaned)
            clearTokens()
        }
        publish()
    }

    /** Spotify's login page for this app, or null without a Client ID. */
    fun loginIntent(): Intent? {
        val clientId = prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val verifier = randomText(64)
        val state = randomText(24)
        prefs.edit {
            putString(KEY_VERIFIER, verifier)
            putString(KEY_STATE, state)
        }
        val uri = Uri.parse("$accountsBase/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", Pkce.challenge(verifier))
            .appendQueryParameter("state", state)
            .build()
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Finishes the login with Spotify's answer. True when it worked. */
    suspend fun complete(callback: Uri): Boolean = withContext(Dispatchers.IO) {
        val expected = prefs.getString(KEY_STATE, null)
        val verifier = prefs.getString(KEY_VERIFIER, null)
        val clientId = prefs.getString(KEY_CLIENT_ID, null)
        prefs.edit {
            remove(KEY_STATE)
            remove(KEY_VERIFIER)
        }
        if (expected == null || verifier == null || clientId == null || callback.getQueryParameter("state") != expected) {
            Timber.w("$TAG: login answer did not match a login that was started")
            return@withContext false
        }
        val code = callback.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            Timber.w("$TAG: login refused: ${callback.getQueryParameter("error")}")
            return@withContext false
        }
        val tokens = postToken(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", clientId)
                .add("code_verifier", verifier)
                .build()
        ) ?: return@withContext false
        saveTokens(tokens)
        prefs.edit { putString(KEY_NAME, fetchDisplayName(tokens.getString("access_token"))) }
        publish()
        true
    }

    /** A valid access token, refreshed when it ran out or when Spotify said it was not good any more. */
    suspend fun accessToken(forceRefresh: Boolean = false): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val token = prefs.getString(KEY_ACCESS, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
            if (!forceRefresh && token != null && System.currentTimeMillis() < expiresAt - EARLY_REFRESH_MS) {
                return@withContext token
            }
            val refresh = prefs.getString(KEY_REFRESH, null) ?: return@withContext null
            val clientId = prefs.getString(KEY_CLIENT_ID, null) ?: return@withContext null
            val tokens = postToken(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .add("client_id", clientId)
                    .build()
            )
            if (tokens == null) {
                // A refresh token lasts six months: after that only a new login helps.
                if (lastTokenError == "invalid_grant") logout()
                return@withContext null
            }
            saveTokens(tokens)
            tokens.getString("access_token")
        }
    }

    fun logout() {
        prefs.edit { clearTokens() }
        publish()
    }

    private var lastTokenError: String? = null

    private fun postToken(body: FormBody): JSONObject? = runCatching {
        val request = Request.Builder().url("$accountsBase/api/token").post(body).build()
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body.string()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful || json == null || !json.has("access_token")) {
                lastTokenError = json?.optString("error")
                Timber.w("$TAG: token request answered ${response.code} ${lastTokenError.orEmpty()}")
                null
            } else {
                lastTokenError = null
                json
            }
        }
    }.onFailure { Timber.w("$TAG: token request failed: ${it.javaClass.simpleName}") }.getOrNull()

    private fun fetchDisplayName(accessToken: String): String? = runCatching {
        val request = Request.Builder().url("$apiBase/v1/me").header("Authorization", "Bearer $accessToken").build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            JSONObject(response.body.string()).optString("display_name").takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun saveTokens(tokens: JSONObject) {
        prefs.edit {
            putString(KEY_ACCESS, tokens.getString("access_token"))
            putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + tokens.optLong("expires_in", 3600L) * 1_000L)
            // Spotify does not always send a new refresh token; the old one stays good then.
            tokens.optString("refresh_token").takeIf { it.isNotBlank() }?.let { putString(KEY_REFRESH, it) }
        }
    }

    private fun SharedPreferences.Editor.clearTokens() {
        remove(KEY_ACCESS)
        remove(KEY_REFRESH)
        remove(KEY_EXPIRES_AT)
        remove(KEY_NAME)
    }

    private fun readAccount() = Account(
        clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        loggedIn = prefs.getString(KEY_REFRESH, null) != null,
        displayName = prefs.getString(KEY_NAME, null)
    )

    private fun publish() {
        _account.value = readAccount()
    }

    private fun randomText(length: Int): String {
        val random = SecureRandom()
        return buildString(length) { repeat(length) { append(PKCE_CHARS[random.nextInt(PKCE_CHARS.length)]) } }
    }

    companion object {
        /** Has to be entered exactly like this under Redirect URIs in the Spotify app. */
        const val REDIRECT_URI = "pixelplaymod://spotify-callback"
        const val DASHBOARD_URL = "https://developer.spotify.com/dashboard"
        private const val TAG = "SpotifyAuth"
        private const val PREFS_NAME = "spotify_prefs"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_NAME = "display_name"
        private const val KEY_STATE = "login_state"
        private const val KEY_VERIFIER = "login_verifier"
        private const val EARLY_REFRESH_MS = 60_000L
        private const val PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    }
}

/** The PKCE challenge: base64url of the SHA-256 of the verifier, without padding. */
internal object Pkce {
    fun challenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
