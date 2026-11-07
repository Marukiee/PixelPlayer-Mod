package com.theveloper.pixelplay

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.theveloper.pixelplay.data.spotify.SpotifyAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Catches Spotify's answer after the login page (the redirect URI) and goes back to the app. Shows nothing itself.
 * The answer only counts when it belongs to a login the app started, see [SpotifyAuth.complete].
 */
@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {
    @Inject
    lateinit var spotifyAuth: SpotifyAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val answer = intent?.data
        if (answer == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val ok = spotifyAuth.complete(answer)
            Toast.makeText(
                applicationContext,
                if (ok) R.string.spotify_login_done else R.string.spotify_login_failed,
                Toast.LENGTH_LONG
            ).show()
            // The answer arrives in the browser's task, where a new main screen would start on the home page. Bring
            // the app's own task forward instead, as the launcher icon does, so it is back on the Spotify settings.
            packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }
            finish()
        }
    }
}
