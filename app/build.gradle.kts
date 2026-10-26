plugins {
    // AGP 9 já compila Kotlin sozinho (built-in Kotlin): o plugin kotlin-android não é mais usado.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.juscelino.sonivell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.juscelino.sonivell"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
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
        // Chave de TESTE versionada no repositório. Para publicar de verdade, defina
        // SONIVELL_KEYSTORE, SONIVELL_STORE_PASSWORD, SONIVELL_KEY_ALIAS e
        // SONIVELL_KEY_PASSWORD (ex.: secrets do GitHub) com a chave real.
        create("release") {
            storeFile = file(System.getenv("SONIVELL_KEYSTORE") ?: "../keystore/sonivell-teste.jks")
            storePassword = System.getenv("SONIVELL_STORE_PASSWORD") ?: "sonivell123"
            keyAlias = System.getenv("SONIVELL_KEY_ALIAS") ?: "sonivell-teste"
            keyPassword = System.getenv("SONIVELL_KEY_PASSWORD") ?: "sonivell123"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
}
