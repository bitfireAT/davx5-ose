/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    id("davx5.common-buildconfig")
}

android {
    namespace = "com.davx5.feature.featureflags.shared"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":features:experimental-settings:ui"))
    implementation(project(":core"))
    implementation(project(":features:feature-flags:api"))

    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.androidx.annotation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines)
}
