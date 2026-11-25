import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    systemProperty("easypaper.demo", providers.systemProperty("easypaper.demo").getOrElse("true"))
}

compose.desktop {
    application {
        mainClass = "org.easypaper.MainKt"

        nativeDistributions {
            modules("jdk.httpserver") // Desktop Google OAuth loopback listener in packaged runtimes
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.easypaper"
            packageVersion = "1.0.0"
        }
    }
}
