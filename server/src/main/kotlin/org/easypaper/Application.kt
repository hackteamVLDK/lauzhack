package org.easypaper

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import org.easypaper.catalog.CatalogService
import org.easypaper.catalog.CatalogStore
import org.easypaper.catalog.catalogRoutes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main() {
    val relay = Relay.fromEnv()
    val ai = AiService.fromEnv() // the OpenRouter key is read here from the environment and never leaves this process
    // medicine catalog snapshots written by :server:importCatalog (task 71); empty until the first import
    val catalog = CatalogService(CatalogStore.fromEnv())
    val webDir = File(
        System.getenv("EASYPAPER_WEB_DIR") ?: System.getProperty("easypaper.webDir")
            ?: "app/webApp/build/dist/wasmJs/productionExecutable",
    )
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080, host = "0.0.0.0") {
        module(relay, webDir.takeIf { it.isDirectory }, ai = ai, catalog = catalog)
    }.start(wait = true)
}

/**
 * Blind report relay, the AI routes (cloud, clinic module or off, see [AiService]) + the doctor's web app (wasmJs build) at "/" when [webDir] is given. Expired objects are
 * purged every [purgeEvery], so ciphertext never outlives the TTL even if nobody asks for it.
 */
fun Application.module(
    relay: Relay = Relay(),
    webDir: File? = null,
    purgeEvery: Duration = 10.minutes,
    ai: AiService = AiService.off(),
    aiToken: String? = System.getenv("EASYPAPER_CLINIC_TOKEN"),
    handoff: Handoff = Handoff(maxBytes = relay.maxBytes, maxTotalBytes = relay.maxTotalBytes),
    catalog: CatalogService? = null,
    accounts: Accounts? = null,
) {
    val accountService = accounts ?: Accounts.fromEnv(this)
    allowWebOrigins()
    protectAiRoutes(ai.mode, aiToken)
    launch {
        while (isActive) {
            delay(purgeEvery)
            relay.purge()
            handoff.purge()
        }
    }
    routing {
        relayRoutes(relay)
        handoffRoutes(handoff, aiToken)
        aiRoutes(ai)
        catalog?.let { catalogRoutes(it, ai) }
        accountRoutes(accountService)
        if (webDir != null) staticFiles("/", webDir) { default("index.html") }
    }
}

/**
 * The web app is also published on GitHub Pages (another origin than this server): those pages may call the API.
 * EASYPAPER_CORS_ORIGINS adds more, comma-separated ("http://localhost:8081" for the web dev server). Headers are only
 * added for these origins and nothing is refused here (same-origin pages and the apps send no CORS questions); no
 * cookies are used, the clinic token and the account session travel in headers.
 */
fun Application.allowWebOrigins(extra: String? = System.getenv("EASYPAPER_CORS_ORIGINS")) {
    val origins = (listOf(GITHUB_PAGES_ORIGIN) + extra.orEmpty().split(',')).map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.toSet()
    intercept(ApplicationCallPipeline.Plugins) {
        val origin = call.request.headers[HttpHeaders.Origin]?.trimEnd('/') ?: return@intercept
        if (origin !in origins) return@intercept
        call.response.header(HttpHeaders.AccessControlAllowOrigin, origin)
        call.response.header(HttpHeaders.Vary, HttpHeaders.Origin)
        if (call.request.httpMethod == HttpMethod.Options && call.request.headers[HttpHeaders.AccessControlRequestMethod] != null) {
            call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, PUT, DELETE")
            call.response.header(HttpHeaders.AccessControlAllowHeaders, "Content-Type, Authorization, X-EasyPaper-Clinic-Token")
            call.response.header(HttpHeaders.AccessControlMaxAge, "600")
            call.respond(HttpStatusCode.NoContent)
            finish()
        }
    }
}

const val GITHUB_PAGES_ORIGIN = "https://hackteamvldk.github.io"
