package org.easypaper.visit

import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import org.easypaper.model.WidgetSpec
import org.easypaper.report.Report

/** One piece of a summary line: plain text, a link to an original entry, or a link the record does not contain. */
sealed interface SummaryPart {
    data class Text(val text: String) : SummaryPart
    data class Source(val entry: Entry) : SummaryPart
    data class MissingSource(val entryId: String) : SummaryPart
}

/** One statement of the AI summary with its links to originals. */
data class SummaryLine(val parts: List<SummaryPart>) {
    val sources: List<Entry> get() = parts.filterIsInstance<SummaryPart.Source>().map { it.entry }

    /** False when the statement links to no existing entry: the doctor sees it as unsupported. */
    val cited: Boolean get() = sources.isNotEmpty()

    /** The statement without link markers: whitespace collapsed, no space left before punctuation. */
    val text: String
        get() = parts.filterIsInstance<SummaryPart.Text>().joinToString("") { it.text }
            .replace(WHITESPACE, " ").replace(SPACE_BEFORE_PUNCTUATION, "$1").trim()
}

data class ParsedSummary(val lines: List<SummaryLine>) {
    /** Ids the summary cites but the record does not contain. */
    val unknownRefs: List<String>
        get() = lines.flatMap { line -> line.parts.filterIsInstance<SummaryPart.MissingSource>().map { it.entryId } }.distinct()

    val uncitedLines: List<SummaryLine> get() = lines.filter { !it.cited }

    /** Every entry the summary links to, each once, in order of first mention. */
    val citedIds: List<String> get() = lines.flatMap { line -> line.sources.map { it.id } }.distinct()
}

private val WHITESPACE = Regex("\\s+")
private val SPACE_BEFORE_PUNCTUATION = Regex("\\s+([.,;:!?])")

/** "[e12]" or "[e1, e2]": a bracket group whose every part looks like an entry id. */
private val CITATION = Regex("\\[([A-Za-z0-9_.-]+(?:\\s*,\\s*[A-Za-z0-9_.-]+)*)]")

/**
 * Splits [Report.summary] (written by the AI pipeline, #7) into lines with links to the originals.
 * One line = one statement. A null or blank summary gives no lines: the screen then shows originals only.
 */
fun parseSummary(report: Report): ParsedSummary = parseSummary(report.summary, report.record)

fun parseSummary(summary: String?, record: PatientRecord): ParsedSummary {
    if (summary.isNullOrBlank()) return ParsedSummary(emptyList())
    val byId = record.entries.associateBy { it.id }
    val lines = summary.lines().filter { it.isNotBlank() }.map { line ->
        val parts = mutableListOf<SummaryPart>()
        var from = 0
        for (match in CITATION.findAll(line)) {
            if (match.range.first > from) parts += SummaryPart.Text(line.substring(from, match.range.first))
            for (id in match.groupValues[1].split(',').map { it.trim() }) {
                parts += byId[id]?.let { SummaryPart.Source(it) } ?: SummaryPart.MissingSource(id)
            }
            from = match.range.last + 1
        }
        if (from < line.length) parts += SummaryPart.Text(line.substring(from))
        SummaryLine(parts)
    }
    return ParsedSummary(lines)
}

/** Originals for the doctor: every widget of the request in its order, with its entries sorted by time. */
fun PatientRecord.originalsByWidget(): List<Pair<WidgetSpec, List<Entry>>> {
    val byWidget = entries.groupBy { it.widgetId }
    return request.widgets.map { widget -> widget to (byWidget[widget.id] ?: emptyList()).sortedBy { it.at } }
}
