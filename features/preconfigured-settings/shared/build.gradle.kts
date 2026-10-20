/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("davx5.common-buildconfig")
}

android {
    namespace = "com.davx5.feature.preconfiguredsettings.shared"
    resourcePrefix = "preconfigured_settings_"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":synctools"))

    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.androidx.annotation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.hilt.android.base)

    // Avoids an erroneous IDE warning. See https://youtrack.jetbrains.com/projects/KTIJ/issues/KTIJ-31549
    implementation(libs.androidx.annotation.experimental)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
