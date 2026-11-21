package org.easypaper.invite

import org.easypaper.model.CareRequest
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.WidgetSpec
import kotlin.random.Random

/** Longest collection period the request builder offers, in days. */
const val MAX_REQUEST_DAYS = 30

/** What the doctor is filling in the request builder; becomes a [CareRequest] once [validate] is empty. */
data class RequestDraft(
    val doctorName: String = "",
    val complaint: String = "",
    val startDate: String = "", // ISO date, e.g. "2026-11-21"
    val days: Int = 5,
    val locale: String = "en",
    val widgets: List<WidgetSpec> = emptyList(),
)

/** Problems the builder shows next to the matching field. */
enum class DraftError {
    BLANK_DOCTOR,
    BLANK_COMPLAINT,
    BAD_START_DATE,
    DAYS_OUT_OF_RANGE,
    NO_WIDGETS,
    DUPLICATE_WIDGET_ID,
    BLANK_WIDGET_TITLE,
    /** A questionnaire without questions, a blank question, or a choice with fewer than two options. */
    QUESTIONNAIRE_INCOMPLETE,
}

/** All problems of the draft, in field order; empty means it can be sent. */
fun RequestDraft.validate(): List<DraftError> = buildList {
    if (doctorName.isBlank()) add(DraftError.BLANK_DOCTOR)
    if (complaint.isBlank()) add(DraftError.BLANK_COMPLAINT)
    if (!isIsoDate(startDate)) add(DraftError.BAD_START_DATE)
    if (days !in 1..MAX_REQUEST_DAYS) add(DraftError.DAYS_OUT_OF_RANGE)
    if (widgets.isEmpty()) add(DraftError.NO_WIDGETS)
    if (widgets.map { it.id }.toSet().size != widgets.size) add(DraftError.DUPLICATE_WIDGET_ID)
    if (widgets.any { it.title.isBlank() }) add(DraftError.BLANK_WIDGET_TITLE)
    if (widgets.filterIsInstance<QuestionnaireWidget>().any { !it.isComplete() }) add(DraftError.QUESTIONNAIRE_INCOMPLETE)
}

/** Builds the request; call only when [validate] returned no errors. */
fun RequestDraft.toCareRequest(id: String = newRequestId(), createdAt: Long): CareRequest {
    val errors = validate()
    require(errors.isEmpty()) { "draft is not valid: $errors" }
    return CareRequest(
        id = id,
        createdAt = createdAt,
        doctorName = doctorName.trim(),
        complaint = complaint.trim(),
        startDate = startDate,
        days = days,
        locale = locale,
        widgets = widgets,
    )
}

/** Random 128-bit id as 32 hex chars (no ':' so it is safe inside QR strings). */
fun newRequestId(random: Random = Random.Default): String =
    random.nextBytes(16).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun QuestionnaireWidget.isComplete(): Boolean =
    questions.isNotEmpty() && questions.all { q ->
        q.text.isNotBlank() && (q.kind != QuestionKind.CHOICE || q.options.count { it.isNotBlank() } >= 2)
    }

private val ISO_DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

/** Calendar-correct "YYYY-MM-DD" check without a date library. */
internal fun isIsoDate(text: String): Boolean {
    val (y, m, d) = ISO_DATE.find(text)?.destructured ?: return false
    val year = y.toInt()
    val month = m.toInt()
    val day = d.toInt()
    if (month !in 1..12) return false
    val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    val maxDay = when (month) {
        2 -> if (leap) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..maxDay
}
