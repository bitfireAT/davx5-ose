/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("davx5.common-buildconfig")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "at.bitfire.davdroid.tasks.provider"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // public contract — the only part of this module a frontend would ever see
    api(project(":tasks-provider-contract"))

    implementation(libs.kotlin.stdlib)
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines)

    implementation(libs.androidx.room.base)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // RRULE expansion (RecurrenceExpander) - deliberately not a dependency on synctools, which
    // wraps ical4j with DAVx5 sync-engine concerns this module has no business knowing about
    implementation(libs.ical4j)
    implementation(libs.slf4j.jdk)
    // force the same strict Apache Commons versions synctools uses (see version catalog) - without
    // this, ical4j's own transitive commons-lang3 conflicts with synctools' pin on synctools'
    // androidTest classpath (synctools:androidTest depends on this module to test DavTasksProvider)
    implementation(libs.commons.codec)
    implementation(libs.commons.lang)

    // instrumented tests
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    options {
        // Prevent Robolectric from instrumenting ical4j classes to avoid problems with registering
        // ical4j's ZoneRulesProviderImpl more than once with Java's ZoneRulesProvider.
        systemProperty("org.robolectric.packagesToNotAcquire", "net.fortuna.ical4j")
    }
}
