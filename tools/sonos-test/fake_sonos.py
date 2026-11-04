#!/usr/bin/env python3
"""Fake Sonos household for testing PixelPlayer on the emulator.

Three speakers on ports 1400-1402 share one state. Every SOAP request is parsed with a
real XML parser (so escaping mistakes fail loudly), applied to a simulated transport,
and logged as JSON lines.

Timing is measured on a real speaker: calls take 12 to 90 ms, and Play or a track Seek
during playback gives TRANSITIONING before PLAYING. A Pause is accepted during
TRANSITIONING and takes effect at once. See README.md for how to run it.
"""
import json
import random
import sys
import threading
import time
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from xml.sax.saxutils import escape

LOG = sys.argv[1] if len(sys.argv) > 1 else "fake_sonos.log"
HOST = "127.0.0.1"
LOCK = threading.RLock()

SPEAKERS = {
    1400: {"uuid": "RINCON_LIVING01400", "name": "Living room", "model": "Sonos Era 300"},
    1401: {"uuid": "RINCON_KITCHEN01400", "name": "Kitchen", "model": "Sonos Era 100"},
    1402: {"uuid": "RINCON_BEDROOM01400", "name": "Bedroom", "model": "Sonos One"},
}
for port, sp in SPEAKERS.items():
    sp.update(port=port, coordinator=sp["uuid"], volume=20 + port - 1400,
              uri="", queue=[], track=0, state="STOPPED", pos=0.0, started=None, mode="NORMAL")
BY_UUID = {sp["uuid"]: sp for sp in SPEAKERS.values()}


def log(entry):
    entry["t"] = round(time.time(), 2)
    with open(LOG, "a") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def hms(ms):
    s = int(ms // 1000)
    return "%d:%02d:%02d" % (s // 3600, (s // 60) % 60, s % 60)


def parse_hms(v):
    h, m, s = v.split(":")
    return ((int(h) * 60 + int(m)) * 60 + float(s)) * 1000


LATENCY = {"Play": (14, 58), "Pause": (20, 86), "Seek": (16, 20), "AddURIToQueue": (30, 90),
           "RemoveAllTracksFromQueue": (20, 60), "RemoveTrackRangeFromQueue": (20, 60), "SetAVTransportURI": (20, 60)}


def transition(sp):
    """A speaker needs a moment before it really plays."""
    sp["state"], sp["started"] = "TRANSITIONING", None
    sp["until"] = time.time() + random.uniform(0.2, 0.6)


def settle(sp):
    if sp["state"] == "TRANSITIONING" and time.time() >= sp.get("until", 0):
        sp["state"], sp["started"] = "PLAYING", time.time()
        log({"event": "now_playing", "track": sp["track"] + 1})


def position(sp):
    if sp["state"] == "PLAYING" and sp["started"] is not None:
        return sp["pos"] + (time.time() - sp["started"]) * 1000
    return sp["pos"]


def tick(sp):
    """Advances the simulated playback past finished tracks."""
    settle(sp)
    while sp["state"] == "PLAYING" and sp["queue"]:
        dur = sp["queue"][sp["track"]]["duration"]
        p = position(sp)
        if p < dur:
            return
        overshoot = p - dur
        if sp["track"] + 1 < len(sp["queue"]):
            sp["track"] += 1
            sp["pos"], sp["started"] = 0.0, time.time() - overshoot / 1000
            log({"event": "auto_next", "track": sp["track"] + 1})
        else:
            sp["state"], sp["pos"], sp["started"] = "STOPPED", 0.0, None
            log({"event": "queue_end"})


def set_pos(sp, ms):
    sp["pos"] = ms
    sp["started"] = time.time() if sp["state"] == "PLAYING" else None


def topology():
    groups = {}
    for sp in SPEAKERS.values():
        groups.setdefault(sp["coordinator"], []).append(sp)
    parts = ["<ZoneGroupState><ZoneGroups>"]
    for coord, members in groups.items():
        parts.append('<ZoneGroup Coordinator="%s" ID="%s:1">' % (coord, coord))
        for m in members:
            parts.append('<ZoneGroupMember UUID="%s" Location="http://%s:%d/xml/device_description.xml" ZoneName="%s"/>'
                         % (m["uuid"], HOST, m["port"], m["name"]))
        parts.append("</ZoneGroup>")
    parts.append("</ZoneGroups><VanishedDevices/></ZoneGroupState>")
    return "".join(parts)


class Fault(Exception):
    def __init__(self, code):
        self.code = code


def av_transport(sp, action, a):
    coord_only = action not in ("SetAVTransportURI", "BecomeCoordinatorOfStandaloneGroup")
    if coord_only and sp["coordinator"] != sp["uuid"]:
        raise Fault(800)
    tick(sp)
    if action == "GetTransportInfo":
        return {"CurrentTransportState": sp["state"], "CurrentTransportStatus": "OK", "CurrentSpeed": "1"}
    if action == "GetPositionInfo":
        q = sp["queue"]
        cur = q[sp["track"]] if q and sp["uri"].startswith("x-rincon-queue:") else None
        return {"Track": str(sp["track"] + 1 if cur else 0), "TrackDuration": hms(cur["duration"]) if cur else "0:00:00",
                "TrackMetaData": "", "TrackURI": cur["uri"] if cur else "", "RelTime": hms(position(sp)),
                "AbsTime": "NOT_IMPLEMENTED", "RelCount": "2147483647", "AbsCount": "2147483647"}
    if action == "GetMediaInfo":
        return {"NrTracks": str(len(sp["queue"])), "MediaDuration": "NOT_IMPLEMENTED", "CurrentURI": sp["uri"],
                "CurrentURIMetaData": "", "NextURI": "", "NextURIMetaData": "", "PlayMedium": "NETWORK",
                "RecordMedium": "NOT_IMPLEMENTED", "WriteStatus": "NOT_IMPLEMENTED"}
    if action == "SetAVTransportURI":
        uri = a["CurrentURI"]
        if uri.startswith("x-rincon:"):
            target = uri.split(":", 1)[1]
            sp.update(coordinator=target, uri=uri, state="PLAYING" if BY_UUID[target]["state"] == "PLAYING" else "STOPPED")
            log({"event": "joined", "speaker": sp["name"], "coordinator": BY_UUID[target]["name"]})
        else:
            sp.update(uri=uri, track=0, state="STOPPED")
            set_pos(sp, 0)
        return {}
    if action == "BecomeCoordinatorOfStandaloneGroup":
        sp.update(coordinator=sp["uuid"], uri="", state="STOPPED")
        log({"event": "left", "speaker": sp["name"]})
        return {}
    if action == "AddURIToQueue":
        didl = ET.fromstring(a["EnqueuedURIMetaData"])  # second layer must be valid XML
        ns = {"d": "urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/", "dc": "http://purl.org/dc/elements/1.1/"}
        res = didl.find(".//d:res", ns)
        if res is None or res.text != a["EnqueuedURI"]:
            raise Fault(402)
        title = didl.find(".//dc:title", ns).text
        dur = parse_hms(res.get("duration", "0:03:00"))
        art = didl.find(".//{urn:schemas-upnp-org:metadata-1-0/upnp/}albumArtURI")
        sp["cover"] = art.text.split("/rest/")[0] if art is not None else None
        sp["queue"].append({"uri": a["EnqueuedURI"], "title": title, "duration": dur, "mime": res.get("protocolInfo")})
        n = len(sp["queue"])
        return {"FirstTrackNumberEnqueued": str(n), "NumTracksAdded": "1", "NewQueueLength": str(n)}
    if action == "RemoveAllTracksFromQueue":
        sp.update(queue=[], track=0, state="STOPPED")
        set_pos(sp, 0)
        return {}
    if action == "RemoveTrackRangeFromQueue":
        start, count = int(a["StartingIndex"]), int(a["NumberOfTracks"])
        if start < 1 or start - 1 + count > len(sp["queue"]):
            raise Fault(402)
        del sp["queue"][start - 1:start - 1 + count]
        return {"NewUpdateID": "1"}
    if action == "Seek":
        if a["Unit"] == "TRACK_NR":
            n = int(a["Target"])
            if not 1 <= n <= len(sp["queue"]):
                raise Fault(711)
            sp["track"] = n - 1
            if sp["state"] in ("PLAYING", "TRANSITIONING"):
                sp["pos"] = 0.0
                transition(sp)
            else:
                set_pos(sp, 0)
        else:
            set_pos(sp, parse_hms(a["Target"]))
        return {}
    if action == "Play":
        if not sp["queue"] or not sp["uri"].startswith("x-rincon-queue:"):
            raise Fault(701)
        if sp["state"] not in ("PLAYING", "TRANSITIONING"):
            transition(sp)
        return {}
    if action == "Pause":
        if sp["state"] not in ("PLAYING", "TRANSITIONING"):
            raise Fault(701)
        sp["pos"], sp["state"], sp["started"] = position(sp), "PAUSED_PLAYBACK", None
        return {}
    if action == "Next":
        if sp["track"] + 1 >= len(sp["queue"]):
            raise Fault(711)
        sp["track"] += 1
        set_pos(sp, 0)
        return {}
    if action == "SetPlayMode":
        sp["mode"] = a["NewPlayMode"]
        return {}
    raise Fault(401)


def members_of(sp):
    return [m for m in SPEAKERS.values() if m["coordinator"] == sp["uuid"]]


def handle(sp, service, action, a):
    if service == "AVTransport":
        return av_transport(sp, action, a)
    if service == "RenderingControl":
        if action == "GetVolume":
            return {"CurrentVolume": str(sp["volume"])}
        if action == "SetVolume":
            sp["volume"] = int(a["DesiredVolume"])
            return {}
    if service == "GroupRenderingControl":
        if sp["coordinator"] != sp["uuid"]:
            raise Fault(701)
        ms = members_of(sp)
        if action == "GetGroupVolume":
            return {"CurrentVolume": str(round(sum(m["volume"] for m in ms) / len(ms)))}
        if action == "SetGroupVolume":
            for m in ms:
                m["volume"] = int(a["DesiredVolume"])
            return {}
        if action == "GetGroupMute":
            return {"CurrentMute": "1" if sp.get("muted") else "0"}
        if action == "SetGroupMute":
            sp["muted"] = str(a["DesiredMute"]) == "1"
            return {}
    if service == "ZoneGroupTopology" and action == "GetZoneGroupState":
        return {"ZoneGroupState": topology()}
    raise Fault(401)


def envelope(urn, action, out):
    body = "".join("<%s>%s</%s>" % (k, escape(v), k) for k, v in out.items())
    return ('<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
            's:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>'
            '<u:%sResponse xmlns:u="%s">%s</u:%sResponse></s:Body></s:Envelope>' % (action, urn, body, action))


def fault(code):
    return ('<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>'
            '<faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>'
            '<UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>%d</errorCode></UPnPError>'
            '</detail></s:Fault></s:Body></s:Envelope>' % code)


class Handler(BaseHTTPRequestHandler):
    # One packet per answer: two small writes wait on the delayed ack of the emulator network (a full second).
    wbufsize = -1
    disable_nagle_algorithm = True

    def log_message(self, *args):
        pass

    def reply(self, code, text, ctype="text/xml"):
        data = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        sp = SPEAKERS[self.server.server_port]
        with LOCK:
            if self.path == "/xml/device_description.xml":
                return self.reply(200, "<root><device><modelName>%s</modelName><roomName>%s</roomName></device></root>"
                                  % (sp["model"], sp["name"]))
            coord = BY_UUID["RINCON_LIVING01400"]
            if self.path == "/debug/state":
                for s in SPEAKERS.values():
                    tick(s)
                state = {s["name"]: {"coordinator": BY_UUID[s["coordinator"]]["name"], "volume": s["volume"],
                                     "state": s["state"], "track": s["track"] + 1, "pos": round(position(s) / 1000, 1),
                                     "mode": s["mode"], "uri": s["uri"][:40], "cover": s.get("cover"),
                                     "queue": [q["title"] for q in s["queue"]]} for s in SPEAKERS.values()}
                return self.reply(200, json.dumps(state, ensure_ascii=False, indent=1), "application/json")
            if self.path == "/debug/nudge":
                # Moves the speaker a little, like the drift a real speaker shows against the phone.
                if coord["state"] == "PLAYING" and coord["started"] is not None:
                    coord["started"] -= 0.6
                return self.reply(200, "ok", "text/plain")
            if self.path == "/debug/skip":
                av_transport(coord, "Next", {})
                log({"event": "debug_skip"})
                return self.reply(200, "ok", "text/plain")
            if self.path == "/debug/pause":
                av_transport(coord, "Pause", {})
                log({"event": "debug_pause"})
                return self.reply(200, "ok", "text/plain")
            if self.path == "/debug/takeover":
                coord.update(uri="x-sonos-htastream:RINCON_LIVING01400:spdif", state="PLAYING")
                log({"event": "debug_takeover"})
                return self.reply(200, "ok", "text/plain")
        self.reply(404, "not found", "text/plain")

    def do_POST(self):
        sp = SPEAKERS[self.server.server_port]
        arrived = time.time()
        raw = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode("utf-8")
        body_read = time.time()
        log({"event": "req", "path": self.path, "read_ms": round((body_read - arrived) * 1000), "hdr": dict(self.headers)})
        soap_action = self.headers.get("SOAPACTION", "").strip('"')
        try:
            root = ET.fromstring(raw)
            call = list(list(root)[0])[0]
            urn, action = call.tag[1:].split("}")
            a = {c.tag.split("}")[-1]: (c.text or "") for c in call}
            service = urn.split(":")[-2]
            if soap_action != "%s#%s" % (urn, action):
                raise Fault(401)
        except Fault as f:
            log({"speaker": sp["name"], "error": f.code, "raw": raw[:200]})
            return self.reply(500, fault(f.code))
        except Exception as e:  # noqa: BLE001
            log({"speaker": sp["name"], "parse_error": str(e), "raw": raw[:300]})
            return self.reply(500, fault(402))
        shown = {k: (v[:70] + "...") if len(v) > 70 else v for k, v in a.items()
                 if k not in ("InstanceID", "EnqueuedURIMetaData", "CurrentURIMetaData")}
        quiet = action in ("GetTransportInfo", "GetPositionInfo", "GetMediaInfo", "GetVolume", "GetGroupVolume")
        low, high = LATENCY.get(action, (12, 16))
        time.sleep(random.uniform(low, high) / 1000)
        with LOCK:
            try:
                out = handle(sp, service, action, a)
                if not quiet:
                    log({"speaker": sp["name"], "action": action, "args": shown})
                self.reply(200, envelope(urn, action, out))
            except Fault as f:
                log({"speaker": sp["name"], "action": action, "args": shown, "fault": f.code})
                self.reply(500, fault(f.code))


if __name__ == "__main__":
    for port in SPEAKERS:
        server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
    log({"event": "started"})
    while True:
        time.sleep(3600)
