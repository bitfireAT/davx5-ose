/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 24        // Android 7.0
        targetSdk = 36     // Android 16

        applicationId = "at.bitfire.davdroid"

        versionCode = 405090005
        versionName = "4.5.9"

        //base.archivesName = "davx5-$versionCode-$versionName"
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    // Java namespace for our classes (not to be confused with Android package ID)
    namespace = "com.davx5.ose"

    flavorDimensions += "distribution"
    productFlavors {
        create("ose") {
            dimension = "distribution"
            versionNameSuffix = "-ose"
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules-release.pro")

            isShrinkResources = true

            signingConfig = signingConfigs.findByName("bitfire")
        }
    }

    signingConfigs {
        create("bitfire") {
            storeFile = file(System.getenv("ANDROID_KEYSTORE") ?: "/dev/null")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
}

dependencies {
    // include core module
    implementation(project(":core"))

    // Kotlin / Android
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    coreLibraryDesugaring(libs.android.desugaring)

    // Hilt
    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    // support libs
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.viewmodel.base)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.base)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.toolingPreview)

    // own libraries
    implementation(libs.bitfire.cert4android)

    // third-party libs
    implementation(libs.guava)
    implementation(libs.okhttp.base)
}