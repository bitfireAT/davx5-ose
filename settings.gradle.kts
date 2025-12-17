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
if (System.getenv("GRADLE_BUILDCACHE_URL") != null) {
    buildCache {
        remote<HttpBuildCache> {
            url = uri(System.getenv("GRADLE_BUILDCACHE_URL"))
            credentials {
                username = System.getenv("GRADLE_BUILDCACHE_USERNAME")
                password = System.getenv("GRADLE_BUILDCACHE_PASSWORD")
            }
            isPush = true        // read/write
        }
    }
}

include(":app")
