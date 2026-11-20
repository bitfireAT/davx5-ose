/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("davx5.common-buildconfig")
}

android {
    // TODO: Adjust namespace
    namespace = "com.davx5.feature.featureflags.template.alwayson"
}

dependencies {
    // TODO: Adjust reference to API module for this feature flag
    api(project(":features:feature-flags:template:api"))

    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)
}
