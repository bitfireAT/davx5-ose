/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    id("davx5.common-buildconfig")
}

android {
    namespace = "com.davx5.feature.experimentalsettings.api"
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
