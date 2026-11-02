# PixelPlayer Mod

Marks eigen versie van [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer), met extra functies en fixes uit open pull requests.

## Installeren en updaten

Download `PixelPlayer-Mod.apk` bij de nieuwste [release](../../releases/latest) en installeer hem over de bestaande app heen. Likes, wachtrij en instellingen blijven staan, omdat elke release met dezelfde sleutel getekend is.

Een nieuwe release maken: `APP_VERSION_NAME` en `APP_VERSION_CODE` in `gradle.properties` ophogen, `MOD_CHANGELOG.md` bijwerken, en een tag `v<versie>` pushen. GitHub bouwt dan de APK.

## Wat er anders is dan upstream

**Wachtrij**
- Handmatig toegevoegde nummers komen op volgorde na het huidige nummer (zoals Spotify) en blijven staan als je iets anders gaat afspelen
- Swipen op een nummer zet het in de wachtrij; acties links en rechts zijn instelbaar
- Swipen in de wachtrij verwijdert of zet achteraan, met popup en ongedaan maken
- Icoontje bij nummers die je zelf in de wachtrij zette

**Zoeken**
- Accenten en apostrofs hoeven niet: `hakan hellstrom` vindt Håkan Hellström, `dont` vindt Don't
- Meerdere woorden in willekeurige volgorde

**Speler**
- Swipen op de mini-speler wisselt van nummer
- Lang drukken op volgende of vorige spoelt door
- Elastisch sluiten met haptiek voor speler en wachtrij
- Knop met drie puntjes voor nummerinfo, bron-icoon (lokaal of gestreamd)

**Bibliotheek**
- Meerdere ARTIST-tags worden allemaal gelezen
- Kwaliteitslabels LQ/HQ/SQ/HR, uit te zetten in Instellingen
- Sorteren op luistertijd, sorteren met accenten klopt

**Likes en Navidrome**
- Likes overleven back-up, herstel en herscan (koppeling op bestandsnaam en tags)
- Likes worden naar Navidrome gesynchroniseerd, met controle in Instellingen
- Navidrome-nummers optioneel buiten de bibliotheek houden

**Sonos**
- Afspelen op Sonos via de verbindknop in de speler: kies een ruimte en de muziek gaat erheen
- De speakers halen de nummers zelf van Navidrome; de hele wachtrij staat op de Sonos, dus hij speelt door als je telefoon uit is
- De telefoon speelt stil mee, zodat melding, vergrendelscherm, swipes en wachtrij gewoon werken
- Groeperen zoals in de Sonos-app (vinkjes per ruimte), groepsvolume en volume per speaker
- Wijzigingen in de Sonos-app (volgende, pauze) volgt de telefoon; speelt de speaker iets anders, dan stopt de koppeling
- Volumeknoppen regelen de Sonos zolang de app open is
- Instellingen > Sonos: hoeveel nummers op Navidrome staan, overslaan of stoppen bij nummers die alleen op je telefoon staan
- Werkt alleen als de Sonos je Navidrome-server kan bereiken (adres op je thuisnetwerk, niet via Tailscale)
