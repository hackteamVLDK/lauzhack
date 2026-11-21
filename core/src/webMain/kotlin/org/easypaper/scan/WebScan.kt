package org.easypaper.scan

// shortcut: no recognition in the browser yet (patient types the name), add BarcodeDetector where the browser has it.
actual suspend fun readPhoto(photo: ByteArray): PhotoReading = PhotoReading(emptyList(), "")
