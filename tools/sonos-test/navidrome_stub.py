#!/usr/bin/env python3
"""Subsonic stub for the emulator tests. Answers everything with ok and streams one short file.
usage: navidrome_stub.py audio.flac cover.png songs.json   (listens on 127.0.0.1:4533; adb reverse tcp:4533 tcp:4533)"""
import json, re, sys
from urllib.parse import urlparse, parse_qs
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
DATA = open(sys.argv[1], "rb").read(); COVER = open(sys.argv[2], "rb").read()
SONGS = json.load(open(sys.argv[3]))
def ok(extra=None):
    d = {"status": "ok", "version": "1.16.1"}; d.update(extra or {})
    return json.dumps({"subsonic-response": d}).encode()
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def out(self, b, ct="application/json"):
        self.send_response(200); self.send_header("Content-Type", ct); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_POST(self): self.do_GET()
    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query); ep = u.path.rsplit("/", 1)[-1].replace(".view", "")
        # Only the call and the song id, never the login in the address.
        if ep in ("scrobble", "star", "unstar"):
            print(ep, q.get("id", [""])[0], q.get("time", [""])[0], q.get("submission", [""])[0], flush=True)
        if ep == "getCoverArt": return self.out(COVER, "image/png")
        if ep == "stream":
            total = len(DATA); start, end = 0, total - 1; code = 200
            r = re.match(r"bytes=(\d+)-(\d*)", self.headers.get("Range", ""))
            if r: start = int(r[1]); end = int(r[2]) if r[2] else total - 1; code = 206
            self.send_response(code); self.send_header("Content-Type", "audio/flac"); self.send_header("Accept-Ranges", "bytes"); self.send_header("Content-Length", str(end - start + 1))
            if code == 206: self.send_header("Content-Range", f"bytes {start}-{end}/{total}")
            self.end_headers()
            try: self.wfile.write(DATA[start:end + 1])
            except (BrokenPipeError, ConnectionResetError): pass
            return
        if ep == "search3":
            t = q.get("query", [""])[0].lower()
            return self.out(ok({"searchResult3": {"song": [x for x in SONGS if t in x["title"].lower()]}}))
        # PixelPlayerOSS fetches the library album by album.
        if ep == "getAlbumList2":
            first = int(q.get("offset", ["0"])[0]) == 0
            album = {"id": "a1", "name": "Test album", "title": "Test album", "artist": "Test artist", "songCount": len(SONGS), "coverArt": "a1"}
            return self.out(ok({"albumList2": {"album": [album] if first else []}}))
        if ep == "getAlbum":
            return self.out(ok({"album": {"id": "a1", "name": "Test album", "artist": "Test artist", "songCount": len(SONGS), "song": SONGS}}))
        if ep == "getSong":
            return self.out(ok({"song": next((x for x in SONGS if x["id"] == q.get("id", [""])[0]), {})}))
        return self.out(ok())
ThreadingHTTPServer(("127.0.0.1", 4533), H).serve_forever()
