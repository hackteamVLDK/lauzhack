package org.easypaper.report

/** Facts for display without AI. Only labels are translated; patient text and references stay verbatim. */
fun FactDigest.toLocalizedText(language: String): String {
    if (language != "de" && language != "uk") return toText()
    fun label(de: String, uk: String) = if (language == "de") de else uk
    return buildString {
        fun refs(ids: List<String>) = ids.joinToString(",", "[", "]")
        fun IntRange3.show() = "$min-$max (${label("Mittelwert", "середнє")} $avg)"
        appendLine(
            "${label("Beschwerde", "Скарга")}: $complaint. " +
                "${label("Einträge", "Записи")}: $entryCount, ${label("von", "від")} ${firstDate ?: "-"} " +
                "${label("bis", "до")} ${lastDate ?: "-"}.",
        )
        for (d in bloodPressure) {
            append(
                "${label("Blutdruck", "Артеріальний тиск")} ${d.date}: ${d.count} ${label("Messungen", "вимірювань")}, " +
                    "${label("systolisch", "систолічний")} ${d.systolic.show()}, " +
                    "${label("diastolisch", "діастолічний")} ${d.diastolic.show()}",
            )
            d.pulse?.let { append(", ${label("Puls", "пульс")} ${it.show()}") }
            appendLine(" mmHg ${refs(d.refs)}")
        }
        for (d in water) appendLine("${label("Wasser", "Вода")} ${d.date}: ${d.totalMl} mL ${refs(d.refs)}")
        for (m in medications) {
            val what = listOfNotNull(m.name, m.dose?.takeIf { it !in m.name }, m.code?.let { "GTIN $it" }).joinToString(", ")
            val state = if (m.confirmed) label("vom Patienten bestätigt", "підтверджено пацієнтом")
                else label("NICHT vom Patienten bestätigt", "НЕ підтверджено пацієнтом")
            appendLine(
                "${label("Medikament", "Ліки")} ($state): $what; " +
                    "${label("erfasst am", "записано")} ${m.dates.joinToString(", ")} ${refs(m.refs)}",
            )
        }
        for (q in questions) {
            appendLine("${label("Frage", "Запитання")} \"${q.text}\": " + q.answers.joinToString("; ") { "${it.date} ${it.value} [${it.ref}]" })
        }
        for (n in notes) {
            val kind = when (n.kind) {
                "diary" -> label("Tagebuch", "Щоденник")
                "food" -> label("Essen", "Їжа")
                else -> label("Frage an den Arzt", "Запитання до лікаря")
            }
            val photo = n.photoRef?.let { " (${label("Foto", "фото")} $it)" } ?: ""
            appendLine("$kind ${n.date}: ${n.text ?: ""}$photo [${n.ref}]")
        }
        // the name is the person's own word (or the app's preset in their language); only the frame is translated
        for (m in measurements) appendLine("${m.name} (${m.unit}): " + m.values.joinToString("; ") { "${it.date} ${it.value} [${it.ref}]" })
    }.trimEnd()
}
