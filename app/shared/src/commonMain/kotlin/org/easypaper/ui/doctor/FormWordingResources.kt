package org.easypaper.ui.doctor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.date_range_one_month
import easypaper.app.shared.generated.resources.date_range_two_months
import easypaper.app.shared.generated.resources.med_not_confirmed
import easypaper.app.shared.generated.resources.months_short
import easypaper.app.shared.generated.resources.no_entries
import easypaper.app.shared.generated.resources.wording_bp
import easypaper.app.shared.generated.resources.wording_fluid
import easypaper.app.shared.generated.resources.wording_pulse
import kotlinx.datetime.LocalDate
import org.easypaper.ui.format.formatDateRange
import org.easypaper.visit.BloodPressureStats
import org.easypaper.visit.FluidStats
import org.easypaper.visit.FormWording
import org.easypaper.visit.MedicationLine
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Fills "%1$s" / "%2$d" placeholders of a string resource outside composition. */
internal fun String.fill(vararg args: Any): String {
    var out = this
    args.forEachIndexed { index, arg ->
        val n = index + 1
        out = out.replace("%$n\$s", arg.toString()).replace("%$n\$d", arg.toString())
    }
    return out
}

/** The clinic form's field texts in the app language (EN/DE/UK); templates are read once per composition. */
class ResourceFormWording(
    private val bloodPressure: String,
    private val pulse: String,
    private val fluid: String,
    private val notConfirmed: String,
    private val none: String,
    private val rangeOneMonth: String,
    private val rangeTwoMonths: String,
    private val monthsShort: List<String>,
) : FormWording {
    override fun bloodPressure(stats: BloodPressureStats): String {
        val period = runCatching {
            formatDateRange(LocalDate.parse(stats.firstDate), LocalDate.parse(stats.lastDate), rangeOneMonth, rangeTwoMonths, monthsShort)
        }.getOrDefault("${stats.firstDate} – ${stats.lastDate}")
        return bloodPressure.fill(period, stats.systolic.range(), stats.diastolic.range(), stats.readings) +
            (stats.pulse?.let { pulse.fill(it.range()) } ?: "")
    }

    override fun medications(items: List<MedicationLine>): String = items.joinToString("\n") { item ->
        listOfNotNull(item.name, item.dose?.takeIf { it !in item.name }).joinToString(", ") +
            if (item.unconfirmed) " ($notConfirmed)" else ""
    }

    override fun fluidIntake(stats: FluidStats): String = fluid.fill(stats.mlPerDay, stats.days)

    override fun patientQuestions(items: List<String>): String = items.joinToString("\n") { "– $it" }

    override fun noEntries(): String = none

    private fun IntRange.range() = if (first == last) "$first" else "$first–$last"
}

@Composable
fun rememberFormWording(): FormWording {
    val bp = stringResource(Res.string.wording_bp)
    val pulse = stringResource(Res.string.wording_pulse)
    val fluid = stringResource(Res.string.wording_fluid)
    val notConfirmed = stringResource(Res.string.med_not_confirmed)
    val none = stringResource(Res.string.no_entries)
    val one = stringResource(Res.string.date_range_one_month)
    val two = stringResource(Res.string.date_range_two_months)
    val months = stringArrayResource(Res.array.months_short)
    return remember(bp, pulse, fluid, notConfirmed, none, one, two, months) {
        ResourceFormWording(bp, pulse, fluid, notConfirmed, none, one, two, months)
    }
}
