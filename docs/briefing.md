> Historical document: the original briefing (German) that started the project. The current state and all later decisions are in [plan.md](plan.md).

# KoalaShelf: Start-Prompt für Claude Desktop

Kopiere alles ab "Rolle" in die erste Nachricht. Hänge `koalashelf-design-grundlagen.png` an.

---

## Rolle

Du baust **KoalaShelf**: einen eigenen Client für einen **Audiobookshelf-Server** (Hörbücher). Der Server bleibt unverändert. Wir ersetzen nur die Oberfläche und ergänzen Funktionen.

Zielplattformen:
1. **Web-App** (responsive, installierbar als PWA)
2. **Desktop-App** (übernimmt die Web-App, z. B. per Tauri)
3. **Native Android-App** (Kotlin, Jetpack Compose, Media3)

Antworte knapp. **Erst Plan, dann Code.** Vor Änderungen an mehreren Dateien zeigst du mir den Plan und wartest auf mein OK. Schlage `/compact` vor, wenn der Kontext lang wird.

## Erste Aufgabe (nur Plan, noch kein Code)

1. Lies die Audiobookshelf-API-Doku: https://api.audiobookshelf.org
2. Schlage einen Stack vor und begründe kurz. Mein Vorschlag zum Prüfen: Monorepo, Web mit TypeScript + SvelteKit oder React + Vite, Desktop mit Tauri, Android nativ mit Compose.
3. Schlage eine Ordnerstruktur vor. Ein Paket `design-tokens` liefert Farben, Schrift und Abstände für Web und Android aus **einer** Quelle.
4. Liefere einen Phasenplan (siehe unten) und frage nur das, was du wirklich nicht selbst entscheiden kannst.

## Funktionen, Phase 1 (Web)

- Login mit Server-URL, Benutzername, Passwort. Token sicher speichern.
- Start: "Weiterhören" (große Karte), Sammlungen als "Kisten" (Neu, Weiter in der Serie).
- Bibliothek: Regal-Ansicht, Liste, Serien. Filter: Status (Alle, Laufend, Neu, Fertig), Genre, Sprecher:in, Serie. Sortierung.
- Detailseite: Cover mit Platte, Serie und Band, Autor:in, Sprecher:in, Dauer, Kapitel, Fortschritt, Beschreibung, Datei-Infos (Format, Größe).
- Player: Kapitel, Vor/Zurück 30 s, Geschwindigkeit 0,8 bis 2,0 als Schalter, Sleep-Timer inklusive "bis Kapitelende", Lesezeichen mit Notiz, Fortschritt mit dem Server synchronisieren.
- Mini-Player über der Navigation.
- Theme hell/dunkel, folgt dem System, manuell umschaltbar.

Später: Downloads/Offline, Android-Auto, Clip teilen, Fahrmodus, Statistik, Autor:innen-Seiten, Sammlungen, Suche.

## Design: "KoalaShelf Vinyl"

Beiliegendes Bild zeigt die Grundlagen. Diese Werte gelten verbindlich.

**Leitprinzipien**
- **Cover zuerst.** Quadratische Cover groß und ohne Beschnitt. Titel klein darunter, wenn Platz ist.
- **Platte als Motiv.** Im Player dreht sie sich, der Tonarm zeigt den Fortschritt, Ticks am Rand sind Kapitel. In der Detailansicht ragt sie rechts aus dem Cover. Im Mini-Player ist sie klein und dreht sich.
- **Kiste und Regal.** Kiste = Sammlung auf der Startseite: Covers stehen in einer Reihe, eine dunkle Leiste verdeckt unten etwas (Titel deshalb oben auf dem Cover). Regal = Bibliothek: Covers auf einer Leiste, Titel und Autor:in darunter.
- Hörbuch-Funktionen sichtbar: Lesezeichen als Pins auf der Zeitleiste.
- `prefers-reduced-motion` respektieren (Platte dreht dann nicht).
- Touch-Ziele mindestens 44 px. Kontrast mindestens 4,5:1.

**Schrift**
- Titel, Überschriften, Zeitanzeige, Kapitelnummern: **Gloock** 400
- Alles andere: **Manrope** 400 / 600 / 800
- Skala (px): 12, 13, 14, 15, 18, 24, 34, 56. Zeitanzeige im Player 38. Label: 12 px, 800, Versalien, +0,1 em.
- Abstände im 4er-Raster. Radien: Cover 5, Karten 16 bis 22, Chips und Buttons voll rund.

**Farb-Tokens** (Theme-Klasse `th-light` / `th-dark`)

| Token | Hell | Dunkel |
|---|---|---|
| outer | #CBD2DC | #0A0807 |
| bg | #EEF1F5 | #17130F |
| panel | #E4E8EE | #17130F |
| card | #FFFFFF | #211B16 |
| ink | #1E222B | #F1E8DC |
| muted | #566075 | #A39888 |
| line | #D5DAE2 | #2E261F |
| ctrl | #2A2F3A | #2A2119 |
| ctrl-ink | #EEF1F5 | #F1E8DC |
| accent | #C23D20 | #E8A33D |
| on-accent | #FFFFFF | #1A1206 |
| track | #C3CAD6 | #3A3027 |
| ledge | #B7BFCC | #3A3027 |
| sel / sel-ink | #2A2F3A / #EEF1F5 | #2A2119 / #E8A33D |
| cur-bg / cur-ink | #2A2F3A / #FFFFFF | #2A2119 / #E8A33D |
| platter | #CBD2DC | #211B16 |
| vinyl a / b | #161820 / #22252F | #0A0807 / #1B1612 |

**Platte (CSS-Idee)**
- Rille: `repeating-radial-gradient(circle at 50% 50%, var(--va) 0 2px, var(--vb) 2px 4px)`
- Kapitel-Ticks: `repeating-conic-gradient(from 0deg, rgba(255,255,255,.5) 0 .8deg, transparent .8deg <360/Kapitelzahl>deg)` mit Ring-Maske (transparent bis 92 %, dann sichtbar)
- Label in der Mitte: das Cover, rund, ca. 40 % des Durchmessers
- Tonarm-Winkel: ca. `18° + Fortschritt × 22°`, Drehpunkt oben rechts
- Dreh-Animation: 9 s linear, endlos

**Layouts**
- **Desktop/Web:** feste Seitenleiste links (196 px): Start, Bibliothek, Serien, Autor:innen, Sammlungen, Downloads, Statistik, unten das Nutzerprofil. Hauptbereich rechts. Unter 800 px wird die Seitenleiste zur Tab-Leiste unten (Start, Bibliothek, Suche, Offline).
- **Android:** Tab-Leiste unten, Mini-Player darüber. Player als Vollbild-Sheet.
- **Player (Android):** oben Schließen und Fahrmodus. Dann Plattenspieler (Platte, Tonarm), Titel, Autor:in und Sprecher:in, große Zeit, Zeitleiste mit Pins, Steuerung (Kapitel zurück, −30, Play groß, +30, Kapitel vor), Geschwindigkeits-Schalter, vier Aktionen (Timer: Kapitelende, Kapitel, Marken, Clip teilen).
- **Detail:** Cover mit Platte, rechts Serie (Label), Titel, Autor:in, Sprecher:in, Chips (Dauer, Kapitel, Sprache), Fortschrittsbalken, Button "Weiter bei h:mm:ss". Darunter Tabs Kapitel / Marken / Mehr vom Autor und eine Info-Spalte (Beschreibung, Format, Größe, Genre, Erschienen, Bibliothek).

**Cover**
- Echte Cover kommen vom Server (`/api/items/{id}/cover`). Fehlt eins, zeige einen Platzhalter im Stil der Beispiel-Covers (Farbfläche, Autorenzeile oben, Titel unten in Gloock).
- Titel und Autorenzeile skalieren mit der Cover-Größe (CSS Container Queries, `cqw`): Autor 4,4 cqw, Versalien, +0,14 em, 800. Titel 13 cqw, Gloock.

## Arbeitsweise

- Komponenten klein halten, Tokens nie hart codieren.
- Pro Phase: lauffähiger Stand, kurze Test-Anleitung, ein Commit.
- Keine Funktionen erfinden, die der Server nicht kann. Unklare API-Stellen zuerst klären.
- Schreibe Texte in der UI auf Deutsch. Code und Kommentare auf Englisch.

## Phasen

1. Plan, Stack, Repo-Struktur, Design-Tokens-Paket
2. Web: Login, Bibliothek, Detail (lesend)
3. Web: Player mit Fortschritts-Sync, Kapitel, Geschwindigkeit, Timer, Lesezeichen
4. Web: Start, Kisten, Filter, Theme, PWA
5. Desktop-Hülle
6. Android nativ (Tokens übernehmen, Media3, Hintergrundwiedergabe, Offline)
