/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("davx5.common-buildconfig")
}

android {
    namespace = "com.davx5.feature.experimentalsettings.ui"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":features:experimental-settings:api"))
    implementation(project(":core"))

    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.toolingPreview)
}
