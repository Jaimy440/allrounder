package de.allrounder.app.moodle

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodleParserTest {

    @Test fun extractsOnlyFiles() {
        val json = JSONArray(
            """
            [
              {"name": "Allgemeines", "modules": [
                {"name": "Skript", "modname": "resource", "contents": [
                  {"type": "file", "filename": "skript.pdf", "fileurl": "https://m.example/pluginfile.php/1/skript.pdf?forcedownload=1",
                   "filesize": 1234, "timemodified": 1700000000, "mimetype": "application/pdf"}
                ]},
                {"name": "Link", "modname": "url", "contents": [
                  {"type": "url", "filename": "Link", "fileurl": "https://example.org"}
                ]},
                {"name": "Forum", "modname": "forum"}
              ]},
              {"name": "", "modules": []}
            ]
            """.trimIndent()
        )
        val files = MoodleClient.parseCourseContents(MoodleCourse(7, "INF1", "Informatik 1"), json)
        assertEquals(1, files.size)
        with(files.single()) {
            assertEquals("skript.pdf", fileName)
            assertEquals("Allgemeines", section)
            assertEquals("Informatik 1", courseName)
            assertEquals(1234L, fileSize)
        }
    }
}
