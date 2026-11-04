# Overdracht: PixelPlayer Mod

**Werkwijze**
- Werk één item tegelijk af en build na elke wijziging.
- Gebruik nergens em-dashes of en-dashes: niet in code, commits, changelog of dit bestand.
- Kom niet aan `sleutel/`, keystores of GitHub-secrets.
- Breng niets uit zonder akkoord van Mark.

## Status

De ronde mod.11 is af en uitgebracht als `0.7.5-mod.12`. De open punten staan in `docs/ROADMAP.md`, bovenaan. Daar staat ook wat al is geprobeerd voor de hoesjes in de Sonos-app; gebruik geen `/getaa`, dat geeft 404.

## Waar alles staat

- Repo: deze map, branch `mark-mod`, remote `mod` (`Marukiee/PixelPlayer-Mod`).
- Sonos-logica: `app/src/main/java/com/theveloper/pixelplay/data/sonos/`
  - `SonosController.kt`: sessie, wachtrij, volume, groeperen
  - `SonosDiscovery.kt`: speakers zoeken, `SonosGroup` (met `displayName` en `shortName`)
  - `SonosTrackResolver.kt`: nummer naar Navidrome-stream-URL en DIDL-metadata
  - `SonosSoapClient.kt`: UPnP SOAP-aanroepen
- Sonos-UI: `app/src/main/java/com/theveloper/pixelplay/presentation/components/SonosConnectSection.kt` en `CastBottomSheet.kt` (zelfde map)
- Speler: `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt`
- Teksten: `app/src/main/res/values/strings_sonos.xml` (Engels) en `app/src/main/res/values-nl/strings_sonos.xml` (Nederlands). Elke nieuwe tekst in allebei.
- Tests: `app/src/test/java/com/theveloper/pixelplay/data/sonos/SonosParsingTest.kt`
- Lijst van Mark: `docs/ROADMAP.md`.

## Commando's

Altijd met Java 21:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

Snel controleren of het compileert (gebruik dit na elke wijziging):

```bash
./gradlew :app:compileDebugKotlin
```

Sonos-tests:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.theveloper.pixelplay.data.sonos.*'
```

Volledige build (duurt lang, alleen aan het eind):

```bash
./gradlew :app:assembleRelease
```

## Valkuilen

- **Vastgezette versies.** Werk Media3 (1.10.1), Material3 (1.5.0-alpha22) en Navigation (2.9.8) niet bij in `gradle/libs.versions.toml`. Nieuwere versies breken volgende en vorige, de Slider en de terug-animatie.
- **Geen Gradle-configuratie of plugins veranderen** om een fout op te lossen. Repareer de code.
- **Debug-build.** Een debug-APK kan niet over Marks app heen geïnstalleerd worden (andere sleutel). Gebruik debug alleen om te compileren.
- **Emulator.** De emulator ziet geen echte Sonos-speakers. Nep-speakers stonden in een tijdelijke map die er misschien niet meer is. Test op de emulator dus alleen of de app opent en het verbinden-scherm werkt; het echte Sonos-gedrag test Mark thuis.
- **Sonos-teksten.** Zet ze in `strings_sonos.xml`, niet in `strings.xml`, en altijd in beide talen.
- **Toasts bij afbreken.** `SonosController.report()` slaat `CancellationException` bewust over. Haal dat niet weg; anders krijg je valse foutmeldingen.
- **Volume.** `setGroupVolume` neemt de verhouding tussen speakers op bij het begin van een sleepbeweging of reeks toetsdrukken (`volumeShares`, binnen 1,5 seconde). `groupVolumeTargets` heeft een test; verander die rekensom niet.
- **Overzetten.** `fullReset` pauzeert eerst wat er speelt (anders pakt Spotify de speaker terug), en spoelt daarna vóór Play naar de plek van de telefoon plus 550 ms. Op echte speakers kost dat samen 0,6 seconde.

## Niet doen zonder akkoord

Deze staan op `docs/ROADMAP.md`, maar Mark heeft er nog geen akkoord voor gegeven:
- volumeknoppen buiten de app via een externe mediasessie (voor Drip)
- een volumeschuif in beeld bij de volumeknoppen
- animaties in de hele app nalopen
