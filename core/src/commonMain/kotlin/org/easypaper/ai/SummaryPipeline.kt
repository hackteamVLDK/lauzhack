package org.easypaper.ai

import ai.koog.prompt.executor.model.PromptExecutorAPI
import kotlinx.serialization.Serializable
import org.easypaper.model.Food
import org.easypaper.model.PatientRecord
import org.easypaper.report.factDigest
import org.easypaper.report.toLocalizedText
import org.easypaper.report.toText

/** One line of the doctor's summary with the entries it is based on. Unsupported lines are shown as such. */
@Serializable
data class SummaryLine(val text: String, val refs: List<String>, val supported: Boolean)

@Serializable
data class SummaryResult(
    /** Goes into Report.summary: one fact per line, each ending with [e01, e02]. */
    val summary: String,
    val lines: List<SummaryLine>,
    /** false: the doctor gets the facts computed without AI (budget, no executor, or AI failed). */
    val usedAi: Boolean,
    /** AI descriptions of food photos the patient left without text, by entry id. */
    val photoDescriptions: Map<String, String>,
    val usage: List<AiUsage>,
    /** Why AI was skipped or what was removed, for the price screen; null when nothing happened. */
    val note: String? = null,
)

/**
 * The frugal AI pipeline (task 7), shared by every platform on Koog's [PromptExecutorAPI]:
 * 1. facts without AI ([factDigest]): numbers, ranges, medications, answers, notes, all with entry ids;
 * 2. a small vision model only for food photos without text (size-capped, cached);
 * 3. the big model once per report, writing only facts with references, no diagnoses, no advice;
 * 4. every line checked: references must point to real entries, advice and diagnoses are dropped;
 * 5. a budget per report: when the estimate is above it, the facts are returned without AI.
 * The patient's name never reaches a prompt: the digest has none.
 */
class SummaryPipeline(
    private val executor: PromptExecutorAPI,
    private val config: AiConfig,
    private val cache: AiCache = InMemoryAiCache(),
    val ledger: UsageLedger = UsageLedger(),
) {
    private val caller = ModelCaller(executor, config, cache, ledger)

    /** [language] selects English (en), German (de), or Ukrainian (uk) output; other values use English. */
    suspend fun summarize(
        record: PatientRecord,
        photos: Map<String, ByteArray> = emptyMap(),
        utcOffsetMinutes: Int = 0,
        language: String = "en",
    ): SummaryResult {
        val start = ledger.items.size
        val knownIds = record.entries.map { it.id }.toSet()
        val digest = record.factDigest(utcOffsetMinutes)
        val facts = digest.toText()
        val outputLanguage = when (language) { "de" -> "German"; "uk" -> "Ukrainian"; else -> "English" }
        val photoSystem = PHOTO_SYSTEM.replace("English", outputLanguage)
        val summarySystem = SUMMARY_SYSTEM.replace("English", outputLanguage)
        val notes = mutableListOf<String>()
        fun spent() = ledger.items.drop(start).sumOf { it.costUsd }

        // 2. Photos the patient did not describe; the patient's own words always win over a model's guess.
        val descriptions = linkedMapOf<String, String>()
        var photoAttempts = 0
        val untold = record.entries.filter { (it.payload as? Food)?.let { f -> f.text.isNullOrBlank() && f.photoRef != null } == true }
        for (entry in untold) {
            if (photoAttempts >= config.maxPhotos) { notes += "photo attempt limit reached (${config.maxPhotos})"; break }
            val bytes = photos[(entry.payload as Food).photoRef] ?: continue
            if (bytes.size > config.maxPhotoBytes) { notes += "photo ${entry.id} too large, skipped"; continue }
            val estimate = caller.estimateCost(config.small, estimateTokens(photoSystem) + PHOTO_IMAGE_TOKENS, config.maxPhotoOutputTokens)
            if (spent() + estimate > config.budgetUsdPerReport) { notes += "budget reached before photo ${entry.id}"; break }
            photoAttempts++
            val answer = caller.call(
                AiStep.PhotoDescription, config.small, photoSystem, PHOTO_USER, bytes, config.maxPhotoOutputTokens,
                reportId = record.request.id,
            )
                ?: continue
            cleanDescription(answer)?.let { descriptions[entry.id] = it }
        }

        // 3. One call to the big model on facts only.
        val input = buildString {
            append(facts)
            if (descriptions.isNotEmpty()) {
                append("\nFood photos described by a vision model (not by the patient):")
                for ((id, text) in descriptions) append("\n- $text [$id]")
            }
        }
        val estimate = caller.estimateCost(config.big, estimateTokens(summarySystem + input), config.maxSummaryOutputTokens)
        val answer = if (spent() + estimate > config.budgetUsdPerReport) {
            notes += "budget: summary without AI"
            null
        } else {
            caller.call(
                AiStep.Summary, config.big, summarySystem, input, null, config.maxSummaryOutputTokens,
                reportId = record.request.id,
            )
                .also { if (it == null) notes += "AI not reachable (${caller.lastError ?: "empty answer"}): summary without AI" }
        }

        // 4. Check every line; fall back to the facts themselves.
        val (lines, dropped) = if (answer != null) checkLines(answer, knownIds) else factLines(digest.toLocalizedText(language), knownIds) to 0
        if (dropped > 0) notes += "$dropped line(s) with advice or diagnosis removed"
        return SummaryResult(
            summary = lines.joinToString("\n") { it.text },
            lines = lines,
            usedAi = answer != null,
            photoDescriptions = descriptions,
            usage = ledger.items.drop(start),
            note = notes.takeIf { it.isNotEmpty() }?.joinToString("; "),
        )
    }

    internal companion object {
        const val PHOTO_IMAGE_TOKENS = 1_300 // upper bound of a downscaled photo for current vision models

        val PHOTO_SYSTEM = """
            You label photos of food for a patient's food diary. Answer with 3 to 8 plain words naming the
            food and drinks you see, in English, e.g. "oatmeal with banana, black tea". No judgement, no
            calories, no health or diet advice. If it is not food, answer exactly: not food.
        """.trimIndent()

        const val PHOTO_USER = "What food is in this photo?"

        val SUMMARY_SYSTEM = """
            You prepare a short pre-visit summary for a doctor from a patient's diary facts.
            Rules:
            - Use ONLY the facts given. Never add, guess or generalise beyond them.
            - One fact per line, at most 12 lines, most relevant to the complaint first.
            - End every line with the ids of the entries it is based on, exactly as given: [e01] or [e01, e02].
            - No diagnosis, no interpretation of causes, no advice, no recommendations, no reassurance.
            - Keep numbers and units exactly as given (mmHg, mL). Mention unconfirmed medications as unconfirmed.
            - Quote questions the patient asks the doctor; do not answer them.
            - Keep quoted patient text in its original language, without translating it.
            - Plain English, no headings, no bullet symbols, no markdown.
        """.trimIndent()

        private val REFS = Regex("""\[([A-Za-z0-9_-]+(?:\s*,\s*[A-Za-z0-9_-]+)*)\][.;,:\s]*$""")
        private val QUOTES = Regex(""""[^"]*"|“[^”]*”|«[^»]*»|(?<![\p{L}])'.*?'(?![\p{L}])""")
        private val BULLET = Regex("""^\s*(?:[-*•]|\d+[.)])\s*""")
        // Advice or diagnosis in the languages of the app and of the models' slips (EN, DE, RU, UK).
        private val ADVICE = Regex(
            """(?<![\p{L}])(?:should|recommend\p{L}*|advis\p{L}*|diagnos\p{L}*|likely|probably|suggests?|consider|must|""" +
                """empfehl\p{L}*|sollte\p{L}*|vermutlich|рекоменд\p{L}*|следует|советую|диагноз\p{L}*|вероятно|""" +
                """варто|радимо|ймовірно)(?![\p{L}])""",
            RegexOption.IGNORE_CASE,
        )

        /** A reference group, optional punctuation, then more text on the same line: the end of one fact. */
        private val FACT_END = Regex("""(\[[A-Za-z0-9_-]+(?:\s*,\s*[A-Za-z0-9_-]+)*\][.;,]?)[ \t]+(?=[\p{L}\p{N}"“«'])""")

        /** Splits the model's answer into checked lines; returns the lines and how many were dropped as advice. */
        fun checkLines(answer: String, knownIds: Set<String>): Pair<List<SummaryLine>, Int> {
            var dropped = 0
            // Some models put every fact on one line; a reference group followed by more text ends a fact.
            val split = answer.replace(FACT_END, "\$1\n")
            val lines = split.lines().map { it.replace(BULLET, "").trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
                if (ADVICE.containsMatchIn(line.replace(QUOTES, ""))) { dropped++; return@mapNotNull null }
                val match = REFS.find(line)
                val refs = match?.groupValues?.get(1)?.split(',')?.map { it.trim() }.orEmpty()
                val real = refs.filter { it in knownIds }
                val body = match?.let { line.substring(0, it.range.first).trimEnd() } ?: line
                val text = if (real.isEmpty()) body else "$body ${real.joinToString(", ", "[", "]")}"
                SummaryLine(text, real, supported = real.isNotEmpty() && real.size == refs.size)
            }
            return lines to dropped
        }

        /** The facts without AI, line by line, as the fallback summary. */
        fun factLines(facts: String, knownIds: Set<String>): List<SummaryLine> = facts.lines().filter { it.isNotBlank() }.map { line ->
            val refs = Regex("""\[([^\]]+)\]""").findAll(line).flatMap { it.groupValues[1].split(',') }.map { it.trim() }.filter { it in knownIds }.toList()
            SummaryLine(line.trim(), refs, supported = refs.isNotEmpty())
        }

        /** Keeps the photo label short and plain; "not food" and empty answers are dropped. */
        fun cleanDescription(answer: String): String? {
            val text = answer.lines().firstOrNull { it.isNotBlank() }?.trim()?.trim('"', '.', ' ')?.take(80) ?: return null
            return text.takeIf { it.isNotEmpty() && !it.equals("not food", ignoreCase = true) && !ADVICE.containsMatchIn(it) }
        }
    }
}
