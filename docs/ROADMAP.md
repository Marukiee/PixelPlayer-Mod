# Roadmap PixelPlayer Mod

## Open lijst voor een grote ronde

- [ ] Hele app in het Nederlands vertalen
- [ ] Tweede variant van de crash na herstarten van de telefoon
- [ ] Spotify-koppeling, gericht op FLAC kopen: eerst de te kopen-lijst (keuze van Mark, 2026-11-02)

## Hele app in het Nederlands vertalen

Nu is alleen de homepage vertaald (`values-nl/strings_home_redesign.xml`). De rest van de app heeft geen `values-nl`, dus op een Nederlandse telefoon is alleen de homepage Nederlands en de rest Engels. Voor een volledige vertaling moeten alle `strings*.xml` uit `values/` een Nederlandse versie krijgen. Daarnaast moet Nederlands in de taalkeuze van de app staan (Instellingen > Appearance > App Language).

## Tweede variant van de crash na herstarten

Zat al in de juli-versie en in upstream. Na een herstart start Android de app via media-hervatting. Media3 zet de melding dan asynchroon op de voorgrond (`MediaNotificationManager`, na het laden van de hoes). Op dat moment verbiedt Android dat nog (`ForegroundServiceStartNotAllowedException ... from BOOT_COMPLETED`). De eerste variant, in `MusicService.onStartCommand`, is opgelost in 0.7.5-mod.1. Deze tweede variant gebeurt niet bij elke herstart (op de emulator 1 van de 2 keer). Een vlag in `onUpdateNotification` helpt niet, omdat Media3 in de callback zelf opnieuw berekent of de voorgrond nodig is. Een oplossing moet voorkomen dat er bij het opstarten iets gaat spelen, of de vangst in Media3 zelf regelen.

## Spotify-koppeling

Doel: Spotify gebruiken om te ontdekken, en zoveel mogelijk zelf als FLAC kopen. Dit kan via de Spotify Web API, met inloggen via PKCE (geen geheime sleutel in de app). Mark moet daarvoor één keer een gratis app aanmaken op developer.spotify.com. Ideeën:

1. **Te kopen-lijst (komt als eerste):** Spotify-likes en -afspeellijsten vergelijken met de eigen bibliotheek (op ISRC als dat in de tags staat, anders op artiest en titel). Wat ontbreekt, komt op een lijst met knoppen naar Qobuz, Bandcamp en 7digital.
2. **Nieuwe releases:** albums van artiesten uit je bibliotheek, of die je op Spotify volgt, die je nog niet hebt. Als lijst of melding.
3. **Afspeellijsten importeren:** een Spotify-afspeellijst wordt een PixelPlayer-afspeellijst met de nummers die je hebt; de rest gaat naar de te kopen-lijst.
4. **Likes synchroniseren:** een like in PixelPlayer wordt ook op Spotify bewaard, en andersom.
5. **Afspeellijst exporteren** naar Spotify, om te delen.
6. **Statistieken:** je Spotify-top en -geschiedenis erbij.

Beperkingen: afspelen van Spotify-audio in PixelPlayer kan niet, alleen de Spotify-app bedienen. Aanbevelingen en "gerelateerde artiesten" zijn sinds eind 2024 dicht voor nieuwe apps. Voor nieuwe releases kan ook MusicBrainz of ListenBrainz, zonder account.

## Technisch, om later uit te zoeken

Deze bibliotheken staan bewust op een oudere versie (zie `gradle/libs.versions.toml`):

- **Media3 1.10.1:** met 1.11.1 werken volgende en vorige nergens meer (de mediasessie meldt `actions=128`).
- **Material3 1.5.0-alpha22:** alpha28 verandert de Slider-API in zeven schermen.
- **Navigation 2.9.8:** 2.10 vervangt de terug-animatie (de kaart schuift niet meer mee met de vinger).
