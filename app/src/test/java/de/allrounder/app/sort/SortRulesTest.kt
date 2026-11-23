package de.allrounder.app.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SortRulesTest {

    private val subjects = SortRules.parseSubjects(
        """
        # Kommentar
        Mathe = mathe, analysis, ma1
        Mathe 2 = mathe 2, lineare algebra
        Deutsch = deutsch, erörterung
        """.trimIndent()
    )

    @Test fun categories() {
        assertEquals(Category.NOTES, Category.of("Vorlesung 3.sdocx"))
        assertEquals(Category.PDF, Category.of("Blatt.PDF"))
        assertEquals(Category.PRESENTATIONS, Category.of("folien.pptx"))
        assertEquals(Category.IMAGES, Category.of("tafel", "image/jpeg"))
        assertEquals(Category.OTHER, Category.of("unbekannt.xyz"))
    }

    @Test fun parsesRules() {
        assertEquals(3, subjects.size)
        assertEquals(listOf("Mathe", "mathe", "analysis", "ma1"), subjects[0].keywords)
    }

    @Test fun matchesSubjects() {
        assertEquals("Mathe", SortRules.matchSubject("Analysis_Uebung_04.pdf", subjects)?.name)
        assertEquals("Mathe", SortRules.matchSubject("MA1-Blatt3.pdf", subjects)?.name)
        assertEquals("Mathe 2", SortRules.matchSubject("Mathe 2 - Klausur.pdf", subjects)?.name)
        assertEquals("Deutsch", SortRules.matchSubject("Eroerterung Entwurf.docx", subjects)?.name)
        // Stichwort nur am Wortanfang: "thema" enthält "ma", soll aber nicht passen
        assertNull(SortRules.matchSubject("Thema1.pdf", subjects))
    }

    @Test fun buildsTargetPath() {
        val time = LocalDate.of(2026, 11, 3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val all = SortOptions(bySubject = true, byCategory = true, byMonth = true)
        assertEquals(
            listOf("Mathe", "Notizen", "2026-11"),
            SortRules.targetPath("Analysis Mitschrift.sdocx", null, time, subjects, all, ZoneOffset.UTC),
        )
        assertEquals(
            listOf("Allgemein", "Bilder"),
            SortRules.targetPath("IMG_0001.jpg", null, time, subjects, SortOptions(), ZoneOffset.UTC),
        )
        assertEquals(
            listOf("PDF"),
            SortRules.targetPath("x.pdf", null, time, subjects, SortOptions(bySubject = false), ZoneOffset.UTC),
        )
    }

    @Test fun sanitizesNames() {
        assertEquals("Kurs_ A_B", SortRules.sanitize("Kurs: A/B"))
        assertEquals("_", SortRules.sanitize("..."))
    }

    @Test fun subjectsFromMoodleCourses() {
        val s = SortRules.subjectsFromCourses(listOf("INF1" to "Informatik 1: Grundlagen"))
        assertEquals("Informatik 1_ Grundlagen", s.single().name)
        assertEquals("Informatik 1_ Grundlagen", SortRules.matchSubject("INF1_Übung2.pdf", s)?.name)
    }
}
