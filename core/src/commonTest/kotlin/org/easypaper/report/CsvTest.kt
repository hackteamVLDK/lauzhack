package org.easypaper.report

import org.easypaper.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvTest {
    private val record = PatientRecord(
        CareRequest(
            id = "r", createdAt = 0, doctorName = "Dr", complaint = "c", startDate = "2026-11-17", days = 1, locale = "en",
            widgets = listOf(DiaryWidget("d", "Diary")),
        ),
        listOf(
            Entry("e2", "w", 2_000, Water(250)),
            Entry("e1", "bp", 1_000, BloodPressure(130, 85, 72)),
            Entry("e3", "q", 3_000, Answers(mapOf("q1" to "yes", "q2" to "7"))),
            Entry("e4", "m", 4_000, Medication("Dafalgan, 1 g", "7680000000000", "1 g", MedicationSource.SCAN, false)),
            Entry("e5", "f", 5_000, Food("soup \"homemade\"\nand bread", "p1")),
            Entry("e6", "d", 6_000, DiaryNote("=HYPERLINK(\"http://x\")")),
            Entry("e7", "dq", 7_000, DoctorQuestion("-5 kg since summer?")),
        ),
    )

    /** Minimal RFC 4180 reader, only for the tests. */
    private fun parse(csv: String, sep: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when {
                quoted && c == '"' && csv.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == sep -> { row += cell.toString(); cell.clear() }
                !quoted && c == '\r' && csv.getOrNull(i + 1) == '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf(); i++ }
                else -> cell.append(c)
            }
            i++
        }
        return rows
    }

    private fun rowsOf(csv: String, sep: Char = ',') = parse(csv, sep).let { all -> all.drop(1).map { all[0].zip(it).toMap() } }

    @Test
    fun everyEntryTypeIsExportedInTimeOrder() {
        val csv = record.toCsv()
        assertTrue(csv.endsWith("\r\n"))
        val rows = rowsOf(csv)
        assertEquals(CSV_COLUMNS, parse(csv)[0])
        assertEquals(listOf("e1", "e2", "e3", "e3", "e4", "e5", "e6", "e7"), rows.map { it["entry_id"] })
        assertEquals("1970-01-01T00:00:01Z", rows[0]["time_utc"])
        assertEquals(listOf("130", "85", "72"), listOf("systolic", "diastolic", "pulse").map { rows[0][it] })
        assertEquals("250", rows[1]["water_ml"])
        assertEquals(listOf("q1" to "yes", "q2" to "7"), rows.filter { it["type"] == "answers" }.map { it["question_id"] to it["answer"] })
        assertEquals("Dafalgan, 1 g", rows[4]["medication"])
        assertEquals(listOf("7680000000000", "1 g", "scan", "false"), listOf("gtin", "dose", "med_source", "confirmed").map { rows[4][it] })
        assertEquals("soup \"homemade\"\nand bread", rows[5]["text"])
        assertEquals("p1", rows[5]["photo_ref"])
        assertTrue(rows.all { it.size == CSV_COLUMNS.size })
    }

    @Test
    fun quotingFollowsRfc4180() {
        val csv = record.toCsv()
        assertTrue("\"Dafalgan, 1 g\"" in csv)
        assertTrue("\"soup \"\"homemade\"\"\nand bread\"" in csv)
    }

    @Test
    fun formulasAreNotExecuted() {
        val rows = rowsOf(record.toCsv())
        assertEquals("'=HYPERLINK(\"http://x\")", rows[6]["text"])
        assertEquals("'-5 kg since summer?", rows[7]["text"])
        assertEquals("130", rows[0]["systolic"]) // plain numbers untouched
    }

    @Test
    fun semicolonForExcel() {
        val csv = record.toCsv(';')
        assertTrue(csv.startsWith("entry_id;time_utc;"))
        assertTrue("Dafalgan, 1 g;" in csv) // comma no longer needs quotes
        assertEquals("Dafalgan, 1 g", rowsOf(csv, ';')[4]["medication"])
    }
}
