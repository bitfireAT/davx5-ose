/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()

        // AboutLibraries
        maven("https://plugins.gradle.org/m2/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // AppIntro, dav4jvm
        maven("https://jitpack.io")

        // To use ViewModel and Material 3 with Nav3
        // See: https://developer.android.com/guide/navigation/navigation-3/get-started#artifacts
        maven {
            // View latest build id here: https://androidx.dev/snapshots/builds
            url = uri("https://androidx.dev/snapshots/builds/13550935/artifacts/repository")
        }
    }
}

include(":app")
