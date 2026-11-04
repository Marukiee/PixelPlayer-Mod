#!/usr/bin/env python3
"""The scenarios of skiptest.py, on one real speaker, with media keys and with real taps.

There is no command log here, so a thread polls the speaker and notes every change. It also keeps
the speaker muted, or with vol=N under that volume, and says so when it has to step in.
Usage: real_skiptest.py SERIAL HOST ROUNDS SEED [tap=NEXT_X,NEXT_Y,PLAY_X,PLAY_Y] [onlytap] [tapskip] [vol=N] [dry]
"""
import html
import os
import random
import re
import subprocess
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from real_sonos import avt, get_mute, get_volume, set_mute, soap, title_of


def set_volume(host, volume):
    soap(host, "RenderingControl", "SetVolume", InstanceID=0, Channel="Master", DesiredVolume=volume)


SERIAL, HOST, ROUNDS, SEED = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
# vol=N: keeps the sound on, but never above N. Without it the speaker stays muted.
MAX_VOLUME = next((int(a[4:]) for a in sys.argv[5:] if a.startswith("vol=")), None)
TAP = next((tuple(map(int, a[4:].split(","))) for a in sys.argv[5:] if a.startswith("tap=")), None)
DRY = "dry" in sys.argv[5:]
random.seed(SEED)
NEXT, PREV, PLAY, PAUSE = 87, 88, 126, 127


def adb(cmd, timeout=40):
    return subprocess.run(["adb", "-s", SERIAL, "shell", cmd], capture_output=True, text=True, timeout=timeout).stdout


def keys(seq):
    """seq: (ms, key). Every key starts on its own, so the spacing is not stretched by the time one key takes."""
    adb(" ".join(f"(sleep {ms/1000:.3f}; input keyevent {k}) &" for ms, k in seq) + " wait")


def taps(seq):
    adb(" ".join(f"(sleep {ms/1000:.3f}; input tap {x} {y}) &" for ms, (x, y) in seq) + " wait")


def speaker():
    state = avt(HOST, "GetTransportInfo").get("CurrentTransportState", "?")
    p = avt(HOST, "GetPositionInfo")
    n = int(p.get("Track") or 0)
    q = soap(HOST, "ContentDirectory", "Browse", timeout=10, ObjectID="Q:0", BrowseFlag="BrowseDirectChildren",
             Filter="dc:title", StartingIndex=0, RequestedCount=1, SortCriteria="")
    return state, title_of(p.get("TrackMetaData")), n, int(q.get("TotalMatches", 0))


def phone():
    out = adb("dumpsys media_session")
    m = re.search(r"package=com\.theveloper\.pixelplay.*?(?=\n\s*Sessions Stack|\Z)", out, re.S)
    b = m.group(0) if m else ""
    b = b[:b.find("package=", 20)] if b.find("package=", 20) > 0 else b
    st = re.search(r"state=PlaybackState \{state=(\w+)", b)
    t = re.search(r"description=([^,\n]*)", b)
    return st.group(1) if st else "?", html.unescape(t.group(1).strip()) if t else None


class Watcher(threading.Thread):
    daemon = True

    def __init__(self):
        super().__init__()
        self.events, self.mute_off, self.running = [], 0, True
        self.lock = threading.Lock()

    def run(self):
        last, last_mute = None, 0.0
        while self.running:
            try:
                state = avt(HOST, "GetTransportInfo", ).get("CurrentTransportState", "?")
                track = avt(HOST, "GetPositionInfo").get("Track")
                if (state, track) != last:
                    with self.lock:
                        self.events.append((time.time(), state, track))
                    last = (state, track)
                if time.time() - last_mute > 0.5:
                    last_mute = time.time()
                    if MAX_VOLUME is not None:
                        if int(get_volume(HOST)) > MAX_VOLUME:
                            self.mute_off += 1
                            set_volume(HOST, MAX_VOLUME)
                            print(f"LET OP: volume stond te hoog, terug naar {MAX_VOLUME}", flush=True)
                    elif get_mute(HOST) != "1":
                        self.mute_off += 1
                        set_mute(HOST, True)
                        print("LET OP: mute stond uit, weer aangezet", flush=True)
            except Exception as e:
                print("meetfout:", type(e).__name__, flush=True)
            time.sleep(0.08)

    def since(self, t0):
        with self.lock:
            return [e for e in self.events if e[0] >= t0]


w = Watcher()
w.start()
if DRY:
    time.sleep(2)
    print("speaker:", speaker(), "telefoon:", phone(), "gebeurtenissen:", len(w.events), "mute:", get_mute(HOST))
    sys.exit(0)


def starts(evs, before_track):
    """How many songs reached PLAYING after t0, on any track that was not simply still playing."""
    count, prev_state, prev_track = 0, "PLAYING", before_track
    for _, s, t in evs:
        if s == "PLAYING" and (prev_state != "PLAYING" or t != prev_track):
            count += 1
        prev_state, prev_track = s, t
    return count


def short(evs, t0):
    return " ".join(f"{e[0]-t0:.1f}:{e[1][:5]}/{e[2]}" for e in evs)


fails = total = errors = invalid = 0
plays, pause_ms = [], []


def recover():
    if phone()[0] == "ERROR":
        keys([(0, NEXT)])
        time.sleep(1.5)
        keys([(0, PLAY)])
        time.sleep(1.5)


def ensure_playing():
    recover()
    for _ in range(6):
        if phone()[0] == "PLAYING" and speaker()[0] == "PLAYING":
            return True
        if phone()[0] != "PLAYING":
            keys([(0, PLAY)])
        time.sleep(1.6)
    return phone()[0] == "PLAYING" and speaker()[0] == "PLAYING"


def ensure_paused():
    recover()
    for _ in range(4):
        if phone()[0] == "PAUSED" and speaker()[0] not in ("PLAYING", "TRANSITIONING"):
            return True
        if phone()[0] != "PAUSED":
            keys([(0, PAUSE)])
        time.sleep(1.6)
    return False


def room(needed):
    for _ in range(3):
        _, _, n, qlen = speaker()
        if qlen - n > needed:
            return
        keys([(i * 130, PREV) for i in range(18)])
        time.sleep(4.0)
        recover()


def same(title, pt):
    return title == pt or (bool(pt) and bool(title) and title.startswith(pt))


def check(name, want, t0, settle=6.5, before=None, before_track=None, pause_at=None, count=True, skips=None):
    """Waits past every grace period, because the old fault showed when the phone started following the speaker."""
    global fails, total, errors, invalid
    total += 1
    time.sleep(settle)
    s, title, n, qlen = speaker()
    p, pt = phone()
    if p == "BUFFERING" or s == "TRANSITIONING":
        time.sleep(3)
        s, title, n, qlen = speaker()
        p, pt = phone()
    ok_state = (s == "PLAYING" and p == "PLAYING") if want == "play" else (s in ("PAUSED_PLAYBACK", "STOPPED") and p == "PAUSED")
    # The song the phone was told to go to, not the one a speaker happened to start on the way there.
    landed = skips is None or before is None or n == before[0] + skips
    ok = ok_state and same(title, pt) and landed
    if p == "ERROR":
        errors += 1
    if before is not None and (n, title) == before:
        invalid += 1
        total -= 1
        print(f"ONGELDIG {name}: er is niet geskipt", flush=True)
        return True
    evs = w.since(t0)
    started = starts(evs, before_track)
    if count:
        plays.append(started)
    delay = ""
    if pause_at is not None:
        stopped = [e for e in evs if e[0] >= pause_at and e[1] in ("PAUSED_PLAYBACK", "STOPPED")]
        if stopped and want == "pause":
            pause_ms.append((stopped[-1][0] - pause_at) * 1000)
            delay = f" stil_na={(stopped[-1][0]-pause_at)*1000:.0f}ms"
    print(f"{'ok  ' if ok else 'FOUT'} {name:46s} speaker={s[:7]:7s} '{(title or '')[:14]}' telefoon={p[:7]:7s} '{(pt or '')[:14]}' startte={started}{delay}{'' if landed else f' LANDING: nummer {n}, verwacht {before[0] + skips}'}", flush=True)
    if not ok:
        fails += 1
        print("     ", short(evs, t0)[:220], flush=True)
    return ok


def marker():
    st = speaker()
    return (st[2], st[1]), str(st[2]), time.time()


def scenarios():
    # A: snel skippen terwijl hij speelt, en dan pauze, op allerlei momenten
    for n, gap, after in ((3, 150, 60), (3, 150, 300), (3, 300, 60), (3, 300, 300), (3, 300, 700), (5, 200, 100),
                          (5, 350, 0), (5, 350, 500), (2, 500, 100), (4, 700, 150), (6, 120, 30)):
        room(n + 1)
        if not ensure_playing():
            print("kon niet starten", flush=True)
            break
        before, track, t0 = marker()
        t, seq = 0, []
        for i in range(n):
            seq.append((t, NEXT))
            t += gap
        pause_offset = t - gap + after
        seq.append((pause_offset, PAUSE))
        keys(seq)
        check(f"{n} skips om de {gap} ms, pauze {after} ms later", "pause", t0, before=before, before_track=track,
              pause_at=t0 + pause_offset / 1000, skips=n)
    # B: alleen snel skippen: de speaker moet eindigen op het nummer van de telefoon en spelen
    for n, gap in ((3, 150), (4, 300), (6, 350), (5, 500)):
        room(n + 1)
        if not ensure_playing():
            break
        before, track, t0 = marker()
        keys([(i * gap, NEXT) for i in range(n)])
        check(f"{n} skips om de {gap} ms, door spelen", "play", t0, settle=8, before=before, before_track=track, skips=n)
    # C: gepauzeerd skippen en dan play
    for n, gap, after in ((3, 300, 100), (4, 200, 400)):
        room(n + 1)
        if not ensure_paused():
            print("kon niet pauzeren", flush=True)
            break
        before, track, t0 = marker()
        seq = [(i * gap, NEXT) for i in range(n)]
        seq.append(((n - 1) * gap + after, PLAY))
        keys(seq)
        check(f"gepauzeerd {n} skips om de {gap} ms, play na {after}", "play", t0, settle=8, before=before,
              before_track=None, count=False, skips=n)
    # D: pauze en play vlak na elkaar tijdens een skip
    for a, b in ((100, 250), (50, 400)):
        room(2)
        if not ensure_playing():
            break
        before, track, t0 = marker()
        keys([(0, NEXT), (a, PAUSE), (a + b, PLAY)])
        check(f"skip, pauze na {a}, play na {a+b}", "play", t0, settle=8, before=before, before_track=track, count=False, skips=1)
    # E: de speaker loopt net anders (een echte tijdsprong), en dan op een willekeurig moment pauze
    for i in range(8):
        if not ensure_playing():
            break
        rel = avt(HOST, "GetPositionInfo").get("RelTime", "0:00:10")
        h, m, s = map(int, rel.split(":"))
        target = h * 3600 + m * 60 + s + 5
        avt(HOST, "Seek", Unit="REL_TIME", Target=f"{target//3600}:{(target%3600)//60:02d}:{target%60:02d}")
        time.sleep(random.uniform(1.0, 3.2))
        _, track, t0 = marker()
        keys([(0, PAUSE)])
        check(f"tijdsprong, daarna pauze ({i+1})", "pause", t0, settle=4.5, before_track=track, pause_at=t0, count=False)


for r in range(ROUNDS):
    print(f"--- ronde {r+1}", flush=True)
    if "onlytap" not in sys.argv[5:]:
        scenarios()
    # F: met echte tikken op de knoppen van de kleine player, zoals jij het doet
    if TAP and "tapskip" not in sys.argv[5:]:
        nx, ny, px, py = TAP
        for n, gap, after in ((3, 350, 300), (3, 500, 200), (4, 300, 100), (2, 400, 600)):
            room(n + 1)
            if not ensure_playing():
                break
            if "pixelplay" not in adb("dumpsys window | grep mCurrentFocus"):
                print("scherm niet op de app, tikken overgeslagen", flush=True)
                break
            before, track, t0 = marker()
            t, seq = 0, []
            for i in range(n):
                seq.append((t, (nx, ny)))
                t += gap
            pause_offset = t - gap + after
            seq.append((pause_offset, (px, py)))
            taps(seq)
            check(f"getikt: {n} volgende om de {gap} ms, pauze {after} later", "pause", t0, before=before, before_track=track,
                  pause_at=t0 + pause_offset / 1000, skips=n)
    # G: alleen tikken op volgende: de speaker moet daarna gewoon doorspelen op het laatste nummer
    if TAP and "tapskip" in sys.argv[5:]:
        nx, ny = TAP[0], TAP[1]
        for n, gap in ((3, 350), (4, 300), (5, 250), (2, 500), (6, 200), (3, 700)):
            room(n + 1)
            if not ensure_playing():
                break
            if "pixelplay" not in adb("dumpsys window | grep mCurrentFocus"):
                print("scherm niet op de app, tikken overgeslagen", flush=True)
                break
            before, track, t0 = marker()
            taps([(i * gap, (nx, ny)) for i in range(n)])
            check(f"getikt: {n} volgende om de {gap} ms, door spelen", "play", t0, settle=8, before=before, before_track=track, skips=n)

w.running = False
avg = sum(pause_ms) / max(1, len(pause_ms))
print(f"\n{total-fails} van {total} goed, seed {SEED}; ongeldig: {invalid}; telefoon in ERROR: {errors}; "
      f"startte tijdens skips gemiddeld {sum(plays)/max(1,len(plays)):.1f} keer, maximaal {max(plays or [0])}; "
      f"stil na pauze gemiddeld {avg:.0f} ms, langzaamst {max(pause_ms or [0]):.0f} ms; mute ging uit: {w.mute_off} keer", flush=True)
sys.exit(1 if fails else 0)
