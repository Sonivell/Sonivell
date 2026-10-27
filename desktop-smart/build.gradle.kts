// Motor SMART do LatentJam para o PC: as fontes comuns de :core:smart (compartilhadas, sem cópia) mais as
// peças de plataforma em jvmMain (ffmpeg no lugar do MediaCodec, ONNX Runtime para JVM, índice em arquivo).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(rootProject.file("core/smart/src/commonMain/kotlin"))
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(libs.koin.core)
            }
        }
        jvmMain {
            dependencies {
                api(libs.onnxruntime.jvm)
            }
            // Modelos (ml/*.onnx, *.bin) como recursos do pacote.
            resources.srcDir(rootProject.file("app/src/smart/assets"))
            resources.srcDir(layout.buildDirectory.dir("native-res"))
        }
    }
}

// libljq4.so: operadores de 4 bits do LatentJam, compilados com CMake para o PC (x86_64).
val buildLjq4 = tasks.register<Exec>("buildLjq4") {
    val src = rootProject.file("core/ort-ops/src/main/cpp")
    val cmakeOut = layout.buildDirectory.dir("ljq4-cmake").get().asFile
    val dest = layout.buildDirectory.dir("native-res/native/linux-x86_64").get().asFile
    inputs.dir(src)
    outputs.dir(dest)
    commandLine(
        "bash", "-c",
        "set -e; cmake -S '${src.absolutePath}' -B '${cmakeOut.absolutePath}' -DCMAKE_BUILD_TYPE=Release && " +
            "cmake --build '${cmakeOut.absolutePath}' -j && mkdir -p '${dest.absolutePath}' && " +
            "cp '${cmakeOut.absolutePath}/libljq4.so' '${dest.absolutePath}/libljq4.so'",
    )
}
tasks.named("jvmProcessResources") { dependsOn(buildLjq4) }
