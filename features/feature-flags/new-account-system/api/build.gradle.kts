/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    id("davx5.common-buildconfig")
}

android {
    namespace = "com.davx5.feature.featureflags.newaccountsystem.api"
}

dependencies {
    api(project(":features:feature-flags:api"))

    coreLibraryDesugaring(libs.android.desugaring)
}
