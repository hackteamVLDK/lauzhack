package org.easypaper.ui.doctor

import org.easypaper.ai.DocumentFact
import org.easypaper.visit.FieldState
import org.easypaper.visit.FormFieldKey
import org.easypaper.visit.demoVisitReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentsModelTest {
    private val wording = ResourceFormWording(
        bloodPressure = "%1\$s: %2\$s/%3\$s mmHg, readings: %4\$d", pulse = ", pulse %1\$s/min",
        fluid = "%1\$d ml per day on average, days: %2\$d", notConfirmed = "not confirmed by the patient", none = "No entries",
        rangeOneMonth = "%1\$d–%2\$d %3\$s", rangeTwoMonths = "%1\$d %2\$s – %3\$d %4\$s",
        monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    )
    private val ramipril = DocumentFact("medication", "Ramipril 5 mg once daily", "Ramipril 5 mg 1-0-0", "letter-1", verified = true)
    private val allergy = DocumentFact("allergy", "Penicillin", "Allergie: Penicillin", "letter-1", verified = true)

    @Test
    fun factsGoToTheirFieldAsDraftsAndOnlyOnce() {
        val state = VisitState(demoVisitReport(), wording)
        state.confirm(FormFieldKey.MEDICATIONS, now = 1)
        val historyBefore = state.form.field(FormFieldKey.HISTORY).value

        state.addFact(ramipril, "From document")
        state.addFact(allergy, "From document")
        state.addFact(allergy, "From document") // a second tap adds nothing

        val meds = state.form.field(FormFieldKey.MEDICATIONS)
        assertTrue(meds.value.lines().last() == "Ramipril 5 mg once daily")
        assertEquals(FieldState.DRAFT, meds.state, "a confirmed field becomes a draft again: the doctor confirms the new text")
        assertNull(state.confirmedAt[FormFieldKey.MEDICATIONS])
        val history = state.form.field(FormFieldKey.HISTORY).value
        assertEquals(historyBefore + "\nFrom document: Penicillin («Allergie: Penicillin»)", history)
        assertTrue(state.hasFact(allergy, "From document"))
        assertNull(state.save(now = 2), "nothing is saved while fields are undecided")
    }

    @Test
    fun linesAreAddedOnceAndEmptyFieldsTakeTheLineAlone() {
        assertEquals("a", withLine("", "a"))
        assertEquals("a\nb", withLine("a\n", "b"))
        assertEquals("a\nb", withLine("a\nb", " b "))
        assertFalse(withLine("ab", "a") == "ab")
    }
}
