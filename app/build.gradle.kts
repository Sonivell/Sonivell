plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.juscelino.sonivell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.juscelino.sonivell"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
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
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.coil.compose)
}
