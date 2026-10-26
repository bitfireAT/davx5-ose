import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("davx5.common-buildconfig")
}

android {
    namespace = "com.davx5.feature.preconfiguredsettings.externalscanner"
    resourcePrefix = "preconfigured_settings_external_scanner_"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    api(project(":features:preconfigured-settings:api"))
    implementation(project(":features:preconfigured-settings:shared"))

    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialIconsExtended)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.ktor.client.core)
}
