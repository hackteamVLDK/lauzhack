package org.easypaper.ui.patient.v2

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Task 59: the date of birth is typed the way people write it. */
class BirthDateTest {
    private val today = LocalDate(2026, 11, 22)
    private val birth = LocalDate(1958, 3, 14)

    @Test
    fun theCommonWaysOfWritingADateAreUnderstood() {
        for (text in listOf("14.03.1958", "14.3.1958", "14/03/1958", "1958-03-14", " 14. 03. 1958 ")) {
            assertEquals(birth, parseBirth(text), text)
        }
    }

    @Test
    fun impossibleOrAmbiguousDatesAreNotGuessed() {
        for (text in listOf("31.02.1958", "14.03.58", "1958/03", "March 14", "14-03-1958x", "")) {
            assertNull(parseBirth(text), text)
        }
    }

    @Test
    fun emptyIsFineButTheFutureAndVeryOldDatesAreNot() {
        assertTrue(validBirth(null, today))
        assertTrue(validBirth(" ", today))
        assertTrue(validBirth("22.11.2026", today))
        assertFalse(validBirth("23.11.2026", today))
        assertFalse(validBirth("01.01.1850", today))
        assertFalse(validBirth("1958-13-01", today))
    }
}
