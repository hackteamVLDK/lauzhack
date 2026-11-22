package org.easypaper.catalog

import java.io.File
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * Minimal streaming reader for the first sheet of an .xlsx file (Swissmedic and EMA lists): no extra dependency,
 * shared strings and inline strings, one row at a time so a 100 MB sheet never sits in memory. Each row is a map
 * column letter -> text.
 */
fun readXlsxRows(file: File, onRow: (rowIndex: Int, cells: Map<String, String>) -> Unit) {
    ZipFile(file).use { zip ->
        val strings = zip.getEntry("xl/sharedStrings.xml")?.let { entry -> zip.getInputStream(entry).use { readSharedStrings(it) } }.orEmpty()
        val sheet = zip.getEntry("xl/worksheets/sheet1.xml") ?: error("No first sheet in ${file.name}")
        zip.getInputStream(sheet).use { input ->
            val xml = factory().createXMLStreamReader(input)
            var rowIndex = -1
            var cells = mutableMapOf<String, String>()
            var column = ""
            var type = ""
            val value = StringBuilder()
            var inValue = false
            while (xml.hasNext()) {
                when (xml.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (xml.localName) {
                        "row" -> { rowIndex++; cells = mutableMapOf() }
                        "c" -> {
                            column = xml.attr("r").takeWhile { it.isLetter() }
                            type = xml.attr("t")
                            value.setLength(0)
                        }
                        "v", "t" -> inValue = true
                    }
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inValue) value.append(xml.text)
                    XMLStreamConstants.END_ELEMENT -> when (xml.localName) {
                        "v", "t" -> inValue = false
                        "c" -> {
                            val raw = value.toString()
                            val text = if (type == "s") raw.toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty() else raw
                            if (text.isNotEmpty()) cells[column] = text
                        }
                        "row" -> onRow(rowIndex, cells)
                    }
                }
            }
            xml.close()
        }
    }
}

private fun readSharedStrings(input: java.io.InputStream): List<String> {
    val out = ArrayList<String>()
    val xml = factory().createXMLStreamReader(input)
    val current = StringBuilder()
    var inText = false
    while (xml.hasNext()) {
        when (xml.next()) {
            XMLStreamConstants.START_ELEMENT -> when (xml.localName) {
                "si" -> current.setLength(0)
                "t" -> inText = true
            }
            XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inText) current.append(xml.text)
            XMLStreamConstants.END_ELEMENT -> when (xml.localName) {
                "t" -> inText = false
                "si" -> out += current.toString()
            }
        }
    }
    xml.close()
    return out
}

/** A StAX factory that never resolves external entities (the files come from the internet). */
internal fun factory(): XMLInputFactory = XMLInputFactory.newInstance().apply {
    setProperty(XMLInputFactory.SUPPORT_DTD, false)
    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
}

private fun XMLStreamReader.attr(name: String): String = getAttributeValue(null, name).orEmpty()
