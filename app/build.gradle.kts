plugins {
    // AGP 9 já compila Kotlin sozinho (built-in Kotlin): o plugin kotlin-android não é mais usado.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Assinatura do release (ver signingConfigs): a chave real vem do ambiente; a de teste só se pedida.
val releaseKeystore: String? = System.getenv("SONIVELL_KEYSTORE")?.takeIf { it.isNotBlank() }
val useTestKey: Boolean = providers.gradleProperty("sonivell.chaveDeTeste").orNull == "true"

fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("SONIVELL_KEYSTORE foi definido, mas falta $name.")

android {
    namespace = "com.juscelino.sonivell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.juscelino.sonivell"
        minSdk = 26
        targetSdk = 35
        // Cresce a cada build do CI (número da execução), para o Android aceitar a atualização por
        // cima da versão anterior; build local fica em 1 (auditoria CI-03).
        versionCode = System.getenv("SONIVELL_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    // Duas edições do mesmo app:
    //  - lite:  só o player, poucos MB
    //  - smart: inclui o motor SMART do LatentJam (análise do som no aparelho)
    // Instaláveis lado a lado, porque o SMART tem applicationId próprio.
    flavorDimensions += "edition"
    productFlavors {
        create("lite") {
            dimension = "edition"
        }
        create("smart") {
            dimension = "edition"
            applicationIdSuffix = ".smart"
            versionNameSuffix = "-smart"
            // Só 64 bits ARM: praticamente todo celular atual, e o APK fica bem menor.
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    signingConfigs {
        // Chave de release: só pelas variáveis SONIVELL_KEYSTORE, SONIVELL_STORE_PASSWORD,
        // SONIVELL_KEY_ALIAS e SONIVELL_KEY_PASSWORD (secrets do CI ou do seu computador).
        // A chave de TESTE versionada (keystore/sonivell-teste.jks) é pública: qualquer um assina com ela.
        // Por isso só é usada quando pedida de propósito (-Psonivell.chaveDeTeste=true), e o APK sai
        // com "-teste" na versão. Sem nenhuma das duas, o release não é empacotado (ver abaixo).
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = requiredEnv("SONIVELL_STORE_PASSWORD")
                keyAlias = requiredEnv("SONIVELL_KEY_ALIAS")
                keyPassword = requiredEnv("SONIVELL_KEY_PASSWORD")
            }
        } else if (useTestKey) {
            create("release") {
                storeFile = file("../keystore/sonivell-teste.jks")
                storePassword = "sonivell123"
                keyAlias = "sonivell-teste"
                keyPassword = "sonivell123"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            if (releaseKeystore == null && useTestKey) versionNameSuffix = "-teste"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Os modelos já são compactos; comprimir de novo só deixa a leitura mais lenta.
        noCompress += listOf("onnx", "bin")
    }
}

// Sem chave de release e sem pedir a de teste, empacotar o release falha com uma explicação, em vez
// de assinar em silêncio com uma chave pública (auditoria SEC-01) ou gerar um APK que não instala.
val missingReleaseKey = releaseKeystore == null && !useTestKey
tasks.matching { it.name.startsWith("package") && it.name.endsWith("Release") }.configureEach {
    val missing = missingReleaseKey
    doFirst {
        if (missing) {
            throw GradleException(
                "Sem chave de release. Defina SONIVELL_KEYSTORE, SONIVELL_STORE_PASSWORD, SONIVELL_KEY_ALIAS e " +
                    "SONIVELL_KEY_PASSWORD, ou rode com -Psonivell.chaveDeTeste=true para um APK de teste (não distribua).",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.coil.compose)

    // Só a edição SMART leva o motor e o ONNX Runtime.
    "smartImplementation"(project(":core:smart"))
    "smartImplementation"(libs.koin.core)

    // Testes unitários no JVM (lógica sem Android: backup, gravações, busca…). ./gradlew testLiteDebugUnitTest
    testImplementation(libs.junit)
}
