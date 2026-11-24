package com.lostf1sh.pixelplayeross.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The language between a phone that controls (the controller) and a phone that plays (the player).
 *
 * One JSON object per line. The controller keeps the queue and sends what the player should play; the player plays its
 * own copy of each file and reports where it is. Songs travel as a [RemoteTrack]: a few path segments plus the tags,
 * never a media id, because each phone numbers its songs in its own way.
 */
object RemoteProtocol {
    const val VERSION = 1

    /** The Bluetooth service PixelPlayer listens on as a remote player. Fixed, so every copy of the app finds the others. */
    val SERVICE_UUID: UUID = UUID.fromString("6f0c6a52-8d1e-4b0e-9f3a-2c7d1e5b9a41")
    const val SERVICE_NAME = "PixelPlayer remote player"

    // Message types.
    const val HELLO = "hello"
    const val QUEUE = "queue"
    const val JUMP = "jump"
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val VOLUME = "volume"
    const val PING = "ping"
    const val STATE = "state"
    const val QUEUED = "queued"
    const val BYE = "bye"
    /** The player's system would not go as loud as asked: Android's hearing warning waits on its screen. */
    const val VOLUME_HELD = "volume_held"

    fun hello(name: String, role: String): JSONObject =
        JSONObject().put("t", HELLO).put("v", VERSION).put("name", name).put("role", role)

    fun queue(qid: Long, items: List<RemoteTrack>, index: Int, positionMs: Long, play: Boolean): JSONObject =
        JSONObject()
            .put("t", QUEUE)
            .put("qid", qid)
            .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
            .put("index", index)
            .put("pos", positionMs)
            .put("play", play)

    /** Moves to the song with this id, the one nearest to [index] in the last queue. */
    fun jump(qid: Long, id: String, index: Int, positionMs: Long, play: Boolean): JSONObject =
        JSONObject().put("t", JUMP).put("qid", qid).put("id", id).put("index", index).put("pos", positionMs).put("play", play)

    fun play(): JSONObject = JSONObject().put("t", PLAY)
    fun pause(): JSONObject = JSONObject().put("t", PAUSE)
    fun seek(positionMs: Long): JSONObject = JSONObject().put("t", SEEK).put("pos", positionMs)
    fun volume(percent: Int): JSONObject = JSONObject().put("t", VOLUME).put("v", percent)
    fun ping(): JSONObject = JSONObject().put("t", PING)
    fun bye(): JSONObject = JSONObject().put("t", BYE)
    fun volumeHeld(): JSONObject = JSONObject().put("t", VOLUME_HELD)

    fun state(state: RemotePlayerState): JSONObject = JSONObject()
        .put("t", STATE)
        .put("qid", state.queueId)
        .put("id", state.currentId ?: JSONObject.NULL)
        .put("foreign", state.foreign)
        .put("pos", state.positionMs)
        .put("dur", state.durationMs)
        .put("play", state.playWhenReady)
        .put("playing", state.isPlaying)
        .put("vol", state.volumePercent)
        .put("steps", state.volumeSteps)

    fun queued(qid: Long, missing: List<String>, found: Int): JSONObject =
        JSONObject().put("t", QUEUED).put("qid", qid).put("missing", JSONArray(missing)).put("found", found)

    fun parseState(json: JSONObject) = RemotePlayerState(
        queueId = json.optLong("qid", 0L),
        currentId = json.optString("id").takeIf { !json.isNull("id") && it.isNotEmpty() },
        foreign = json.optBoolean("foreign", false),
        positionMs = json.optLong("pos", 0L),
        durationMs = json.optLong("dur", 0L),
        playWhenReady = json.optBoolean("play", false),
        isPlaying = json.optBoolean("playing", false),
        volumePercent = json.optInt("vol", -1).takeIf { it >= 0 },
        volumeSteps = json.optInt("steps", 0)
    )
}

/** What the player reports, about once a second and on every change. */
data class RemotePlayerState(
    /** The queue this song belongs to; 0 before the controller sent one. */
    val queueId: Long,
    /** The controller's id of the song that plays, or null when nothing of the controller's queue plays. */
    val currentId: String?,
    /** Something else plays: picked on the player itself. */
    val foreign: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    /** The player's media volume, 0 to 100. */
    val volumePercent: Int?,
    /** How many steps the player's volume has, so a volume key moves it one step. */
    val volumeSteps: Int = 0
)

/**
 * A song as it travels: the controller's own id for it, the last folders and the file name, and the tags.
 * Both phones hold the same music folder (Syncthing), but each may keep it in another place, so only the
 * end of the path counts.
 */
data class RemoteTrack(
    val id: String,
    /** The last [PATH_SEGMENTS] segments of the file path, or empty for a song that is not a file (a server song). */
    val pathTail: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("p", pathTail)
        .put("ti", title)
        .put("ar", artist)
        .put("al", album)
        .put("d", durationMs)

    companion object {
        const val PATH_SEGMENTS = 3

        fun fromJson(json: JSONObject) = RemoteTrack(
            id = json.optString("id"),
            pathTail = json.optString("p"),
            title = json.optString("ti"),
            artist = json.optString("ar"),
            album = json.optString("al"),
            durationMs = json.optLong("d", 0L)
        )

        /** The end of a file path that both phones share: artist, album and file, without the folder Syncthing lives in. */
        fun pathTail(path: String?, segments: Int = PATH_SEGMENTS): String {
            if (path.isNullOrBlank() || path.contains("://")) return ""
            return path.replace('\\', '/').split('/').filter { it.isNotEmpty() }.takeLast(segments).joinToString("/")
        }
    }
}
