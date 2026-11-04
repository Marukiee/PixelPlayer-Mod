package com.theveloper.pixelplay.data.sonos

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import timber.log.Timber
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class SonosSpeaker(
    val uuid: String,
    val name: String,
    /** "ip:port" of the speaker's UPnP server, usually port 1400. */
    val host: String,
    val model: String? = null
)

data class SonosGroup(
    val id: String,
    val coordinatorUuid: String,
    /** Coordinator first, then the other rooms by name. */
    val members: List<SonosSpeaker>
) {
    val coordinator: SonosSpeaker get() = members.first { it.uuid == coordinatorUuid }
    val displayName: String get() = members.joinToString(" + ") { it.name }
    /** Every room on its own line, for places with room to show the whole group. */
    val stackedNames: String get() = members.joinToString("\n") { it.name }
    /** "Living room +2": fits in a small pill. */
    val shortName: String get() = if (members.size > 1) "${coordinator.name} +${members.size - 1}" else coordinator.name
    val queueUri: String get() = "x-rincon-queue:$coordinatorUuid#0"
}

/**
 * Finds Sonos speakers with SSDP and reads the room groups from any one of them.
 *
 * Hosts that answered before are remembered and tried again, so a network that drops
 * multicast still finds known speakers. Extra hosts can be listed one per line in
 * `Android/data/<package>/files/sonos_hosts.txt` (used for testing on the emulator).
 */
class SonosDiscovery(
    private val context: Context,
    private val soap: SonosSoapClient
) {
    private val prefs = context.getSharedPreferences("sonos", Context.MODE_PRIVATE)
    private val modelCache = HashMap<String, String?>()

    suspend fun discoverGroups(): List<SonosGroup> {
        val hosts = LinkedHashSet<String>()
        hosts += ssdpSearch()
        hosts += manualHosts()
        hosts += prefs.getStringSet(KEY_KNOWN_HOSTS, emptySet()).orEmpty()
        for (host in hosts) {
            val groups = runCatching { readGroups(host) }
                .onFailure { Timber.tag(TAG).d("No topology from %s: %s", host, it.message) }
                .getOrNull() ?: continue
            val known = groups.flatMap { g -> g.members.map { it.host } }.toSet()
            prefs.edit().putStringSet(KEY_KNOWN_HOSTS, known).apply()
            saveGroups(groups)
            return groups
        }
        return emptyList()
    }

    /** The rooms from the last search, so the connect sheet can show them before searching again. */
    fun cachedGroups(): List<SonosGroup> = runCatching {
        val array = JSONArray(prefs.getString(KEY_GROUPS, null) ?: return emptyList())
        (0 until array.length()).map { i ->
            val g = array.getJSONObject(i)
            val members = g.getJSONArray("members")
            SonosGroup(
                id = g.getString("id"),
                coordinatorUuid = g.getString("coordinator"),
                members = (0 until members.length()).map { j ->
                    val m = members.getJSONObject(j)
                    SonosSpeaker(
                        uuid = m.getString("uuid"),
                        name = m.getString("name"),
                        host = m.getString("host"),
                        model = m.optString("model").ifBlank { null }
                    )
                }
            )
        }
    }.getOrDefault(emptyList())

    fun saveGroups(groups: List<SonosGroup>) {
        val array = JSONArray()
        for (group in groups) {
            val members = JSONArray()
            group.members.forEach { m ->
                members.put(
                    JSONObject().put("uuid", m.uuid).put("name", m.name).put("host", m.host).put("model", m.model ?: "")
                )
            }
            array.put(JSONObject().put("id", group.id).put("coordinator", group.coordinatorUuid).put("members", members))
        }
        prefs.edit().putString(KEY_GROUPS, array.toString()).apply()
    }

    suspend fun readGroups(host: String): List<SonosGroup> {
        val state = soap.call(host, SonosService.ZONE_GROUP_TOPOLOGY, "GetZoneGroupState")["ZoneGroupState"]
            ?: throw SonosException("No ZoneGroupState")
        return parseZoneGroupState(state).map { group ->
            group.copy(members = group.members.map { it.copy(model = modelFor(it)) })
        }
    }

    private suspend fun modelFor(speaker: SonosSpeaker): String? {
        modelCache[speaker.uuid]?.let { return it }
        val model = runCatching {
            val xml = soap.get(speaker.host, "/xml/device_description.xml")
            Regex("<modelName>([^<]*)</modelName>").find(xml)?.groupValues?.get(1)
                ?.removePrefix("Sonos ")?.trim()
        }.getOrNull()
        if (model != null) modelCache[speaker.uuid] = model
        return model
    }

    private fun manualHosts(): List<String> = runCatching {
        val file = File(context.getExternalFilesDir(null), "sonos_hosts.txt")
        if (!file.exists()) emptyList() else file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    private suspend fun ssdpSearch(timeoutMs: Int = 2_000): Set<String> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("pixelplay-sonos")?.apply { setReferenceCounted(false) }
        val found = LinkedHashSet<String>()
        try {
            lock?.acquire()
            DatagramSocket().use { socket ->
                socket.soTimeout = 400
                val message = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\n" +
                        "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n"
                    ).toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(message, message.size, group, 1900)) }
                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length)
                    parseLocationHost(text)?.let { found += it }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "SSDP search failed")
        } finally {
            runCatching { lock?.release() }
        }
        found
    }

    companion object {
        private const val TAG = "SonosDiscovery"
        private const val KEY_KNOWN_HOSTS = "known_hosts"
        private const val KEY_GROUPS = "groups"

        /** Takes "ip:port" out of the LOCATION header of an SSDP answer. */
        fun parseLocationHost(response: String): String? {
            val location = response.lineSequence()
                .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                ?.substringAfter(':')?.trim() ?: return null
            return hostOf(location)
        }

        fun hostOf(url: String): String? =
            Regex("^https?://([^/]+)").find(url.trim())?.groupValues?.get(1)

        /**
         * Parses the ZoneGroupState XML. Hidden members (surrounds, subs, bonded pairs)
         * are left out; a group without visible members is dropped.
         */
        fun parseZoneGroupState(xml: String): List<SonosGroup> {
            val doc = SonosSoapClient.parseXml(xml)
            val groupNodes = doc.getElementsByTagName("ZoneGroup")
            val groups = ArrayList<SonosGroup>()
            for (i in 0 until groupNodes.length) {
                val groupEl = groupNodes.item(i) as Element
                val coordinator = groupEl.getAttribute("Coordinator")
                val memberNodes = groupEl.getElementsByTagName("ZoneGroupMember")
                val members = ArrayList<SonosSpeaker>()
                for (j in 0 until memberNodes.length) {
                    val m = memberNodes.item(j) as Element
                    if (m.getAttribute("Invisible") == "1") continue
                    val host = hostOf(m.getAttribute("Location")) ?: continue
                    members += SonosSpeaker(
                        uuid = m.getAttribute("UUID"),
                        name = m.getAttribute("ZoneName").ifBlank { "Sonos" },
                        host = host
                    )
                }
                if (members.none { it.uuid == coordinator }) continue
                val sorted = members.sortedWith(compareBy({ it.uuid != coordinator }, { it.name }))
                groups += SonosGroup(groupEl.getAttribute("ID"), coordinator, sorted)
            }
            return groups.sortedBy { it.coordinator.name }
        }
    }
}
