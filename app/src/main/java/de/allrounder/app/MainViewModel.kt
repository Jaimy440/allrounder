package de.allrounder.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.allrounder.app.moodle.MoodleClient
import de.allrounder.app.moodle.MoodleCourse
import de.allrounder.app.moodle.MoodleFile
import de.allrounder.app.sort.SortOptions
import de.allrounder.app.sort.SortRules
import de.allrounder.app.storage.Settings
import de.allrounder.app.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CourseItem(val course: MoodleCourse, val selected: Boolean)

data class PlannedMove(val source: DocumentFile, val name: String, val from: String, val path: List<String>)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    val settings = Settings(app)

    // ---- allgemeiner Zustand ----
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf<String?>(null); private set
    val log = mutableStateListOf<String>()

    // ---- Ordner ----
    var targetName by mutableStateOf(folderName(settings.targetFolder)); private set
    var notesName by mutableStateOf(folderName(settings.notesFolder)); private set
    var extraName by mutableStateOf(folderName(settings.extraFolder)); private set

    // ---- Moodle ----
    var moodleUrl by mutableStateOf(settings.moodleUrl)
    var moodleUser by mutableStateOf(settings.moodleUser)
    var moodlePassword by mutableStateOf("")
    var manualToken by mutableStateOf("")
    var siteName by mutableStateOf<String?>(null); private set
    val courses = mutableStateListOf<CourseItem>()
    var loggedIn by mutableStateOf(settings.moodleToken.isNotBlank()); private set

    // ---- Sortieren ----
    var subjectRules by mutableStateOf(settings.subjectRules)
    var bySubject by mutableStateOf(settings.bySubject)
    var byCategory by mutableStateOf(settings.byCategory)
    var byMonth by mutableStateOf(settings.byMonth)
    var moveFiles by mutableStateOf(settings.moveFiles)
    var recursive by mutableStateOf(settings.recursive)
    val plan = mutableStateListOf<PlannedMove>()

    private val pendingShares = mutableListOf<Uri>()

    init {
        if (loggedIn) loadCourses()
    }

    // ================= Ordner =================

    enum class FolderKind { TARGET, NOTES, EXTRA }

    fun setFolder(kind: FolderKind, uri: Uri) {
        runCatching { Storage.persist(ctx, uri) }
        when (kind) {
            FolderKind.TARGET -> { settings.targetFolder = uri; targetName = folderName(uri) }
            FolderKind.NOTES -> { settings.notesFolder = uri; notesName = folderName(uri) }
            FolderKind.EXTRA -> { settings.extraFolder = uri; extraName = folderName(uri) }
        }
        if (kind == FolderKind.TARGET && pendingShares.isNotEmpty()) {
            importShared(pendingShares.toList()); pendingShares.clear()
        }
    }

    private fun folderName(uri: Uri?): String? = Storage.tree(ctx, uri)?.name

    private fun targetRoot(): DocumentFile? = Storage.tree(ctx, settings.targetFolder).also {
        if (it == null) say("Bitte zuerst unter \"Ordner\" einen Zielordner wählen.")
    }

    // ================= Moodle =================

    private fun client() = MoodleClient(moodleUrl, settings.moodleToken.ifBlank { null })

    fun moodleLogin() = work("Anmelden …") {
        settings.moodleUrl = moodleUrl.trim()
        settings.moodleUser = moodleUser.trim()
        val client = MoodleClient(moodleUrl)
        val token = manualToken.trim().ifBlank { client.login(moodleUser.trim(), moodlePassword) }
        client.token = token
        val info = client.siteInfo()  // prüft gleichzeitig, ob das Token gültig ist
        settings.moodleToken = token
        loggedIn = true
        moodlePassword = ""
        manualToken = ""
        say("Angemeldet bei ${info.optString("sitename")} als ${info.optString("fullname")}.")
        fetchCourses(client, info.getLong("userid"), info.optString("sitename"))
    }

    fun moodleLogout() {
        settings.moodleToken = ""
        loggedIn = false
        siteName = null
        courses.clear()
        say("Abgemeldet.")
    }

    fun loadCourses() = work("Kurse laden …") {
        val client = client()
        val info = client.siteInfo()
        fetchCourses(client, info.getLong("userid"), info.optString("sitename"))
    }

    private suspend fun fetchCourses(client: MoodleClient, userId: Long, site: String) {
        val list = client.courses(userId)
        siteName = site
        courses.clear()
        courses.addAll(list.map { CourseItem(it, true) })
        settings.cachedCourses = list.map { it.shortName to it.fullName }
        say("${list.size} Kurse gefunden.")
    }

    fun toggleCourse(index: Int) {
        courses[index] = courses[index].let { it.copy(selected = !it.selected) }
    }

    /** Lädt alle Dateien der ausgewählten Kurse nach <Ziel>/<Kurs>/Moodle/<Abschnitt>/. */
    fun syncMoodle() = work("Moodle-Dateien laden …") {
        val root = targetRoot() ?: return@work
        val client = client()
        var loaded = 0
        var skipped = 0
        for (item in courses.filter { it.selected }) {
            progress = "Kurs: ${item.course.fullName}"
            val files = client.courseFiles(item.course)
            for ((i, f) in files.withIndex()) {
                progress = "${item.course.shortName}: ${i + 1}/${files.size} – ${f.fileName}"
                if (downloadIfNew(client, root, f)) loaded++ else skipped++
            }
        }
        say("Moodle: $loaded neue Dateien geladen, $skipped schon vorhanden.")
    }

    private suspend fun downloadIfNew(client: MoodleClient, root: DocumentFile, f: MoodleFile): Boolean =
        withContext(Dispatchers.IO) {
            val dir = Storage.ensureDir(root, listOf(f.courseName, "Moodle", f.section))
            val name = SortRules.sanitize(f.fileName)
            val existing = dir.findFile(name)
            if (existing != null && existing.length() == f.fileSize) return@withContext false
            existing?.delete() // geänderte Datei → neu laden
            val target = dir.createFile(f.mimeType ?: "application/octet-stream", name)
                ?: error("Konnte $name nicht anlegen")
            try {
                ctx.contentResolver.openOutputStream(target.uri).use { out ->
                    client.download(f, requireNotNull(out))
                }
            } catch (e: Exception) {
                target.delete(); throw e
            }
            true
        }

    // ================= Samsung Notes / Teilen =================

    /** Dateien, die per "Teilen" (z. B. aus Samsung Notes) kamen, in <Ziel>/Eingang ablegen. */
    fun importShared(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (Storage.tree(ctx, settings.targetFolder) == null) {
            pendingShares += uris
            say("${uris.size} Datei(en) empfangen. Bitte Zielordner wählen, dann werden sie importiert.")
            return
        }
        work("Importieren …") {
            val root = targetRoot() ?: return@work
            val inbox = withContext(Dispatchers.IO) { Storage.ensureDir(root, listOf(INBOX)) }
            for (uri in uris) {
                val name = withContext(Dispatchers.IO) { Storage.importUri(ctx, uri, inbox) }
                say("Importiert: $name")
            }
            say("Tipp: Unter \"Sortieren\" jetzt \"Vorschau\" tippen, um den Eingang zu sortieren.")
        }
    }

    fun openSamsungNotes(): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(SAMSUNG_NOTES) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }

    // ================= Sortieren =================

    fun saveSortSettings() {
        settings.subjectRules = subjectRules
        settings.bySubject = bySubject
        settings.byCategory = byCategory
        settings.byMonth = byMonth
        settings.moveFiles = moveFiles
        settings.recursive = recursive
    }

    /** Durchsucht Eingang, Samsung-Notes-Ordner und Zusatzordner und berechnet die Zielpfade. */
    fun buildPlan() = work("Dateien suchen …") {
        saveSortSettings()
        val root = targetRoot() ?: return@work
        val subjects = SortRules.parseSubjects(subjectRules) +
            SortRules.subjectsFromCourses(settings.cachedCourses)
        val options = SortOptions(bySubject, byCategory, byMonth)

        val sources = listOfNotNull(
            root.findFile(INBOX)?.takeIf { it.isDirectory }?.let { "Eingang" to it },
            Storage.tree(ctx, settings.notesFolder)?.let { "Samsung Notes" to it },
            Storage.tree(ctx, settings.extraFolder)?.let { (it.name ?: "Zusatzordner") to it },
        )
        val result = withContext(Dispatchers.IO) {
            sources.flatMap { (label, dir) ->
                Storage.listFiles(dir, recursive).map { f ->
                    val name = f.name ?: "unbenannt"
                    PlannedMove(f, name, label, SortRules.targetPath(name, f.type, f.lastModified(), subjects, options))
                }
            }
        }
        plan.clear()
        plan.addAll(result)
        say(if (result.isEmpty()) "Keine Dateien zum Sortieren gefunden." else "${result.size} Dateien gefunden – bitte Vorschau prüfen.")
    }

    fun removeFromPlan(item: PlannedMove) { plan.remove(item) }

    fun executePlan() = work("Sortieren …") {
        val root = targetRoot() ?: return@work
        val items = plan.toList()
        var done = 0
        var dup = 0
        for ((i, p) in items.withIndex()) {
            progress = "${i + 1}/${items.size}: ${p.name}"
            val copied = withContext(Dispatchers.IO) {
                val dir = Storage.ensureDir(root, p.path)
                Storage.copy(ctx, p.source, dir).also { if (moveFiles) p.source.delete() }
            }
            if (copied == null) dup++ else done++
        }
        plan.clear()
        val verb = if (moveFiles) "verschoben" else "kopiert"
        say("Fertig: $done Dateien $verb" + if (dup > 0) ", $dup waren schon vorhanden." else ".")
    }

    // ================= Hilfen =================

    private fun work(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        progress = label
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                say("Fehler: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
                progress = null
            }
        }
    }

    private fun say(msg: String) {
        val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date())
        log.add(0, "$time  $msg")
        if (log.size > 100) log.removeAt(log.lastIndex)
    }

    companion object {
        const val INBOX = "Eingang"
        const val SAMSUNG_NOTES = "com.samsung.android.app.notes"
    }
}
