package org.easypaper.ui.doctor

import org.easypaper.report.Report

/**
 * A short number that tells two reports apart on the doctor's screen ("7F3A"): the report carries no patient name
 * (privacy by design), and complaint and period alone can repeat. Derived from the report id, so it is stable.
 */
fun Report.shortCode(): String {
    var h = 0
    for (c in id) h = h * 31 + c.code
    return (h.toUInt() % 0x10000u).toString(16).uppercase().padStart(4, '0')
}
