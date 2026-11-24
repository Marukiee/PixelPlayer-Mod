package com.lostf1sh.pixelplayeross.data.remote

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * One open line to the other phone, over Bluetooth or (for tests) TCP: JSON objects, one per line, both ways.
 * Reading and writing run on their own threads, so a slow radio never holds up the caller.
 */
class RemoteLink(
    input: InputStream,
    output: OutputStream,
    private val closer: Closeable,
    /** What the other side calls itself as far as the radio knows; the hello may give a better name. */
    val peerName: String,
    /** Bluetooth address or host and port: tells a reconnect it reached the same phone. */
    val peerKey: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 16 * 1024)
    private val writer = OutputStreamWriter(output, Charsets.UTF_8)
    private val outbox = Channel<String>(OUTBOX_SIZE)
    private val inbox = Channel<JSONObject>(Channel.UNLIMITED)

    @Volatile
    var isOpen = true
        private set

    /** When the last message came in, by the uptime clock, which stands still while the phone sleeps. */
    @Volatile
    var lastReceivedAt = SystemClock.uptimeMillis()
        private set

    /** Every message from the other side; ends when the line closes. */
    val messages: Flow<JSONObject> = inbox.receiveAsFlow()

    init {
        scope.launch {
            try {
                for (line in outbox) {
                    writer.write(line)
                    writer.write("\n")
                    writer.flush()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Write ended: %s", e.message)
            } finally {
                close()
            }
        }
        scope.launch {
            try {
                while (isOpen) {
                    val line = reader.readLine() ?: break
                    lastReceivedAt = SystemClock.uptimeMillis()
                    if (line.isBlank()) continue
                    if (line.length > MAX_LINE) {
                        Timber.tag(TAG).w("Dropped a message of %d chars", line.length)
                        continue
                    }
                    val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    inbox.trySend(json)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Read ended: %s", e.message)
            } finally {
                close()
            }
        }
    }

    /** Queues a message; false once the line is closed or so far behind that it is as good as gone. */
    fun send(message: JSONObject): Boolean {
        if (!isOpen) return false
        val ok = outbox.trySend(message.toString()).isSuccess
        if (!ok) close()
        return ok
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        outbox.close()
        inbox.close()
        runCatching { closer.close() }
        scope.cancel()
    }

    private companion object {
        const val TAG = "RemoteLink"
        const val OUTBOX_SIZE = 64
        /** A queue of a few hundred songs is far below this; anything bigger is not ours. */
        const val MAX_LINE = 4 * 1024 * 1024
    }
}
