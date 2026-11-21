package org.easypaper.visit

import org.easypaper.demo.Demo
import org.easypaper.report.Report

/** Time zone of the demo patient (Lausanne, CET). */
const val DEMO_UTC_OFFSET_MINUTES = 60

/**
 * A summary of [Demo.record] as the AI pipeline (#7) should write it: one fact per line, every fact linked to
 * the entries it comes from, no ratings or advice. The numbers are checked against FactDigest in VisitTest.
 */
val DEMO_SUMMARY: String = listOf(
    "Headache on 4 of 5 days, intensity 4–7 of 10, mostly in the temples [e06, e12, e22, e26]; none on 2026-11-19 [e17].",
    "Blood pressure 125–142/81–91 mmHg in 10 readings; each evening reading higher than that morning's [e01, e02, e07, e08, e13, e14, e18, e19, e23, e24].",
    "DOLO SPEDIFEN forte 400 mg taken on 3 days [e27, e28, e29]; PANTOPRAZOL Streuli 20 mg scanned once, not confirmed by the patient [e30].",
    "Water 500–1250 mL per day, 990 mL on average [e03, e04, e05, e09, e10, e11, e15, e16, e20, e21, e25].",
    "Three cups of coffee and pasta on 2026-11-18 [e31]; slept 5 hours [e33]; chocolate [e32] and a long day at the screen on 2026-11-20 [e34].",
    "Asks whether ibuprofen can be taken together with coffee [e35].",
).joinToString("\n")

/** The report the doctor screen opens in the demo: Demo.report (#13) with [DEMO_SUMMARY]. */
fun demoVisitReport(): Report = Demo.report.copy(summary = DEMO_SUMMARY)
