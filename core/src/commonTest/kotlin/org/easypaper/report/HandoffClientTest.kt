package org.easypaper.report

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class, InternalAPI::class)
class HandoffClientTest {
    private class Engine(
        override val dispatcher: CoroutineDispatcher,
        val respond: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ) : HttpClientEngineBase("handoff-test") {
        override val config = HttpClientEngineConfig()
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val (status, body) = respond(data)
            return HttpResponseData(status, GMTDate(), Headers.Empty, HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body), callContext())
        }
    }

    @Test
    fun reportIsPolledEveryTwoSeconds() = runTest {
        val times = mutableListOf<Long>()
        val engine = Engine(StandardTestDispatcher(testScheduler)) {
            times += testScheduler.currentTime
            if (times.size < 3) HttpStatusCode.NoContent to "" else HttpStatusCode.OK to "packet"
        }
        val http = HttpClient(engine)
        try {
            val api = HandoffClient("https://example.test", http, now = { testScheduler.currentTime })
            assertContentEquals("packet".encodeToByteArray(), api.awaitReport("123456", "clinic", "claim"))
            assertEquals(listOf(0L, 2_000L, 4_000L), times)
        } finally { http.close() }
    }

    @Test
    fun pollingEndsAtEachDeadline() = runTest {
        val engine = Engine(StandardTestDispatcher(testScheduler)) { HttpStatusCode.NoContent to "" }
        val http = HttpClient(engine)
        try {
            val api = HandoffClient("https://example.test", http, now = { testScheduler.currentTime })
            assertEquals(HandoffException.Kind.Expired, assertFailsWith<HandoffException> {
                api.awaitDoctorKey(HandoffSlot("123456", "creator", 4_500))
            }.kind)
            assertEquals(4_500, testScheduler.currentTime)
            assertEquals(HandoffException.Kind.Expired, assertFailsWith<HandoffException> {
                api.awaitReport("123456", "clinic", "claim")
            }.kind)
            assertEquals(4_500 + HANDOFF_TTL_MILLIS, testScheduler.currentTime)
        } finally { http.close() }
    }

    @Test
    fun deadlineAlsoCancelsAnInFlightRequest() = runTest {
        val engine = Engine(StandardTestDispatcher(testScheduler)) { awaitCancellation() }
        val http = HttpClient(engine)
        try {
            val api = HandoffClient("https://example.test", http, now = { testScheduler.currentTime })
            assertEquals(HandoffException.Kind.Expired, assertFailsWith<HandoffException> {
                api.awaitDoctorKey(HandoffSlot("123456", "creator", 500))
            }.kind)
            assertEquals(500, testScheduler.currentTime)
        } finally { http.close() }
    }

    @Test
    fun ioExceptionsAndCancellationPropagate() = runTest {
        for (failure in listOf(IOException("offline"), CancellationException("screen closed"))) {
            val engine = Engine(StandardTestDispatcher(testScheduler)) { throw failure }
            val http = HttpClient(engine)
            try {
                val api = HandoffClient("https://example.test", http)
                val actual = if (failure is IOException) assertFailsWith<IOException> { api.create() }
                else assertFailsWith<CancellationException> { api.create() }
                // coroutines may copy an exception for stack-trace recovery: same type and message, not the same object
                assertEquals(failure.message, actual.message)
            } finally { http.close() }
        }
    }

    @Test
    fun httpErrorsAreTypedEvenWhenClientExpectsSuccess() = runTest {
        val errors = mapOf(
            HttpStatusCode.NotFound to HandoffException.Kind.NotFound,
            HttpStatusCode.Unauthorized to HandoffException.Kind.Unauthorized,
            HttpStatusCode.TooManyRequests to HandoffException.Kind.TooMany,
            HttpStatusCode.Conflict to HandoffException.Kind.Conflict,
            HttpStatusCode.InternalServerError to HandoffException.Kind.Network,
        )
        for ((status, kind) in errors) {
            val http = HttpClient(Engine(StandardTestDispatcher(testScheduler)) { status to "" }) { expectSuccess = true }
            try {
                assertEquals(kind, assertFailsWith<HandoffException> { HandoffClient("https://example.test", http).create() }.kind)
            } finally { http.close() }
        }
    }
}
