package org.easypaper.scan

import org.easypaper.diary.Diary
import org.easypaper.diary.EntryCategory
import org.easypaper.diary.EntryProblem
import org.easypaper.diary.check
import org.easypaper.diary.ownWidgetId
import org.easypaper.diary.selection
import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import org.easypaper.model.SideEffectCheck
import org.easypaper.report.factDigest
import org.easypaper.report.toCsv
import org.easypaper.report.toLocalizedText
import org.easypaper.report.toText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #53: three common side effects per active substance, asked as yes/no; facts only. */
class SideEffectsTest {
    private val db = MedicationDb()
    private val own = ownWidgetId(EntryCategory.MEDICINES)

    @Test
    fun knownPackagesGetThreeQuestionsAndOthersNone() {
        assertEquals(listOf("stomach_pain", "heartburn", "nausea"), db.sideEffectsFor("7680567200025").map { it.key }) // ibuprofen
        assertEquals(emptyList(), db.sideEffectsFor("7680623550019")) // paracetamol: no common side effects listed
        assertEquals(emptyList(), db.sideEffectsFor(null)) // typed in by hand
        assertTrue(COMMON_SIDE_EFFECTS.values.all { it.size == 3 })
    }

    @Test
    fun answersAreCheckedAndReachTheDoctorAsFacts() {
        val ok = SideEffectCheck("DOLO SPEDIFEN", "7680567200025", mapOf("stomach_pain" to "yes", "nausea" to "no"))
        assertEquals(emptyList(), Diary().check(Entry("s1", own, 1, ok)))
        assertEquals(listOf(EntryProblem.UNKNOWN_QUESTION), Diary().check(Entry("s2", own, 1, ok.copy(answers = mapOf("cure" to "yes")))))
        assertEquals(listOf(EntryProblem.BAD_ANSWER), Diary().check(Entry("s3", own, 1, ok.copy(answers = mapOf("nausea" to "maybe")))))

        val record: PatientRecord = Diary(listOf(Entry("s1", own, 0, ok))).selection(setOf(EntryCategory.MEDICINES), "1970-01-01", "1970-01-01")
        val digest = record.factDigest()
        assertTrue("Side effect asked (DOLO SPEDIFEN), Stomach pain: 1970-01-01 yes [s1]" in digest.toText())
        assertTrue("Bauchschmerzen: 1970-01-01 ja [s1]" in digest.toLocalizedText("de"))
        assertTrue(record.toCsv().lines().any { it.contains("side_effect") && it.contains("stomach_pain") && it.contains("yes") })
    }
}
