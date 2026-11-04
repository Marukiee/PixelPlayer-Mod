#!/usr/bin/env python3
"""Stresstest for skip and pause. Drives the app with media keys and compares the speaker with the phone."""
import json, random, re, subprocess, sys, time, urllib.request
SERIAL=sys.argv[1]; LOG=sys.argv[2]; ROUNDS=int(sys.argv[3]) if len(sys.argv)>3 else 1
SEED=int(sys.argv[4]) if len(sys.argv)>4 else 7
random.seed(SEED)
FAKE='http://127.0.0.1:1400'
NEXT,PREV,PLAY,PAUSE=87,88,126,127
def adb(cmd,timeout=20): return subprocess.run(['adb','-s',SERIAL,'shell',cmd],capture_output=True,text=True,timeout=timeout).stdout
def keys(seq):
    """seq: (ms,key). Every key starts on its own, so the spacing is not stretched by the time one key takes."""
    adb(' '.join(f'(sleep {ms/1000:.3f}; input keyevent {k}) &' for ms,k in seq)+' wait',timeout=40)
def fake(path='/debug/state'): return urllib.request.urlopen(FAKE+path,timeout=4).read().decode()
def speaker():
    d=json.loads(fake())['Living room']; q=d['queue']; n=d['track']
    return d['state'],(q[n-1] if 0<n<=len(q) else None),n,len(q)
def phone():
    out=adb('dumpsys media_session')
    m=re.search(r'package=com\.theveloper\.pixelplay.*?(?=\n\s*Sessions Stack|\Z)',out,re.S); b=m.group(0) if m else ''
    b=b[:b.find('package=',20)] if b.find('package=',20)>0 else b
    st=re.search(r'state=PlaybackState \{state=(\w+)',b); t=re.search(r'description=([^,\n]*)',b)
    return st.group(1) if st else '?',(t.group(1).strip() if t else None)
def logmark(): 
    try: return sum(1 for _ in open(LOG))
    except FileNotFoundError: return 0
def cmds(mark):
    out=[]
    for l in open(LOG).read().splitlines()[mark:]:
        e=json.loads(l)
        if e.get('action') in('Play','Pause','Seek','RemoveAllTracksFromQueue','SetAVTransportURI') or e.get('event')=='now_playing': out.append(e)
    return out
def short(evs):
    r=[]
    for e in evs:
        a=e.get('action') or e['event']
        if a=='Seek': a=('T' if e['args'].get('Unit')=='TRACK_NR' else 'S')+str(e['args'].get('Target'))
        r.append(a+('!' if e.get('fault') else ''))
    return ' '.join(r)
errors=0
def recover():
    """A phone player in ERROR takes no keys any more; the next song gets it going again."""
    if phone()[0]=='ERROR':
        keys([(0,NEXT)]); time.sleep(1.5); keys([(0,PLAY)]); time.sleep(1.5)
def ensure_playing():
    recover()
    for _ in range(4):
        if phone()[0]=='PLAYING' and speaker()[0]=='PLAYING': return True
        if phone()[0]!='PLAYING': keys([(0,PLAY)])
        time.sleep(1.6)
    return phone()[0]=='PLAYING' and speaker()[0]=='PLAYING'
def ensure_paused():
    recover()
    for _ in range(4):
        if phone()[0]=='PAUSED' and speaker()[0] not in('PLAYING','TRANSITIONING'): return True
        if phone()[0]!='PAUSED': keys([(0,PAUSE)])
        time.sleep(1.6)
    return False
fails=0; total=0; plays=[]; echoes=0; invalid=0
def room(needed):
    """Goes back in the queue when the next run of skips would hit its end, where a skip does nothing."""
    for _ in range(3):
        s,t,n,qlen=speaker()
        if qlen-n>needed: return
        keys([(i*130,PREV) for i in range(18)]); time.sleep(3.5); recover()
def check(name,want,mark,settle=6.5,count_play=None,before=None):
    """Waits past every grace period, because the fault shows when the phone starts following the speaker."""
    global fails,total,errors,invalid
    total+=1
    time.sleep(settle)
    s,title,n,qlen=speaker(); p,pt=phone()
    if p=='BUFFERING': time.sleep(2.5); s,title,n,qlen=speaker(); p,pt=phone()
    ok_state=(s=='PLAYING' and p=='PLAYING') if want=='play' else (s in('PAUSED_PLAYBACK','STOPPED') and p=='PAUSED')
    ok_title= title==pt
    ok=ok_state and ok_title
    if p=='ERROR': errors+=1
    if before is not None and n==before:
        invalid+=1; print(f"ONGELDIG {name}: er is niet geskipt",flush=True); total-=1; return True
    evs=cmds(mark)
    nowplaying=sum(1 for e in evs if e.get('event')=='now_playing')
    if count_play is not None: plays.append(nowplaying)
    print(f"{'ok  ' if ok else 'FOUT'} {name:44s} speaker={s[:7]:7s} '{(title or '')[:14]}' telefoon={p[:7]:7s} '{(pt or '')[:14]}' startte={nowplaying}",flush=True)
    if not ok:
        fails+=1; print('     ',short(evs)[:190],flush=True)
    return ok
for r in range(ROUNDS):
    # A: snel skippen terwijl hij speelt, en dan pauze, op allerlei momenten
    for n,gap,after in ((3,150,60),(3,150,300),(3,300,60),(3,300,300),(3,300,700),(5,200,100),(5,350,0),(5,350,500),(2,500,100),(4,700,150),(6,120,30)):
        room(n+1)
        if not ensure_playing(): print('kon niet starten'); break
        before=speaker()[2]; mark=logmark(); t=0; seq=[]
        for i in range(n): seq.append((t,NEXT)); t+=gap
        seq.append((t-gap+after,PAUSE))
        keys(seq); check(f'{n} skips om de {gap} ms, pauze {after} ms later','pause',mark,count_play=True,before=before)
    # B: alleen snel skippen: de speaker moet eindigen op het nummer van de telefoon en spelen
    for n,gap in ((3,150),(4,300),(6,350),(5,500)):
        room(n+1)
        if not ensure_playing(): break
        before=speaker()[2]; mark=logmark(); keys([(i*gap,NEXT) for i in range(n)]); check(f'{n} skips om de {gap} ms, door spelen','play',mark,count_play=True,before=before)
    # C: gepauzeerd skippen en dan play
    for n,gap,after in ((3,300,100),(4,200,400)):
        room(n+1)
        if not ensure_paused(): print('kon niet pauzeren'); break
        before=speaker()[2]; mark=logmark(); seq=[(i*gap,NEXT) for i in range(n)]; seq.append(((n-1)*gap+after,PLAY))
        keys(seq); check(f'gepauzeerd {n} skips om de {gap} ms, play na {after}','play',mark,before=before)
    # D: pauze en play vlak na elkaar tijdens een skip
    for a,b,want in ((100,250,'play'),(50,400,'play')):
        room(2)
        if not ensure_playing(): break
        before=speaker()[2]; mark=logmark(); keys([(0,NEXT),(a,PAUSE),(a+b,PLAY)]); check(f'skip, pauze na {a}, play na {a+b}',want,mark,before=before)
    # E: blinde periode: de telefoon loopt net iets anders, en dan op een willekeurig moment een knop
    for i in range(8):
        if not ensure_playing(): break
        mark0=logmark(); fake('/debug/nudge'); time.sleep(random.uniform(1.0,3.2))
        mark=logmark(); keys([(0,PAUSE)]); check(f'tijdverschil, daarna pauze ({i+1})','pause',mark,settle=4.5)
        echoes+=sum(1 for e in cmds(mark0) if e.get('action')=='Seek' and e['args'].get('Unit')=='REL_TIME')
print(f"\n{total-fails} van {total} goed, seed {SEED}; ongeldig: {invalid}; telefoon in ERROR: {errors}; startte tijdens skips gemiddeld {sum(plays)/max(1,len(plays)):.1f} keer, maximaal {max(plays or [0])}; teruggestuurde tijd-seeks: {echoes}")
sys.exit(1 if fails else 0)
