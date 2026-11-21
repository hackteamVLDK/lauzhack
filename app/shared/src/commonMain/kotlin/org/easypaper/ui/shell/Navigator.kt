package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * A stack of screens with an optional sheet on top, as on iOS: [push] drills in, [presentSheet]
 * raises a sheet, [back] closes the sheet first, then pops. The root never pops.
 */
@Stable
class Navigator<S : Any>(root: S) {
    var stack: List<S> by mutableStateOf(listOf(root))
        private set
    var sheet: S? by mutableStateOf(null)
        private set

    val current: S get() = stack.last()
    val canGoBack: Boolean get() = sheet != null || stack.size > 1

    fun push(screen: S) {
        stack = stack + screen
    }

    fun presentSheet(screen: S) {
        sheet = screen
    }

    fun dismissSheet() {
        sheet = null
    }

    /** Closes the sheet or pops one screen; false when there is nothing to go back to. */
    fun back(): Boolean = when {
        sheet != null -> { sheet = null; true }
        stack.size > 1 -> { stack = stack.dropLast(1); true }
        else -> false
    }

    /** Root plus [screen]: a detail chosen from a list (doctor sidebar). */
    fun resetTo(screen: S) {
        sheet = null
        stack = listOf(stack.first(), screen)
    }

    /** Back to the root screen, sheet closed (e.g. tapping the selected tab again). */
    fun popToRoot() {
        sheet = null
        stack = stack.take(1)
    }
}

/** System back (Android gesture, Esc on Desktop) goes through [navigator] while it can go back. */
@Composable
fun NavigatorBackHandler(navigator: Navigator<*>) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = navigator.canGoBack,
        onBackCancelled = {},
        onBackCompleted = { navigator.back() },
    )
}
