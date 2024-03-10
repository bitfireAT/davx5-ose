/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

plugins {
    alias(libs.plugins.mikepenz.aboutLibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
}

aboutLibraries {
    // This means that we have to generate the dependencies explicitly:
    // ./gradlew --no-configuration-cache --no-build-cache -PaboutLibraries.exportPath=src/main/res/raw/ app:exportLibraryDefinitions
    registerAndroidTasks = false
}

// Android configuration
android {
    compileSdk = 34

    defaultConfig {
        applicationId = "at.bitfire.davdroid"

        versionCode = 403150001
        versionName = "4.3.15-alpha.2"

        buildConfigField("long", "buildTime", "${System.currentTimeMillis()}L")

        setProperty("archivesBaseName", "davx5-ose-" + versionName)

        minSdk = 24        // Android 7.0
        targetSdk = 34     // Android 14

        buildConfigField("String", "userAgent", "\"DAVx5\"")

        testInstrumentationRunner = "at.bitfire.davdroid.CustomTestRunner"
    }

    compileOptions {
        // enable because ical4android requires desugaring
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
        dataBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    // Java namespace for our classes (not to be confused with Android package ID)
    namespace = "at.bitfire.davdroid"

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

            signingConfig = signingConfigs.findByName("bitfire")
        }
    }

    lint {
        disable += arrayOf("GoogleAppIndexingWarning", "ImpliedQuantity", "MissingQuantity", "MissingTranslation", "ExtraTranslation", "RtlEnabled", "RtlHardcoded", "Typos", "NullSafeMutableLiveData")
    }

    packaging {
        resources {
            excludes += arrayOf("META-INF/*.md")
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

configurations {
    configureEach {
        // exclude modules which are in conflict with system libraries
        exclude(module="commons-logging")
        exclude(group="org.json", module="json")

        // Groovy requires SDK 26+, and it's not required, so exclude it
        exclude(group="org.codehaus.groovy")
    }
}

dependencies {
    // core
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
    implementation(libs.androidx.cardView)
    implementation(libs.androidx.concurrentFuture)
    implementation(libs.androidx.constraintLayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.viewmodel.base)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.security)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.base)
    implementation(libs.android.flexbox)
    implementation(libs.android.material)

    // Jetpack Compose
    implementation(libs.compose.accompanist.permissions)
    implementation(libs.compose.accompanist.themeAdapter)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.compose.runtime.livedata)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.toolingPreview)

    // Jetpack Room
    implementation(libs.room.runtime)
    implementation(libs.room.base)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // own libraries
    implementation(libs.bitfire.cert4android)
    implementation(libs.bitfire.dav4jvm) {
        exclude(group="junit")
    }
    implementation(libs.bitfire.ical4android)
    implementation(libs.bitfire.vcard4android)

    // third-party libs
    implementation(libs.appintro)
    implementation(libs.commons.collections)
    @Suppress("RedundantSuppression")
    implementation(libs.commons.io)
    implementation(libs.commons.lang)
    implementation(libs.commons.text)
    @Suppress("RedundantSuppression")
    implementation(libs.dnsjava)
    implementation(libs.jaredrummler.colorpicker)
    implementation(libs.mikepenz.aboutLibraries)
    implementation(libs.nsk90.kstatemachine)
    implementation(libs.okhttp.base)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.logging)
    implementation(libs.openid.appauth)

    // for tests
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.room.testing)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}