import java.util.Properties

plugins {
    // AGP 9 já compila Kotlin sozinho (built-in Kotlin): o plugin kotlin-android não é mais usado.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Assinatura do release (ver signingConfigs): a chave vem só do ambiente, nunca do repositório.
val releaseKeystore: String? = System.getenv("SONIVELL_KEYSTORE")?.takeIf { it.isNotBlank() }

fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("SONIVELL_KEYSTORE foi definido, mas falta $name.")

// Versão única (version.properties na raiz): a mesma no APK lite, no SMART, no .deb e no AppImage.
val sonivellVersionName: String = Properties()
    .apply { rootProject.file("version.properties").inputStream().use { load(it) } }
    .getProperty("versionName")
    ?.takeIf { Regex("""\d+\.\d+\.\d+""").matches(it) }
    ?: throw GradleException("version.properties: versionName precisa ser X.Y.Z")
val sonivellVersionCode: Int = sonivellVersionName.split('.').map(String::toInt)
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

android {
    namespace = "com.juscelino.sonivell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.juscelino.sonivell"
        minSdk = 26
        targetSdk = 35
        // Os dois vêm de version.properties: todo build da mesma versão (CI, local, release) tem o
        // mesmo versionCode, e cada versão nova tem um maior, para o Android aceitar a atualização.
        versionCode = sonivellVersionCode
        versionName = sonivellVersionName
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
        // Não existe mais chave de teste no repositório: sem a chave, o release não é empacotado (ver abaixo).
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = requiredEnv("SONIVELL_STORE_PASSWORD")
                keyAlias = requiredEnv("SONIVELL_KEY_ALIAS")
                keyPassword = requiredEnv("SONIVELL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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

// Sem a chave de release, empacotar o release falha com uma explicação, em vez de gerar um APK
// sem assinatura, que não instala (auditoria SEC-01). Para testar no aparelho, use o debug.
val missingReleaseKey = releaseKeystore == null
tasks.matching { it.name.startsWith("package") && it.name.endsWith("Release") }.configureEach {
    val missing = missingReleaseKey
    doFirst {
        if (missing) {
            throw GradleException(
                "Sem chave de release. Defina SONIVELL_KEYSTORE, SONIVELL_STORE_PASSWORD, SONIVELL_KEY_ALIAS e " +
                    "SONIVELL_KEY_PASSWORD (a chave fica fora do repositório). Para testar no aparelho, use assembleLiteDebug.",
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
    constraints {
        implementation(libs.guava) { because("CVE-2026-102554 (CVSS 8.2) na guava 33.3.1, trazida pelo media3") }
    }

    implementation(libs.coil.compose)

    // Só a edição SMART leva o motor e o ONNX Runtime.
    "smartImplementation"(project(":core:smart"))
    "smartImplementation"(libs.koin.core)

    // Testes unitários no JVM (lógica sem Android: backup, gravações, busca…). ./gradlew testLiteDebugUnitTest
    testImplementation(libs.junit)
}
