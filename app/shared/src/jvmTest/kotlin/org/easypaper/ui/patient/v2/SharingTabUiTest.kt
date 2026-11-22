package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.PatientRecord
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.ui.theme.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class SharingTabUiTest {
    private val today = LocalDate(2026, 11, 22)
    private val shareButton = hasText("Share with your doctor") and hasClickAction()
    private val inviteButton = hasText("Add a request from a doctor") and hasClickAction()

    private fun patientSettings(): PatientSettings {
        val setting = { SavedSetting(null) {} }
        return PatientSettings(setting(), setting(), setting(), setting(), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
    }

    private fun personalRecord() = PatientRecord(
        personalRequest(today, "en", 0L, Category.entries.associateWith { it.name }), emptyList())

    private fun ComposeUiTest.showSharing(
        records: List<PatientRecord> = listOf(personalRecord()),
        settings: PatientSettings = patientSettings(),
        onRequest: (String) -> Unit = {},
        onInvite: () -> Unit = {},
        onShare: () -> Unit = {},
    ) {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    SharingTab(records, settings, today, {}, {}, onRequest, onInvite, onShare)
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("You decide").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun shareSheetPluralizesEntriesAndPhotosIndependently() = runComposeUiTest {
        val at = Instant.parse("2026-11-22T10:00:00Z").toEpochMilliseconds()
        val record = mutableStateOf(personalRecord().copy(entries = listOf(
            Entry("a", "personal-food", at, Food("Lunch", "photo-a")),
        )))
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    WhatToShareSheet(true, listOf(record.value), today, TimeZone.UTC, {}, {})
                }
            }
        }
        val one = "To be shared: 1 entry, 1 photo"
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(one).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(one).assertExists()
        runOnIdle {
            record.value = record.value.copy(entries = record.value.entries +
                Entry("b", "personal-food", at, Food("Dinner")))
        }
        val two = "To be shared: 2 entries, 1 photo"
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(two).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(two).assertExists()
    }

    @Test
    fun introductionHasThreeBenefitsAndBothButtonsCallExistingCallbacks() = runComposeUiTest {
        var shares = 0
        var invites = 0
        showSharing(onShare = { shares++ }, onInvite = { invites++ })

        val benefits = listOf(
            "You decide",
            "Choose the period and what to share. Nothing leaves your phone without you.",
            "Your doctor sees a summary",
            "Every line links to your original entry.",
            "Private and secure",
            "Encrypted end to end. A QR code or a 6-digit code works once.",
        )
        for (text in benefits) {
            onNode(hasScrollAction()).performScrollToNode(hasText(text))
            onNodeWithText(text).assertIsDisplayed()
        }
        // The card heading and primary button deliberately use the same wording.
        onNode(hasScrollAction()).performScrollToNode(shareButton)
        onNode(shareButton).assertIsDisplayed().assertIsEnabled().performClick()
        onNode(hasScrollAction()).performScrollToNode(inviteButton)
        onNode(inviteButton).assertIsDisplayed().assertIsEnabled().performClick()
        runOnIdle {
            assertEquals(1, shares)
            assertEquals(1, invites)
        }
    }

    @Test
    fun withoutRecordsSharingStaysDisabledAndAddingARequestStaysAvailable() = runComposeUiTest {
        var shares = 0
        var invites = 0
        showSharing(records = emptyList(), onShare = { shares++ }, onInvite = { invites++ })
        onNode(hasScrollAction()).performScrollToNode(shareButton)
        onNode(shareButton).assertIsNotEnabled().performClick()
        onNode(hasScrollAction()).performScrollToNode(inviteButton)
        onNode(inviteButton).assertIsEnabled().performClick()
        onNode(hasScrollAction()).performScrollToNode(hasText("No requests yet. You can use your diary on its own."))
        onNodeWithText("No requests yet. You can use your diary on its own.").assertIsDisplayed()
        onNode(hasScrollAction()).performScrollToNode(hasText("Reports you choose to share will appear here."))
        onNodeWithText("Reports you choose to share will appear here.").assertIsDisplayed()
        runOnIdle {
            assertEquals(0, shares)
            assertEquals(1, invites)
        }
    }

    @Test
    fun doctorPlansStillOpenAndSharedHistoryStaysVisible() = runComposeUiTest {
        val personal = personalRecord()
        val doctor = personal.copy(request = personal.request.copy(
            id = "doctor-request", doctorName = "Dr. Keller", complaint = "Visit notes"))
        val settings = patientSettings().also { it.addSharedHistory("Shared report for the visit") }
        var opened: String? = null
        showSharing(listOf(personal, doctor), settings, onRequest = { opened = it })
        onNode(hasScrollAction()).performScrollToNode(hasText("Requests from doctors"))
        onNodeWithText("Requests from doctors").assertIsDisplayed()
        onNode(hasScrollAction()).performScrollToNode(hasText("Dr. Keller"))
        onNodeWithText("Dr. Keller").performClick()
        runOnIdle { assertEquals("doctor-request", opened) }
        onNode(hasScrollAction()).performScrollToNode(hasText("Shared report for the visit"))
        onNodeWithText("Shared").assertIsDisplayed()
        onNodeWithText("Shared report for the visit").assertIsDisplayed()
    }

    @Test
    fun bothActionsAreReachableOnANarrowDarkScreenWithLargeText() = runComposeUiTest {
        var shares = 0
        var invites = 0
        val settings = patientSettings()
        val records = listOf(personalRecord())
        setContent {
            ShellTheme(ShellSettings(dark = true, largeText = true)) {
                PatientLanguage("en") {
                    Box(Modifier.width(320.dp)) {
                        SharingTab(records, settings, today, {}, {}, {}, { invites++ }, { shares++ })
                    }
                }
            }
        }
        onNode(hasScrollAction()).performScrollToNode(shareButton)
        onNode(shareButton).assertIsDisplayed().performClick()
        onNode(hasScrollAction()).performScrollToNode(inviteButton)
        onNode(inviteButton).assertIsDisplayed().performClick()
        runOnIdle {
            assertEquals(1, shares)
            assertEquals(1, invites)
        }
    }
}
