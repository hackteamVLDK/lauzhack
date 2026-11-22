package org.easypaper.report

/** Facts for display without AI. Only labels are translated; patient text and references stay verbatim. */
fun FactDigest.toLocalizedText(language: String): String {
    if (language !in listOf("de", "uk", "fr", "ru")) return toText()
    fun label(de: String, uk: String, fr: String, ru: String) = when (language) {
        "de" -> de
        "uk" -> uk
        "fr" -> fr
        else -> ru
    }
    return buildString {
        fun refs(ids: List<String>) = ids.joinToString(",", "[", "]")
        fun IntRange3.show() = "$min-$max (${label("Mittelwert", "середнє", "moyenne", "среднее")} $avg)"
        appendLine(
            "${label("Beschwerde", "Скарга", "Motif de consultation", "Жалоба")}: $complaint. " +
                "${label("Einträge", "Записи", "Entrées", "Записи")}: $entryCount, ${label("von", "від", "du", "с")} ${firstDate ?: "-"} " +
                "${label("bis", "до", "au", "по")} ${lastDate ?: "-"}.",
        )
        for (d in bloodPressure) {
            val readings = if (language == "ru") "измерений: ${d.count}"
                else "${d.count} ${label("Messungen", "вимірювань", "mesures", "измерений")}"
            append(
                "${label("Blutdruck", "Артеріальний тиск", "Tension artérielle", "Артериальное давление")} ${d.date}: $readings, " +
                    "${label("systolisch", "систолічний", "systolique", "систолическое")} ${d.systolic.show()}, " +
                    "${label("diastolisch", "діастолічний", "diastolique", "диастолическое")} ${d.diastolic.show()}",
            )
            d.pulse?.let { append(", ${label("Puls", "пульс", "pouls", "пульс")} ${it.show()}") }
            appendLine(" mmHg ${refs(d.refs)}")
        }
        for (d in water) appendLine("${label("Wasser", "Вода", "Eau", "Вода")} ${d.date}: ${d.totalMl} mL ${refs(d.refs)}")
        for (m in medications) {
            val what = listOfNotNull(m.name, m.dose?.takeIf { it !in m.name }, m.code?.let { "GTIN $it" }).joinToString(", ")
            val state = if (m.confirmed) label("vom Patienten bestätigt", "підтверджено пацієнтом", "confirmé par le patient", "подтверждено пациентом")
                else label("NICHT vom Patienten bestätigt", "НЕ підтверджено пацієнтом", "NON confirmé par le patient", "НЕ подтверждено пациентом")
            appendLine(
                "${label("Medikament", "Ліки", "Médicament", "Лекарство")} ($state): $what; " +
                    "${label("erfasst am", "записано", "noté le", "записано")} ${m.dates.joinToString(", ")} ${refs(m.refs)}",
            )
        }
        for (q in questions) {
            appendLine("${label("Frage", "Запитання", "Question", "Вопрос")} \"${q.text}\": " + q.answers.joinToString("; ") { "${it.date} ${it.value} [${it.ref}]" })
        }
        for (n in notes) {
            val kind = when (n.kind) {
                "diary" -> label("Tagebuch", "Щоденник", "Journal", "Дневник")
                "food" -> label("Essen", "Їжа", "Repas", "Еда")
                else -> label("Frage an den Arzt", "Запитання до лікаря", "Question au médecin", "Вопрос врачу")
            }
            val photo = n.photoRef?.let { " (${label("Foto", "фото", "photo", "фото")} $it)" } ?: ""
            appendLine("$kind ${n.date}: ${n.text ?: ""}$photo [${n.ref}]")
        }
        // the name is the person's own word (or the app's preset in their language); only the frame is translated
        for (m in measurements) appendLine("${m.name} (${m.unit}): " + m.values.joinToString("; ") { "${it.date} ${it.value} [${it.ref}]" })
    }.trimEnd()
}
