package org.easypaper

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import java.io.File

fun main() {
    val relay = Relay.fromEnv()
    val webDir = File(System.getenv("EASYPAPER_WEB_DIR") ?: "app/webApp/build/dist/wasmJs/productionExecutable")
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080, host = "0.0.0.0") {
        module(relay, webDir.takeIf { it.isDirectory })
    }.start(wait = true)
}

/** Blind report relay + the doctor's web app (wasmJs build) at "/" when [webDir] is given. */
fun Application.module(relay: Relay = Relay(), webDir: File? = null) {
    routing {
        relayRoutes(relay)
        if (webDir != null) staticFiles("/", webDir) { default("index.html") }
    }
}
