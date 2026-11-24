package com.lostf1sh.pixelplayeross.data.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lostf1sh.pixelplayeross.MainActivity
import com.lostf1sh.pixelplayeross.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Keeps "Use as remote player" going with the screen off: waits on Bluetooth for the controlling phone and hands each
 * line to [RemotePlayerHost]. A foreground service of the connected-device kind, with a quiet notification.
 */
@AndroidEntryPoint
class RemotePlayerService : Service() {
    @Inject lateinit var host: RemotePlayerHost
    @Inject lateinit var settings: RemotePlayerSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val servers = ArrayList<RemoteServer>()
    private val listening = MutableStateFlow(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        if (!goForeground(buildNotification(null, listening = false))) {
            stopSelf()
            return
        }
        servers += BluetoothRemoteServer(this, onLink = host::attach, onListening = { listening.value = it })
        RemoteTransport.tcpListenPort(this)?.let { port -> servers += TcpRemoteServer(port, host::attach) }
        scope.launch {
            combine(host.controllerName, listening) { name, on -> name to on }.collect { (name, on) ->
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(name, on))
            }
        }
        scope.launch {
            settings.enabled.collect { on -> if (!on) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TURN_OFF) {
            settings.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        servers.forEach { it.stop() }
        servers.clear()
        host.detachAll()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(notification: Notification): Boolean = runCatching {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }.onFailure { Timber.tag(TAG).w(it, "Could not start the remote player in the foreground") }.isSuccess

    private fun buildNotification(controller: String?, listening: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val turnOff = PendingIntent.getService(
            this, 1,
            Intent(this, RemotePlayerService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = when {
            controller != null -> getString(R.string.remote_player_notification_connected, controller)
            listening -> getString(R.string.remote_player_notification_ready)
            else -> getString(R.string.remote_player_notification_no_bluetooth)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(getString(R.string.remote_player_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.remote_player_notification_turn_off), turnOff)
            .build()
    }

    companion object {
        private const val TAG = "RemotePlayerService"
        private const val CHANNEL_ID = "remote_player"
        private const val NOTIFICATION_ID = 7341
        private const val ACTION_TURN_OFF = "com.lostf1sh.pixelplayeross.remote.TURN_OFF"

        /** Starts or stops the service to match the setting. Only from the app in the foreground: Android refuses otherwise. */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, RemotePlayerService::class.java)
            if (enabled) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { Timber.tag(TAG).w(it, "Could not start the remote player") }
            } else {
                context.stopService(intent)
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.remote_player_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
    }
}
