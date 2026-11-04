#!/usr/bin/env python3
"""Small SOAP helper for one real speaker. Never prints stream addresses, they hold the Navidrome login."""
import html
import json
import os
import re
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

SVC = {
    "AVTransport": "/MediaRenderer/AVTransport/Control",
    "RenderingControl": "/MediaRenderer/RenderingControl/Control",
    "ContentDirectory": "/MediaServer/ContentDirectory/Control",
}
# The queue holds the Navidrome login, so this never goes next to the code. Delete it when the test is done.
BACKUP = os.path.join(tempfile.gettempdir(), "sonos_queue_backup.json")


def soap(host, service, action, timeout=5, **args):
    body = "".join(f"<{k}>{v}</{k}>" for k, v in args.items())
    env = (
        '<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" '
        's:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>'
        f'<u:{action} xmlns:u="urn:schemas-upnp-org:service:{service}:1">{body}</u:{action}>'
        "</s:Body></s:Envelope>"
    )
    req = urllib.request.Request(
        f"http://{host}{SVC[service]}",
        data=env.encode(),
        headers={
            "Content-Type": 'text/xml; charset="utf-8"',
            "SOAPACTION": f'"urn:schemas-upnp-org:service:{service}:1#{action}"',
        },
    )
    raw = urllib.request.urlopen(req, timeout=timeout).read()
    out = {}
    for el in ET.fromstring(raw).iter():
        if el.text is not None and "}" not in el.tag:
            out[el.tag] = el.text
    return out


def avt(host, action, **args):
    return soap(host, "AVTransport", action, InstanceID=0, **args)


def title_of(metadata):
    m = re.search(r"<dc:title>(.*?)</dc:title>", metadata or "", re.S)
    return html.unescape(m.group(1)) if m else None


def get_mute(host):
    return soap(host, "RenderingControl", "GetMute", InstanceID=0, Channel="Master").get("CurrentMute")


def set_mute(host, on):
    soap(host, "RenderingControl", "SetMute", InstanceID=0, Channel="Master", DesiredMute=1 if on else 0)


def get_volume(host):
    return soap(host, "RenderingControl", "GetVolume", InstanceID=0, Channel="Master").get("CurrentVolume")


def queue(host):
    items, start = [], 0
    while True:
        r = soap(host, "ContentDirectory", "Browse", timeout=15, ObjectID="Q:0",
                 BrowseFlag="BrowseDirectChildren", Filter="*", StartingIndex=start,
                 RequestedCount=100, SortCriteria="")
        total = int(r.get("TotalMatches", 0))
        for item in re.findall(r"<item\b.*?</item>", r.get("Result", ""), re.S):
            res = re.search(r"<res\b[^>]*>(.*?)</res>", item, re.S)
            uri = html.unescape(res.group(1)) if res else ""
            items.append({"uri": uri, "didl": item})
        start = len(items)
        if start >= total or not r.get("Result"):
            return items


def backup(host):
    t = avt(host, "GetTransportInfo")
    p = avt(host, "GetPositionInfo")
    m = avt(host, "GetMediaInfo")
    s = avt(host, "GetTransportSettings")
    data = {
        "state": t.get("CurrentTransportState"),
        "track": p.get("Track"),
        "time": p.get("RelTime"),
        "uri": m.get("CurrentURI"),
        "mode": s.get("PlayMode"),
        "volume": get_volume(host),
        "mute": get_mute(host),
        "queue": queue(host),
    }
    fd = os.open(BACKUP, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(data, f)
    print("bewaard:", {k: (len(v) if k == "queue" else v) for k, v in data.items() if k != "uri"})
    print("bron is wachtrij:", (data["uri"] or "").startswith("x-rincon-queue:"))


DIDL_OPEN = ('<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" '
             'xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" '
             'xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" '
             'xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">')


def restore(host):
    d = json.load(open(BACKUP))
    if avt(host, "GetTransportInfo").get("CurrentTransportState") in ("PLAYING", "TRANSITIONING"):
        avt(host, "Pause")
    avt(host, "RemoveAllTracksFromQueue")
    for i, q in enumerate(d["queue"], 1):
        soap(
            host, "AVTransport", "AddURIToQueue", timeout=15, InstanceID=0,
            EnqueuedURI=escape(q["uri"]),
            EnqueuedURIMetaData=escape(DIDL_OPEN + q["didl"] + "</DIDL-Lite>"),
            DesiredFirstTrackNumberEnqueued=i, EnqueueAsNext=0)
    avt(host, "SetAVTransportURI", CurrentURI=escape(d["uri"]), CurrentURIMetaData="")
    avt(host, "SetPlayMode", NewPlayMode=d["mode"])
    avt(host, "Seek", Unit="TRACK_NR", Target=d["track"])
    avt(host, "Seek", Unit="REL_TIME", Target=d["time"])
    state = avt(host, "GetTransportInfo").get("CurrentTransportState")
    if state in ("PLAYING", "TRANSITIONING"):
        avt(host, "Pause")
    soap(host, "RenderingControl", "SetVolume", InstanceID=0, Channel="Master", DesiredVolume=d["volume"])
    set_mute(host, d["mute"] == "1")
    print("teruggezet:", len(queue(host)), "nummers")


if __name__ == "__main__":
    h, cmd = sys.argv[1], sys.argv[2]
    if cmd == "backup":
        backup(h)
    elif cmd == "mute":
        set_mute(h, True)
        print("mute:", get_mute(h))
    elif cmd == "restore":
        restore(h)
