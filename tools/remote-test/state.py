#!/usr/bin/env python3
"""Shows what plays on two emulators side by side: title, state, position now, media volume.
Usage: state.py SERIAL [SERIAL ...] (adb server port from ANDROID_ADB_SERVER_PORT)."""
import os, re, subprocess, sys
PORT = os.environ.get("ANDROID_ADB_SERVER_PORT", "5037")
def adb(serial, *a):
    return subprocess.run(["adb", "-P", PORT, "-s", serial, *a], capture_output=True, text=True).stdout
def state(serial):
    out = adb(serial, "shell", "dumpsys media_session; echo UPTIME; cat /proc/uptime; echo VOL; dumpsys audio | grep -A6 'STREAM_MUSIC:' | grep -m1 'streamVolume'")
    m = re.search(r"state=PlaybackState \{state=(\w+)\((\d)\), position=(-?\d+),.*?speed=([\d.]+), updated=(\d+)", out)
    d = re.search(r"description=([^\n]*)", out)
    up = re.search(r"UPTIME\n([\d.]+)", out)
    vol = re.search(r"streamVolume:(\d+)", out)
    if not m: return f"{serial}: no session"
    name, code, pos, speed, upd = m[1], int(m[2]), int(m[3]), float(m[4]), int(m[5])
    now = float(up[1]) * 1000 if up else upd
    cur = pos + (now - upd) * speed if code == 3 else pos
    return f"{serial}: {name:9} {cur/1000:7.1f}s  {d[1] if d else '?':45.45}  vol={vol[1] if vol else '?'}"
for s in sys.argv[1:]: print(state(s))
