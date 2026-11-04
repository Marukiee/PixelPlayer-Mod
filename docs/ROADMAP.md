# Roadmap PixelPlayer Mod

## Open lijst voor een grote ronde

Volgorde gekozen door Mark (2026-11-02):

1. [x] Sonos: afspelen via Navidrome, met groeperen en volume per speaker (0.7.5-mod.9, thuisadres in mod.10)
   - [x] Mark test thuis op zijn echte speakers: werkt (2026-11-02)
   - [ ] Volumeknoppen buiten de app regelen de Sonos, en het volume verschijnt in het eiland van Drip (zie "Sonos-volume" hieronder)
   - [ ] Volumeschuif in beeld als je in de app op de volumeknoppen drukt
   - [ ] Vanaf die volumeschuif ook het volume van de andere speakers in de groep regelen
   - [ ] Animaties in de hele app nalopen (Material 3 Expressive, `MotionScheme.expressive()`), net als bij de Sonos-onderdelen
   - [x] **Ronde mod.11 en mod.12** (wensen van Mark, 2026-11-02 en 2026-11-04): overzetten op dezelfde plek, speakers vooraf klaar, groepsvolume zoals de Sonos-app, dempknop, drie puntjes, nieuwe indeling, soepel scrollen en krimpen, artiestfoto's, speaker-pil, Spotify overnemen, geen grijze box op de homepage
   - [x] Hoes in de Drip-pil: lag aan Drip (koos de mediasessie van de Sonos-app). Opgelost aan de Drip-kant; PixelPlayer hoeft niets
   - [ ] Hoesjes in de Sonos-app (zie "Hoesjes in de Sonos-app" hieronder): Mark wil eigen hoezen, geen Deezer
2. [ ] PixelPlayerOSS verwerken: hun fixes en verbeteringen in de mod overnemen. Bij de start navragen of de mod daarbij ook Google-vrij moet worden (Cast, Gemini, Firebase eruit), of alleen de fixes.
3. [ ] Spotify: te kopen-lijst
4. [ ] Spotify: nieuwe releases
5. [ ] Spotify: afspeellijsten importeren
6. [ ] Spotify en PixelPlayer: luisterstatistieken samenvoegen
7. [ ] Hele app in het Nederlands vertalen
8. [ ] Tweede variant van de crash na herstarten van de telefoon

## Hele app in het Nederlands vertalen

Nu is alleen de homepage vertaald (`values-nl/strings_home_redesign.xml`). De rest van de app heeft geen `values-nl`, dus op een Nederlandse telefoon is alleen de homepage Nederlands en de rest Engels. Voor een volledige vertaling moeten alle `strings*.xml` uit `values/` een Nederlandse versie krijgen. Daarnaast moet Nederlands in de taalkeuze van de app staan (Instellingen > Appearance > App Language).

## Tweede variant van de crash na herstarten

Zat al in de juli-versie en in upstream. Na een herstart start Android de app via media-hervatting. Media3 zet de melding dan asynchroon op de voorgrond (`MediaNotificationManager`, na het laden van de hoes). Op dat moment verbiedt Android dat nog (`ForegroundServiceStartNotAllowedException ... from BOOT_COMPLETED`). De eerste variant, in `MusicService.onStartCommand`, is opgelost in 0.7.5-mod.1. Deze tweede variant gebeurt niet bij elke herstart (op de emulator 1 van de 2 keer). Een vlag in `onUpdateNotification` helpt niet, omdat Media3 in de callback zelf opnieuw berekent of de voorgrond nodig is. Een oplossing moet voorkomen dat er bij het opstarten iets gaat spelen, of de vangst in Media3 zelf regelen.

## Spotify-koppeling

Doel: Spotify gebruiken om te ontdekken, en zoveel mogelijk zelf als FLAC kopen. Dit kan via de Spotify Web API, met inloggen via PKCE (geen geheime sleutel in de app). Mark moet daarvoor één keer een gratis app aanmaken op developer.spotify.com, en daarna op zijn telefoon inloggen om te testen. Alles komt in de grote ronde (keuze van Mark, 2026-11-02).

### 1. Te kopen-lijst (komt als eerste)

- Spotify-nummers vergelijken met de eigen bibliotheek (op ISRC als dat in de tags staat, anders op artiest en titel). Wat ontbreekt, komt op de lijst.
- **Bronnen:** beslist Mark later. Opties zijn Liked Songs, eigen afspeellijsten, gevolgde afspeellijsten en opgeslagen albums.
- **Winkels:** Qobuz, Bandcamp, 7digital en HDtracks. Mark wil per nummer zien waar het het goedkoopst is. Let op: deze winkels hebben geen open prijs-API (Qobuz en 7digital alleen voor partners, Bandcamp en HDtracks helemaal niet). Prijzen ophalen betekent hun zoekpagina's uitlezen, en dat kan breken als ze hun site wijzigen. Terugval: knoppen die direct de zoekpagina per winkel openen.
- **Plek:** allebei. Een kleine sectie "Nog te kopen" op de homepage, plus het volledige scherm in de bibliotheek.

### 2. Nieuwe releases

Albums van artiesten uit de bibliotheek, of die Mark op Spotify volgt, die hij nog niet heeft. Als lijst of melding, gekoppeld aan de te kopen-lijst. Kan ook via MusicBrainz of ListenBrainz, zonder account.

### 3. Afspeellijsten importeren

Een Spotify-afspeellijst wordt een PixelPlayer-afspeellijst met de nummers die Mark heeft; de rest gaat naar de te kopen-lijst.

### 4. Luisterstatistieken samenvoegen

Wens: Spotify- en PixelPlayer-luistergedrag samen, zodat de Wrapped bij stats.fm klopt.

- **stats.fm:** importeert volgens de eigen documentatie alleen luistergeschiedenis van Spotify en Apple Music (met stats.fm Plus), niet van Last.fm of andere spelers. PixelPlayer-luisterbeurten kunnen er dus niet officieel in, en ook niet in Spotify Wrapped zelf: Spotify heeft geen API om luisterbeurten toe te voegen.
- **Wat wel kan, in PixelPlayer:** Spotify's "uitgebreide streaminggeschiedenis" (JSON-export uit de Spotify-privacy-instellingen) importeren, plus via de API de recent gespeelde nummers bijhouden. PixelPlayer toont dan gecombineerde statistieken en een eigen jaaroverzicht in Wrapped-stijl.
- **Alternatief:** Last.fm als gezamenlijke plek. Spotify scrobbelt van zichzelf naar Last.fm, en PixelPlayer kan dat met upstream-PR #2563. Last.fm heeft eigen jaaroverzichten.
- **Experiment, onzeker:** PixelPlayer-luisterbeurten omzetten naar het Spotify-exportformaat en die in stats.fm importeren. Dat wordt niet ondersteund, kan geweigerd worden en kan tegen de voorwaarden van stats.fm ingaan; eerst navragen voordat dit gebouwd wordt.

### Later

- Likes synchroniseren tussen PixelPlayer en Spotify.
- Afspeellijst exporteren naar Spotify, om te delen.

Beperkingen: afspelen van Spotify-audio in PixelPlayer kan niet, alleen de Spotify-app bedienen. Aanbevelingen en "gerelateerde artiesten" zijn sinds eind 2024 dicht voor nieuwe apps.

## Sonos

Gebouwd in 0.7.5-mod.9. Hoe het werkt:

- De telefoon blijft de baas over de wachtrij en speelt stil mee: het geluid wordt in de audio-sink op 0 gezet (`SonosAudioGate`, `MutingAudioSink`), zonder audiofocus en zonder crossfade. Daardoor werken melding, vergrendelscherm, swipes en luisterstatistieken gewoon.
- `SonosController` stuurt elke actie van de telefoon door (afspelen, pauze, volgende, spoelen, wachtrij) en zet de komende 100 nummers op de Sonos-wachtrij. De Sonos is leidend voor de positie; wat in de Sonos-app gebeurt, volgt de telefoon.
- `SonosTrackResolver` koppelt nummers aan de Navidrome-cache (titel, artiest, album, duur). Hi-res (boven 48 kHz) en Opus of DSD vraagt hij als MP3 320 op, omdat Sonos die niet afspeelt.
- Thuisadres (0.7.5-mod.10): de app gebruikt het Navidrome-inlogadres (bij Mark Tailscale, `100.86.14.39`), de speakers krijgen het adres uit Instellingen > Sonos (`http://192.168.1.157:4533`, maruks-server). `SonosTrackResolver.rebase` zet het begin van de stream- en hoes-URL om.
- Getest op de emulator tegen een echte Navidrome en een nep-Sonos met drie speakers, en door Mark thuis op zijn echte speakers: werkt (2026-11-02).
- Spotify (0.7.5-mod.12): gemeten op de speakers. Staat Spotify Connect op de speaker (`x-sonos-vli:`) en zet je de wachtrij erop, dan pakt Spotify hem binnen 0,6 seconde terug, ook als Spotify al op pauze stond. Een Pause-commando vooraf plus 300 ms wachten lost dat op. `holdSpeaker` kijkt daarna nog even of Spotify echt weg is, want een Play of Seek die op Spotify belandt, zet Spotify weer aan of blijft hangen. Daarna kost overzetten ongeveer 0,6 seconde (Seek terwijl gestopt werkt meteen, Play tot geluid 0,5 tot 0,6 seconde).

### Hoesjes in de Sonos-app (open)

Gemeten op Marks echte speakers en de Sonos-app (Android) op 2026-11-04:

- **http (Navidrome thuis):** de app toont geen hoes en vraagt hem niet eens op; een testserver kreeg geen verzoek, ook niet van de speaker.
- **Openbare https (Deezer-link):** werkt meteen.
- **https via Tailscale (`tailscale serve`, geldig certificaat):** geen hoes en geen verzoek, ook niet toen de telefoon de naam kon opzoeken ("Use Tailscale DNS" aan). De telefoon haalt de hoes dus niet zelf op; waarschijnlijk doet de Sonos-cloud dat, en die kan alleen bij openbare adressen.
- **`/getaa?s=1&u=<stream-URL>` op de speaker:** 404 voor http-streams. Gebouwd in mod.11 en in mod.12 weer weggehaald, want de app pakt de eerste hoes in de lijst.
- **Hoes in de metadata (`data:image/jpeg;base64`):** de speaker weigert dat (UPnP 804, te lang, al bij 15 kB).

Wat overblijft:

1. **Tailscale Funnel op maruks-server, alleen voor het hoes-pad** (`tailscale funnel --bg --set-path=/rest/getCoverArt.view http://localhost:4533/rest/getCoverArt.view`). Eigen hoezen, maar dat pad wordt openbaar, en de link bevat het Subsonic-token. Eerst testen met een testplaatje of hij dan wel wordt opgehaald en door wie. Daarna een instelling "adres voor hoesjes" en `SonosTrackResolver.rebase` gebruiken.
2. **Deezer** (`search/album`, `cover_xl`): werkte in de test en is simpel, maar Mark wil dit liever niet.

### Sonos-volume (wens van Mark, 2026-11-02, nog niet bouwen)

Nu regelen de volumeknoppen de Sonos alleen zolang de app open is (`MainActivity.dispatchKeyEvent` en het verbinden-scherm), en je ziet dan weinig.

Eerst proberen: de Sonos-app meldt zich zelf al als afstandsbediening (mediasessie van `com.sonos.acr2`, type REMOTE). Misschien regelen de volumeknoppen buiten PixelPlayer daardoor al de Sonos, en Drip toont dat volume vanaf zijn volgende release met het label "Sonos". Als dat werkt, is punt 1 hieronder kleiner.

1. **Buiten de app, zichtbaar in Drip.** Volumeknoppen moeten ook met de app op de achtergrond of het scherm uit de Sonos regelen.
   - Kan door de media-sessie op "remote" te zetten. Media3 laat dat via `DeviceInfo` met `PLAYBACK_TYPE_REMOTE` en een volume van 0 tot 100, met `setDeviceVolume` en `increaseDeviceVolume` en `decreaseDeviceVolume` doorgestuurd naar `SonosController`.
   - Let op: de sessiespeler wisselt bij crossfade (`publishMediaSessionPlayer`), maar crossfade staat uit zolang Sonos speelt. Een `ForwardingPlayer` om de sessiespeler heen moet dus alleen tijdens Sonos de afstandsbediening tonen.
   - Android stuurt de volumeknoppen dan naar de sessie en toont zijn eigen volumebalk voor afstandsbediening.
   - **Drip** (eigen dynamic island, repo `Marukiee/Drip`) toont dit vanaf Drip v0.1.17 (gebouwd op 2026-11-02). Afspraken met de Drip-sessie, die PixelPlayer moet nakomen:
     - Media-sessie op REMOTE met een `VolumeProvider` van type ABSOLUTE, maximum 100, huidige waarde het groepsvolume.
     - Bij elke volumewijziging (op de Sonos, in de app of via Drip) `setCurrentVolume` aanroepen; dan krijgt Drip `onAudioInfoChanged`.
     - `onSetVolumeTo` en `onAdjustVolume` van de VolumeProvider gaan naar `SonosController` (groepsvolume) en roepen daarna `setCurrentVolume` aan. Drip stuurt zo de volumebalk op zijn muziek- en volumekaart en de volumeknoppen door terwijl een kaart open is.
     - Groepsnaam in de extras van de media-sessie onder de sleutel `nl.markmaaktmedia.drip.REMOTE_NAME` (Drip leest ook `remote_name`), bijvoorbeeld "Living room + Kitchen". Bij een wijziging opnieuw zetten; Drip luistert naar `onExtrasChanged`. Zonder naam toont Drip de app-naam. Terugval: Drip leest op Android 11 en hoger ook `PlaybackInfo.getVolumeControlId()` als daar een naam met een spatie in staat.
     - Na de Sonos-sessie de extras en het remote-type weer weghalen, zodat Drip het gewone mediavolume toont.
2. **In de app een volumeschuif.** Bij de volumeknoppen in de app verschijnt een Material 3 Expressive volumeschuif met de groepsnaam, die vanzelf weer verdwijnt.
3. **Ook de andere speakers.** Vanaf die schuif uitklappen naar een schuif per speaker in de groep, zoals op de Sonos-kaart in het verbinden-scherm.

Oorspronkelijke wens van Mark (2026-11-02): vanuit PixelPlayer naar zijn Sonos-speakers spelen, met groeperen, waarbij de Sonos de muziek van zijn Navidrome-server haalt.

- **Kan, zonder cloud:** Sonos-speakers zijn lokaal te bedienen via UPnP.
  - Vinden via SSDP.
  - Groepen uitlezen via ZoneGroupTopology.
  - Groeperen door een speaker aan de coördinator te koppelen (`x-rincon:<id>`), losmaken met BecomeCoordinatorOfStandaloneGroup.
  - Groepsvolume via GroupRenderingControl, volume per speaker via RenderingControl.
- **Afspelen via Navidrome:** lokaal nummer koppelen aan het Navidrome-ID (`NavidromeSongMatcher` bestaat al), en de stream-URL op de Sonos-wachtrij zetten met AddURIToQueue. Dan speelt de Sonos de hele wachtrij zelf af, ook als de telefoon of app uit is. In juli speelde de eerste versie per nummer en schoof de app door; dat stopt als de app dicht is.
- **Bediening:** de speakers komen in het bestaande verbinden-scherm (de cast-knop), met kaarten per speaker, groepchips, volumeschuifjes en een balkje "Speelt op Woonkamer + Keuken" in de speler. Afspelen, pauzeren, volgende en spoelen gaan naar de Sonos zolang je verbonden bent. Positie en status komen via polling of UPnP-events (GENA). Animaties met springs zoals elders in de mod.
- **Geschiedenis:** in juli is er al een eerste versie gebouwd (vinden, afspelen, volume) en daarna op verzoek weggehaald. Die stond niet in git; het ontwerp staat in `notities/pixelplayer-project.md`.
- **Testen:** alleen op Marks telefoon in zijn thuisnetwerk. De emulator ziet geen speakers op het netwerk.
- **Omvang:** groot, meerdere sessies.

## PixelPlayerOSS

`PixelPlayerHQ/PixelPlayerOSS` is de officiële Google-vrije versie, onderhouden door @lostf1sh.

- Zonder Telegram, NetEase, QQ Music, Google Drive, Gemini, Cast, Wear OS, Play-billing, Firebase, Crashlytics en Google Play-diensten. Wel Navidrome/Jellyfin en de rest van de speler.
- Veel actiever dan de gewone versie (die staat sinds juni stil): op 2026-11-01 nog fixes, zoals stil afspelen na shuffle en prestaties van Daily Mix, statistieken en Jellyfin. Release v0.4.0 op 2026-10-17, ook op F-Droid.
- Andere pakketnaam (`com.lostf1sh.pixelplayeross`): een overstap betekent een aparte app, je likes overzetten via een back-up, en alle mod-wijzigingen opnieuw inbouwen op die code. Chromecast valt dan weg; Sonos werkt niet via Cast, dus dat raakt de Sonos-plannen niet.
- Twee wegen:
  1. Bij de huidige basis blijven en losse fixes uit OSS overnemen.
  2. Overstappen op OSS als nieuwe basis. Groot werk, maar daarna profiteer je van hun updates.

## Technisch, om later uit te zoeken

Deze bibliotheken staan bewust op een oudere versie (zie `gradle/libs.versions.toml`):

- **Media3 1.10.1:** met 1.11.1 werken volgende en vorige nergens meer (de mediasessie meldt `actions=128`).
- **Material3 1.5.0-alpha22:** alpha28 verandert de Slider-API in zeven schermen.
- **Navigation 2.9.8:** 2.10 vervangt de terug-animatie (de kaart schuift niet meer mee met de vinger).
