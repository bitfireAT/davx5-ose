/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikepenz.aboutLibraries.android)
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 24        // Android 7.0
        targetSdk = 36     // Android 16

        applicationId = "at.bitfire.davdroid"

        /*
         * Version names use Semantic Versioning. Pre-release identifiers are "alpha" (closed alpha in
         * internal track), "beta" (public beta track) and "rc" (public beta track).
         *
         * Version codes are derived from the version name like this:
         *
         * MmmppIIII   (example `405120000`)   where
         *
         * - M is the major version (`4` in the example)
         * - mm the minor version (two decimal digits, `05` in the example),
         * - pp the patch level (two decimal digits, `12` in the example), and
         * - IIII an increasing number (four decimal digits) that starts with `0000` and is increased for
         *   every release with the same major/minor/patch version (alpha-1, alpha-2, beta-1, ..., final).
         *   So usually the first pre-release has `0000` and the final version has the greatest number.
         */
        versionCode = 405120000
        versionName = "4.5.12-beta.1"

        base.archivesName = "davx5-$versionCode-$versionName"

        /* Android prevents having two apps installed with the same provider authority name. In that case,
        Google Play just shows a generic "Can't install DAVx5" message. So we derive the authority names
        from the package ID, so that the build variants (and clones) have their own authority names and
        can be installed beside DAVx5. */
        val webdavAuthority = "${applicationId}.webdav"
        val debugInfoAuthority = "${applicationId}.debug"
        manifestPlaceholders["webdavAuthority"] = webdavAuthority
        manifestPlaceholders["debugInfoAuthority"] = debugInfoAuthority
        /* Override the default string values from the core library (core/src/main/res/values/strings.xml)
        so that code using getString(R.string.webdav_authority) etc. gets the correct authority. */
        resValue("string", "webdav_authority", webdavAuthority)
        resValue("string", "authority_debug_provider", debugInfoAuthority)

        // Currently no instrumentation tests for app-ose, so no testInstrumentationRunner
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
        resValues = true
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

    androidResources {
        generateLocaleConfig = true
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                create("virtual") {
                    device = "Pixel 3"
                    // TBD: API level 35 and higher causes network tests to fail sometimes, see https://github.com/bitfireAT/davx5-ose/issues/1525
                    // Suspected reason: https://developer.android.com/about/versions/15/behavior-changes-all#background-network-access
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
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

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules-release.pro")

            isShrinkResources = true

            // must be after signingConfigs {} block
            signingConfig = signingConfigs.findByName("bitfire")
        }
    }
}

dependencies {
    // include core subproject (manages its own dependencies itself, however from same version catalog)
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
    implementation(libs.openid.appauth)
}