plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
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
    implementation(libs.ktor.clientCio)
    implementation(libs.angus.mail)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

// :server:run starts in server/, so the doctor's web app (wasmJs build) is located from the repo root
tasks.named<JavaExec>("run") {
    systemProperty("easypaper.webDir", rootProject.file("app/webApp/build/dist/wasmJs/productionExecutable").path)
}

// Medicine catalog import (task 71): ./gradlew :server:importCatalog --args="fr ch eu" [--from <dir>]
// Snapshots go to EASYPAPER_CATALOG_DIR (default server/data/catalog); repeatable, keyed by registry ids.
tasks.register<JavaExec>("importCatalog") {
    group = "application"
    description = "Downloads the official medicine registries and writes catalog snapshots"
    mainClass = "org.easypaper.catalog.ImportKt"
    classpath = sourceSets["main"].runtimeClasspath
    maxHeapSize = "2g"
}
