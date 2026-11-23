package de.allrounder.app.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import de.allrounder.app.sort.SortRules

/** Hilfsfunktionen für das Storage Access Framework (vom Nutzer gewählte Ordner). */
object Storage {

    fun persist(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun tree(context: Context, uri: Uri?): DocumentFile? =
        uri?.let { DocumentFile.fromTreeUri(context, it) }?.takeIf { it.canRead() }

    /** Legt den Ordnerpfad unterhalb von [root] an (falls nötig) und gibt den letzten Ordner zurück. */
    fun ensureDir(root: DocumentFile, path: List<String>): DocumentFile {
        var dir = root
        for (part in path) {
            val name = SortRules.sanitize(part)
            dir = dir.findFile(name)?.takeIf { it.isDirectory }
                ?: dir.createDirectory(name)
                ?: error("Ordner '$name' konnte nicht angelegt werden")
        }
        return dir
    }

    /** Findet einen freien Dateinamen: "datei.pdf" → "datei (2).pdf" usw. */
    fun freeName(dir: DocumentFile, fileName: String): String {
        if (dir.findFile(fileName) == null) return fileName
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "").let { if (it.isEmpty() || it == fileName) "" else ".$it" }
        var i = 2
        while (dir.findFile("$base ($i)$ext") != null) i++
        return "$base ($i)$ext"
    }

    /**
     * Kopiert [source] nach [targetDir]. Existiert dort schon eine gleich große Datei
     * mit gleichem Namen, wird nichts kopiert (Rückgabe: null).
     */
    fun copy(context: Context, source: DocumentFile, targetDir: DocumentFile): DocumentFile? {
        val name = source.name ?: "unbenannt"
        val existing = targetDir.findFile(name)
        if (existing != null && existing.length() == source.length()) return null
        val target = targetDir.createFile(source.type ?: "application/octet-stream", freeName(targetDir, name))
            ?: error("Datei '$name' konnte nicht angelegt werden")
        val cr = context.contentResolver
        cr.openInputStream(source.uri).use { input ->
            cr.openOutputStream(target.uri).use { output ->
                requireNotNull(input) { "Quelle nicht lesbar: $name" }
                requireNotNull(output) { "Ziel nicht schreibbar: $name" }
                input.copyTo(output)
            }
        }
        // Manche Provider hängen bei unbekanntem MIME-Typ eine Endung an – Namen korrigieren
        if (target.name != name && existing == null) runCatching { target.renameTo(name) }
        return target
    }

    /** Kopiert eine per "Teilen" empfangene URI in [targetDir]. */
    fun importUri(context: Context, uri: Uri, targetDir: DocumentFile): String {
        val cr = context.contentResolver
        val name = displayName(context, uri) ?: "import_${System.currentTimeMillis()}"
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val target = targetDir.createFile(mime, freeName(targetDir, SortRules.sanitize(name)))
            ?: error("Datei '$name' konnte nicht angelegt werden")
        cr.openInputStream(uri).use { input ->
            cr.openOutputStream(target.uri).use { output ->
                requireNotNull(input).copyTo(requireNotNull(output))
            }
        }
        return target.name ?: name
    }

    fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    /** Alle Dateien in [dir], optional rekursiv. */
    fun listFiles(dir: DocumentFile, recursive: Boolean): List<DocumentFile> =
        dir.listFiles().flatMap { f ->
            when {
                f.isFile -> listOf(f)
                recursive && f.isDirectory -> listFiles(f, true)
                else -> emptyList()
            }
        }
}
