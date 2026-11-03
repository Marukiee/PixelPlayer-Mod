# Overdracht: PixelPlayer Mod, ronde mod.11

**Werkwijze**
- Werk één item tegelijk af en build na elke wijziging.
- Werk de status in dit bestand bij na elk item.
- Gebruik nergens em-dashes of en-dashes: niet in code, commits, changelog of dit bestand.
- Kom niet aan `sleutel/`, keystores of GitHub-secrets.

## Doel

PixelPlayer is een Android-muziekspeler (Kotlin, Jetpack Compose). Deze mod laat de app afspelen op Sonos-speakers, die de muziek van Marks Navidrome-server halen. Rond de ronde mod.11 af en breng hem uit als versie `0.7.5-mod.11`.

## Waar alles staat

- Repo: deze map, branch `mark-mod`, remote `mod` (`Marukiee/PixelPlayer-Mod`).
- Laatste commits: `7d8ae11` (mod.11 werk, nog nooit gebouwd) en `f2ca903` (roadmap).
- Sonos-logica: `app/src/main/java/com/theveloper/pixelplay/data/sonos/`
  - `SonosController.kt`: sessie, wachtrij, volume, groeperen
  - `SonosDiscovery.kt`: speakers zoeken, `SonosGroup` (met `displayName` en `shortName`)
  - `SonosTrackResolver.kt`: nummer naar Navidrome-stream-URL en DIDL-metadata
  - `SonosSoapClient.kt`: UPnP SOAP-aanroepen
- Sonos-UI: `app/src/main/java/com/theveloper/pixelplay/presentation/components/SonosConnectSection.kt` en `CastBottomSheet.kt` (zelfde map)
- Speler: `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt`
- Teksten: `app/src/main/res/values/strings_sonos.xml` (Engels) en `app/src/main/res/values-nl/strings_sonos.xml` (Nederlands). Elke nieuwe tekst in allebei.
- Tests: `app/src/test/java/com/theveloper/pixelplay/data/sonos/SonosParsingTest.kt`
- Lijst van Mark: `docs/ROADMAP.md`, blok "Ronde mod.11".

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

## Volgorde en status

### 1. Commit 7d8ae11 laten compileren
**Status: bezig**

De code in `7d8ae11` is geschreven maar nog nooit gebouwd. Alleen de Sonos-tests zijn gedraaid en die slaagden.

Plekken die kunnen falen:
- `SonosConnectSection.kt`:
  - `DropdownMenu(... shape = ..., containerColor = ...)`. Bestaan die parameters niet in deze Material3-versie, haal ze dan weg.
  - Iconen `Icons.Rounded.AddLink` en `Icons.Rounded.LinkOff`. Ontbreken ze, gebruik dan `Icons.Rounded.Add` en `Icons.Rounded.Close`.
  - `Icons.AutoMirrored.Rounded.VolumeOff` en `VolumeUp`.
  - De lambda `close` in `SonosRowMenu`.
  - `DeviceIconBackdrop` (extensie op `BoxScope`) en `SonosPlayingBars` (nieuw).
- `CastBottomSheet.kt`:
  - `CastDeviceRow` gebruikt nu `DeviceIconBackdrop`.
  - De wifi- en Bluetooth-tegels zitten nu in de nieuwe `ConnectivitySection` in het Devices-tabblad.
  - `CastControlsTabContent` heeft nog parameters die niet meer gebruikt worden; dat geeft alleen een waarschuwing.
  - Ongebruikte imports zijn geen fout.
- `SonosController.kt`: nieuwe `setGroupVolume` (`coroutineScope`, `async`, `awaitAll`), `setGroupMuted`, `ungroup`, `addToActiveGroup`.

**Klaar als:** `compileDebugKotlin` slaagt en de Sonos-tests slagen. Verander niets aan het gedrag; repareer alleen compileerfouten.

### 2. Speaker-pil in de speler mooier
**Status: KLAAR**

- Gebruikt `shortName` ("Marks kamer +1") in plaats van `displayName`.
- Toont `Icons.Rounded.SpeakerGroup` icoon als groep groter dan 1 speaker.
- Vervangt het stipje door `SonosPlayingBars` (bewegende balkjes) voor Sonos; Cast blijft het stipje houden.

Het bestand is `FullPlayerContent.kt`, rond de regels 774 tot 889, in het blok `actions = {`. Daar staan `sonosName`, `castIconPainter` en een stipje van 8 dp.

Wat er moet gebeuren:
1. Gebruik `sonosSession?.group?.shortName` (geeft bijvoorbeeld "Marks kamer +1") in plaats van `displayName` voor de tekst in de pil.
2. Is `sonosSession.group.members.size > 1`, toon dan het groepsicoon `Icons.Rounded.SpeakerGroup` (een `ImageVector`, dus `Icon(imageVector = ...)`). Laat anders `R.drawable.rounded_speaker_24` staan.
3. Vervang voor Sonos het stipje (de `Box` met `.size(8.dp)` en `.background(LocalMaterialTheme.current.onTertiaryContainer)`) door `SonosPlayingBars(playing = sonosSession?.isPlaying == true, color = playerAccentColor)`.
   - Importeer `com.theveloper.pixelplay.presentation.components.SonosPlayingBars`.
   - Laat het stipje voor Cast gewoon staan.

**Klaar als:** het compileert en de pil bij Sonos de korte naam, het juiste icoon en de balkjes toont.

### 3. Hoesjes in de Sonos-app
**Status: KLAAR**

- Tweede `albumArtURI` met relatief pad `/getaa?s=1&u=<stream-URL>` toegevoegd vóór de Navidrome URL.
- Speaker haalt zelf de hoes uit de stream; Navidrome URL als reserve.
- Test toegevoegd in `SonosParsingTest.kt`.

De Sonos-app toont nu geen hoes. Waarschijnlijk laadt die app geen hoes van een gewone http-URL op het thuisnetwerk. De oplossing: laat de speaker zelf de hoes uit de stream halen.

Waar: `SonosTrackResolver.kt`, functie `buildDidl` (companion object).
1. Voeg vóór de bestaande `albumArtURI`-regel een tweede `albumArtURI` toe met een relatief pad. De speaker vult zijn eigen adres aan:
   ```kotlin
   val speakerArt = "/getaa?s=1&u=" + java.net.URLEncoder.encode(uri, "UTF-8")
   append("<upnp:albumArtURI>").append(e(speakerArt)).append("</upnp:albumArtURI>")
   ```
2. Laat de bestaande `albumArtURI` met de Navidrome-URL erna staan, als reserve.
3. Escape niet extra. `e()` escapet al voor XML, en `SonosSoapClient.buildEnvelope` escapet de hele DIDL nog een keer. Dat is zo bedoeld.
4. Voeg in `SonosParsingTest.kt` een test toe die controleert dat de DIDL `/getaa?s=1&amp;u=http%3A%2F%2F` bevat. Pas bestaande tests aan als ze nu falen door de extra regel.

**Klaar als:** het compileert en de tests slagen. Of de hoes echt verschijnt, kan alleen Mark thuis op zijn speakers zien.

### 4. Uitbrengen als 0.7.5-mod.11
**Status: nog doen.** Vraag Mark eerst of het uit mag.

1. Ga in `gradle.properties` naar `APP_VERSION_NAME=0.7.5-mod.11` en `APP_VERSION_CODE=20`.
2. Vervang in `MOD_CHANGELOG.md` de bovenste regels (vóór "Ook in deze versie") door een korte lijst voor mod.11:
   - Elke regel kort.
   - Schrijf nooit dat iets niet getest is.
   - Laat de rest van het bestand staan.

   Voorbeeldregels:
   - Overzetten naar Sonos gaat verder op dezelfde plek
   - Speakers staan al klaar als je Verbinden opent
   - Groepsvolume zoals de Sonos-app, dempknop
   - Drie puntjes bij een ruimte: groeperen, opheffen, toevoegen
   - Devices toont alle apparaten, Controls het apparaat dat speelt
   - Soepeler scrollen, nettere speaker-pil in de speler
   - Foto's bij Your artists this week
3. Zet in `MOD.md` onder **Sonos** de nieuwe functies erbij (groepsvolume zoals de Sonos-app, menu met drie puntjes, dempen).
4. Vink in `docs/ROADMAP.md` de items van "Ronde mod.11" af.
5. Commit, push, tag en controleer:
   ```bash
   git add -A
   git commit -m "Release 0.7.5-mod.11"
   git push mod mark-mod
   git tag v0.7.5-mod.11
   git push mod v0.7.5-mod.11
   gh run list --repo Marukiee/PixelPlayer-Mod --limit 3
   gh release view v0.7.5-mod.11 --repo Marukiee/PixelPlayer-Mod
   ```

   GitHub bouwt en tekent de APK zelf (`.github/workflows/mod-release.yml`). Teken nooit zelf met een andere sleutel.

**Klaar als:** de release op GitHub staat met `PixelPlayer-Mod.apk`.

## Valkuilen

- **Vastgezette versies.** Werk Media3 (1.10.1), Material3 (1.5.0-alpha22) en Navigation (2.9.8) niet bij in `gradle/libs.versions.toml`. Nieuwere versies breken volgende en vorige, de Slider en de terug-animatie.
- **Geen Gradle-configuratie of plugins veranderen** om een fout op te lossen. Repareer de code.
- **Debug-build.** Een debug-APK kan niet over Marks app heen geïnstalleerd worden (andere sleutel). Gebruik debug alleen om te compileren.
- **Emulator.** De emulator ziet geen echte Sonos-speakers. Nep-speakers stonden in een tijdelijke map die er misschien niet meer is. Test op de emulator dus alleen of de app opent en het verbinden-scherm werkt; het echte Sonos-gedrag test Mark thuis.
- **Sonos-teksten.** Zet ze in `strings_sonos.xml`, niet in `strings.xml`, en altijd in beide talen.
- **Toasts bij afbreken.** `SonosController.report()` slaat `CancellationException` bewust over. Haal dat niet weg; anders krijg je valse foutmeldingen.
- **Volume.** `setGroupVolume` neemt de verhouding tussen speakers op bij het begin van een sleepbeweging of reeks toetsdrukken (`volumeShares`, binnen 1,5 seconde). `groupVolumeTargets` heeft een test; verander die rekensom niet.
- **Overzetten.** `fullReset` spoelt de speaker vóór Play al naar de plek van de telefoon plus 700 ms. Daarna zet `alignWithPhone` hem gelijk zodra hij echt speelt. Maak dat niet eenvoudiger: zonder deze stap begint het nummer opnieuw.

## Niet doen in deze ronde

Deze staan op `docs/ROADMAP.md`, maar Mark heeft er nog geen akkoord voor gegeven:
- volumeknoppen buiten de app via een externe mediasessie (voor Drip)
- een volumeschuif in beeld bij de volumeknoppen
- animaties in de hele app nalopen
