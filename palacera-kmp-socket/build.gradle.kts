import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.gradle.ktlint.plugin)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover.core)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
        }
    }
}

android {
    namespace = "com.palacera.kmpsocket"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
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
