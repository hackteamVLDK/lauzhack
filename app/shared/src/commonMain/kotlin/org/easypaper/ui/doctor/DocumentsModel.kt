package org.easypaper.ui.doctor

import org.easypaper.ai.DocumentFact
import org.easypaper.visit.FormFieldKey

/** Where a fact from a clinic document goes in the clinic form (task 41): medicines to Medications, the rest to History. */
fun DocumentFact.formField(): FormFieldKey = if (field == "medication") FormFieldKey.MEDICATIONS else FormFieldKey.HISTORY

/** The line added to the form: a medicine as written; other facts marked as coming from the document, with its words. */
fun DocumentFact.formLine(fromDocument: String): String =
    if (field == "medication") value else "$fromDocument: $value («$quote»)"

/** [current] with [line] on a new line; unchanged when the line is already there, so a second tap adds nothing. */
fun withLine(current: String, line: String): String = when {
    current.lines().any { it.trim() == line.trim() } -> current
    current.isBlank() -> line
    else -> current.trimEnd() + "\n" + line
}

fun VisitState.hasFact(fact: DocumentFact, fromDocument: String): Boolean =
    form.field(fact.formField()).value.lines().any { it.trim() == fact.formLine(fromDocument).trim() }

/**
 * Adds the fact as the doctor's edit of the field: the field becomes a draft again, so nothing from a document
 * reaches the saved form before the doctor confirms that field.
 */
fun VisitState.addFact(fact: DocumentFact, fromDocument: String) {
    val key = fact.formField()
    edit(key, withLine(form.field(key).value, fact.formLine(fromDocument)))
}
