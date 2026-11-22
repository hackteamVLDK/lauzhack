package org.easypaper.ui.doctor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.easypaper.ai.SummaryResult
import org.easypaper.model.Entry
import org.easypaper.report.Report
import org.easypaper.report.factDigest
import org.easypaper.report.toLocalizedText
import org.easypaper.visit.ClinicForm
import org.easypaper.visit.FieldState
import org.easypaper.visit.FormFieldKey
import org.easypaper.visit.FormWording
import org.easypaper.visit.SavedClinicForm
import org.easypaper.visit.SummaryPart
import org.easypaper.visit.checkedReportSummary
import org.easypaper.visit.parseSummary
import org.easypaper.visit.prefillClinicForm

/** Where the summary on the visit screen comes from. */
enum class SummarySource {
    /** Written by the AI pipeline (#7) for this visit. */
    Ai,
    /** Came with the report (Report.summary). */
    Report,
    /** Facts computed on this computer, no AI. */
    Facts,
}

/** One statement of the summary with the entries it links to; [supported] false = shown as "No source". */
data class VisitLine(val text: String, val sources: List<Entry>, val supported: Boolean)

data class VisitSummary(val lines: List<VisitLine>, val source: SummarySource) {
    /** The summary as text for the clinic form's history field, without link markers. */
    val text: String get() = lines.joinToString("\n") { it.text }
    val sourceIds: List<String> get() = lines.flatMap { line -> line.sources.map { it.id } }.distinct()
}

// "]" escaped: JavaScript reads the pattern in unicode mode, where a lone "]" is a syntax error.
private val TRAILING_REFS = Regex("""\s*\[[^\[\]]*\]\s*$""")

/**
 * The summary for [report]: the AI result when there is one, else the report's own summary, else the
 * facts without AI (FactDigest), always split into statements with links to the originals.
 */
fun visitSummary(report: Report, ai: SummaryResult?, utcOffsetMinutes: Int = 0, language: String = "en"): VisitSummary {
    val record = report.record
    val byId = record.entries.associateBy { it.id }
    if (ai != null) {
        return VisitSummary(
            ai.lines.map { line ->
                val sources = line.refs.mapNotNull { byId[it] }
                VisitLine(line.text.replace(TRAILING_REFS, "").trim(), sources, line.supported && sources.isNotEmpty())
            },
            // The server may answer without AI (off, budget, provider down): then these are the facts.
            if (ai.usedAi) SummarySource.Ai else SummarySource.Facts,
        )
    }
    // A summary that came with the report gets the pipeline's check: no advice, no diagnoses, no invented links.
    val (text, source) = report.summary?.takeIf { it.isNotBlank() }?.let { checkedReportSummary(it, record).first to SummarySource.Report }
        ?: (record.factDigest(utcOffsetMinutes).toLocalizedText(language) to SummarySource.Facts)
    val parsed = parseSummary(text, record)
    return VisitSummary(
        parsed.lines.map { line ->
            VisitLine(line.text, line.sources, line.cited && line.parts.none { it is SummaryPart.MissingSource })
        },
        source,
    )
}

/**
 * Everything the doctor does on the visit screen. The clinic form starts from the report's facts; the
 * history field follows the summary. Saving is possible only when every field is decided, and only
 * confirmed fields are saved (ClinicForm).
 */
@Stable
class VisitState(
    val report: Report,
    val wording: FormWording,
    /** The doctor's (and usually the patient's) time zone: days of the facts are local days. */
    private val utcOffsetMinutes: Int = 0,
    val language: String = "en",
) {
    var summary: VisitSummary by mutableStateOf(visitSummary(report, null, utcOffsetMinutes, language))
        private set
    var form: ClinicForm by mutableStateOf(
        prefillClinicForm(report, wording, utcOffsetMinutes).let { form ->
            form.replaceDraft(FormFieldKey.HISTORY, summary.text, summary.sourceIds)
        },
    )
        private set
    var saved: SavedClinicForm? by mutableStateOf(null)
        private set

    /** When each field was confirmed, for "Confirmed 10:24". */
    val confirmedAt = mutableStateMapOf<FormFieldKey, Long>()

    val decided: Int get() = form.fields.count { it.state != FieldState.DRAFT }

    /** A new AI summary replaces the history draft; other decisions stay. */
    fun applyAi(result: SummaryResult) {
        summary = visitSummary(report, result, utcOffsetMinutes, language)
        form = form.replaceDraft(FormFieldKey.HISTORY, summary.text, summary.sourceIds)
        confirmedAt.remove(FormFieldKey.HISTORY)
        saved = null
    }

    fun confirm(key: FormFieldKey, now: Long) {
        form = form.confirm(key)
        confirmedAt[key] = now
        saved = null
    }

    fun edit(key: FormFieldKey, value: String) {
        form = form.edit(key, value)
        confirmedAt.remove(key)
        saved = null
    }

    fun remove(key: FormFieldKey) {
        form = form.reject(key)
        confirmedAt.remove(key)
        saved = null
    }

    fun reopen(key: FormFieldKey) {
        form = form.reopen(key)
        confirmedAt.remove(key)
        saved = null
    }

    /** Null when not every field is decided or nothing is confirmed: nothing is saved then. */
    fun save(now: Long): SavedClinicForm? {
        if (!form.canSave) return null
        return form.save(now).also { saved = it }
    }
}
