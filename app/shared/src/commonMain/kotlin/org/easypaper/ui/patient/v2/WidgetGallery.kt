package org.easypaper.ui.patient.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** One family of outline drawables for the collage, gallery and widgets. */
val Category.lineIcon: DrawableResource
    get() = when (this) {
        Category.BloodPressure -> Res.drawable.ic_line_pressure
        Category.Water -> Res.drawable.ic_line_water
        Category.Food -> Res.drawable.ic_line_food
        Category.Medicines -> Res.drawable.ic_line_medicines
        Category.Questionnaire -> Res.drawable.ic_line_symptoms
        Category.Diary -> Res.drawable.ic_line_diary
        Category.DoctorQuestion -> Res.drawable.ic_line_questions
        Category.Measurements -> Res.drawable.ic_line_measurements
    }

@Composable
private fun categoryDescription(category: Category): String = stringResource(when (category) {
    Category.BloodPressure -> Res.string.p2_widget_pressure_body
    Category.Water -> Res.string.p2_widget_water_body
    Category.Food -> Res.string.p2_widget_food_body
    Category.Medicines -> Res.string.p2_widget_medicines_body
    Category.Questionnaire -> Res.string.p2_widget_symptoms_body
    Category.Diary -> Res.string.p2_widget_diary_body
    Category.DoctorQuestion -> Res.string.p2_widget_questions_body
    Category.Measurements -> Res.string.p2_widget_measurements_body
})

@Composable
fun PastelCategoryCard(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier,
                       description: Boolean = false, added: Boolean = false) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val foreground = category.softText(colors)
    Column(modifier.clip(EasyPaperTheme.shapes.widget).background(category.soft(colors))
        .clickable(enabled = !added, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { if (added) disabled() }
        .heightIn(min = spacing.categoryCardMinHeight).padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(categoryTitle(category), style = EasyPaperTheme.typography.headline, color = foreground)
        if (description) Text(categoryDescription(category), style = EasyPaperTheme.typography.callout, color = foreground)
        if (added) Text(stringResource(Res.string.p2_widget_added), style = EasyPaperTheme.typography.footnote, color = foreground)
        Spacer(Modifier.weight(1f))
        Icon(painterResource(category.lineIcon), null, Modifier.size(spacing.rowMinHeight).align(Alignment.End), tint = foreground)
    }
}

@Composable
fun WidgetGallery(visible: Boolean, settings: PatientSettings, onDismiss: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    Sheet(visible, stringResource(Res.string.p2_add_widgets), stringResource(Res.string.p2_done), onDismiss) {
        Text(stringResource(Res.string.p2_gallery_body), style = EasyPaperTheme.typography.body,
            color = EasyPaperTheme.colors.secondaryLabel, modifier = Modifier.padding(vertical = spacing.m))
        Category.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                row.forEach { category ->
                    PastelCategoryCard(category, { settings.addWidget(category); onDismiss() },
                        Modifier.weight(1f).fillMaxHeight(), description = true, added = category in settings.pinnedCategories)
                }
            }
            Spacer(Modifier.height(spacing.s))
        }
    }
}
