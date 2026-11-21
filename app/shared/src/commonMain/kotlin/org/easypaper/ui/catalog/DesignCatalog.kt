package org.easypaper.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.add
import easypaper.app.shared.generated.resources.appearance_dark
import easypaper.app.shared.generated.resources.ask_question
import easypaper.app.shared.generated.resources.cat_blood_pressure
import easypaper.app.shared.generated.resources.cat_diary
import easypaper.app.shared.generated.resources.cat_food
import easypaper.app.shared.generated.resources.cat_medicines
import easypaper.app.shared.generated.resources.cat_question
import easypaper.app.shared.generated.resources.cat_water
import easypaper.app.shared.generated.resources.catalog_date
import easypaper.app.shared.generated.resources.catalog_day
import easypaper.app.shared.generated.resources.confirm
import easypaper.app.shared.generated.resources.confirmed_at
import easypaper.app.shared.generated.resources.draft
import easypaper.app.shared.generated.resources.edit
import easypaper.app.shared.generated.resources.evening_reading
import easypaper.app.shared.generated.resources.footer_privacy
import easypaper.app.shared.generated.resources.ic_add
import easypaper.app.shared.generated.resources.ic_check_circle
import easypaper.app.shared.generated.resources.ic_remove
import easypaper.app.shared.generated.resources.ic_favorite
import easypaper.app.shared.generated.resources.ic_qr_code_2
import easypaper.app.shared.generated.resources.ic_history
import easypaper.app.shared.generated.resources.ic_edit_note
import easypaper.app.shared.generated.resources.v2_catalog_title
import easypaper.app.shared.generated.resources.v2_search_placeholder
import easypaper.app.shared.generated.resources.v2_period_day
import easypaper.app.shared.generated.resources.v2_period_week
import easypaper.app.shared.generated.resources.v2_period_month
import easypaper.app.shared.generated.resources.v2_period_half_year
import easypaper.app.shared.generated.resources.v2_show_options
import easypaper.app.shared.generated.resources.v2_options_title
import easypaper.app.shared.generated.resources.v2_option_qr
import easypaper.app.shared.generated.resources.v2_option_qr_subtitle
import easypaper.app.shared.generated.resources.v2_option_nearby
import easypaper.app.shared.generated.resources.v2_option_nearby_subtitle
import easypaper.app.shared.generated.resources.v2_option_file
import easypaper.app.shared.generated.resources.v2_option_file_subtitle
import easypaper.app.shared.generated.resources.v2_show_onboarding
import easypaper.app.shared.generated.resources.v2_onboarding_title
import easypaper.app.shared.generated.resources.v2_onboarding_body
import easypaper.app.shared.generated.resources.v2_onboarding_primary
import easypaper.app.shared.generated.resources.v2_onboarding_secondary
import easypaper.app.shared.generated.resources.v2_onboarding_detail
import easypaper.app.shared.generated.resources.v2_progress_label
import easypaper.app.shared.generated.resources.v2_change_progress
import easypaper.app.shared.generated.resources.v2_profile_sample
import easypaper.app.shared.generated.resources.v2_qr_selected
import easypaper.app.shared.generated.resources.v2_sample_initials
import easypaper.app.shared.generated.resources.pulse
import easypaper.app.shared.generated.resources.sample_food
import easypaper.app.shared.generated.resources.sample_med_form
import easypaper.app.shared.generated.resources.sample_medicine
import easypaper.app.shared.generated.resources.sample_sources
import easypaper.app.shared.generated.resources.sample_summary
import easypaper.app.shared.generated.resources.section_doctor
import easypaper.app.shared.generated.resources.show_qr
import easypaper.app.shared.generated.resources.summary
import easypaper.app.shared.generated.resources.taken_at
import easypaper.app.shared.generated.resources.today
import easypaper.app.shared.generated.resources.unit_mmhg
import easypaper.app.shared.generated.resources.water_amount
import easypaper.app.shared.generated.resources.write_feeling
import easypaper.app.shared.generated.resources.written_from
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.DraftBadge
import org.easypaper.ui.components.GrayButton
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.IosSwitch
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.components.AvatarButton
import org.easypaper.ui.components.OnboardingPage
import org.easypaper.ui.components.OptionsSheet
import org.easypaper.ui.components.ProgressRing
import org.easypaper.ui.components.SearchField
import org.easypaper.ui.components.SegmentedControl
import org.easypaper.ui.components.SheetOption
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Living sample of the design system (task 3): every token and component in light and dark, with
 * real-looking content. Screens of tasks 4, 5 and 10 replace it in App; keep it for reference.
 */
@Composable
fun DesignCatalog(dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    var showOptions by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(false) }
    var qrSelected by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        CatalogContent(dark, onDarkChange, { showOptions = true }, { showOnboarding = true }, qrSelected)
        OptionsSheet(
            visible = showOptions,
            title = stringResource(Res.string.v2_options_title),
            options = listOf(
                SheetOption(
                    title = stringResource(Res.string.v2_option_qr),
                    subtitle = stringResource(Res.string.v2_option_qr_subtitle),
                    icon = Res.drawable.ic_qr_code_2, enabled = true,
                    onClick = { qrSelected = true; showOptions = false },
                ),
                SheetOption(
                    title = stringResource(Res.string.v2_option_nearby),
                    subtitle = stringResource(Res.string.v2_option_nearby_subtitle),
                    icon = Res.drawable.ic_history, enabled = false, onClick = {},
                ),
                SheetOption(
                    title = stringResource(Res.string.v2_option_file),
                    subtitle = stringResource(Res.string.v2_option_file_subtitle),
                    icon = Res.drawable.ic_edit_note, enabled = false, onClick = {},
                ),
            ),
            onDismiss = { showOptions = false },
        )
        if (showOnboarding) {
            OnboardingPage(
                icon = Res.drawable.ic_favorite,
                title = stringResource(Res.string.v2_onboarding_title),
                body = stringResource(Res.string.v2_onboarding_body),
                primary = stringResource(Res.string.v2_onboarding_primary),
                onPrimary = { showOnboarding = false },
                secondary = stringResource(Res.string.v2_onboarding_secondary),
                onSecondary = { showOnboarding = false },
            ) {
                Text(stringResource(Res.string.v2_onboarding_detail), style = EasyPaperTheme.typography.footnote,
                    color = EasyPaperTheme.colors.secondaryLabel)
            }
        }
    }
}

@Composable
private fun CatalogContent(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onShowOptions: () -> Unit,
    onShowOnboarding: () -> Unit,
    qrSelected: Boolean,
) {
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val spacing = EasyPaperTheme.spacing
    Column(
        Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = spacing.contentMaxWidth).fillMaxWidth()
                .padding(horizontal = spacing.screenMargin, vertical = spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
        ) {
            Column {
                Text(stringResource(Res.string.today), style = type.largeTitle, color = colors.label)
                Text(stringResource(Res.string.catalog_date), style = type.subheadline, color = colors.secondaryLabel)
                Text(stringResource(Res.string.catalog_day), style = type.subheadline, color = colors.secondaryLabel)
            }

            GroupedSection {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.appearance_dark), style = type.body, color = colors.label,
                        modifier = Modifier.weight(1f))
                    IosSwitch(checked = dark, onCheckedChange = onDarkChange)
                }
            }

            GroupedSection {
                CategoryRow(
                    Category.BloodPressure, stringResource(Res.string.cat_blood_pressure), onClick = {},
                    trailing = { Text("08:10", style = type.footnote.tabular(), color = colors.secondaryLabel) },
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("128/84", style = type.title1.tabular(), color = colors.label)
                        Text(stringResource(Res.string.unit_mmhg), style = type.subheadline, color = colors.secondaryLabel,
                            modifier = Modifier.padding(bottom = spacing.xxs))
                        Text(stringResource(Res.string.pulse, "72"), style = type.subheadline.tabular(),
                            color = colors.secondaryLabel, modifier = Modifier.padding(start = spacing.xs, bottom = spacing.xxs))
                    }
                }
                RowSeparator()
                CategoryRow(
                    Category.BloodPressure, stringResource(Res.string.evening_reading),
                    trailing = { TintedButton(stringResource(Res.string.add), onClick = {}, icon = Res.drawable.ic_add) },
                )
            }

            GroupedSection {
                CategoryRow(
                    Category.Water, stringResource(Res.string.cat_water),
                    trailing = {
                        Text(stringResource(Res.string.water_amount, "1.25", "2"), style = type.subheadline.tabular(),
                            color = colors.label)
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        GrayButton("", onClick = {}, icon = Res.drawable.ic_remove)
                        GrayButton("", onClick = {}, icon = Res.drawable.ic_add)
                    }
                }
            }

            GroupedSection(footer = stringResource(Res.string.footer_privacy)) {
                CategoryRow(
                    Category.Food, stringResource(Res.string.cat_food), onClick = {},
                    trailing = { Text("13:00", style = type.footnote.tabular(), color = colors.secondaryLabel) },
                ) { Text(stringResource(Res.string.sample_food), style = type.body, color = colors.label) }
                RowSeparator()
                CategoryRow(Category.Medicines, stringResource(Res.string.cat_medicines)) {
                    Text(stringResource(Res.string.sample_medicine), style = type.body, color = colors.label)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Icon(painterResource(Res.drawable.ic_check_circle), contentDescription = null, tint = colors.confirmed,
                            modifier = Modifier.size(spacing.m))
                        Text(stringResource(Res.string.taken_at, "08:15"), style = type.footnote.tabular(), color = colors.confirmed)
                    }
                }
                RowSeparator()
                CategoryRow(Category.Diary, stringResource(Res.string.cat_diary), onClick = {}) {
                    Text(stringResource(Res.string.write_feeling), style = type.body, color = colors.link)
                }
                RowSeparator()
                CategoryRow(Category.DoctorQuestion, stringResource(Res.string.cat_question), onClick = {}) {
                    Text(stringResource(Res.string.ask_question), style = type.body, color = colors.link)
                }
            }

            GroupedSection(header = stringResource(Res.string.section_doctor)) {
                Column(Modifier.padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(stringResource(Res.string.summary), style = type.title2, color = colors.label)
                        DraftBadge(stringResource(Res.string.draft))
                    }
                    Text(stringResource(Res.string.written_from, "41"), style = type.footnote, color = colors.secondaryLabel)
                    Text(stringResource(Res.string.sample_summary), style = type.callout, color = colors.label)
                    Text(stringResource(Res.string.sample_sources), style = type.footnote.tabular(), color = colors.link)
                }
                RowSeparator(inset = spacing.m)
                Row(
                    Modifier.fillMaxWidth().background(colors.review).padding(horizontal = spacing.m, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.sample_med_form), style = type.body, color = colors.label, modifier = Modifier.weight(1f))
                    TintedButton(stringResource(Res.string.confirm), onClick = {})
                }
                RowSeparator(inset = spacing.m)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text("138/88 " + stringResource(Res.string.unit_mmhg), style = type.body.tabular(), color = colors.label, modifier = Modifier.weight(1f))
                    Icon(painterResource(Res.drawable.ic_check_circle), contentDescription = null, tint = colors.confirmed,
                        modifier = Modifier.size(spacing.m))
                    Text(stringResource(Res.string.confirmed_at, "10:24"), style = type.footnote.tabular(), color = colors.confirmed)
                    Spacer(Modifier.size(spacing.xs))
                    PlainButton(stringResource(Res.string.edit), onClick = {})
                }
            }

            V2CatalogSection(onShowOptions, onShowOnboarding, qrSelected)

            ProminentButton(stringResource(Res.string.show_qr), onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun V2CatalogSection(onShowOptions: () -> Unit, onShowOnboarding: () -> Unit, qrSelected: Boolean) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    var selected by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0.6f) }
    var profileSelected by remember { mutableStateOf(false) }
    val progressLabel = stringResource(Res.string.v2_progress_label)
    GroupedSection(header = stringResource(Res.string.v2_catalog_title)) {
        Column(Modifier.padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            SegmentedControl(
                options = listOf(
                    stringResource(Res.string.v2_period_day), stringResource(Res.string.v2_period_week),
                    stringResource(Res.string.v2_period_month), stringResource(Res.string.v2_period_half_year),
                ),
                selected = selected, onSelect = { selected = it },
            )
            SearchField(query, { query = it }, stringResource(Res.string.v2_search_placeholder))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                ProgressRing(progress, Modifier.semantics { contentDescription = progressLabel })
                Text(progressLabel, style = EasyPaperTheme.typography.body, color = colors.label,
                    modifier = Modifier.weight(1f))
                AvatarButton(stringResource(Res.string.v2_sample_initials), { profileSelected = !profileSelected })
            }
            GrayButton(stringResource(Res.string.v2_change_progress), onClick = {
                progress = if (progress >= 1f) 0f else (progress + 0.2f).coerceAtMost(1f)
            })
            if (profileSelected) Text(stringResource(Res.string.v2_profile_sample),
                style = EasyPaperTheme.typography.footnote, color = colors.secondaryLabel)
            GrayButton(stringResource(Res.string.v2_show_options), onShowOptions, Modifier.fillMaxWidth())
            if (qrSelected) Text(stringResource(Res.string.v2_qr_selected),
                style = EasyPaperTheme.typography.footnote, color = colors.secondaryLabel)
            PlainButton(stringResource(Res.string.v2_show_onboarding), onShowOnboarding)
        }
    }
}
