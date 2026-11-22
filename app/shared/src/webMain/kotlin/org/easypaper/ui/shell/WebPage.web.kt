package org.easypaper.ui.shell

/** The query of the page's address ("?patient"), read by the web entry point to pick the role. */
expect fun webPageSearch(): String

/** The doctor's app by default (a PC in the practice); `?patient` opens the patient's diary (a phone's browser). */
fun webRole(search: String = webPageSearch()): Role =
    if (search.removePrefix("?").split('&').any { it == "patient" || it == "role=patient" }) Role.Patient else Role.Doctor
