package com.lostf1sh.pixelplayeross.data.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Where a remote player can be reached. */
sealed interface RemoteEndpoint {
    /** Stays the same for one phone, also across restarts. */
    val key: String
    val name: String

    data class Bluetooth(val address: String, override val name: String) : RemoteEndpoint {
        override val key get() = "bt:$address"
    }

    /** Only for tests on emulators, which have no Bluetooth between them, and later for a computer. */
    data class Tcp(val host: String, val port: Int, override val name: String) : RemoteEndpoint {
        override val key get() = "tcp:$host:$port"
    }
}

object RemoteTransport {
    private const val TAG = "RemoteTransport"

    /**
     * Test hook, like `sonos_hosts.txt`: each line `host:port name` in the app's external files folder adds a remote
     * player reached over TCP.
     */
    private const val TCP_HOSTS_FILE = "remote_hosts.txt"

    /** Test hook: a port number in this file makes the remote player listen on TCP as well. */
    private const val TCP_LISTEN_FILE = "remote_listen_port.txt"

    fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun bluetoothOn(context: Context): Boolean = runCatching { adapter(context)?.isEnabled == true }.getOrDefault(false)

    /** The name this phone shows to others. */
    fun ownName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    /**
     * Paired phones that might run PixelPlayer as remote player. Headphones, speakers, cars and computers are left
     * out: knocking on those would only cost time.
     */
    @SuppressLint("MissingPermission")
    fun pairedCandidates(context: Context): List<RemoteEndpoint.Bluetooth> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val adapter = adapter(context) ?: return emptyList()
        return runCatching {
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices.orEmpty().filter { device ->
                // Paging a device out of reach holds the radio for seconds, which can make Bluetooth headphones stutter,
                // so only phones get a knock.
                val major = device.bluetoothClass?.majorDeviceClass
                major == null || major == BluetoothClass.Device.Major.PHONE
            }.map { device ->
                val name = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null) ?: device.name ?: device.address
                RemoteEndpoint.Bluetooth(device.address, name)
            }
        }.getOrElse {
            Timber.tag(TAG).w(it, "Could not read the paired devices")
            emptyList()
        }
    }

    fun tcpCandidates(context: Context): List<RemoteEndpoint.Tcp> {
        val file = context.getExternalFilesDir(null)?.let { File(it, TCP_HOSTS_FILE) } ?: return emptyList()
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val address = trimmed.substringBefore(' ')
            val name = trimmed.substringAfter(' ', "").trim().ifEmpty { address }
            val host = address.substringBeforeLast(':')
            val port = address.substringAfterLast(':').toIntOrNull() ?: return@mapNotNull null
            RemoteEndpoint.Tcp(host, port, name)
        }
    }

    fun tcpListenPort(context: Context): Int? =
        context.getExternalFilesDir(null)?.let { File(it, TCP_LISTEN_FILE) }
            ?.takeIf { it.isFile }
            ?.readText()?.trim()?.toIntOrNull()

    /** Opens a line to [endpoint]; throws when it cannot be reached within [timeoutMs]. */
    @SuppressLint("MissingPermission")
    suspend fun open(context: Context, endpoint: RemoteEndpoint, timeoutMs: Long): RemoteLink = withContext(Dispatchers.IO) {
        when (endpoint) {
            is RemoteEndpoint.Tcp -> {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs.toInt())
                    socket.tcpNoDelay = true
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    throw e
                }
                RemoteLink(socket.getInputStream(), socket.getOutputStream(), socket, endpoint.name, endpoint.key)
            }
            is RemoteEndpoint.Bluetooth -> {
                check(hasBluetoothPermission(context)) { "No Bluetooth permission" }
                val adapter = adapter(context) ?: error("No Bluetooth")
                check(adapter.isEnabled) { "Bluetooth is off" }
                // A running search slows every connection down a lot.
                runCatching { adapter.cancelDiscovery() }
                val device = adapter.getRemoteDevice(endpoint.address)
                val socket = device.createRfcommSocketToServiceRecord(RemoteProtocol.SERVICE_UUID)
                // connect() has no timeout of its own and only stops when the socket is closed.
                coroutineScope {
                    val watchdog = launch {
                        delay(timeoutMs)
                        runCatching { socket.close() }
                    }
                    try {
                        socket.connect()
                    } catch (e: Exception) {
                        runCatching { socket.close() }
                        throw e
                    } finally {
                        watchdog.cancel()
                    }
                }
                // Given up on while connecting: the line would stay open with nobody on it.
                if (!isActive) {
                    runCatching { socket.close() }
                    throw kotlinx.coroutines.CancellationException("Search stopped")
                }
                RemoteLink(socket.inputStream, socket.outputStream, socket, endpoint.name, endpoint.key)
            }
        }
    }
}

/** Waits for controllers; hands every new line to [onLink]. Runs on a thread of its own until [stop]. */
interface RemoteServer {
    fun stop()
}

class BluetoothRemoteServer(
    private val context: Context,
    private val onLink: (RemoteLink) -> Unit,
    private val onListening: (Boolean) -> Unit
) : RemoteServer {
    @Volatile
    private var running = true

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    private val worker = thread(name = "RemoteBtServer", isDaemon = true) { loop() }

    @SuppressLint("MissingPermission")
    private fun loop() {
        while (running) {
            val adapter = RemoteTransport.adapter(context)
            if (adapter == null || !RemoteTransport.hasBluetoothPermission(context) || !runCatching { adapter.isEnabled }.getOrDefault(false)) {
                onListening(false)
                sleep(RETRY_MS)
                continue
            }
            val server = runCatching {
                adapter.listenUsingRfcommWithServiceRecord(RemoteProtocol.SERVICE_NAME, RemoteProtocol.SERVICE_UUID)
            }.getOrElse {
                Timber.tag(TAG).w(it, "Could not listen on Bluetooth")
                onListening(false)
                sleep(RETRY_MS)
                continue
            }
            serverSocket = server
            onListening(true)
            try {
                while (running) {
                    val socket = server.accept() ?: break
                    val device = socket.remoteDevice
                    val name = runCatching { (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null) ?: device.name }.getOrNull() ?: device.address
                    onLink(RemoteLink(socket.inputStream, socket.outputStream, socket, name, "bt:${device.address}"))
                }
            } catch (e: Exception) {
                // Bluetooth went off, or stop() closed the socket.
                Timber.tag(TAG).d("Bluetooth accept ended: %s", e.message)
            } finally {
                runCatching { server.close() }
                serverSocket = null
                onListening(false)
            }
            if (running) sleep(RETRY_MS)
        }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    override fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        worker.interrupt()
    }

    private companion object {
        const val TAG = "RemoteBtServer"
        const val RETRY_MS = 3_000L
    }
}

class TcpRemoteServer(private val port: Int, private val onLink: (RemoteLink) -> Unit) : RemoteServer {
    @Volatile
    private var running = true
    private var server: ServerSocket? = null

    init {
        thread(name = "RemoteTcpServer", isDaemon = true) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(port))
                server = socket
                while (running) {
                    val client = socket.accept()
                    client.tcpNoDelay = true
                    val key = "tcp:${client.inetAddress.hostAddress}:${client.port}"
                    onLink(RemoteLink(client.getInputStream(), client.getOutputStream(), client, "TCP", key))
                }
            } catch (e: Exception) {
                Timber.tag("RemoteTcpServer").d("TCP accept ended: %s", e.message)
            }
        }
    }

    override fun stop() {
        running = false
        runCatching { server?.close() }
    }
}
