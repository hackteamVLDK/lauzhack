package org.easypaper.ui.patient.medications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.easypaper.catalog.Candidate
import org.easypaper.catalog.Catalog
import org.easypaper.catalog.CatalogException
import org.easypaper.catalog.CatalogProduct
import org.easypaper.catalog.CatalogQuery
import org.easypaper.catalog.Resolution
import org.easypaper.catalog.ResolutionKind
import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import org.easypaper.scan.PhotoReading
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionOnlineTest {
    private val photo = byteArrayOf(1, 2, 3)
    private val candidate = Candidate(CatalogProduct("ua:test", MedicineProduct("НУРОФЄН", "НУРОФЄН",
        source = RegistrySource("ua-drlz", "test", "UA", "test", "https://example.invalid", "2026-11-22"))))

    @Test
    fun emptyLocalOcrOffersOnlineWithoutSendingUntilRequested() = runTest {
        var uploads = 0
        val catalog = object : Catalog {
            override suspend fun resolve(query: CatalogQuery): Resolution = error("No local evidence to resolve")
            override suspend fun readLabel(jpeg: ByteArray): Resolution {
                uploads++
                assertContentEquals(photo, jpeg)
                return Resolution(ResolutionKind.EXACT, listOf(candidate))
            }
        }
        val recognition = Recognition(catalog) { PhotoReading(emptyList(), "") }
        recognition.start()
        recognition.onPhoto(photo)
        assertTrue(recognition.canReadOnline)
        assertEquals(0, uploads)
        recognition.readOnline()
        assertEquals(candidate, assertIs<RecognitionStep.Card>(recognition.step).candidate)
        assertEquals(1, uploads)
        assertFalse(recognition.canReadOnline)
    }

    @Test
    fun notFoundOnlineUsesTheNormalCandidateStep() = runTest {
        val catalog = object : Catalog {
            override suspend fun resolve(query: CatalogQuery) = Resolution(ResolutionKind.NOT_FOUND)
            override suspend fun readLabel(jpeg: ByteArray) = Resolution(ResolutionKind.CANDIDATES, listOf(candidate))
        }
        val recognition = Recognition(catalog) { PhotoReading(emptyList(), "unreadable") }
        recognition.onPhoto(photo)
        assertTrue(recognition.canReadOnline)
        recognition.readOnline()
        assertEquals(listOf(candidate), assertIs<RecognitionStep.Result>(recognition.step).resolution.candidates)
    }

    @Test
    fun unavailablePreservesRetryButTypedSearchAndCloseForgetThePhoto() = runTest {
        val catalog = object : Catalog {
            override suspend fun resolve(query: CatalogQuery) = Resolution(ResolutionKind.NOT_FOUND)
            override suspend fun readLabel(jpeg: ByteArray): Resolution = throw CatalogException(503, "unavailable")
        }
        val recognition = Recognition(catalog) { PhotoReading(emptyList(), "unreadable") }
        recognition.onPhoto(photo)
        recognition.readOnline()
        assertTrue(recognition.onlineUnavailable)
        assertTrue(recognition.canReadOnline)
        recognition.onLabelText("manually typed")
        assertFalse(recognition.canReadOnline)
        assertFalse(recognition.onlineUnavailable)
        recognition.close()
        assertNull(recognition.step)
    }

    @Test
    fun aLateOnlineResponseDoesNotReopenAClosedSheet() = runTest {
        val reply = CompletableDeferred<Resolution>()
        val catalog = object : Catalog {
            override suspend fun resolve(query: CatalogQuery) = Resolution(ResolutionKind.NOT_FOUND)
            override suspend fun readLabel(jpeg: ByteArray) = reply.await()
        }
        val recognition = Recognition(catalog) { PhotoReading(emptyList(), "unreadable") }
        recognition.onPhoto(photo)
        val request = launch { recognition.readOnline() }
        runCurrent()
        recognition.close()
        reply.complete(Resolution(ResolutionKind.EXACT, listOf(candidate)))
        request.join()
        assertNull(recognition.step)
    }
}
