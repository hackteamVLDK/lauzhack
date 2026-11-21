package org.easypaper.ui.patient.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import org.easypaper.model.Entry
import org.easypaper.storage.PatientRepository
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

@Composable
fun EntryPhoto(repository: PatientRepository, entry: Entry) {
    val ref = entry.photoReference ?: return
    val photo by produceState<ByteArray?>(null, repository, ref) {
        try { value = repository.photo(ref)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { value = null }
    }
    val bitmap = remember(photo) { photo?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    if (bitmap != null) Image(bitmap, stringResource(Res.string.p2_include_photos), Modifier.fillMaxWidth())
}
