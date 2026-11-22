package org.easypaper.ui.patient

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.easypaper.catalog.Catalog
import org.easypaper.catalog.CatalogIndex
import org.easypaper.catalog.CatalogPack
import org.easypaper.catalog.CatalogProduct
import org.easypaper.catalog.CatalogQuery
import org.easypaper.catalog.Resolution
import org.easypaper.catalog.ResolutionKind
import org.easypaper.catalog.resolve
import org.easypaper.model.Ingredient
import org.easypaper.model.Medication
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.MedicineProduct
import org.easypaper.model.PatientRecord
import org.easypaper.model.RegistrySource
import org.easypaper.report.Report
import org.easypaper.scan.PhotoReading
import org.easypaper.ui.doctor.VisitScreen
import org.easypaper.ui.patient.medications.MedicationsScreen
import org.easypaper.ui.patient.medications.Recognition
import org.easypaper.ui.patient.medications.recordMedicine
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 71 through the real screens: the patient's medicines screen (recognise, confirm, intake) and the doctor's
 * visit screen (brief). The catalog runs the real matching over three products whose fields are copied from the
 * BDPM records CIS 69329731, 63368332 and 60234100 (files of 2026-11-22); the device reader is faked per "photo".
 */
@OptIn(ExperimentalTestApi::class)
class MedicineRecognitionUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun bdpm(cis: String) = RegistrySource("fr-bdpm", "Base de données publique des médicaments (France)", "FR", cis,
        "https://base-donnees-publique.medicaments.gouv.fr/medicament/$cis/extrait", "2026-11-22")

    private val fervex = CatalogProduct(
        "fr-bdpm:69329731",
        MedicineProduct(
            name = "FERVEX ADULTES, granulés pour solution buvable en sachet", brand = "FERVEX ADULTES", form = "granulés pour solution buvable",
            ingredients = listOf(Ingredient("acide ascorbique", "200", "mg"), Ingredient("paracétamol", "500", "mg"), Ingredient("maléate de phéniramine", "25", "mg")),
            perUnit = "un sachet", doseUnit = "sachet",
            indication = "Ce médicament est indiqué chez l’adulte et l’enfant à partir de 50 kg (environ 15 ans) dans le traitement au cours " +
                "des rhumes, rhinites, rhinopharyngites et des états grippaux.",
            indicationLanguage = "fr", source = bdpm("69329731"),
        ),
        packs = listOf(CatalogPack("3400930316948", "12 sachet(s) papier aluminium polyéthylène"), CatalogPack("3400932705917", "8 sachet(s) papier aluminium polyéthylène")),
    )
    private fun doliprane(cis: String, mg: String, gtin: String) = CatalogProduct(
        "fr-bdpm:$cis",
        MedicineProduct("DOLIPRANE $mg mg, comprimé", "DOLIPRANE $mg mg", "comprimé", listOf(Ingredient("paracétamol", mg, "mg")),
            "un comprimé", "comprimé", source = bdpm(cis)),
        packs = listOf(CatalogPack(gtin, "plaquette(s) thermoformée(s) PVC-aluminium")),
    )
    private val index = CatalogIndex(listOf(fervex, doliprane("63368332", "500", "3400932320189"), doliprane("60234100", "1000", "3400935955838")))
    private val catalog = Catalog { query -> resolve(index, query) }

    /** What the phone's reader returns for each test "photo". */
    private val photos = mapOf(
        "box-of-8" to PhotoReading(listOf("]d2010340093270591717281231"), "FERVEX ADULTES\n8 sachets"),
        "sachet" to PhotoReading(emptyList(), "FERVEX\nADULTES\nparacétamol 500 mg\nacide ascorbique 200 mg\nmaléate de phéniramine 25 mg"),
        "cut-blister" to PhotoReading(emptyList(), "DOLIPRANE\nparacétamol\nLot 4512"),
        "unknown-code" to PhotoReading(listOf("4006381333931"), "DOLIPRANE 1000 mg\ncomprimés"),
        "supplement" to PhotoReading(emptyList(), "Burgerstein ZinkVital Nahrungsergänzungsmittel 150 Minitabletten"),
    )
    private val reader: suspend (ByteArray) -> PhotoReading = { photos.getValue(it.decodeToString()) }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private class Setup(val state: ShellState, val recognition: Recognition) {
        val record: PatientRecord get() = state.repository.records.value.first { r -> r.request.widgets.any { it is MedicationsWidget } }
        val widget get() = record.request.widgets.filterIsInstance<MedicationsWidget>().first()
        val medicines get() = record.entries.filter { it.widgetId == widget.id }.map { it.payload }.filterIsInstance<Medication>()
    }

    private fun ComposeUiTest.patientScreen(catalog: Catalog = this@MedicineRecognitionUiTest.catalog): Setup {
        val state = ShellState(Role.Patient)
        runBlocking { state.fillWithDemoData() }
        val setup = Setup(state, Recognition(catalog, reader))
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                val records by state.repository.records.collectAsState()
                val record = records.first { r -> r.request.widgets.any { it is MedicationsWidget } }
                MedicationsScreen(state.repository, record, record.request.widgets.filterIsInstance<MedicationsWidget>().first(), back = null,
                    catalog = catalog, reader = reader, injectedRecognition = setup.recognition)
            }
        }
        waitForText("Recognise medicine")
        onNodeWithText("Recognise medicine").performClick()
        waitForText("Choose image")
        return setup
    }

    private fun ComposeUiTest.photo(setup: Setup, name: String) = runBlocking { setup.recognition.onPhoto(name.encodeToByteArray()) }

    @Test
    fun recognitionHasNoCountryChoiceAndPhotoAndTypedQueriesSearchEveryRegistry() = runComposeUiTest {
        val queries = mutableListOf<CatalogQuery>()
        val setup = patientScreen(Catalog { query -> queries.add(query); resolve(index, query) })
        onNodeWithText("Bought in").assertDoesNotExist()
        onNodeWithText("Any country").assertDoesNotExist()
        onNodeWithText("Switzerland").assertDoesNotExist()
        photo(setup, "box-of-8")
        waitForText("Confirm")
        assertEquals(1, queries.size)
        assertNull(queries.single().country)
        onNodeWithText("Bought in").assertDoesNotExist()

        runOnIdle { setup.recognition.start() }
        waitForText("Type what’s printed on the package")
        onNodeWithText("Type what’s printed on the package").performClick()
        waitForText("Search the registries")
        onNode(hasSetTextAction()).performTextInput("DOLIPRANE 1000 mg")
        onNodeWithText("Search the registries").performClick()
        waitForText("Confirm")
        assertEquals(2, queries.size)
        assertTrue(queries.all { it.country == null })
        onNodeWithText("Bought in").assertDoesNotExist()
    }

    @Test
    fun anUnregisteredSupplementOffersThePatientsOwnWords() = runComposeUiTest {
        val setup = patientScreen()
        photo(setup, "supplement")
        waitForText("Not enough is readable on this photo.", substring = true)
        onNodeWithText("Describe it in my words").assertExists()
        onNodeWithText("Confirm").assertDoesNotExist()
        onNodeWithText("Bought in").assertDoesNotExist()
    }

    @Test
    fun notFoundOffersOnlineReadingAndSendsTheSamePhotoOnlyAfterClick() = runComposeUiTest {
        var uploads = 0
        var uploaded: ByteArray? = null
        val onlineCatalog = object : Catalog {
            override suspend fun resolve(query: CatalogQuery) = Resolution(ResolutionKind.NOT_FOUND)
            override suspend fun readLabel(jpeg: ByteArray): Resolution {
                uploads++
                uploaded = jpeg
                return resolve(index, CatalogQuery(text = "FERVEX ADULTES"))
            }
        }
        val setup = patientScreen(onlineCatalog)
        photo(setup, "box-of-8")
        waitForText("Read the box online")
        onNodeWithText("The photo of the box is sent to the Easy Paper server to read the label; nothing else.").assertExists()
        assertEquals(0, uploads)
        onNodeWithText("Read the box online").performClick()
        waitForText("Confirm")
        assertEquals(1, uploads)
        assertEquals("box-of-8", uploaded!!.decodeToString())
    }

    @Test
    fun onlineUnavailableKeepsTheRetryAndManualChoices() = runComposeUiTest {
        val setup = patientScreen(Catalog { Resolution(ResolutionKind.TOO_LITTLE) })
        photo(setup, "box-of-8")
        waitForText("Read the box online")
        onNodeWithText("Read the box online").performClick()
        waitForText("Reading online is not available right now")
        onNodeWithText("Read the box online").assertExists()
        onNodeWithText("Describe it in my words").assertExists()
    }

    @Test
    fun anEmptyCatalogHasItsOwnMessageAndStillOffersThePatientsOwnWords() = runComposeUiTest {
        val setup = patientScreen(Catalog { Resolution(ResolutionKind.NOT_LOADED) })
        photo(setup, "box-of-8")
        waitForText("Medicine lists are not loaded on the server yet")
        onNodeWithText("Describe it in my words").assertExists()
        onNodeWithText("can’t be reached", substring = true).assertDoesNotExist()
        onNodeWithText("Bought in").assertDoesNotExist()
    }

    /** The doctor's screen for the patient's record as it is now. */
    private fun ComposeUiTest.doctorSees(setup: Setup, vararg texts: String) {
        val report = Report(id = "r1", createdAt = 0, record = setup.record)
        setContent { ShellTheme(ShellSettings(dark = false)) { VisitScreen(report, back = null) } }
        waitForText("Confirm summary", substring = true)
        for (text in texts) {
            onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
            waitForText(text, substring = true)
        }
    }

    // Scenarios 1 and 6: a known code -> exact card -> confirmed -> 1 sachet taken from a box of 8 -> the doctor's brief.
    @Test
    fun aKnownCodeLeadsToTheDoctorsBriefWithTheAmountActuallyTaken() = runComposeUiTest {
        val setup = patientScreen()
        photo(setup, "box-of-8")
        waitForText("FERVEX ADULTES, granulés pour solution buvable en sachet")
        onNodeWithText("8 sachet(s) papier aluminium polyéthylène").assertExists() // the pack, as information
        onNodeWithText("maléate de phéniramine 25 mg").assertExists()
        onNodeWithText("Confirm").performClick()
        waitForText("What did you take?")
        onNodeWithText("1 sachet").assertExists() // asked separately; the pack size is not the amount
        onNodeWithText("Save intake").performClick()
        waitUntil(timeoutMillis = 10_000) { setup.medicines.any { it.product != null } }
        val saved = setup.medicines.single { it.product != null }
        assertEquals("1", saved.intake?.amount)
        assertEquals("sachet", saved.intake?.unit)
        assertEquals("3400932705917", saved.code)
        assertEquals("8 sachet(s) papier aluminium polyéthylène", saved.product?.pack)

        doctorSees(setup,
            "FERVEX ADULTES · granulés pour solution buvable",
            "Ce médicament est indiqué chez l’adulte",
            "Per sachet: acide ascorbique 200 mg, paracétamol 500 mg, maléate de phéniramine 25 mg.",
            "Patient reports: 1 sachet, today at",
            "Source: official product information, France (BDPM)",
        )
    }

    // Scenario 2: no barcode, the sachet's printed text: every substance per sachet reaches the doctor.
    @Test
    fun aSachetWithoutBarcodeShowsEverySubstancePerSachetToTheDoctor() = runComposeUiTest {
        val setup = patientScreen()
        photo(setup, "sachet")
        waitForText("FERVEX ADULTES, granulés pour solution buvable en sachet")
        onNodeWithText("Confirm").performClick()
        waitForText("Save intake")
        onNodeWithText("Save intake").performClick()
        waitUntil(timeoutMillis = 10_000) { setup.medicines.any { it.product != null } }
        assertEquals(3, setup.medicines.single { it.product != null }.product!!.ingredients.size)
        doctorSees(setup, "Per sachet: acide ascorbique 200 mg, paracétamol 500 mg, maléate de phéniramine 25 mg.")
    }

    // Scenario 3: the strength is cut off on the blister: a question, never a chosen strength.
    @Test
    fun aCutBlisterAsksForTheStrength() = runComposeUiTest {
        val setup = patientScreen()
        photo(setup, "cut-blister")
        waitForText("Which strength is printed on the package?", substring = true)
        onNodeWithText("FR · 500 mg").assertExists()
        onNodeWithText("FR · 1000 mg").assertExists()
        assertTrue(onAllNodesWithText("Confirm").fetchSemanticsNodes().isEmpty(), "no card is offered before a strength is chosen")
        onNodeWithText("DOLIPRANE 1000 mg, comprimé").performClick() // the person picks what the box says
        waitForText("Confirm")
    }

    // Scenario 4: a code no registry knows: the printed name is searched.
    @Test
    fun anUnknownCodeIsFollowedByTheTextSearch() = runComposeUiTest {
        val setup = patientScreen()
        photo(setup, "unknown-code")
        waitForText("DOLIPRANE 1000 mg, comprimé")
        onNodeWithText("The barcode 4006381333931 is not in the registries, so the printed name was searched.").assertExists()
    }

    // Scenario 5: the patient's own words: saved as written, the doctor sees them without any added substance.
    @Test
    fun thePatientsOwnWordsReachTheDoctorWithoutAComposition() = runComposeUiTest {
        val setup = patientScreen()
        onNodeWithText("Describe it in my words").performClick()
        waitForText("Next")
        onNode(hasSetTextAction() and hasText("What did you take?", substring = true)).performTextInput("cold tea")
        onNodeWithText("Next").performClick()
        waitForText("Save intake")
        onNodeWithText("Save intake").performClick()
        waitForText("Unit, e.g. tablet, sachet, ml") // no registry unit: the patient says what they counted
        onNode(hasSetTextAction() and hasContentDescription("Unit, e.g. tablet, sachet, ml")).performTextInput("cup")
        onNodeWithText("Save intake").performClick()
        waitUntil(timeoutMillis = 10_000) { setup.medicines.any { it.patientWords != null } }
        val saved = setup.medicines.single { it.patientWords != null }
        assertNull(saved.product)
        assertEquals("cup", saved.intake?.unit)
        doctorSees(setup, "«cold tea»", "Patient’s own words", "No registry card confirmed", "Patient reports: 1 cup")
        assertTrue(onAllNodesWithText("paracétamol", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun confirmingTwiceRecordsOneIntake() = runComposeUiTest {
        val state = ShellState(Role.Patient)
        runBlocking { state.fillWithDemoData() }
        val record = state.repository.records.value.first { r -> r.request.widgets.any { it is MedicationsWidget } }
        val widget = record.request.widgets.filterIsInstance<MedicationsWidget>().first()
        val medication = org.easypaper.ui.patient.medications.confirmedMedication(
            org.easypaper.catalog.Candidate(fervex, pack = fervex.packs[1]))
        val intake = org.easypaper.model.Intake("1", "sachet", 1_000L)
        runBlocking {
            repeat(2) { state.repository.recordMedicine(record.request.id, widget.id, "same-entry", medication, intake, 2_000L) }
        }
        val saved = state.repository.records.value.first { it.request.id == record.request.id }.entries.filter { it.id == "same-entry" }
        assertEquals(1, saved.size)
        assertEquals(1_000L, saved.single().at) // the entry's time is the intake time
        // a later catalog change does not touch the stored snapshot
        assertEquals("paracétamol", (saved.single().payload as Medication).product!!.ingredients[1].name)
    }

    // review of #71: a medicine only added to the list is not an intake, anywhere
    @Test
    fun onlyAddedToTheListIsNotAnIntake() = runComposeUiTest {
        val state = ShellState(Role.Patient)
        runBlocking { state.fillWithDemoData() }
        val record = state.repository.records.value.first { r -> r.request.widgets.any { it is MedicationsWidget } }
        val widget = record.request.widgets.filterIsInstance<MedicationsWidget>().first()
        val card = org.easypaper.ui.patient.medications.confirmedMedication(org.easypaper.catalog.Candidate(fervex))
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        runBlocking { state.repository.recordMedicine(record.request.id, widget.id, "list-only", card, null, now) }
        val saved = state.repository.records.value.first { it.request.id == record.request.id }
        val item = org.easypaper.ui.patient.medications.medicinesFor(saved, widget.id, org.easypaper.ui.format.today(), kotlinx.datetime.TimeZone.currentSystemDefault())
            .single { it.name == fervex.card.name }
        assertTrue(item.takenToday.isEmpty())
        assertNull(item.lastTaken)
        // FHIR: no time of taking and no dose for a medicine that is only on the list
        fun statements(e: kotlinx.serialization.json.JsonElement): List<kotlinx.serialization.json.JsonObject> = when (e) {
            is kotlinx.serialization.json.JsonObject -> (if (e["resourceType"]?.toString() == "\"MedicationStatement\"") listOf(e) else emptyList()) +
                e.values.flatMap { statements(it) }
            is kotlinx.serialization.json.JsonArray -> e.flatMap { statements(it) }
            else -> emptyList()
        }
        val fhir = org.easypaper.report.Report(id = "r2", createdAt = 0, record = saved).fhir
        val statement = statements(fhir).single { fervex.card.name in it.toString() }
        assertNull(statement["dosage"])
        assertNull(statement["effectiveDateTime"])
    }

    @Test
    fun aCardFoundByTextKeepsNoForeignBarcode() {
        // the card was found by its printed name; the barcode on the photo belongs to no registry pack
        val medication = org.easypaper.ui.patient.medications.confirmedMedication(org.easypaper.catalog.Candidate(fervex))
        assertNull(medication.code)
        assertNull(medication.product?.gtin)
    }
}
