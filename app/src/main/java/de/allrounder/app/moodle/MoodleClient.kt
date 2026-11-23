package de.allrounder.app.moodle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MoodleCourse(val id: Long, val shortName: String, val fullName: String)

data class MoodleFile(
    val courseId: Long,
    val courseName: String,
    val section: String,
    val module: String,
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val timeModified: Long, // Sekunden seit 1970
    val mimeType: String?,
)

class MoodleException(message: String) : IOException(message)

/**
 * Minimaler Client für die Moodle Web-Service-API
 * (dieselbe Schnittstelle, die auch die offizielle Moodle-App benutzt).
 */
class MoodleClient(baseUrl: String, var token: String? = null) {

    val baseUrl: String = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
    }

    /** Holt ein Token mit Benutzername/Passwort. Funktioniert nicht bei reinem SSO-Login. */
    suspend fun login(username: String, password: String): String {
        val json = JSONObject(
            post(
                "$baseUrl/login/token.php",
                mapOf("username" to username, "password" to password, "service" to "moodle_mobile_app"),
            )
        )
        if (json.has("error")) throw MoodleException(json.optString("error"))
        return json.getString("token").also { token = it }
    }

    suspend fun siteInfo(): JSONObject = JSONObject(call("core_webservice_get_site_info"))

    suspend fun courses(userId: Long): List<MoodleCourse> {
        val arr = JSONArray(call("core_enrol_get_users_courses", mapOf("userid" to userId.toString())))
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            MoodleCourse(c.getLong("id"), c.optString("shortname"), c.optString("fullname"))
        }
    }

    suspend fun courseFiles(course: MoodleCourse): List<MoodleFile> {
        val sections = JSONArray(call("core_course_get_contents", mapOf("courseid" to course.id.toString())))
        return parseCourseContents(course, sections)
    }

    /** Lädt eine Datei herunter und schreibt sie in [out]. */
    suspend fun download(file: MoodleFile, out: OutputStream) = withContext(Dispatchers.IO) {
        val sep = if (file.fileUrl.contains('?')) '&' else '?'
        val url = URL(file.fileUrl + sep + "token=" + enc(requireToken()))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        try {
            if (conn.responseCode !in 200..299) throw MoodleException("Download fehlgeschlagen (HTTP ${conn.responseCode}): ${file.fileName}")
            conn.inputStream.use { it.copyTo(out) }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun call(function: String, params: Map<String, String> = emptyMap()): String {
        val body = post(
            "$baseUrl/webservice/rest/server.php",
            params + mapOf(
                "wstoken" to requireToken(),
                "wsfunction" to function,
                "moodlewsrestformat" to "json",
            ),
        )
        // Moodle meldet Fehler mit HTTP 200 und einem "exception"-Objekt
        if (body.trimStart().startsWith("{")) {
            val obj = JSONObject(body)
            if (obj.has("exception")) throw MoodleException(obj.optString("message", obj.optString("errorcode")))
        }
        return body
    }

    private fun requireToken(): String = token ?: throw MoodleException("Nicht angemeldet")

    private suspend fun post(url: String, params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
        try {
            val form = params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
            conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (conn.responseCode !in 200..299) throw MoodleException("HTTP ${conn.responseCode} von $url")
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        /** Wandelt die Antwort von core_course_get_contents in eine Dateiliste um. */
        fun parseCourseContents(course: MoodleCourse, sections: JSONArray): List<MoodleFile> {
            val result = mutableListOf<MoodleFile>()
            for (s in 0 until sections.length()) {
                val section = sections.getJSONObject(s)
                val sectionName = section.optString("name").ifBlank { "Abschnitt ${s + 1}" }
                val modules = section.optJSONArray("modules") ?: continue
                for (m in 0 until modules.length()) {
                    val module = modules.getJSONObject(m)
                    val contents = module.optJSONArray("contents") ?: continue
                    for (c in 0 until contents.length()) {
                        val content = contents.getJSONObject(c)
                        if (content.optString("type") != "file") continue
                        val url = content.optString("fileurl")
                        if (url.isBlank()) continue
                        result += MoodleFile(
                            courseId = course.id,
                            courseName = course.fullName.ifBlank { course.shortName },
                            section = sectionName,
                            module = module.optString("name"),
                            fileName = content.optString("filename"),
                            fileUrl = url,
                            fileSize = content.optLong("filesize"),
                            timeModified = content.optLong("timemodified"),
                            mimeType = content.optString("mimetype").ifBlank { null },
                        )
                    }
                }
            }
            return result
        }
    }
}
