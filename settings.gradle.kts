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
