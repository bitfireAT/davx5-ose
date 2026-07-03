/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

import java.util.Properties

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

val gradleDaemonJvmProperties = Properties().also { props ->
    rootDir.resolve("../gradle/gradle-daemon-jvm.properties").reader().use { props.load(it) }
}
val javaToolchainVersion = gradleDaemonJvmProperties.getProperty("toolchainVersion").toInt()

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
