package org.easypaper

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

/** The web app on GitHub Pages calls the public server from another origin; nobody else gets CORS headers. */
class WebOriginsTest {
    private fun app(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(Relay()) }
        test()
    }

    @Test
    fun pagesPreflightIsAnsweredBeforeTheClinicTokenCheck() = app {
        val response = client.options("/ai/status") {
            header(HttpHeaders.Origin, GITHUB_PAGES_ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "X-EasyPaper-Clinic-Token")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(GITHUB_PAGES_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue("X-EasyPaper-Clinic-Token" in response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty())
    }

    @Test
    fun pagesRequestsCarryTheHeaderAndOtherOriginsDoNot() = app {
        val id = "rep-12345678"
        val fromPages = client.post("/reports/$id") { header(HttpHeaders.Origin, GITHUB_PAGES_ORIGIN); setBody(ByteArray(10)) }
        assertEquals(HttpStatusCode.Created, fromPages.status)
        assertEquals(GITHUB_PAGES_ORIGIN, fromPages.headers[HttpHeaders.AccessControlAllowOrigin])
        val other = client.get("/reports/$id") { header(HttpHeaders.Origin, "https://example.org") }
        assertEquals(HttpStatusCode.OK, other.status) // never refused here: the browser enforces it
        assertNull(other.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
