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
include(":app")

// Motor SMART, trazido do LatentJam (Apache 2.0) — usado só pela versão "smart" do app.
include(":core:smart")
include(":core:ort-ops")
include(":core:map-native")

// Versão para PC (Linux): só entra no build quando pedida (-Psonivell.desktop=true),
// para não interferir no build do app Android.
if (providers.gradleProperty("sonivell.desktop").orNull == "true") {
    include(":desktop")
}
