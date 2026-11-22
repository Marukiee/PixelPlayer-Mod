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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.philburk")
                includeGroup("com.github.racra")
            }
        }
    }
}

rootProject.name = "PixelPlayerOSS"
include(":app")
// Marks shared update card, a copy of Keeper android/markupdate (see markupdate/BRON). Never change it here.
include(":markupdate:core", ":markupdate:card")
include(":baselineprofile")
