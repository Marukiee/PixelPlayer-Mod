# Roadmap PixelPlayer Mod

## Open lijst voor een grote ronde

- [ ] Hele app in het Nederlands vertalen
- [ ] Tweede variant van de crash na herstarten van de telefoon
- [ ] Spotify: te kopen-lijst (eerst)
- [ ] Spotify: nieuwe releases
- [ ] Spotify: afspeellijsten importeren
- [ ] Spotify en PixelPlayer: luisterstatistieken samenvoegen
- [ ] Sonos: afspelen via Navidrome, met groeperen en volume per speaker
- [ ] PixelPlayerOSS: fixes overnemen of overstappen (Mark beslist)

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

Wens van Mark (2026-11-02): vanuit PixelPlayer naar zijn Sonos-speakers spelen, met groeperen, waarbij de Sonos de muziek van zijn Navidrome-server haalt.

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
