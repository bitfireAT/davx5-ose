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
    namespace = "com.davx5.feature.featureflags.newaccountsystem.experimental"
}

dependencies {
    api(project(":features:feature-flags:new-account-system:api"))
    implementation(project(":features:experimental-settings:shared"))
    implementation(project(":core"))

    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)
}
