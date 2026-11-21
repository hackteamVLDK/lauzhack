import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    android {
       namespace = "org.easypaper.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       // device tests: commonTest + androidDeviceTest on an emulator/phone (real Android Keystore)
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    // jvmAndAndroidMain: code shared by Desktop and Android only (java.io files, e.g. FileBlobStore)
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serializationJson)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.providerOptimal)
            implementation(libs.ktor.clientCore)
            api(libs.kotlinx.coroutinesCore) // PatientRepository exposes StateFlow
            api(libs.koog.promptExecutorModel) // shared AI logic on Koog (org.easypaper.ai)
        }
        jvmMain.dependencies {
            // real providers: server (cloud, key only there) and the clinic module (local Ollama)
            implementation(libs.koog.openrouterClient)
            implementation(libs.koog.ollamaClient)
            implementation(libs.koog.httpClientKtor)
            implementation(libs.ktor.clientCio)
            implementation(libs.zxing.core) // package codes from photo files on Desktop (task 28)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.barcode)
            implementation(libs.mlkit.text)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
            implementation(libs.zxing.core) // draws real DataMatrix / EAN-13 for ML Kit to read
        }
    }
}