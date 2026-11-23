package de.allrounder.app.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/** Einfache dauerhafte Einstellungen (SharedPreferences). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("allrounder", Context.MODE_PRIVATE)

    var moodleUrl: String
        get() = prefs.getString("moodle_url", "") ?: ""
        set(v) = prefs.edit { putString("moodle_url", v) }

    var moodleUser: String
        get() = prefs.getString("moodle_user", "") ?: ""
        set(v) = prefs.edit { putString("moodle_user", v) }

    /** Das Moodle-Token (kein Passwort) – das Passwort wird nie gespeichert. */
    var moodleToken: String
        get() = prefs.getString("moodle_token", "") ?: ""
        set(v) = prefs.edit { putString("moodle_token", v) }

    /** Hauptordner, in den alles einsortiert wird. */
    var targetFolder: Uri?
        get() = prefs.getString("target_folder", null)?.let(Uri::parse)
        set(v) = prefs.edit { putString("target_folder", v?.toString()) }

    /** Ordner, in den Samsung Notes exportiert (z. B. Documents/Samsung Notes). */
    var notesFolder: Uri?
        get() = prefs.getString("notes_folder", null)?.let(Uri::parse)
        set(v) = prefs.edit { putString("notes_folder", v?.toString()) }

    /** Weiterer unsortierter Ordner, z. B. Download. */
    var extraFolder: Uri?
        get() = prefs.getString("extra_folder", null)?.let(Uri::parse)
        set(v) = prefs.edit { putString("extra_folder", v?.toString()) }

    var subjectRules: String
        get() = prefs.getString("subject_rules", DEFAULT_RULES) ?: DEFAULT_RULES
        set(v) = prefs.edit { putString("subject_rules", v) }

    /** Moodle-Kurse als "kurzname\tlangname" je Zeile, für die automatische Fach-Erkennung. */
    var cachedCourses: List<Pair<String, String>>
        get() = (prefs.getString("courses", "") ?: "").lines().filter { it.contains('\t') }
            .map { it.substringBefore('\t') to it.substringAfter('\t') }
        set(v) = prefs.edit { putString("courses", v.joinToString("\n") { "${it.first}\t${it.second}" }) }

    var bySubject: Boolean
        get() = prefs.getBoolean("by_subject", true)
        set(v) = prefs.edit { putBoolean("by_subject", v) }
    var byCategory: Boolean
        get() = prefs.getBoolean("by_category", true)
        set(v) = prefs.edit { putBoolean("by_category", v) }
    var byMonth: Boolean
        get() = prefs.getBoolean("by_month", false)
        set(v) = prefs.edit { putBoolean("by_month", v) }
    var moveFiles: Boolean
        get() = prefs.getBoolean("move_files", false)
        set(v) = prefs.edit { putBoolean("move_files", v) }
    var recursive: Boolean
        get() = prefs.getBoolean("recursive", false)
        set(v) = prefs.edit { putBoolean("recursive", v) }

    companion object {
        const val DEFAULT_RULES = """# Fach = Stichwort, Stichwort, ...
# Steht ein Stichwort im Dateinamen, kommt die Datei in den Ordner des Fachs.
Mathe = mathe, mathematik, analysis, algebra
Deutsch = deutsch, erörterung, gedichtanalyse
Englisch = englisch, english
Physik = physik, mechanik
Informatik = informatik, info, programmierung"""
    }
}
