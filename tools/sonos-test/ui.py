#!/usr/bin/env python3
"""Reads the screen of a device through uiautomator and taps a node by its text. Usage: ui.py SERIAL dump | tap TEXT"""
import html, os, re, subprocess, sys
PORT = os.environ.get("ANDROID_ADB_SERVER_PORT", "5038")
serial = sys.argv[1]
def adb(*a): return subprocess.run(["adb", "-P", PORT, "-s", serial, *a], capture_output=True, text=True).stdout
def nodes():
    x = adb("exec-out", "uiautomator", "dump", "/dev/tty")
    out = []
    for m in re.finditer(r"<node\b[^>]*>", x):
        n = m[0]
        t = html.unescape(re.search(r' text="([^"]*)"', n)[1])
        d = html.unescape(re.search(r' content-desc="([^"]*)"', n)[1])
        b = tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
        if t or d: out.append((t, d, b))
    return out
if sys.argv[2] == "dump":
    for t, d, b in nodes(): print(f"{t!r} {d!r} {b}")
else:
    want = sys.argv[3]
    all_nodes = nodes()
    hit = next((b for t, d, b in all_nodes if want in (t, d)), None) or next((b for t, d, b in all_nodes if want in t or want in d), None)
    if not hit:
        print("NOT FOUND", want); sys.exit(1)
    adb("shell", "input", "tap", str((hit[0] + hit[2]) // 2), str((hit[1] + hit[3]) // 2))
    print("tapped", want, hit)
