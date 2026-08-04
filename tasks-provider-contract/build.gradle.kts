/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    id("davx5.common-buildconfig")
}

android {
    namespace = "at.bitfire.tasks.contract"

    defaultConfig {
        aarMetadata {
            minCompileSdk = 24
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    // deliberately minimal: this module is what third-party frontend apps depend on,
    // it must not pull in anything DAVx5-internal (Room, synctools, Hilt, ...)
    compileOnly(libs.androidx.annotation)
}
