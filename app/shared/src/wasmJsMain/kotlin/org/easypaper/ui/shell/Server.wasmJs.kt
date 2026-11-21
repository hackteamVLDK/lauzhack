package org.easypaper.ui.shell

@OptIn(ExperimentalWasmJsInterop::class)
private fun pageOrigin(): String = js("window.location.origin")

actual fun defaultServerUrl(): String? = pageOrigin()
