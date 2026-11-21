plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "org.easypaper"
version = "1.0.0"
application {
    mainClass = "org.easypaper.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

// :server:run starts in server/, so the doctor's web app (wasmJs build) is located from the repo root
tasks.named<JavaExec>("run") {
    systemProperty("easypaper.webDir", rootProject.file("app/webApp/build/dist/wasmJs/productionExecutable").path)
}
