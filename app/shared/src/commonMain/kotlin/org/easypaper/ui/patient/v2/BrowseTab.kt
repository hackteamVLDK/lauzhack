package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.*
import org.easypaper.ui.components.*
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun BrowseTab(settings: PatientSettings, onProfile: () -> Unit, onDeveloper: () -> Unit, onCategory: (Category) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val categories = Category.entries.map { it to categoryTitle(it) }.filter { it.second.contains(query.trim(), ignoreCase = true) }
    LargeTitleScaffold(stringResource(Res.string.p2_browse), bottomInset = false,
        actions = { AvatarButton(initials(settings.name.value), onProfile) }, onTitleLongPress = onDeveloper) {
        item {
            SearchField(query, { query = it }, stringResource(Res.string.p2_search))
            Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
        }
        item {
            GroupedSection {
                categories.forEachIndexed { i, (category, title) ->
                    if (i > 0) RowSeparator()
                    CategoryRow(category, title, onClick = { onCategory(category) })
                }
                if (categories.isEmpty()) PatientMessage(stringResource(Res.string.p2_no_results))
            }
        }
    }
}
