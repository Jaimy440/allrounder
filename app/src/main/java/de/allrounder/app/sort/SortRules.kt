package de.allrounder.app.sort

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Dateikategorien, nach denen sortiert wird. `folder` ist der Ordnername im Zielverzeichnis. */
enum class Category(val folder: String, val extensions: Set<String>) {
    NOTES("Notizen", setOf("sdocx", "sdoc", "snb", "spd", "note")),
    PDF("PDF", setOf("pdf")),
    DOCUMENTS("Dokumente", setOf("doc", "docx", "odt", "rtf", "txt", "md", "pages")),
    PRESENTATIONS("Präsentationen", setOf("ppt", "pptx", "odp", "key")),
    SPREADSHEETS("Tabellen", setOf("xls", "xlsx", "ods", "csv", "numbers")),
    IMAGES("Bilder", setOf("jpg", "jpeg", "png", "gif", "heic", "heif", "webp", "bmp", "svg", "tif", "tiff")),
    AUDIO("Audio", setOf("mp3", "m4a", "wav", "ogg", "aac", "flac", "amr", "3ga")),
    VIDEO("Videos", setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")),
    ARCHIVES("Archive", setOf("zip", "rar", "7z", "tar", "gz", "tgz")),
    CODE("Code", setOf("py", "java", "kt", "c", "cpp", "h", "js", "ts", "html", "css", "ipynb", "json", "xml", "sql")),
    OTHER("Sonstiges", emptySet());

    companion object {
        fun of(fileName: String, mimeType: String? = null): Category {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            entries.firstOrNull { ext in it.extensions }?.let { return it }
            // Fallback über den MIME-Typ, falls die Datei keine (bekannte) Endung hat
            val mime = mimeType?.lowercase() ?: return OTHER
            return when {
                mime == "application/pdf" -> PDF
                mime.startsWith("image/") -> IMAGES
                mime.startsWith("audio/") -> AUDIO
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("text/") -> DOCUMENTS
                else -> OTHER
            }
        }
    }
}

/**
 * Ein Fach / Kurs mit Stichwörtern. Enthält ein Dateiname eines der Stichwörter,
 * landet die Datei im Ordner [name].
 */
data class Subject(val name: String, val keywords: List<String>)

data class SortOptions(
    val bySubject: Boolean = true,
    val byCategory: Boolean = true,
    val byMonth: Boolean = false,
    val fallbackSubject: String = "Allgemein",
)

object SortRules {

    private val monthFormat = DateTimeFormatter.ofPattern("yyyy-MM")

    /**
     * Liest Fach-Regeln im Format `Fachname = stichwort1, stichwort2` (eine Regel pro Zeile).
     * Der Fachname selbst zählt immer auch als Stichwort.
     */
    fun parseSubjects(text: String): List<Subject> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val name = line.substringBefore('=').trim()
                if (name.isEmpty()) return@mapNotNull null
                val keywords = line.substringAfter('=', "")
                    .split(',', ';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                Subject(name, (listOf(name) + keywords).distinct())
            }

    /** Erzeugt Fach-Regeln aus Moodle-Kursen (Kurzname + Langname als Stichwörter). */
    fun subjectsFromCourses(courses: List<Pair<String, String>>): List<Subject> =
        courses.map { (shortName, fullName) ->
            Subject(sanitize(fullName.ifBlank { shortName }), listOf(shortName, fullName).filter { it.isNotBlank() }.distinct())
        }

    /** Findet das passende Fach. Längere Stichwörter gewinnen ("Mathe 2" vor "Mathe"). */
    fun matchSubject(fileName: String, subjects: List<Subject>): Subject? {
        val haystack = normalize(fileName)
        return subjects
            .flatMap { s -> s.keywords.map { kw -> s to normalize(kw) } }
            .filter { (_, kw) -> kw.length >= 2 && containsWord(haystack, kw) }
            .maxByOrNull { (_, kw) -> kw.length }
            ?.first
    }

    /** Liefert den relativen Zielpfad (Ordnerliste) für eine Datei. */
    fun targetPath(
        fileName: String,
        mimeType: String?,
        lastModifiedMillis: Long,
        subjects: List<Subject>,
        options: SortOptions,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> {
        val path = mutableListOf<String>()
        if (options.bySubject) {
            path += matchSubject(fileName, subjects)?.name ?: options.fallbackSubject
        }
        if (options.byCategory) {
            path += Category.of(fileName, mimeType).folder
        }
        if (options.byMonth && lastModifiedMillis > 0) {
            path += monthFormat.format(Instant.ofEpochMilli(lastModifiedMillis).atZone(zone))
        }
        return path.map(::sanitize)
    }

    /** Entfernt Zeichen, die in Datei-/Ordnernamen nicht erlaubt sind. */
    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifEmpty { "_" }.take(120)

    /** Kleinschreibung, Umlaute vereinfachen, Trennzeichen zu Leerzeichen. */
    fun normalize(s: String): String {
        val lower = s.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        return noAccents.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    /** Stichwort muss am Wortanfang stehen: "ma" passt auf "ma1 blatt", nicht auf "thema". */
    private fun containsWord(haystack: String, keyword: String): Boolean =
        Regex("(^|\\s)" + Regex.escape(keyword)).containsMatchIn(haystack)
}
