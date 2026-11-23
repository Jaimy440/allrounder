package de.allrounder.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.allrounder.app.MainViewModel
import de.allrounder.app.MainViewModel.FolderKind

private enum class Tab(val title: String, val icon: ImageVector) {
    MOODLE("Moodle", Icons.Default.Home),
    NOTES("Samsung Notes", Icons.Default.Edit),
    SORT("Sortieren", Icons.Default.Build),
    FOLDERS("Ordner", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllrounderApp(vm: MainViewModel) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        var tab by rememberSaveable { mutableIntStateOf(if (vm.targetName == null) Tab.FOLDERS.ordinal else 0) }
        Scaffold(
            topBar = { TopAppBar(title = { Text("Allrounder – ${Tab.entries[tab].title}") }) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t.ordinal,
                            onClick = { tab = t.ordinal },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (vm.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.progress?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Column(Modifier.weight(1f)) {
                    when (Tab.entries[tab]) {
                        Tab.MOODLE -> MoodleScreen(vm)
                        Tab.NOTES -> NotesScreen(vm)
                        Tab.SORT -> SortScreen(vm)
                        Tab.FOLDERS -> FoldersScreen(vm)
                    }
                }
                vm.log.firstOrNull()?.let {
                    HorizontalDivider()
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Moodle

@Composable
private fun MoodleScreen(vm: MainViewModel) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!vm.loggedIn) {
            item {
                Text("Mit Moodle verbinden", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(vm.moodleUrl, { vm.moodleUrl = it }, Modifier.fillMaxWidth(), label = { Text("Moodle-Adresse (z. B. moodle.meine-schule.de)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(vm.moodleUser, { vm.moodleUser = it }, Modifier.fillMaxWidth(), label = { Text("Benutzername") }, singleLine = true)
                OutlinedTextField(vm.moodlePassword, { vm.moodlePassword = it }, Modifier.fillMaxWidth(), label = { Text("Passwort") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Text(
                    "Login über Schul-/Uni-Konto (SSO)? Dann stattdessen das Token eintragen: " +
                        "Moodle im Browser → Profil → Einstellungen → Sicherheitsschlüssel → \"Moodle mobile web service\".",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(vm.manualToken, { vm.manualToken = it }, Modifier.fillMaxWidth(), label = { Text("Token (optional)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::moodleLogin, enabled = !vm.busy && vm.moodleUrl.isNotBlank()) { Text("Anmelden") }
            }
        } else {
            item {
                Text(vm.siteName ?: vm.moodleUrl, style = MaterialTheme.typography.titleMedium)
                Text("Dateien werden gespeichert unter: <Zielordner>/<Kurs>/Moodle/<Abschnitt>/", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::syncMoodle, enabled = !vm.busy && vm.courses.any { it.selected }) { Text("Dateien laden") }
                    OutlinedButton(onClick = vm::loadCourses, enabled = !vm.busy) { Text("Aktualisieren") }
                    OutlinedButton(onClick = vm::moodleLogout) { Text("Abmelden") }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
            itemsIndexed(vm.courses) { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(c.selected, { vm.toggleCourse(i) })
                    Column {
                        Text(c.course.fullName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(c.course.shortName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Samsung Notes

@Composable
private fun NotesScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val pickNotes = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.setFolder(FolderKind.NOTES, it) }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weg 1: Teilen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "In Samsung Notes Notiz(en) auswählen → Teilen → PDF oder Samsung-Notes-Datei → \"In Allrounder sortieren\". " +
                        "Die Dateien landen im Ordner \"Eingang\" und werden beim Sortieren automatisch einem Fach zugeordnet.",
                )
                OutlinedButton(onClick = {
                    if (!vm.openSamsungNotes()) Toast.makeText(context, "Samsung Notes ist nicht installiert", Toast.LENGTH_SHORT).show()
                }) { Text("Samsung Notes öffnen") }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weg 2: Export-Ordner", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Samsung Notes speichert exportierte Notizen (\"Als Datei speichern\") meist unter " +
                        "Interner Speicher/Documents/Samsung Notes. Diesen Ordner hier einmal wählen – " +
                        "er wird dann bei jedem Sortiervorgang mit durchsucht.",
                )
                Text("Gewählt: ${vm.notesName ?: "–"}", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { pickNotes.launch(null) }) { Text("Export-Ordner wählen") }
            }
        }
        Text(
            "Hinweis: Samsung bietet keine Schnittstelle, über die andere Apps Notizen direkt auslesen dürfen. " +
                "Deshalb funktioniert der Zugriff nur über Teilen oder Export.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------------------------------------------------------- Sortieren

@Composable
private fun SortScreen(vm: MainViewModel) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text("Durchsucht: Eingang, Samsung-Notes-Ordner${vm.extraName?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            Toggle("Nach Fach / Kurs", vm.bySubject) { vm.bySubject = it }
            Toggle("Nach Dateityp (PDF, Bilder, Notizen …)", vm.byCategory) { vm.byCategory = it }
            Toggle("Nach Monat (2026-11 …)", vm.byMonth) { vm.byMonth = it }
            Toggle("Unterordner mit durchsuchen", vm.recursive) { vm.recursive = it }
            Toggle("Verschieben statt kopieren", vm.moveFiles) { vm.moveFiles = it }
            if (vm.bySubject) {
                OutlinedTextField(
                    vm.subjectRules, { vm.subjectRules = it },
                    Modifier.fillMaxWidth().height(180.dp),
                    label = { Text("Fächer & Stichwörter") },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Text("Moodle-Kurse werden zusätzlich automatisch als Fächer erkannt.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::buildPlan, enabled = !vm.busy) { Text("Vorschau") }
                Button(onClick = vm::executePlan, enabled = !vm.busy && vm.plan.isNotEmpty()) {
                    Text("${vm.plan.size} Dateien sortieren")
                }
            }
            if (vm.plan.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        items(vm.plan, key = { it.source.uri.toString() }) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.from} → ${p.path.joinToString(" / ").ifEmpty { "(Hauptordner)" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { vm.removeFromPlan(p) }) { Icon(Icons.Default.Close, contentDescription = "Auslassen") }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

// ---------------------------------------------------------------- Ordner

@Composable
private fun FoldersScreen(vm: MainViewModel) {
    val pickTarget = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.setFolder(FolderKind.TARGET, it) } }
    val pickNotes = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.setFolder(FolderKind.NOTES, it) } }
    val pickExtra = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.setFolder(FolderKind.EXTRA, it) } }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FolderRow("Zielordner (hier wird alles einsortiert)", vm.targetName) { pickTarget.launch(null) } }
        item { FolderRow("Samsung-Notes-Export", vm.notesName) { pickNotes.launch(null) } }
        item { FolderRow("Zusätzlicher Ordner (z. B. Download)", vm.extraName) { pickExtra.launch(null) } }
        item {
            HorizontalDivider()
            Text("Protokoll", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
        items(vm.log) { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FolderRow(label: String, current: String?, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(current ?: "nicht gewählt", style = MaterialTheme.typography.bodySmall, color = if (current == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPick) { Text(if (current == null) "Wählen" else "Ändern") }
    }
}
