# Overstap naar PixelPlayerOSS

Besluit van Mark (2026-11-21): de mod gaat verder op PixelPlayerOSS in plaats van de Google-versie. De Google-versie (PixelPlayerHQ/PixelPlayer) heeft sinds 2026-11-18 een archiefmelding en krijgt geen werk meer; PixelPlayerOSS (PixelPlayerHQ/PixelPlayerOSS) is actief (94 commits in november).

## Wat er anders is

- **Geen gedeelde geschiedenis.** PixelPlayerOSS is op 2026-05-31 als nieuwe repo geimporteerd en heeft daarna 565 eigen commits. Een git merge of rebase kan dus niet; het is een port: de mod-wijzigingen opnieuw aanbrengen op de OSS-code, met de huidige mod als voorbeeld.
- **Andere pakketnaam in de code:** `com.lostf1sh.pixelplayeross` (namespace en applicationId). De mod houdt applicationId `com.theveloper.pixelplay` en de Fedora-sleutel, zodat hij over de huidige app heen installeert en de versleutelde Navidrome- en Spotify-logins leesbaar blijven. De namespace in de code mag die van OSS blijven.
- **Andere database:** OSS opent `pixelplayer_database` (schema versie 6), de mod nu `pixelplay_database` (versie 42). Bij installeren over de huidige app blijft de oude database staan, maar de app begint leeg. Daarom komt er een eenmalige import bij de eerste start: likes, afspeellijsten, luistertellingen, zoekgeschiedenis, `files/playback_history.json` en de instellingen.
- **Google-vrij:** geen Chromecast, Gemini, Firebase. Telegram, NetEase, QQ Music en Google Drive zitten niet in OSS. Navidrome, Jellyfin en ListenBrainz wel.
- **Bibliotheken:** Media3 1.10.1 (gelijk, goed). Material3 1.5.0-beta02 (nieuwer dan alpha28: de schuiven van de volumebalk moeten mee naar de nieuwe Slider-API). Navigation 2.9.8 (gelijk). `androidx.mediarouter` zit niet in OSS en moet terug voor de Sonos-kamers in het castmenu (AndroidX, geen Google-dienst).
- **Nederlands:** OSS heeft 18 talen, geen Nederlands.
- OSS heeft een eigen `DuplicateFinder`; de regel "lokaal gaat voor" bouwt daarop voort.

## Wat er mee moet (uit de mod)

Nieuwe bestanden, vooral overzetten en aansluiten: Sonos (controller, zoeken, kamers in het castmenu, volumebalk, verbinden-paneel), Spotify (zoeken, inloggen, afspelen op Sonos), Navidrome-likes en luisterbeurten, recente zoekopdrachten, statistieken die doorklikken.

Bestaande schermen met veel mod-werk, opnieuw aanbrengen op de OSS-versie: Zoeken, Recently Played, Instellingen (Afspelen via), Genre- en Daily Mix-pagina's, Home, de speler (hoezen, chip, titel naar album), de tijdbalk.

Het verbinden-paneel zat in `CastBottomSheet`, dat OSS samen met Cast heeft weggehaald. Het paneel krijgt een eigen plek (telefoon, Sonos, Bluetooth) in dezelfde vorm als nu.

## Keuzes van Mark (2026-11-21)

Op de vergelijkpagina koos Mark overal mijn voorstel:

1. Home: zijn homepage van de mod terug (Your Mix-kaart, Listened this week, Made for you, artiesten, Rediscover; bovenin alleen PixelPlayer en instellingen).
2. Speler: bovenbalk van de mod (kamerpil plus menuknop, geen titel Now Playing) en de golvende tijdbalk. De bladwijzer van OSS gaat naar het menu achter de drie puntjes.
3. Minispeler van de mod, met de uitvoerknop en de ring om de afspeelknop.
4. Zoeken van de mod: recente zoekopdrachten, filters, Spotify, lokaal gaat voor.
5. Verbinden-paneel: blijft zoals overgezet.
6. Instellingen: Afspelen via terug als eigen onderdeel bovenaan; de nieuwe onderdelen van OSS blijven.
7. Naam onder het icoon: PixelPlayer.

Daarbij: kleine dingen die OSS mooier heeft gemaakt (iconen, de nieuwe zoekbalk en andere bijgewerkte onderdelen) gaan mee. De opbouw en functies komen van de mod, het uiterlijk van zulke losse onderdelen van OSS.

## Volgorde

1. Branch `oss-basis` vanaf `oss/main`, met applicationId, sleutel, versienummer en release-workflow van de mod. Bouwt en start op de emulator.
2. Import van de oude gegevens bij de eerste start, getest met een kopie van een echte v42-database.
3. Sonos en het verbinden-paneel, daarna Spotify, Navidrome-likes en luisterbeurten.
4. De schermwijzigingen van de mod (Zoeken, speler, Home, Recently Played, Instellingen).
5. Lokaal gaat voor, overal: zoeken, bibliotheek, albums, artiesten, Liked Songs, mixen, shuffle, en een tik in een lijst met servernummers.
6. De updatemelding wordt de gedeelde updatekaart van Mark (module `markupdate` uit de Keeper-repo, via `sync.sh`, niet zelf bouwen of aanpassen). Die vervangt de `UpdateCheckScheduler` van OSS, die nu naar de OSS-releases kijkt, en kijkt naar Marukiee/PixelPlayer-Mod. Wacht tot de module er is.
7. Screenshots van elk scherm dat er anders uitziet dan nu (ook door OSS zelf) naar Mark, pas na zijn akkoord de eerste OSS-release.
8. Daarna de rest van de roadmap op de nieuwe basis.

De oude mod (branch `mark-mod`) blijft bestaan tot de OSS-versie op de telefoon goed werkt.

## Stand op 2026-11-21, 23:15 (verder om 01:20, daarna uitbrengen)

Mark: "stop voor nu en ga verder om 1:20", daarna "breng dan alles uit als nieuwe release". Dus om 01:20 de rest afmaken en uitbrengen als 0.8.0-mod.32 (code 41).

Klaar en gecommit op `oss-basis`: import oude gegevens, likes bij scans, Sonos en verbinden-paneel, Navidrome-sterren en scrobbles, navigatiebalk 450 ms, naam PixelPlayer, Afspelen via in de instellingen, sha256 in de release-workflow.

Bezig (niet gecommit): `SearchScreen.kt` heeft de mod-diff als patch gekregen, 13 van 19 stukken. De 6 afgewezen stukken staan in de scratchpad (`oss/rej_search.rej`); verdwenen, maak de patch dan opnieuw met `git diff 63cb59b9 mark-mod -- .../SearchScreen.kt` in broncode, pakketnaam omzetten, `patch -p1 --dry-run`. Zoekbalk van OSS houden (Marks wens). Nodig erbij: `SpotifySearchSection.kt`, `data/search/RecentSearchSongs.kt`, de zoek- en Spotify-functies in `PlayerViewModel` (recentSearchSongs, playSongKeepingQueue, sendToast), `EnhancedSongListItem` met trailingIcon.

Nog te doen, in deze volgorde: Zoeken afmaken, minispeler (3A), speler (2A, bladwijzer in het menu), homepage (1A), lokaal gaat voor overal, dan release. De mod-diffs passen grotendeels als patch (minispeler/UnifiedPlayerSheetShared 2 van 6, FullPlayerContent 25 van 35, HomeScreen 5 van 14, PlayerViewModel 5 van 10).

Updatekaart: de gedeelde module `markupdate` bouwt nog niet op AGP 9 (gemeld aan de Tandem-sessie). Is die er om 01:20 niet, dan gaat de release zonder kaart; de update-check van OSS moet dan uit of naar Marukiee/PixelPlayer-Mod wijzen, nooit naar de OSS-releases.

Release: branch `oss-basis` naar remote `mod` pushen (nooit `origin` of `oss`), changelog bovenaan MOD_CHANGELOG.md als nieuw blok met lege regel erna, tag `v0.8.0-mod.32`, `gh run watch`, `gh release view`, handtekening `fe01845b...` nakijken, APK naar `PixelPlayer/apk/`.
