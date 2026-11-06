# Roadmap PixelPlayer Mod

## Open lijst voor een grote ronde

Volgorde gekozen door Mark (2026-11-02):

1. [x] Sonos: afspelen via Navidrome, met groeperen en volume per speaker (0.7.5-mod.9, thuisadres in mod.10)
   - [x] Mark test thuis op zijn echte speakers: werkt (2026-11-02)
   - [x] Volumeknoppen buiten de app regelen de Sonos, en het volume verschijnt in het eiland van Drip (0.7.5-mod.13, `SonosSessionPlayer`)
   - [x] App gesloten terwijl de Sonos speelt: bij openen pakt hij de sessie vanzelf weer op (0.7.5-mod.13, `resumeSavedSession`)
   - [ ] Volumeschuif in beeld als je in de app op de volumeknoppen drukt
   - [ ] Vanaf die volumeschuif ook het volume van de andere speakers in de groep regelen
   - [ ] Animaties in de hele app nalopen (Material 3 Expressive, `MotionScheme.expressive()`), net als bij de Sonos-onderdelen
   - [x] **Ronde mod.11 en mod.12** (wensen van Mark, 2026-11-02 en 2026-11-04): overzetten op dezelfde plek, speakers vooraf klaar, groepsvolume zoals de Sonos-app, dempknop, drie puntjes, nieuwe indeling, soepel scrollen en krimpen, artiestfoto's, speaker-pil, Spotify overnemen, geen grijze box op de homepage
   - [x] **Ronde mod.16** (wensen van Mark, 2026-11-05): likes twee kanten op met Navidrome, Navidrome in Instellingen, genrepagina's, homepage en geschiedenis, swipe in lijsten, mediakaart, nieuw Verbinden-paneel
   - [x] Hoes in de Drip-pil: lag aan Drip (koos de mediasessie van de Sonos-app). Opgelost aan de Drip-kant; PixelPlayer hoeft niets
   - [x] Hoesjes in de Sonos-app (zie "Hoesjes in de Sonos-app" hieronder): eigen hoezen via een https-adres dat alleen thuis werkt, geen Deezer
   - [ ] Pauze in het eiland van Drip blijft soms op play staan: lag aan Drip, de kaart bleef op `expandedFallback` hangen zodra er een tweede sessie was. Met een sessie klopt het (12 van 12 op de emulator). Oplossing staat in de Drip-repo op branch `claude/pauze-icoon`, nog niet uitgebracht. PixelPlayer moet `DRIP_REMOTE_NAME` in de session extras blijven zetten zolang hij cast. Afvinken als Mark het met die Drip-versie niet meer ziet
2. [ ] PixelPlayerOSS verwerken: hun fixes en verbeteringen in de mod overnemen. Bij de start navragen of de mod daarbij ook Google-vrij moet worden (Cast, Gemini, Firebase eruit), of alleen de fixes.
3. [ ] Spotify: te kopen-lijst
4. [ ] Spotify: nieuwe releases
5. [ ] Spotify: afspeellijsten importeren
6. [ ] Spotify en PixelPlayer: luisterstatistieken samenvoegen
7. [ ] Hele app in het Nederlands vertalen
8. [ ] Tweede variant van de crash na herstarten van de telefoon
9. [x] Luistergegevens bij elkaar op de server (0.7.5-mod.17, zie "Luistergegevens naar Navidrome")
   - [x] Getest op de echte server via de Sonos (2026-11-06): een heel nummer telt een keer met de begintijd, overslaan telt niet. Offline en herstart zijn alleen tegen de nepserver getest
   - [ ] Oude scrobbles op de server staan dubbel (zelfde seconde, van voor mod.17). Opruimen is een keuze van Mark
   - [ ] Oude geschiedenis eenmalig meesturen: aparte keuze van Mark, zie hieronder
10. [ ] Site en screenshots bijwerken naar het Verbinden-paneel van mod.16: `scherm-3` en de hero op de site, en de map met screenshots. Pas als Mark het zegt (Mark, 2026-11-05)
11. [ ] Muziek bedienen in het Verbinden-paneel, zonder het eerst weg te drukken: mooi geïntegreerd aan de onderkant of bovenkant, met een soepele overgang in en uit het paneel. Eerst voorbeelden maken en Mark laten kiezen (Mark, 2026-11-06)
12. [x] Volumeschuif gaat met de volumetoetsen soepel naar het nieuwe volume in plaats van in sprongen (0.7.5-mod.18, `glidingVolume`)
13. [x] Hapering bij het uitklappen van individueel volume: de schuiven worden nu vooraf gebouwd, de veer loopt korter uit, en de ring onder het paneel tikt nog maar eens per seconde (0.7.5-mod.18). Gemeten op de emulator; Mark kijkt op de telefoon of het goed voelt

## Luistergegevens naar Navidrome

Wens: alles wat Mark luistert op een plek, dus ook offline op de telefoon, op de server samen met wat via Navidrome op de Sonos speelt. Eisen van Mark (2026-11-05): niets dubbel geteld, niets kwijt bij geen verbinding of een herstart, en het echte tijdstip op de server.

Hoe het werkt sinds mod.17 (`NavidromeScrobbles`):

- **Een plek telt:** `ListeningStatsTracker` meldt elke luisterbeurt, ook van lokale bestanden en de Sonos, want de telefoon speelt dan stil mee. De losse `scrobble`-aanroepen in `MusicService` zijn weg en `reportPlayback` gaat mee met `ignoreScrobble=true`, anders telt een nieuwe Navidrome alles dubbel.
- **Wat telt:** de helft van een nummer, of 4 minuten bij een lang nummer. Zonder bekende lengte 30 seconden. Korte nummers tellen dus ook, anders dan bij Last.fm.
- **Wachtrij:** de luisterbeurt staat meteen in `navidrome_scrobbles` (SharedPreferences), met de tijd waarop hij begon. `NavidromeScrobbleWorker` verstuurt zodra er verbinding is, oudste eerst, en haalt elke beurt weg na zijn eigen antwoord. Een extra ronde bij het openen van de app en elke 6 uur. Hooguit 5000 op de wachtrij.
- **Koppelen:** lokale bestanden via `FavoriteSongIndex`, dezelfde koppeling als de likes. Geen kopie op de server: 14 dagen bewaren, want Syncthing en de scan kunnen achterlopen, daarna weg.
- **Fouten:** geen verbinding of een inlogfout: bewaren en later opnieuw. Een andere weigering van de server: alleen die beurt weg, zodat hij de rest niet ophoudt.
- De telefoon houdt daarnaast zijn eigen geschiedenis in `files/playback_history.json` (tot ongeveer 730 dagen). Daar telt alles vanaf 5 seconden, de server is strenger.

Nog open:

- De oude geschiedenis eenmalig meesturen is een aparte keuze: de play counts lopen dan in een keer op, en Navidrome-nummers die al een scrobble kregen tellen dubbel als dat niet wordt uitgesloten.

Grenzen:

- Alleen wat op de server staat, want `scrobble` vraagt een song-id. Een nummer dat alleen op de telefoon staat blijft lokaal, tenzij het rechtstreeks naar ListenBrainz of Last.fm gaat (die werken op titel en artiest).
- Een antwoord dat onderweg verloren gaat kan die ene beurt dubbel sturen; de server heeft geen manier om dat te herkennen. Een nummer dat stopt doordat Android de app hard afsluit telt niet mee.
- Navidrome bewaart een volledige geschiedenis met tijdstippen pas sinds 0.59.0, daarvoor alleen play count en laatst gespeeld.
- De documentatie noemt nog geen manier om die geschiedenis terug te lezen ("future features"). De app blijft dus zijn eigen geschiedenis tonen en de server krijgt een kopie. Navidrome kan per gebruiker doorsturen naar Last.fm of ListenBrainz; of een late scrobble daar met het oude tijdstip aankomt moet nog getest worden.
- Raakt punt 6 hierboven: Last.fm of ListenBrainz kan de gezamenlijke plek worden, ook voor Spotify.

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
- Thuisadres (0.7.5-mod.10): de app gebruikt het Navidrome-inlogadres (bij Mark via Tailscale), de speakers krijgen het thuisadres van de server uit Instellingen > Sonos. `SonosTrackResolver.rebase` zet het begin van de stream- en hoes-URL om.
- Getest op de emulator tegen een echte Navidrome en een nep-Sonos met drie speakers, en door Mark thuis op zijn echte speakers: werkt (2026-11-02).
- Terugverbinden (0.7.5-mod.13): het coördinator-id en de wachtrij staan in SharedPreferences. Bij het starten dempt de app eerst, vraagt de speaker wat hij speelt, en pakt de sessie op als die nog op onze wachtrij staat (spelend, of gepauzeerd en minder dan 12 uur oud). De speaker krijgt daarbij geen commando's; de telefoon volgt hem. Wegvegen uit Recent stopt de service niet zolang Sonos actief is.
- Volume buiten de app (0.7.5-mod.13): `SonosSessionPlayer` om de sessiespeler meldt een remote device (0 tot 100) en stuurt de knoppen door naar het groepsvolume. Uit te zetten met dezelfde instelling als de volumeknoppen in de app.
- Spotify (0.7.5-mod.12): gemeten op de speakers. Staat Spotify Connect op de speaker (`x-sonos-vli:`) en zet je de wachtrij erop, dan pakt Spotify hem binnen 0,6 seconde terug, ook als Spotify al op pauze stond. Een Pause-commando vooraf plus 300 ms wachten lost dat op. `holdSpeaker` kijkt daarna nog even of Spotify echt weg is, want een Play of Seek die op Spotify belandt, zet Spotify weer aan of blijft hangen. Daarna kost overzetten ongeveer 0,6 seconde (Seek terwijl gestopt werkt meteen, Play tot geluid 0,5 tot 0,6 seconde).

- Snel skippen en pauze (0.7.5-mod.15): gemeten op een echte speaker. Calls duren 12 tot 90 ms, na Play of een track-Seek tijdens het spelen staat hij eerst op `TRANSITIONING`, en een Pause werkt dan ook meteen. Reeksen skips houden de speaker stil tot 800 ms na de laatste (`SKIP_RUN_DEBOUNCE_MS`). Een pauze gaat altijd meteen door en de speakerstand wordt op het laatste moment van de telefoon gelezen. De oude tijdvenster-beveiliging bij het volgen van de speaker is weg, want die negeerde knoppen. `tools/sonos-test` is de stresstest hiervoor: de oude code haalde 15 van de 27 rondes, de nieuwe alles.
- Pauzeknop (0.7.5-mod.15): de knoppen keken naar `Player.isPlaying`, en dat is false zolang het volgende nummer laadt. Een tik op pauze net na een skip stuurde dus play. Nu kijken ze of de speler wil spelen (`wantsToPlay`). Mediaknoppen sturen altijd een expliciete pauze, dus deze fout vind je alleen met echte tikken: `tools/sonos-test/real_skiptest.py`. Op Marks echte speaker: voor de fix 5 van de 8 goed, erna alles.
- Naam in de Android-mediakaart (0.7.5-mod.15): SysUI haalt de naam uit een routing session die hoort bij `volumeControlId`, en `MediaStyle.setRemotePlaybackInfo` vraagt een systeemrecht. Daarom biedt de app de Sonos-groep aan als eigen route (`SonosOutputRoute`, `SonosRouteProvider`), gaat er zelf op staan via `MediaRouter2.transferTo`, en geeft de id door met `DeviceInfo.setRoutingControllerId`. De schuif in Androids uitvoermenu leest de route, dus die krijgt ook elk nieuw volume. Kiezen voor deze telefoon in dat menu hoort de Sonos-sessie te beëindigen (`onReleaseSession`); de emulator biedt die keuze niet aan, dus dat is nog niet gezien.
- Sessiespeler (0.7.5-mod.15): een nieuwe `SonosSessionPlayer` na een spelerwissel had nog niets gemeld. Na een disconnect bleef de sessie dan op remote staan, dus `announceDevice` na elke wissel.
- Korte veeg in Connect device (0.7.5-mod.15): `ModalBottomSheet` in Material3 1.5.0-alpha22 negeert de drempels van `SheetState`. Hij sluit pas voorbij de helft van het paneel of als de vinger bij het loslaten nog snel gaat, dus een korte veeg die afremt veert terug. `closesOnShortPull` telt de vinger vanaf het neerzetten en sluit vanaf 24 dp omlaag, als de sheet echt meebewoog (een lijst of schuif telt dus niet). Testen: met een sessie opent het paneel op Controls en is het korter, dus een veeg op de plek van de Devices-tab raakt dan de scrim, en daar sluit een tik ook.
- Mediakaart bij volgend nummer (0.7.5-mod.16): `DefaultMediaNotificationProvider` levert de hoes later via de callback, onder zijn eigen id 1001. `startForeground` met een ander id laat Android de melding 101 weghalen, en SysUI zet de kaart dan even op zijn resume-stand (`Converting ... to resume` in de log). `LocalOnlyMediaNotificationProvider` zet nu ook op de callback id 101. Testen: `notification_canceled` en `notification_enqueue ...,1001` in `logcat -b events`, met een nummer dat echt een hoes in de sessie heeft. Navidrome-nummers op de emulator hebben die niet (`navidrome_cover://` staat niet in de artwork-schema's), dus daar zie je de fout niet.
- Likes met Navidrome (0.7.5-mod.16): `NavidromeFavoritesSync` en `NavidromeFavoritesMerge`. Drierichtingsmerge met een base per account, dus een eerste sync voegt alleen toe. Een unlike in de app is een eigen gebeurtenis in `pending` en wordt nooit afgeleid uit een verschil, want een nummer dat uit de bibliotheek verdwijnt is geen unlike. Verdwijnt in een keer 20 of meer en meer dan de helft van de base, dan blijven de likes staan en gaan de sterren terug (Navidrome leidt id's af van bestandspaden). Koppelen via de cache `navidrome_songs`: naar de server mag een remaster nog matchen, terug alleen dezelfde titel. App naar server duurt ongeveer 2 s, server naar app gebeurt bij openen van de app (60 s pauze ertussen) en op het interval.
- Navidrome in Instellingen (0.7.5-mod.16): eigen categorie, de cloudknop op de homepage is weg. Accounts staat onderaan Instellingen.
- Hoesadres-suggestie (0.7.5-mod.16): `SonosTrackResolver.suggestCoverAddress`, alleen bij een https-login die niet via Tailscale loopt en alleen in een leeg veld. Bij inloggen via Tailscale http is er niets af te leiden.
- Drip (0.7.5-mod.16): Boolean `nl.markmaaktmedia.drip.HIDE_ISLAND` in de session extras zolang Connect device open staat (`DripIslandSignal`), naast `nl.markmaaktmedia.drip.REMOTE_NAME`.
- Dubbele nummers (0.7.5-mod.16): `withoutDuplicateTracks`, zelfde titel, zelfde artiest en hooguit 3 s verschil is hetzelfde nummer, het lokale bestand wint. Live-versies met een andere lengte blijven. Geldt voor genre-pagina's, Daily Mix, Your Mix en Herontdekken.
- Homepage en geschiedenis (0.7.5-mod.16): volgorde Your Mix, Listened this week, Made for you, artiesten, Rediscover, Recently Played als lijst met See all. Het scherm toont alle dagen uit `playback_history.json` met dagstrip en kalender. Genre-mixen slaan "Unknown Genre" over: die naam komt uit de statistieken en bestaat niet als genre, dus de kaart opende een lege pagina.
- Swipe in lijsten (0.7.5-mod.16): `modifier` van `EnhancedSongListItem` zit nu buiten de swipe (`SongSwipeContainer`), en het paneel loopt door tot onder de rij. Geldt voor alle lijsten.
- Standaardinstellingen (0.7.5-mod.16): palet Vibrant (`AlbumArtPaletteStyle.default`), kwaliteitslabels uit. Wie zelf al iets koos, houdt dat.
- Luistertijd op de homepage (0.7.5-mod.16): `BasicText` met `TextAutoSize`. Een gewone `Text` met `maxLines = 1` liet het laatste woord stil wegvallen ("1 h 58" zonder m).
- Apparaatpaneel (0.7.5-mod.16): `DeviceCardHeader`, `DeviceVolumeSlider` en `DeviceCardShape` (vier gelijke hoeken) in `SonosConnectSection.kt`, gedeeld door de Sonos-kaart en `ActiveDeviceHero`, zodat telefoon, Cast en Sonos dezelfde opbouw hebben. De schuiven zijn de oude; het getal staat er los naast. Bij een groep staat onder de kaart een tweede kaart met de knop Individueel volume. De speakers openen boven de knop, omdat het vel omhoog groeit; zo blijft de knop onder de vinger en raakt een tweede tik nooit een schuif. Geen dempknop in de kaart en geen schaduw: de kaart ligt in het vel en zweeft niet, en een schaduw zou in het gat tussen de twee kaarten vallen. Verbreken is het kruisje (`Icons.Rounded.Close`). Groeperen is een speaker met een plusje over de hoek (`ic_output_speaker_add`), omdat de schakel op een paperclip leek. Rond het plusje loopt een smalle uitsparing in dezelfde vorm, gemaakt met een `clip-path`. De iconen in de ronde knoppen zijn 24 dp; in het menu met drie puntjes staan nog de schakels (`Link`, `AddLink`, `LinkOff`). `closesOnShortPull` sluit alleen als de vinger meer omlaag dan opzij ging, anders sloot een trage veeg van Devices naar Controls het paneel. Lang indrukken toont het label, zoals in `LibraryActionRow`.

### Hoesjes in de Sonos-app

Instelling "Adres voor hoesjes" (Instellingen > Sonos): `SonosTrackResolver.coverForSonos` zet de hoes-URL op dat https-adres, de muziek blijft op het thuisadres. Geldt voor de eerstvolgende wachtrij, dus opnieuw verbinden na het invullen.

Het adres staat standaard ingevuld en is te wijzigen in de app. De standaardwaarde zit niet in de code: `SONOS_COVER_ADDRESS` in `local.properties` of, op GitHub, de secret met dezelfde naam. Leeg opslaan zet het uit.

Bij Mark loopt dat via een eigen domeinnaam die naar het thuisadres van de server wijst (DNS only), Nginx Proxy Manager (bestaand wildcard-certificaat) naar Navidrome, en alleen GET en HEAD op exact `/rest/getCoverArt.view`. Niets staat open naar buiten. De link bevat wel het Subsonic-token, dus dat komt in het toegangslog van die proxy host.

Gemeten op Marks echte speakers en de Sonos-app (Android) op 2026-11-04:

- **https thuis, geldig certificaat:** hoes zichtbaar. Volgens het log haalde alleen de telefoon hem op (`okhttp/5.3.2`; PixelPlayer gebruikt 5.5.0, dus vrijwel zeker de Sonos-app). Van de speakers kwam geen enkel verzoek. Andere Sonos-apps in huis zijn niet getest; zij halen de hoes vermoedelijk ook zelf op.
- **http (Navidrome thuis):** geen hoes en geen verzoek, ook niet van de speaker.
- **Openbare https (Deezer-link):** werkt ook, maar Mark wil dat niet.
- **https via Tailscale (`tailscale serve`, geldig certificaat):** geen hoes en geen verzoek, ook niet met "Use Tailscale DNS" aan. Waarom niet is onbekend.
- **`/getaa?s=1&u=<stream-URL>` op de speaker:** 404 voor http-streams. Gebouwd in mod.11 en in mod.12 weer weggehaald.
- **Hoes in de metadata (`data:image/jpeg;base64`):** de speaker weigert dat (UPnP 804).

Let op bij een nieuwe naam: de router bewaart een "bestaat niet"-antwoord tot 30 minuten (SOA-minimum 1800 s). Dat was bij deze test de oorzaak van NXDOMAIN, geen rebind-bescherming: een `nip.io`-naam die naar hetzelfde privé-adres wijst, lost via de router gewoon op.

### Sonos-volume (punt 1 gebouwd in 0.7.5-mod.13, punt 2 en 3 nog niet)

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

Let op bij het overnemen: mod.17 haalde ongebruikte code van upstream weg in een eigen commit, "Remove unused code and resources": 15 Kotlin-bestanden, ongeveer 40 drawables en layouts, en ongeveer 1700 vertalingen in `values-ar` zonder Engelse versie. Botst dat, draai die commit dan terug.

- Zonder Telegram, NetEase, QQ Music, Google Drive, Gemini, Cast, Wear OS, Play-billing, Firebase, Crashlytics en Google Play-diensten. Wel Navidrome/Jellyfin en de rest van de speler.
- Veel actiever dan de gewone versie (die staat sinds juni stil): op 2026-11-01 nog fixes, zoals stil afspelen na shuffle en prestaties van Daily Mix, statistieken en Jellyfin. Release v0.4.0 op 2026-10-17, ook op F-Droid.
- Andere pakketnaam (`com.lostf1sh.pixelplayeross`): een overstap betekent een aparte app, je likes overzetten via een back-up, en alle mod-wijzigingen opnieuw inbouwen op die code. Chromecast valt dan weg; Sonos werkt niet via Cast, dus dat raakt de Sonos-plannen niet.
- Twee wegen:
  1. Bij de huidige basis blijven en losse fixes uit OSS overnemen.
  2. Overstappen op OSS als nieuwe basis. Groot werk, maar daarna profiteer je van hun updates.

## Technisch, om later uit te zoeken

Security-check bij mod.17 (2026-11-05). Bewust zo gelaten:

- Onversleuteld verkeer blijft toegestaan: de Sonos-speakers praten alleen http op het thuisnet, en een Navidrome thuis kan ook http zijn.
- De speakers krijgen een stream-adres met het Navidrome-token. Dat kan niet anders en werkt alleen op het thuisnet.
- De Cast-server luistert op het hele netwerk, maar alleen tijdens casten en met een token en een lijst toegestane nummers.
- `sonos_hosts.txt` in `Android/data` is de testingang voor speakers. Andere apps kunnen daar sinds Android 11 niet bij, en de app vereist minstens Android 11.

Vijf unit-tests lopen achter op de code, ook op mod.16 al, los van de mod.17-wijzigingen: `BackupSectionTest`, `LoadControlBufferProfileTest`, `LyricsStateHolderTest`, `AudioMetaUtilsTest`, `LocalArtworkUriTest`.

Deze bibliotheken staan bewust op een oudere versie (zie `gradle/libs.versions.toml`):

- **Media3 1.10.1:** met 1.11.1 werken volgende en vorige nergens meer (de mediasessie meldt `actions=128`).
- **Material3 1.5.0-alpha22:** alpha28 verandert de Slider-API in zeven schermen.
- **Navigation 2.9.8:** 2.10 vervangt de terug-animatie (de kaart schuift niet meer mee met de vinger).
