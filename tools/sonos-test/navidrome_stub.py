#!/usr/bin/env python3
"""Subsonic stub for the emulator tests. Answers everything with ok and streams one short file.
usage: navidrome_stub.py audio.flac cover.png songs.json [playlists.json]
   (listens on 127.0.0.1:4533, or STUB_PORT; adb reverse tcp:4533 tcp:4533)
Playlists live in memory: playlists.json can start it with some, as a list of
{"id", "name", "comment", "owner", "created", "songs": [song ids]}. GET /stub/playlists shows them."""
import json, re, sys, threading, time
from datetime import datetime, timezone
from urllib.parse import urlparse, parse_qs
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
DATA = open(sys.argv[1], "rb").read(); COVER = open(sys.argv[2], "rb").read()
SONGS = json.load(open(sys.argv[3]))
LOCK = threading.Lock()
def now_iso(): return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
PLAYLISTS = {}
for p in (json.load(open(sys.argv[4])) if len(sys.argv) > 4 else []):
    p.setdefault("created", now_iso()); p.setdefault("changed", p["created"]); p.setdefault("public", False)
    PLAYLISTS[p["id"]] = p
NEXT_ID = [len(PLAYLISTS) + 1]
def song(i): return next((x for x in SONGS if x["id"] == i), {"id": i, "title": i})
def summary(p):
    return {k: p[k] for k in ("id", "name", "comment", "owner", "public", "created", "changed") if k in p} | {"songCount": len(p["songs"])}
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
        if u.path == "/stub/playlists":
            with LOCK: return self.out(json.dumps(list(PLAYLISTS.values()), indent=1).encode())
        if ep in ("getPlaylists", "getPlaylist", "createPlaylist", "updatePlaylist", "deletePlaylist"):
            return self.playlists(ep, q)
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
    def playlists(self, ep, q):
        one = lambda k: q.get(k, [None])[0]
        with LOCK:
            if ep == "getPlaylists":
                return self.out(ok({"playlists": {"playlist": [summary(p) for p in PLAYLISTS.values()]}}))
            p = PLAYLISTS.get(one("id") or one("playlistId") or "")
            if ep == "getPlaylist":
                if not p: return self.out(ok({"status": "failed", "error": {"code": 70, "message": "not found"}}))
                return self.out(ok({"playlist": summary(p) | {"entry": [song(i) for i in p["songs"]]}}))
            if ep == "createPlaylist":
                if p is None and one("playlistId"): return self.out(ok({"status": "failed", "error": {"code": 70, "message": "not found"}}))
                if p is None:
                    p = {"id": f"pl{NEXT_ID[0]}", "name": one("name") or "", "owner": one("u") or "", "public": False, "created": now_iso()}
                    NEXT_ID[0] += 1; PLAYLISTS[p["id"]] = p
                p["songs"] = q.get("songId", []); p["changed"] = now_iso()
                print(ep, p["id"], len(p["songs"]), flush=True)
                return self.out(ok({"playlist": summary(p) | {"entry": [song(i) for i in p["songs"]]}}))
            if not p: return self.out(ok({"status": "failed", "error": {"code": 70, "message": "not found"}}))
            if ep == "updatePlaylist":
                if one("name") is not None: p["name"] = one("name")
                if one("comment") is not None: p["comment"] = one("comment")
                if one("public") is not None: p["public"] = one("public") == "true"
                for i in sorted((int(x) for x in q.get("songIndexToRemove", [])), reverse=True):
                    if 0 <= i < len(p["songs"]): del p["songs"][i]
                p["songs"] += q.get("songIdToAdd", []); p["changed"] = now_iso()
                print(ep, p["id"], len(p["songs"]), p.get("comment", ""), flush=True)
                return self.out(ok())
            if ep == "deletePlaylist":
                del PLAYLISTS[p["id"]]; print(ep, p["id"], flush=True)
                return self.out(ok())
import os
ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("STUB_PORT", "4533"))), H).serve_forever()
