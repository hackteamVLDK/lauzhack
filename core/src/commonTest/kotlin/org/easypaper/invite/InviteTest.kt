package org.easypaper.invite

import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.FoodWidget
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.Question
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.WaterWidget
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InviteTest {
    private val headacheRequest = CareRequest(
        id = "9f2c4e1a7b3d4c5e8f6a1b2c3d4e5f60",
        createdAt = 1_795_276_800_000,
        doctorName = "Dr. Anna Keller",
        complaint = "Головний біль зранку, два тижні",
        startDate = "2026-11-22",
        days = 5,
        locale = "uk",
        widgets = listOf(
            QuestionnaireWidget(
                id = "q",
                title = "Morning headache",
                questions = listOf(
                    Question("q1", "Did you wake up with a headache?", QuestionKind.YES_NO),
                    Question("q2", "How strong was it?", QuestionKind.SCALE, listOf("none", "worst")),
                    Question("q3", "Where did it hurt?", QuestionKind.CHOICE, listOf("forehead", "temples", "back of the head")),
                    Question("q4", "What helped, if anything?", QuestionKind.TEXT),
                ),
            ),
            BloodPressureWidget(id = "bp", title = "Blood pressure", timesPerDay = 2),
            WaterWidget(id = "water", title = "Water", targetMl = 2000),
            FoodWidget(id = "food", title = "Meals"),
            MedicationsWidget(id = "meds", title = "Medicines you take"),
            DiaryWidget(id = "diary", title = "How you feel"),
            DoctorQuestionsWidget(id = "ask", title = "Questions for the doctor"),
        ),
    )

    private fun open(text: String): CareRequest = assertIs<InviteResult.Ok>(parseInvite(text)).request

    private fun errorOf(text: String): InviteError? = (parseInvite(text) as? InviteResult.Error)?.reason

    @OptIn(ExperimentalEncodingApi::class)
    private fun codeOfJson(json: String) =
        "epi1:" + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(json.encodeToByteArray())

    @Test
    fun patientGetsExactlyTheSameWidgetsInOrder() {
        val opened = open(encodeInvite(headacheRequest))
        assertEquals(headacheRequest, opened)
        assertEquals(headacheRequest.widgets.map { it.id }, opened.widgets.map { it.id })
    }

    @Test
    fun linkAndRawCodeOpenTheSameRequest() {
        assertEquals(headacheRequest, open(inviteLink(headacheRequest)))
        assertEquals(headacheRequest, open(inviteLink(headacheRequest, "https://example.org/open")))
        assertEquals(headacheRequest, open("  " + encodeInvite(headacheRequest) + "\n"))
    }

    @Test
    fun codeHasVersionPrefixAndTypicalRequestFitsOneQr() {
        val link = inviteLink(headacheRequest)
        assertTrue(encodeInvite(headacheRequest).startsWith("epi1:"))
        assertTrue(inviteFitsQr(link), "typical link is ${link.length} chars, QR limit $INVITE_QR_MAX_CHARS")
        assertTrue(!inviteFitsQr("x".repeat(INVITE_QR_MAX_CHARS + 1)))
    }

    @Test
    fun notAnInvite() {
        assertEquals(InviteError.NOT_INVITE, errorOf(""))
        assertEquals(InviteError.NOT_INVITE, errorOf("hello doctor"))
        assertEquals(InviteError.NOT_INVITE, errorOf("https://example.org/page#section"))
        assertEquals(InviteError.NOT_INVITE, errorOf("ep1:report-1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) // report QR
        assertEquals(InviteError.NOT_INVITE, errorOf("epi0:abc"))
    }

    @Test
    fun newerVersionAsksForUpdate() {
        assertEquals(InviteError.NEWER_VERSION, errorOf("epi2:anything"))
        assertEquals(InviteError.NEWER_VERSION, errorOf(codeOfJson("""{"schemaVersion":99,"id":"x"}""")))
    }

    @Test
    fun damagedInvitesAreRejected() {
        val code = encodeInvite(headacheRequest)
        assertEquals(InviteError.DAMAGED, errorOf("epi1:"))
        assertEquals(InviteError.DAMAGED, errorOf(code.dropLast(40)))
        assertEquals(InviteError.DAMAGED, errorOf("epi1:not*base64!"))
        assertEquals(InviteError.DAMAGED, errorOf(codeOfJson("""{"a":1}""")))
        assertEquals(InviteError.DAMAGED, errorOf(codeOfJson("""[1,2,3]""")))
        assertEquals(InviteError.DAMAGED, errorOf(codeOfJson("not json")))
        val zeroDays = """{"schemaVersion":1,"id":"x","createdAt":0,"doctorName":"D","complaint":"C",""" +
            """"startDate":"2026-11-22","days":0,"locale":"en","widgets":[]}"""
        assertEquals(InviteError.DAMAGED, errorOf(codeOfJson(zeroDays)))
    }

    @Test
    fun hugeInputIsRejectedBeforeDecoding() {
        assertEquals(InviteError.TOO_LARGE, errorOf("epi1:" + "A".repeat(INVITE_MAX_CHARS)))
    }

    private val validDraft = RequestDraft(
        doctorName = headacheRequest.doctorName,
        complaint = headacheRequest.complaint,
        startDate = headacheRequest.startDate,
        days = headacheRequest.days,
        locale = headacheRequest.locale,
        widgets = headacheRequest.widgets,
    )

    @Test
    fun validDraftBecomesTheRequestThePatientOpens() {
        assertEquals(emptyList(), validDraft.validate())
        val request = validDraft.toCareRequest(id = headacheRequest.id, createdAt = headacheRequest.createdAt)
        assertEquals(headacheRequest, request)
        assertEquals(request, open(inviteLink(request)))
    }

    @Test
    fun draftErrorsAreReportedPerField() {
        val empty = RequestDraft(startDate = "2026-02-30", days = 0)
        assertEquals(
            listOf(
                DraftError.BLANK_DOCTOR, DraftError.BLANK_COMPLAINT, DraftError.BAD_START_DATE,
                DraftError.DAYS_OUT_OF_RANGE, DraftError.NO_WIDGETS,
            ),
            empty.validate(),
        )
        assertFailsWith<IllegalArgumentException> { empty.toCareRequest(createdAt = 0) }

        val badWidgets = validDraft.copy(
            days = MAX_REQUEST_DAYS + 1,
            widgets = listOf(
                WaterWidget(id = "w", title = " "),
                DiaryWidget(id = "w", title = "Diary"),
                QuestionnaireWidget(id = "q", title = "Q", questions = listOf(Question("a", "Pick one", QuestionKind.CHOICE, listOf("yes")))),
            ),
        )
        assertEquals(
            listOf(
                DraftError.DAYS_OUT_OF_RANGE, DraftError.DUPLICATE_WIDGET_ID,
                DraftError.BLANK_WIDGET_TITLE, DraftError.QUESTIONNAIRE_INCOMPLETE,
            ),
            badWidgets.validate(),
        )
    }

    @Test
    fun isoDateCheck() {
        assertTrue(isIsoDate("2028-02-29"))
        assertTrue(isIsoDate("2026-12-31"))
        assertTrue(!isIsoDate("2026-02-29"))
        assertTrue(!isIsoDate("2026-13-01"))
        assertTrue(!isIsoDate("26-11-21"))
        assertTrue(!isIsoDate("2026-11-21T10:00"))
    }

    @Test
    fun requestIdsAreHexAndUnique() {
        val random = Random(42)
        val ids = List(100) { newRequestId(random) }
        assertTrue(ids.all { it.length == 32 && it.all { c -> c in "0123456789abcdef" } })
        assertEquals(ids.size, ids.toSet().size)
    }
}
