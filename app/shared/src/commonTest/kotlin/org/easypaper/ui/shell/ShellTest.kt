package org.easypaper.ui.shell

import kotlinx.datetime.LocalDate
import org.easypaper.demo.Demo
import org.easypaper.ui.components.category
import org.easypaper.ui.format.RequestDay
import org.easypaper.ui.format.formatDateRange
import org.easypaper.ui.format.formatLongDate
import org.easypaper.ui.format.lastDay
import org.easypaper.ui.format.requestDay
import org.easypaper.ui.theme.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellTest {
    private sealed interface Screen {
        data object Root : Screen
        data object Detail : Screen
        data object More : Screen
        data object Menu : Screen
    }

    @Test
    fun backClosesTheSheetFirstThenPopsAndNeverLeavesTheRoot() {
        val nav = Navigator<Screen>(Screen.Root)
        assertFalse(nav.canGoBack)
        assertFalse(nav.back())

        nav.push(Screen.Detail)
        nav.push(Screen.More)
        nav.presentSheet(Screen.Menu)
        assertTrue(nav.canGoBack)

        assertTrue(nav.back())
        assertNull(nav.sheet)
        assertEquals(Screen.More, nav.current)
        assertTrue(nav.back())
        assertEquals(Screen.Detail, nav.current)
        assertTrue(nav.back())
        assertEquals(listOf<Screen>(Screen.Root), nav.stack)
        assertFalse(nav.back())
    }

    @Test
    fun resetToKeepsTheRootAndReplacesTheDetail() {
        val nav = Navigator<Screen>(Screen.Root)
        nav.push(Screen.Detail)
        nav.push(Screen.More)
        nav.presentSheet(Screen.Menu)
        nav.resetTo(Screen.Detail)
        assertEquals(listOf(Screen.Root, Screen.Detail), nav.stack)
        assertNull(nav.sheet)

        nav.popToRoot()
        assertEquals(listOf<Screen>(Screen.Root), nav.stack)
    }

    @Test
    fun dayOfTheRequest() {
        val start = LocalDate(2026, 11, 17)
        assertEquals(RequestDay.NotStarted(start), requestDay(LocalDate(2026, 11, 16), start, 5))
        assertEquals(RequestDay.Running(1, 5), requestDay(start, start, 5))
        assertEquals(RequestDay.Running(5, 5), requestDay(LocalDate(2026, 11, 21), start, 5))
        assertEquals(RequestDay.Finished(LocalDate(2026, 11, 21)), requestDay(LocalDate(2026, 11, 22), start, 5))
        // across a month and a year end
        assertEquals(RequestDay.Running(4, 7), requestDay(LocalDate(2027, 1, 2), LocalDate(2026, 12, 30), 7))
        assertEquals(LocalDate(2027, 1, 5), lastDay(LocalDate(2026, 12, 30), 7))
    }

    private val weekdaysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December")
    private val weekdaysUk = listOf("понеділок", "вівторок", "середа", "четвер", "пʼятниця", "субота", "неділя")
    private val monthsUk = listOf("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня",
        "вересня", "жовтня", "листопада", "грудня")

    @Test
    fun longDatesFollowTheLocalePattern() {
        val date = LocalDate(2026, 11, 21)
        assertEquals("Saturday, 21 November", formatLongDate(date, "%1\$s, %2\$d %3\$s", weekdaysEn, monthsEn))
        assertEquals("субота, 21 листопада", formatLongDate(date, "%1\$s, %2\$d %3\$s", weekdaysUk, monthsUk))
        val de = formatLongDate(date, "%1\$s, %2\$d. %3\$s",
            listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"),
            listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"))
        assertEquals("Samstag, 21. November", de)
        assertEquals("Monday, 1 January", formatLongDate(LocalDate(2029, 1, 1), "%1\$s, %2\$d %3\$s", weekdaysEn, monthsEn))
    }

    @Test
    fun dateRangesInOneOrTwoMonths() {
        val short = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val one = "%1\$d–%2\$d %3\$s"
        val two = "%1\$d %2\$s – %3\$d %4\$s"
        assertEquals("17–21 Nov", formatDateRange(LocalDate(2026, 11, 17), LocalDate(2026, 11, 21), one, two, short))
        assertEquals("30 Nov – 2 Dec", formatDateRange(LocalDate(2026, 11, 30), LocalDate(2026, 12, 2), one, two, short))
        assertEquals("31 Dec – 3 Jan", formatDateRange(LocalDate(2026, 12, 31), LocalDate(2027, 1, 3), one, two, short))
    }

    @Test
    fun everyWidgetKindHasItsOwnCategory() {
        val categories = Demo.request.widgets.map { it.category }
        assertEquals(
            listOf(
                Category.Questionnaire, Category.BloodPressure, Category.Water, Category.Food,
                Category.Medicines, Category.Diary, Category.DoctorQuestion,
            ),
            categories,
        )
        assertEquals(Category.entries.toSet(), categories.toSet())
    }

    @Test
    fun demoDataFillsBothAppsAndClearEmptiesThem() {
        val state = ShellState(Role.Patient)
        assertFalse(state.hasData)
        state.fillWithDemoData()
        assertEquals(Demo.record, state.patientRecord)
        assertEquals(Demo.report, state.selectedReport)
        state.clear()
        assertFalse(state.hasData)
        assertNull(state.selectedReport)
    }
}
