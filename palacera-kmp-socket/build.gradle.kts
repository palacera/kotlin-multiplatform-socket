import dev.mokkery.gradle.ApplicationRule
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.gradle.ktlint.plugin)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover.core)
    alias(libs.plugins.mokkery)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

//    @OptIn(ExperimentalWasmDsl::class)
//    wasmJs {
//        browser()
//    }

    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
            implementation("io.ktor:ktor-network:2.3.9")
            implementation("org.jetbrains.kotlinx:atomicfu:0.23.2")
            implementation("co.touchlab:kermit:2.0.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            //implementation(libs.kotlin.test.junit)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0")

        }

        androidMain.dependencies {
            implementation("com.github.palacera:android-app-context:v0.0.1")
        }
    }
}

android {
    namespace = "com.palacera.kmpsocket"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        // NOTE: targetSdk is deprecated for android libraries. minSdk is sufficient.
    }
}

ktlint {
    version.set(libs.versions.gradle.ktlint.core.get())
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom("${project.rootDir}/config/detekt/detekt-config.yml")
    source.setFrom(kotlin.sourceSets.flatMap { it.kotlin.sourceDirectories })
    ignoreFailures = false
}
