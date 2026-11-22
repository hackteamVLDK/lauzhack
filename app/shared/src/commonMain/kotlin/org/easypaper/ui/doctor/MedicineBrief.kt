package org.easypaper.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.Medication
import org.easypaper.ui.components.DraftBadge
import org.easypaper.ui.format.today
import org.easypaper.ui.medicine.cardTitle
import org.easypaper.ui.medicine.intakeLine
import org.easypaper.ui.medicine.sourceLine
import org.easypaper.ui.medicine.substancesLine
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

/** Whether an entry gets the medicine brief instead of a plain text line (task 71). */
fun Medication.hasBrief(): Boolean = product != null || patientWords != null || intake != null

/**
 * The doctor's brief for one medicine entry, readable in seconds (task 71):
 *  FERVEX ADULTES · granulés pour solution buvable
 *  Ce médicament est indiqué … (official text, quoted)
 *  Per sachet: acide ascorbique 200 mg, paracétamol 500 mg, maléate de phéniramine 25 mg.
 *  Patient reports: 1 sachet, today at 08:30.
 *  Source: official product information, France (BDPM), data of … (link)
 * Everything comes from the snapshot saved with the entry; nothing is inferred about why it was taken.
 * An entry in the patient's own words shows those words only, marked, without any composition.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineBrief(medication: Medication, timeZone: TimeZone, today: LocalDate = today(), modifier: Modifier = Modifier) {
    val type = EasyPaperTheme.typography
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val uri = LocalUriHandler.current
    val card = medication.product
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        if (card != null) {
            Text(cardTitle(card), style = type.headline, color = colors.label)
            card.indication?.let { Text(it, style = type.callout, color = colors.secondaryLabel) }
            substancesLine(card)?.let { Text(it, style = type.callout, color = colors.label) }
            if (!card.strengthVerified) Text(stringResource(Res.string.rec_unverified), style = type.footnote, color = colors.secondaryLabel)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text("«${medication.patientWords ?: medication.name}»", style = type.headline, color = colors.label)
                DraftBadge(stringResource(Res.string.brief_words))
            }
            medication.labelIngredients?.let { Text(stringResource(Res.string.brief_label_ingredients, it), style = type.callout, color = colors.label) }
            Text(stringResource(Res.string.brief_no_card), style = type.footnote, color = colors.secondaryLabel)
        }
        Text(medication.intake?.let { intakeLine(it, timeZone, today) } ?: stringResource(Res.string.brief_not_taken),
            style = type.callout, color = colors.label)
        if (card != null) {
            Text(sourceLine(card.source), style = type.footnote, color = colors.link,
                modifier = Modifier.clickable(role = Role.Button) { runCatching { uri.openUri(card.source.url) } })
        }
    }
}
