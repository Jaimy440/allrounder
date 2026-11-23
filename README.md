# Allrounder

Android-App (Kotlin + Jetpack Compose), die Moodle-Dateien herunterlädt, Notizen aus
Samsung Notes übernimmt und alles automatisch in Ordner sortiert.

## Funktionen

**Moodle**
- Anmeldung mit Benutzername/Passwort (offizielle Moodle-Web-Service-API, wie die Moodle-App)
  oder per Token bei Schul-/Uni-Login (SSO): *Moodle → Profil → Einstellungen → Sicherheitsschlüssel*.
- Kurse auswählen → „Dateien laden“ lädt alle Kursdateien nach
  `<Zielordner>/<Kurs>/Moodle/<Abschnitt>/`. Schon vorhandene Dateien werden übersprungen.

**Samsung Notes**
Samsung bietet keine Schnittstelle, über die fremde Apps Notizen direkt lesen dürfen. Deshalb zwei Wege:
1. In Samsung Notes *Teilen → PDF/Samsung-Notes-Datei → „In Allrounder sortieren“*.
   Die Dateien landen in `<Zielordner>/Eingang`.
2. Den Export-Ordner (meist `Documents/Samsung Notes`) in der App wählen; er wird beim Sortieren mit durchsucht.

**Sortieren**
- Quellen: `Eingang`, Samsung-Notes-Export-Ordner und ein frei wählbarer Zusatzordner (z. B. `Download`).
- Zielpfad: `<Fach>/<Dateityp>/<Monat>` – jede Ebene ist abschaltbar.
- Fach-Erkennung über Stichwörter im Dateinamen (in der App editierbar, z. B. `Mathe = mathe, analysis`);
  Moodle-Kurse werden automatisch als Fächer erkannt.
- Dateitypen: Notizen (.sdocx), PDF, Dokumente, Präsentationen, Tabellen, Bilder, Audio, Videos, Archive, Code, Sonstiges.
- Vorschau vor dem Ausführen, einzelne Dateien können ausgelassen werden. Kopieren oder Verschieben.

Die App nutzt das Storage Access Framework (vom Nutzer gewählte Ordner) und braucht keine
Speicher-Berechtigung. Das Moodle-Passwort wird nicht gespeichert, nur das Token.

## Bauen

Android Studio (Ladybug oder neuer) öffnen → Projekt öffnen → ▶ Run.
Oder per Kommandozeile mit installiertem Android-SDK:

```bash
./gradlew assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # Unit-Tests der Sortier- und Moodle-Logik
```

## Aufbau

| Datei | Inhalt |
|---|---|
| `moodle/MoodleClient.kt` | Login, Kurse, Kursinhalte, Download über die Moodle-REST-API |
| `sort/SortRules.kt` | Dateityp-Erkennung, Fach-Zuordnung, Zielpfade (reines Kotlin, getestet) |
| `storage/Storage.kt` | Ordner anlegen, kopieren, importieren (Storage Access Framework) |
| `storage/Settings.kt` | Gespeicherte Einstellungen |
| `MainViewModel.kt` | App-Logik (Moodle-Sync, Import, Sortieren) |
| `ui/Screens.kt` | Oberfläche: Moodle, Samsung Notes, Sortieren, Ordner |
