/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.library)
    id("davx5.common-buildconfig")
}

android {
    namespace = "at.bitfire.synctools"

    defaultConfig {
        testInstrumentationRunner = "at.bitfire.synctools.LoggingTestRunner"

        buildConfigField("String", "version_ical4j", "\"${libs.versions.ical4j.get()}\"")

        aarMetadata {
            minCompileSdk = 29
        }
    }

    buildFeatures {
        buildConfig = true
    }
    testFixtures {
        enable = true
    }

    sourceSets {
        getByName("main") {
            java.directories += "opentasks-contract/src/main/java"
        }
    }

    packaging {
        resources {
            excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/*.md")
            excludes += listOf("LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
        }
    }

    buildTypes {
        release {
            // Android libraries shouldn't be minified:
            // https://developer.android.com/studio/projects/android-library#Considerations
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))

            // These ProGuard/R8 rules will be included in the final APK.
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    lint {
        disable += listOf("AllowBackup", "InvalidPackage")
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                create("virtual") {
                    device = "Pixel 3"
                    // read API level from environment variable, fallback to Android 15 (35)
                    apiLevel = System.getenv("API_LEVEL")?.toIntOrNull() ?: 35
                    // ATD images are available since API level 30
                    systemImageSource = if (apiLevel > 30) "aosp-atd" else "aosp"
                    testedAbi = "x86_64"
                }
            }
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        // Configure publish variant
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.guava)

    compileOnly(libs.spotbugs.annotations)

    // ical4j/ez-vcard
    api(libs.ical4j)
    implementation(libs.slf4j.jdk)       // ical4j uses slf4j, this module uses java.util.Logger
    api(libs.ezvcard)

    // force some versions for compatibility with our minSdk level (see version catalog for details)
    implementation(libs.commons.codec)
    implementation(libs.commons.lang)

    // useful annotations
    api(libs.spotbugs.annotations)

    // test fixtures
    testFixturesImplementation(libs.androidx.test.rules)

    // instrumented tests
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)

    // install third-party APKs for instrumented tests (if available)
    val apkDir = file("apk")
    if (apkDir.exists() && apkDir.isDirectory) {
        val apkFiles = apkDir.listFiles { file -> file.isFile && file.name.endsWith(".apk") }
        if (apkFiles != null && apkFiles.isNotEmpty()) {
            androidTestUtil(files(apkFiles))
        }
    }

    // unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    options {
        // Prevent Robolectric from instrumenting ical4j classes to avoid problems with registering
        // ical4j's ZoneRulesProviderImpl more than once with Java's ZoneRulesProvider.
        systemProperty("org.robolectric.packagesToNotAcquire", "net.fortuna.ical4j")
    }
}
