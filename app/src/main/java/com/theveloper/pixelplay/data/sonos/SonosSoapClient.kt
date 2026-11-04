package com.theveloper.pixelplay.data.sonos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

enum class SonosService(val controlPath: String, val urn: String) {
    AV_TRANSPORT("/MediaRenderer/AVTransport/Control", "urn:schemas-upnp-org:service:AVTransport:1"),
    RENDERING_CONTROL("/MediaRenderer/RenderingControl/Control", "urn:schemas-upnp-org:service:RenderingControl:1"),
    GROUP_RENDERING_CONTROL(
        "/MediaRenderer/GroupRenderingControl/Control",
        "urn:schemas-upnp-org:service:GroupRenderingControl:1"
    ),
    ZONE_GROUP_TOPOLOGY("/ZoneGroupTopology/Control", "urn:schemas-upnp-org:service:ZoneGroupTopology:1"),
}

/** A UPnP fault or network failure. [upnpCode] is the speaker's errorCode (701, 714, ...) when it sent one. */
class SonosException(message: String, val upnpCode: Int? = null, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Minimal UPnP SOAP client for Sonos speakers on the local network. Each call goes to
 * `http://<host>/<control path>`; arguments are escaped once here, so callers pass plain
 * text (including DIDL-Lite metadata, which therefore ends up escaped as SOAP requires).
 */
class SonosSoapClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    /** Gives up quickly, for a call that must never hold up what comes after it. */
    private val quickHttp = http.newBuilder().callTimeout(1_500, TimeUnit.MILLISECONDS).build()

    suspend fun call(
        host: String,
        service: SonosService,
        action: String,
        vararg args: Pair<String, Any>
    ): Map<String, String> = execute(http, host, service, action, args)

    suspend fun callQuick(
        host: String,
        service: SonosService,
        action: String,
        vararg args: Pair<String, Any>
    ): Map<String, String> = execute(quickHttp, host, service, action, args)

    private suspend fun execute(
        client: OkHttpClient,
        host: String,
        service: SonosService,
        action: String,
        args: Array<out Pair<String, Any>>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val body = buildEnvelope(service, action, args.toList())
        val request = Request.Builder()
            .url("http://$host${service.controlPath}")
            .header("SOAPACTION", "\"${service.urn}#$action\"")
            // Speakers close the socket after each answer; reusing it stalls the next call.
            .header("Connection", "close")
            .post(body.toRequestBody(XML_MEDIA_TYPE))
            .build()
        val (code, text) = try {
            client.newCall(request).execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw SonosException("$action: ${e.message ?: "no connection"}", cause = e)
        }
        if (code !in 200..299) {
            val upnpCode = parseFaultCode(text)
            throw SonosException("$action failed (UPnP ${upnpCode ?: code})", upnpCode ?: code)
        }
        parseResponse(text, action)
    }

    /** Fetches `GET http://host/path`, used for the device description. */
    suspend fun get(host: String, path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("http://$host$path").header("Connection", "close").build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw SonosException("GET $path: ${response.code}", response.code)
                response.body.string()
            }
        } catch (e: IOException) {
            if (e is SonosException) throw e
            throw SonosException("GET $path: ${e.message ?: "no connection"}", cause = e)
        }
    }

    companion object {
        private val XML_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()

        fun buildEnvelope(service: SonosService, action: String, args: List<Pair<String, Any>>): String =
            buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
                append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
                append("<u:").append(action).append(" xmlns:u=\"").append(service.urn).append("\">")
                for ((name, value) in args) {
                    append('<').append(name).append('>')
                    append(xmlEscape(value.toString()))
                    append("</").append(name).append('>')
                }
                append("</u:").append(action).append("></s:Body></s:Envelope>")
            }

        fun xmlEscape(value: String): String = buildString(value.length + 16) {
            for (c in value) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }

        /** Returns the child elements of `<u:ActionResponse>` as name to text. */
        fun parseResponse(xml: String, action: String): Map<String, String> {
            if (xml.isBlank()) return emptyMap()
            val doc = parseXml(xml)
            val nodes = doc.getElementsByTagNameNS("*", "${action}Response")
            val response = (if (nodes.length > 0) nodes.item(0) else null) as? Element ?: return emptyMap()
            val result = LinkedHashMap<String, String>()
            val children = response.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    result[child.localName ?: child.nodeName] = child.textContent.orEmpty()
                }
            }
            return result
        }

        fun parseFaultCode(xml: String): Int? = runCatching {
            val doc = parseXml(xml)
            val codes = doc.getElementsByTagNameNS("*", "errorCode")
            if (codes.length > 0) codes.item(0).textContent.trim().toIntOrNull() else null
        }.getOrNull()

        fun parseXml(xml: String) = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))

        /** "0:03:41" to milliseconds; "NOT_IMPLEMENTED" or blanks give 0. */
        fun hmsToMs(value: String?): Long {
            val parts = value?.trim()?.split(':') ?: return 0L
            if (parts.size != 3) return 0L
            val h = parts[0].toLongOrNull() ?: return 0L
            val m = parts[1].toLongOrNull() ?: return 0L
            val s = parts[2].substringBefore('.').toLongOrNull() ?: return 0L
            return ((h * 60 + m) * 60 + s) * 1000L
        }

        fun msToHms(ms: Long): String {
            val total = (ms.coerceAtLeast(0L) / 1000L)
            return "%d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
        }
    }
}
