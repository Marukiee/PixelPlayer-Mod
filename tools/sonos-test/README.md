# Sonos-stresstest

Test snel skippen en pauzeren op de emulator tegen een nep-Sonos die zich gedraagt als een echte speaker (gemeten tijden, `TRANSITIONING`). Een test zonder die vertragingen vindt deze fouten niet.

1. Start de emulator en log in de app in bij Navidrome.
2. Start op de Mac:
   ```bash
   python3 fake_sonos.py fake.log
   ```
   ```bash
   python3 stream_stub.py pad/naar/nummer.flac
   ```
3. Stuur de speakers via adb, want een gewone verbinding vanaf de emulator kost per keer een seconde:
   ```bash
   for p in 1400 1401 1402; do adb reverse tcp:$p tcp:$p; done
   ```
4. Zet `127.0.0.1:1400` in `Android/data/<package>/files/sonos_hosts.txt`.
5. Start een wachtrij van minstens 10 nummers in de app en kies "Living room".
6. Draai de test, met het serienummer van de emulator, het logbestand, het aantal rondes en een willekeurig getal:
   ```bash
   python3 skiptest.py emulator-5556 fake.log 1 7
   ```

De test drukt op de mediaknoppen en vergelijkt daarna de speaker met de telefoon, ook na afloop van de wachttijden, want dan pas gaat de telefoon de speaker volgen.

Ook of hij eindigt op het goede nummer wordt gecontroleerd: de plek op de telefoon van voor de skips plus het aantal skips (`active item id` uit `dumpsys media_session`). Vergelijken met de speaker alleen is niet genoeg, want als de telefoon het nummer overneemt dat de speaker net startte, zijn ze het ook eens. Het tracknummer van de speaker telt niet mee: nummers die de speaker niet kan spelen staan niet op zijn wachtrij. Verder dan verwacht mag, want de app slaat zo'n nummer zelf over.

## Op een echte speaker

Mediaknoppen zijn niet genoeg. Die sturen altijd een expliciete pauze, terwijl een tik op de knop in de app of in Drip eerst kijkt of hij speelt. De fout met de pauzeknop was daardoor alleen met echte tikken te vinden.

1. Kies een speaker waar niemand naar luistert en bewaar wat erop staat. Het bestand komt in de tijdelijke map van de Mac en bevat de Navidrome-inlog, dus gooi het daarna weg:
   ```bash
   python3 real_sonos.py IP:1400 backup
   ```
2. Verbind in de app met die speaker en laat de telefoon ontgrendeld met de app open.
3. Draai de test. Zonder `vol=` blijft de speaker gedempt, met `vol=5` hoor je hem en gaat het volume niet boven de 5:
   ```bash
   python3 real_skiptest.py SERIEEL IP:1400 1 11 tap=NEXT_X,NEXT_Y,PLAY_X,PLAY_Y onlytap vol=5
   ```
   `onlytap` slaat de mediaknoppen over en `tapskip` tikt alleen op volgende.
4. Zet de speaker terug:
   ```bash
   python3 real_sonos.py IP:1400 restore
   ```
