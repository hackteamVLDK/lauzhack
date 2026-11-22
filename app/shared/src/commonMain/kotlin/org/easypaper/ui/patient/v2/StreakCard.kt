package org.easypaper.ui.patient.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import easypaper.app.shared.generated.resources.*
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** A single announcement; the visual weekday initials and checkmarks are decorative. */
@Composable
internal fun StreakCard(streak: Streak, entriesToday: Int, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    val shapes = EasyPaperTheme.shapes
    val daysLabel = pluralStringResource(Res.plurals.p2_streak_days, streak.days)
    val todayLabel = stringResource(Res.string.p2_streak_today,
        pluralStringResource(Res.plurals.entries, entriesToday, entriesToday))
    val message = stringResource(when {
        streak.todayDone -> Res.string.p2_streak_done
        streak.days > 0 -> Res.string.p2_streak_keep
        else -> Res.string.p2_streak_start
    })
    val description = stringResource(Res.string.p2_streak_description, streak.days, daysLabel, todayLabel, message)
    val weekdays = stringArrayResource(Res.array.weekdays)
    Column(modifier.fillMaxWidth().clip(shapes.widget).background(colors.cell)
        .clearAndSetSemantics { contentDescription = description }
        .padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(stringResource(Res.string.p2_widget_count_value, streak.days), style = type.largeTitle, color = colors.label)
            Text(daysLabel, style = type.subheadline, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            streak.week.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    val outline = if (day.isToday) Modifier.border(spacing.glyphStroke, colors.tint, shapes.capsule)
                        else Modifier
                    Box(outline.padding(spacing.xxs), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(spacing.xl).clip(shapes.capsule)
                            .background(if (day.done) colors.tint else if (day.isFuture) colors.background else colors.fill),
                            contentAlignment = Alignment.Center) {
                            // Shape as well as color distinguishes a completed day.
                            if (day.done) Icon(painterResource(Res.drawable.ic_check_circle), null,
                                Modifier.size(spacing.tileIcon), tint = colors.onTint)
                        }
                    }
                    Text(weekdays.getOrElse(day.date.dayOfWeek.ordinal) { "" }.take(1),
                        style = type.caption1, color = colors.secondaryLabel)
                }
            }
        }
        Text(message, style = type.subheadline, color = colors.label)
        Text(todayLabel, style = type.footnote, color = colors.secondaryLabel)
    }
}
