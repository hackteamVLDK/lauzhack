package org.easypaper

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main() {
    val relay = Relay.fromEnv()
    val webDir = File(
        System.getenv("EASYPAPER_WEB_DIR") ?: System.getProperty("easypaper.webDir")
            ?: "app/webApp/build/dist/wasmJs/productionExecutable",
    )
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080, host = "0.0.0.0") {
        module(relay, webDir.takeIf { it.isDirectory })
    }.start(wait = true)
}

/**
 * Blind report relay + the doctor's web app (wasmJs build) at "/" when [webDir] is given. Expired objects are
 * purged every [purgeEvery], so ciphertext never outlives the TTL even if nobody asks for it.
 */
fun Application.module(relay: Relay = Relay(), webDir: File? = null, purgeEvery: Duration = 10.minutes) {
    launch {
        while (isActive) {
            delay(purgeEvery)
            relay.purge()
        }
    }
    routing {
        relayRoutes(relay)
        if (webDir != null) staticFiles("/", webDir) { default("index.html") }
    }
}
