/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
    compileOnly("com.android.tools.build:gradle:9.2.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
}
