/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

pluginManagement {
    repositories {              // used for resolving plugins
        google()                // Android plugins
        gradlePluginPortal()    // most plugins, including AboutLibraries
    }
}

dependencyResolutionManagement {
    // Repositories declared in settings.gradle(.kts) override those declared in a project.
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS

    repositories {              // used for resolving dependencies
        mavenCentral()          // most Java stuff
        google()                // Android libs

        maven("https://jitpack.io")     // AppIntro, dav4jvm, synctools
    }
}

// use remote build cache, if configured
val buildCacheUrl = System.getenv("GRADLE_BUILDCACHE_URL")
if (!buildCacheUrl.isNullOrEmpty()) {
    buildCache {
        remote<HttpBuildCache> {
            url = uri(buildCacheUrl)
            credentials {
                username = System.getenv("GRADLE_BUILDCACHE_USERNAME")
                password = System.getenv("GRADLE_BUILDCACHE_PASSWORD")
            }
            isPush = true        // read/write
        }
    }
}

include(":app-ose")
include(":core")
