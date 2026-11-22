package org.easypaper.ui.shell

@OptIn(ExperimentalWasmJsInterop::class)
private fun pageSearch(): String = js("window.location.search")

actual fun webPageSearch(): String = pageSearch()
