package org.easypaper.ui.patient.v2

import org.easypaper.ui.shell.LocalWidePatientLayout
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import org.easypaper.ui.components.*
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BrowseTab(settings: PatientSettings, onProfile: () -> Unit, onDeveloper: () -> Unit, onCategory: (Category) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val categories = Category.entries.map { it to categoryTitle(it) }.filter { it.second.contains(query.trim(), ignoreCase = true) }
    // a phone has two tiles per row; the wide (sidebar) layout of a desktop or tablet window three
    val columns = if (LocalWidePatientLayout.current) 3 else 2
    LargeTitleScaffold(stringResource(Res.string.p2_browse), bottomInset = false,
        actions = { AvatarButton(initials(settings.name.value), onProfile) }, onTitleLongPress = onDeveloper) {
        item {
            SearchField(query, { query = it }, stringResource(Res.string.p2_search))
            Spacer(Modifier.height(spacing.sectionGap))
        }
        item {
            Text(stringResource(Res.string.p2_browse_categories), style = EasyPaperTheme.typography.title2,
                color = colors.label, modifier = Modifier.semantics { heading() }.padding(bottom = spacing.s))
        }
        if (categories.isEmpty()) {
            item { PatientMessage(stringResource(Res.string.p2_no_results)) }
        } else {
            items(categories.chunked(columns), key = { row -> row.first().first.name }) { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = spacing.s).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    row.forEach { (category, title) ->
                        key(category) {
                            BrowseCategoryTile(category, title, { onCategory(category) },
                                Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BrowseCategoryTile(category: Category, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val foreground = category.browseText
    Column(modifier.heightIn(min = spacing.browseTileMinHeight).clip(EasyPaperTheme.shapes.widget)
        .background(Brush.linearGradient(category.browseGradient(EasyPaperTheme.colors)))
        .clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = LocalIndication.current,
            role = Role.Button, onClick = onClick,
        )
        .padding(spacing.m)) {
        Icon(painterResource(category.lineIcon), contentDescription = null,
            tint = foreground, modifier = Modifier.size(spacing.xxl))
        Spacer(Modifier.weight(1f).heightIn(min = spacing.m))
        Text(title, style = EasyPaperTheme.typography.headline, color = foreground)
    }
}
