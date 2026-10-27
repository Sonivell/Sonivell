pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sonivell"
// -Psonivell.desktop=only: só o módulo do PC (o CI do .deb não precisa do SDK/NDK do Android).
val desktopMode = providers.gradleProperty("sonivell.desktop").orNull
if (desktopMode != "only") {
    include(":app")
}

// Motor SMART, trazido do LatentJam (Apache 2.0) — usado só pela versão "smart" do app.
if (desktopMode != "only") {
    include(":core:smart")
    include(":core:ort-ops")
    include(":core:map-native")
}

// Versão para PC (Linux): só entra no build quando pedida (-Psonivell.desktop=true),
// para não interferir no build do app Android.
if (desktopMode == "true" || desktopMode == "only") {
    include(":desktop")
    include(":desktop-smart")
}
