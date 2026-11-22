package org.easypaper.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.datetime.LocalDate
import org.easypaper.ui.format.formatDateRange
import org.easypaper.ui.format.formatLongDate
import org.easypaper.ui.patient.litres
import org.easypaper.ui.patient.measurementNumber
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Reads the source XML from disk, independently of generated Compose resources and the host locale. */
class LocalizationResourcesTest {
    private val root = listOf(File("src/commonMain/composeResources"), File("app/shared/src/commonMain/composeResources"))
        .first { it.isDirectory }
    private val folders = listOf("values", "values-de", "values-uk", "values-fr", "values-ru")
    private val placeholder = Regex("""%\d+\$[sd]""")

    private data class Resource(val kind: String, val texts: List<String>, val quantities: List<String>)

    private fun read(folder: String): Map<String, Resource> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val result = linkedMapOf<String, Resource>()
        val files = File(root, folder).listFiles { file -> file.extension == "xml" }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty(), "$folder has no XML resources")
        for (file in files) {
            val document = factory.newDocumentBuilder().parse(file)
            assertEquals("resources", document.documentElement.tagName, file.path)
            val children = document.documentElement.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i) as? Element ?: continue
                assertTrue(node.tagName in setOf("string", "string-array", "plurals"), "Unknown resource in $file")
                val name = node.getAttribute("name")
                assertTrue(name.isNotEmpty(), "Unnamed resource in $file")
                val items = node.getElementsByTagName("item")
                val texts = if (node.tagName == "string") listOf(node.textContent)
                    else (0 until items.length).map { items.item(it).textContent }
                val quantities = if (node.tagName == "plurals")
                    (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") } else emptyList()
                assertTrue(texts.all { it.isNotBlank() }, "$folder/$name is empty")
                val key = "${node.tagName}/$name"
                assertTrue(key !in result, "Duplicate $key in $file")
                result[key] = Resource(node.tagName, texts, quantities)
            }
        }
        return result
    }

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.groupingBy { it }.eachCount()

    @Test
    fun everyLocaleHasAllKeysArrayItemsAndMatchingPlaceholders() {
        assertEquals(folders.toSet(), root.listFiles()!!.filter { it.isDirectory && it.name.startsWith("values") }.map { it.name }.toSet())
        val baseline = read("values")
        for (folder in folders.drop(1)) {
            val localized = read(folder)
            assertEquals(baseline.keys, localized.keys, "Resource keys in $folder")
            assertEquals(baseline.keys.toList(), localized.keys.toList(), "Resource order in $folder")
            for ((key, source) in baseline) {
                val target = localized.getValue(key)
                if (source.kind == "plurals") {
                    // Russian needs few/many as well as the English one/other forms.
                    assertEquals(target.quantities.size, target.quantities.toSet().size, "$folder/$key has duplicate quantities")
                    assertTrue(target.quantities.containsAll(source.quantities), "$folder/$key has missing quantities")
                    val expected = source.quantities.zip(source.texts).associate { (q, text) -> q to placeholders(text) }
                    for ((q, text) in target.quantities.zip(target.texts)) {
                        assertEquals(expected[q] ?: expected.getValue("other"), placeholders(text), "$folder/$key/$q")
                    }
                } else {
                    assertEquals(source.texts.size, target.texts.size, "Array length in $folder/$key")
                    source.texts.zip(target.texts).forEachIndexed { index, (en, translation) ->
                        assertEquals(placeholders(en), placeholders(translation), "$folder/$key[$index]")
                    }
                }
            }
        }
    }

    @Test
    fun languageNamesAreNativeInEveryLocale() {
        for (folder in folders) {
            val resources = read(folder)
            for ((key, name) in mapOf("english" to "English", "german" to "Deutsch", "ukrainian" to "Українська", "french" to "Français", "russian" to "Русский")) {
                assertEquals(name, resources.getValue("string/p2_$key").texts.single(), "$folder/$key")
            }
            assertNotNull(resources["string/p2_system"])
        }
    }

    @Test
    fun frenchAndRussianDatesAndNumbersUseTheirActualResources() {
        for ((folder, expected) in mapOf(
            "values-fr" to listOf("samedi 21 novembre", "17–21 nov.", "30 nov. – 2 déc."),
            "values-ru" to listOf("суббота, 21 ноября", "17–21 нояб.", "30 нояб. – 2 дек."),
        )) {
            val resources = read(folder)
            val long = resources.getValue("string/date_long").texts.single()
            val months = resources.getValue("string-array/months_short").texts
            assertEquals(expected[0], formatLongDate(LocalDate(2026, 11, 21), long,
                resources.getValue("string-array/weekdays").texts, resources.getValue("string-array/months_long").texts))
            val one = resources.getValue("string/date_range_one_month").texts.single()
            val two = resources.getValue("string/date_range_two_months").texts.single()
            assertEquals(expected[1], formatDateRange(LocalDate(2026, 11, 17), LocalDate(2026, 11, 21), one, two, months))
            assertEquals(expected[2], formatDateRange(LocalDate(2026, 11, 30), LocalDate(2026, 12, 2), one, two, months))
            assertEquals("%1\$d %2\$s", resources.getValue("string/date_day_month").texts.single())
            val separator = resources.getValue("string/decimal_separator").texts.single().single()
            assertEquals(',', separator)
            assertEquals("1,25", litres(1250, separator))
            assertEquals("72,5", measurementNumber(72.5, separator))
            assertEquals("72", measurementNumber(72.0, separator))
            assertEquals(",", resources.getValue("string/cost_decimal_separator").texts.single())
        }
    }
}
