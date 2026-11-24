# Speler op afstand testen

Twee emulators praten echt via Bluetooth met elkaar (netsim in de emulator), dus de hele weg is te testen zonder telefoons.

1. Maak twee AVD's van hetzelfde image en start ze op eigen poorten, met een eigen adb-server:
   ```bash
   ANDROID_ADB_SERVER_PORT=5071 emulator -avd pp_remote_xperia -port 5670 -no-window -no-audio
   ```
   ```bash
   ANDROID_ADB_SERVER_PORT=5071 emulator -avd pp_remote_oppo -port 5672 -no-window -no-audio
   ```
2. Zet thuis eerst het LAN dicht met `iptables` (zie de notities over Sonos), anders vindt de app echte speakers.
3. Zet dezelfde nummers op beide, bij voorkeur in verschillende mappen (zoals Syncthing op twee telefoons), en een nummer alleen op de "Oppo".
4. Koppel ze: maak de Xperia zichtbaar (`am start -a android.bluetooth.adapter.action.REQUEST_DISCOVERABLE`), kies hem op de Oppo bij Instellingen › Bluetooth › Pair new device en tik op beide Pair. Een nette naam geef je bij de apparaatdetails met Rename.
5. Xperia: Instellingen › Play from › Remote player › Use as remote player. Oppo: start een nummer, open Connect device en tik onder Nearby devices op de Xperia.
6. Vergelijk wat beide spelen:
   ```bash
   ANDROID_ADB_SERVER_PORT=5071 python3 state.py emulator-5670 emulator-5672
   ```

Zonder Bluetooth kan het ook over TCP: zet een poort in `Android/data/<pakket>/files/remote_listen_port.txt` op de speler en `127.0.0.1:<poort> naam` in `remote_hosts.txt` op de bediener, met `adb forward` en `adb reverse` ertussen.

Op een echte Xperia met kabel kan Android het volume boven ongeveer 60% tegenhouden tot iemand de waarschuwing op die telefoon bevestigt; de Oppo meldt dat dan.
