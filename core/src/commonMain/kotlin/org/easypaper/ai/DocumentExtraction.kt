package org.easypaper.ai

import ai.koog.prompt.executor.model.PromptExecutorAPI
import kotlinx.serialization.Serializable

/**
 * A fact copied from an uploaded clinic document (discharge letter, lab report) for the clinic form.
 * Always a draft: the doctor confirms it. [quote] is the exact text it came from; [verified] = the quote was
 * found in the document text (photos of documents cannot be checked this way).
 */
@Serializable
data class DocumentFact(
    val field: String,
    val value: String,
    val quote: String,
    val documentId: String,
    val verified: Boolean,
)

@Serializable
data class DocumentExtraction(
    val documentId: String,
    val facts: List<DocumentFact>,
    val usedAi: Boolean,
    val usage: List<AiUsage>,
    val note: String? = null,
)

/**
 * Clinic module only (task 8): reads a document the clinic uploads and lists the facts written in it, each
 * with an exact quote. It copies, it does not interpret: no diagnoses of its own, no advice. Quotes are checked
 * against the text; facts whose quote is not in the document are dropped for text documents.
 */
class DocumentExtractor(
    executor: PromptExecutorAPI,
    private val config: AiConfig,
    cache: AiCache = InMemoryAiCache(),
    val ledger: UsageLedger = UsageLedger(),
) {
    private val caller = ModelCaller(executor, config, cache, ledger)

    suspend fun extract(documentId: String, text: String? = null, image: ByteArray? = null, imageFormat: String = "jpeg"): DocumentExtraction {
        require(text != null || image != null) { "Give the document as text or as an image" }
        val start = ledger.items.size
        val clipped = text?.take(MAX_TEXT_CHARS)
        val user = if (clipped != null) "Document:\n$clipped" else "The document is in the image."
        val answer = caller.call(AiStep.DocumentExtraction, config.big, SYSTEM, user, image, config.maxSummaryOutputTokens, imageFormat)
        val usage = ledger.items.drop(start)
        if (answer == null) {
            return DocumentExtraction(documentId, emptyList(), usedAi = false, usage, "AI not reachable (${caller.lastError ?: "empty answer"})")
        }
        val (facts, dropped) = parse(answer, documentId, clipped)
        val notes = listOfNotNull(
            "document cut to $MAX_TEXT_CHARS characters".takeIf { text != null && text.length > MAX_TEXT_CHARS },
            "$dropped fact(s) without a matching quote removed".takeIf { dropped > 0 },
        )
        return DocumentExtraction(documentId, facts, usedAi = true, usage, notes.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }

    internal companion object {
        const val MAX_TEXT_CHARS = 20_000
        val FIELDS = setOf("medication", "allergy", "diagnosis_in_document", "procedure", "lab_value")

        val SYSTEM = """
            You copy facts from a medical document into a clinic intake form. Output one fact per line as:
            FIELD | VALUE | QUOTE
            FIELD is one of: medication, allergy, diagnosis_in_document, procedure, lab_value.
            VALUE is a short normalised value (e.g. "Ramipril 5 mg once daily").
            QUOTE is the exact words from the document the fact comes from, copied character by character.
            Only facts written in the document. Do not infer, do not add your own diagnosis, no advice.
            If there are no such facts, output exactly: NONE
        """.trimIndent()

        private fun normalize(s: String) = s.lowercase().replace(Regex("""\s+"""), " ").trim()

        /** Returns facts and the number of lines dropped (unknown field, broken line, quote not in the text). */
        fun parse(answer: String, documentId: String, documentText: String?): Pair<List<DocumentFact>, Int> {
            val haystack = documentText?.let(::normalize)
            var dropped = 0
            val facts = answer.lines().map { it.trim().trimStart('-', '*', '•').trim() }
                .filter { it.isNotEmpty() && !it.equals("NONE", ignoreCase = true) }
                .mapNotNull { line ->
                    val parts = line.split('|').map { it.trim().trim('"') }
                    val field = parts.getOrNull(0)?.lowercase()
                    if (parts.size != 3 || field !in FIELDS || parts[1].isEmpty() || parts[2].isEmpty()) { dropped++; return@mapNotNull null }
                    val verified = haystack != null && normalize(parts[2]) in haystack
                    if (haystack != null && !verified) { dropped++; return@mapNotNull null }
                    DocumentFact(field!!, parts[1], parts[2], documentId, verified)
                }
                .distinctBy { it.field to normalize(it.value) }
            return facts to dropped
        }
    }
}
