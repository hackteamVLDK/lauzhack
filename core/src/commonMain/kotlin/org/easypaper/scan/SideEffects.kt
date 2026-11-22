package org.easypaper.scan

/** A side effect the app asks about, with its name in the app's languages. */
data class SideEffect(val key: String, val en: String, val de: String, val uk: String, val fr: String, val ru: String) {
    fun name(locale: String): String = when {
        locale.startsWith("de") -> de
        locale.startsWith("uk") -> uk
        locale.startsWith("fr") -> fr
        locale.startsWith("ru") -> ru
        else -> en
    }
}

private val HEADACHE = SideEffect("headache", "Headache", "Kopfschmerzen", "Головний біль", "Maux de tête", "Головная боль")
private val STOMACH_PAIN = SideEffect("stomach_pain", "Stomach pain", "Bauchschmerzen", "Біль у животі", "Douleurs abdominales", "Боль в животе")
private val HEARTBURN = SideEffect("heartburn", "Heartburn", "Sodbrennen", "Печія", "Brûlures d’estomac", "Изжога")
private val NAUSEA = SideEffect("nausea", "Nausea", "Übelkeit", "Нудота", "Nausées", "Тошнота")
private val DIARRHOEA = SideEffect("diarrhoea", "Diarrhoea", "Durchfall", "Діарея", "Diarrhée", "Диарея")
private val CONSTIPATION = SideEffect("constipation", "Constipation", "Verstopfung", "Закреп", "Constipation", "Запор")
private val FLATULENCE = SideEffect("flatulence", "Bloating", "Blähungen", "Здуття", "Ballonnements", "Вздутие")
private val BRUISING = SideEffect("bruising", "Bruising or bleeding easily", "Blaue Flecken oder Blutungen", "Синці або кровотечі", "Bleus ou saignements faciles", "Синяки или кровотечения")
private val TIREDNESS = SideEffect("tiredness", "Tiredness", "Müdigkeit", "Втома", "Fatigue", "Усталость")
private val DIZZINESS = SideEffect("dizziness", "Dizziness", "Schwindel", "Запаморочення", "Vertiges", "Головокружение")
private val COLD_HANDS = SideEffect("cold_hands", "Cold hands or feet", "Kalte Hände oder Füsse", "Холодні руки або ноги", "Mains ou pieds froids", "Холодные руки или ноги")
private val SWOLLEN_ANKLES = SideEffect("swollen_ankles", "Swollen ankles", "Geschwollene Knöchel", "Набряклі щиколотки", "Chevilles enflées", "Отёки лодыжек")
private val FLUSHING = SideEffect("flushing", "Flushing", "Gesichtsrötung", "Почервоніння обличчя", "Rougeur du visage", "Покраснение лица")
private val DRY_COUGH = SideEffect("dry_cough", "Dry cough", "Trockener Husten", "Сухий кашель", "Toux sèche", "Сухой кашель")
private val MUSCLE_PAIN = SideEffect("muscle_pain", "Muscle pain", "Muskelschmerzen", "Біль у м’язах", "Douleurs musculaires", "Боль в мышцах")
private val JOINT_PAIN = SideEffect("joint_pain", "Joint pain", "Gelenkschmerzen", "Біль у суглобах", "Douleurs articulaires", "Боль в суставах")
private val DROWSINESS = SideEffect("drowsiness", "Drowsiness", "Schläfrigkeit", "Сонливість", "Somnolence", "Сонливость")
private val DRY_MOUTH = SideEffect("dry_mouth", "Dry mouth", "Mundtrockenheit", "Сухість у роті", "Bouche sèche", "Сухость во рту")
private val APPETITE = SideEffect("appetite", "More appetite", "Mehr Appetit", "Посилений апетит", "Plus d’appétit", "Повышенный аппетит")

/**
 * Three side effects listed as common in the product information of each active substance (by ATC code) of the
 * local medicine list (#53). The app only asks whether the person noticed them — it never says what caused what.
 * Substances whose information lists no common side effects (paracetamol, levothyroxine at the right dose, …)
 * have no questions.
 * shortcut: a hand-made demo table for the bundled list; a pharmacist should check it, and a real deployment
 * should take it from the product information database (e.g. Swissmedic AIPS).
 */
val COMMON_SIDE_EFFECTS: Map<String, List<SideEffect>> = mapOf(
    "M01AE01" to listOf(STOMACH_PAIN, HEARTBURN, NAUSEA), // ibuprofen
    "M01AB05" to listOf(STOMACH_PAIN, NAUSEA, DIARRHOEA), // diclofenac
    "A02BC01" to listOf(HEADACHE, STOMACH_PAIN, DIARRHOEA), // omeprazole
    "A02BC02" to listOf(HEADACHE, DIARRHOEA, NAUSEA), // pantoprazole
    "A07DA03" to listOf(CONSTIPATION, NAUSEA, FLATULENCE), // loperamide
    "A10BA02" to listOf(NAUSEA, DIARRHOEA, STOMACH_PAIN), // metformin
    "B01AC06" to listOf(HEARTBURN, STOMACH_PAIN, BRUISING), // acetylsalicylic acid, low dose
    "C07AB07" to listOf(TIREDNESS, DIZZINESS, COLD_HANDS), // bisoprolol
    "C08CA01" to listOf(SWOLLEN_ANKLES, HEADACHE, FLUSHING), // amlodipine
    "C09AA05" to listOf(DRY_COUGH, DIZZINESS, HEADACHE), // ramipril
    "C10AA05" to listOf(MUSCLE_PAIN, JOINT_PAIN, NAUSEA), // atorvastatin
    "R06AE07" to listOf(DROWSINESS, DRY_MOUTH, HEADACHE), // cetirizine
    "R06AX13" to listOf(HEADACHE, DROWSINESS, APPETITE), // loratadine
    "R06AX27" to listOf(TIREDNESS, DRY_MOUTH, HEADACHE), // desloratadine
)

/** Every side effect the app can ask about, by key (to check answers and to name them in reports). */
val SIDE_EFFECTS_BY_KEY: Map<String, SideEffect> = COMMON_SIDE_EFFECTS.values.flatten().associateBy { it.key }

/** What to ask about a medicine with this package code; empty for unknown packages and typed-in medicines. */
fun MedicationDb.sideEffectsFor(gtin: String?): List<SideEffect> =
    gtin?.let { parseScanCode(it) }?.let { find(it) }?.let { COMMON_SIDE_EFFECTS[it.atc] }.orEmpty()
