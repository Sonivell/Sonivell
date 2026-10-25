/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 *
 * Vendored from LatentJam (github.com/Nikita-sud/latentjam) into Sonivell;
 * build trimmed to the Android target, tests and iOS sources removed.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    // Library hygiene: every public declaration must state its visibility and
    // return type explicitly, so the API surface is always deliberate.
    explicitApi()

    androidLibrary {
        namespace = "io.github.nikitasud.latentjam.smart"
        compileSdk = 36
        minSdk = 24
        // Host tests are OPT-IN on com.android.kotlin.multiplatform.library.
        // Without this block, commonTest silently never runs on the JVM.
    }

    // iOS inference is injected by the application shell through IosInferenceProvider.
    // The shell owns the CocoaPods ONNX Runtime framework, so :core:smart must not
    // bind or link a second native runtime into the static Kotlin framework.
    // iosX64 (Intel simulators) deliberately omitted; add it if Intel hosts return.

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // api, not implementation: org.koin.core.module.Module appears in
            // this module's public API (smartEngineModule).
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.onnxruntime.android)
            implementation(project(":core:ort-ops"))
            implementation(project(":core:map-native"))
        }
        // An iOS test target exists so the native ports can be gated on the same
        // fixtures the JVM ones are. Without it the iOS actuals — the tokenizer's
        // Unicode predicates especially — are unverified, and a mismatch there
        // shifts vectors silently rather than failing anything.
    }
}

abstract class GenerateMapBuildFlags : DefaultTask() {
    @get:Input abstract val experimental: Property<Boolean>
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @TaskAction fun generate() {
        val output = destination.file("MapBuildFlags.kt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            "package io.github.nikitasud.latentjam.smart.cluster\n" +
                "internal object MapBuildFlags { const val EXPERIMENTAL = ${experimental.get()} }\n",
        )
    }
}
val mapBuildFlags = tasks.register<GenerateMapBuildFlags>("generateMapBuildFlags") {
    experimental = providers.gradleProperty("latentjam.experimentalMap").map(String::toBoolean).orElse(false)
    destination = layout.buildDirectory.dir("generated/mapFlags")
}
kotlin.sourceSets.commonMain { kotlin.srcDir(mapBuildFlags) }
