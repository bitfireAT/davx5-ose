/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    id("davx5.common-buildconfig")
}

android {
    namespace = "com.davx5.feature.preconfiguredsettings.api"
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.hilt.android.base)
}
