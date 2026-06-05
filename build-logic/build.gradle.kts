/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaToolchainVersion = libsCatalog.findVersion("java-toolchain").get().requiredVersion.toInt()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaToolchainVersion)
    }
}

gradlePlugin {
    plugins {
        register("commonBuildConfig") {
            id = "davx5.common-buildconfig"
            implementationClass = "davx5.buildlogic.CommonBuildConfigPlugin"
        }
    }
}

dependencies {
    compileOnly(libs.android.agp)
    compileOnly(libs.kotlin.gradle.plugin)
}
