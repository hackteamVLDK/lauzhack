package org.easypaper.ui.patient

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.easypaper.ui.format.today
import org.easypaper.ui.patient.v2.WhatToShareSheet
import org.easypaper.ui.shell.Role as AppRole
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Task 60: the share source is chosen by request id, so two requests with the same label stay separate choices. */
@OptIn(ExperimentalTestApi::class)
class ShareSourceUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun twoRequestsWithTheSameLabelAreSeparateChoices() = runComposeUiTest {
        val state = ShellState(AppRole.Patient)
        runBlocking { state.fillWithDemoData() }
        val records = state.repository.records.value
        val doctor = records.first { it.request.doctorName.isNotBlank() }
        // the same doctor, complaint and period: only the id differs
        val twin = doctor.copy(request = doctor.request.copy(id = doctor.request.id + "-twin"))
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                WhatToShareSheet(true, records + twin, today(), TimeZone.currentSystemDefault(), {}, {})
            }
        }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(doctor.request.doctorName, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val same = onAllNodes(radio and hasText(doctor.request.doctorName, substring = true))
        assertEquals(2, same.fetchSemanticsNodes().size, "both requests are listed")
        same[1].performClick()
        same[1].assertIsSelected()
        same[0].assertIsNotSelected()
        same[0].performClick()
        same[0].assertIsSelected()
        same[1].assertIsNotSelected()
    }
}
