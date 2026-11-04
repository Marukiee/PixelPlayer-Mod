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
