/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikepenz.aboutLibraries.android)
}

// Android configuration
android {
    compileSdk = 36

    defaultConfig {
        minSdk = 24        // Android 7.0

        testInstrumentationRunner = "at.bitfire.davdroid.HiltTestRunner"
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    compileOptions {
        // required for
        // - dnsjava 3.x: java.nio.file.Path
        // - ical4android: time API
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Java namespace for our classes (not to be confused with Android package ID)
    namespace = "at.bitfire.davdroid"

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    lint {
        disable += arrayOf("GoogleAppIndexingWarning", "ImpliedQuantity", "MissingQuantity", "MissingTranslation", "ExtraTranslation", "RtlEnabled", "RtlHardcoded", "Typos")
    }

    packaging {
        resources {
            // multiple (test) dependencies have LICENSE files at same location
            merges += arrayOf("META-INF/LICENSE*")
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
    export {
        // exclude timestamps for reproducible builds [https://github.com/bitfireAT/davx5-ose/issues/994]
        excludeFields.add("generated")
    }
}

dependencies {
    // Kotlin / Android
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    coreLibraryDesugaring(libs.android.desugaring)

    // Hilt
    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    // support libs
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.base)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.security)
    implementation(libs.androidx.work.base)

    // Jetpack Compose
    implementation(libs.compose.accompanist.permissions)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.materialIconsExtended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.toolingPreview)

    // Glance Widgets
    implementation(libs.androidx.glance.base)
    implementation(libs.androidx.glance.material3)

    // Jetpack Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.base)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // own libraries
    implementation(libs.bitfire.cert4android)
    implementation(libs.bitfire.dav4jvm) {
        exclude(group="junit")
        exclude(group="org.ogce", module="xpp3")    // Android has its own XmlPullParser implementation
    }
    implementation(libs.bitfire.synctools) {
        exclude(group="androidx.test")              // synctools declares test rules, but we don't want them in non-test code
        exclude(group = "junit")
    }

    // third-party libs
    implementation(libs.conscrypt)
    implementation(libs.dnsjava)
    implementation(libs.guava)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.mikepenz.aboutLibraries.m3)
    implementation(libs.okhttp.base)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.logging)
    implementation(libs.openid.appauth)
    implementation(libs.unifiedpush) {
        // UnifiedPush connector seems to be using a workaround by importing this library.
        // Will be removed after https://github.com/tink-crypto/tink-java-apps/pull/5 is merged.
        // See: https://codeberg.org/UnifiedPush/android-connector/src/commit/28cb0d622ed0a972996041ab9cc85b701abc48c6/connector/build.gradle#L56-L59
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.unifiedpush.fcm)

    // force some versions for compatibility with our minSdk level (see version catalog for details)
    implementation(libs.commons.codec)
    implementation(libs.commons.lang)

    // for tests
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.okhttp.mockwebserver)

    testImplementation(libs.bitfire.dav4jvm)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
