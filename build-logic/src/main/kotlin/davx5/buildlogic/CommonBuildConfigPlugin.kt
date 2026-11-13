/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package davx5.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ResValue
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import java.util.Properties

/**
 * This plugin can be applied to other modules (like core, synctools etc.) in order to
 * provide common configuration, like the Java toolchain version and Android configuration
 * like API levels and desugaring.
 */
@Suppress("unused")
class CommonBuildConfigPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val gradleDaemonJvmProperties = Properties().also { props ->
            target.rootDir.resolve("gradle/gradle-daemon-jvm.properties").reader().use { props.load(it) }
        }
        val javaToolchainVersion = gradleDaemonJvmProperties.getProperty("toolchainVersion").toInt()

        with(target) {
            // When this plugin is applied to a module:
            // 1. Apply common Android application configuration
            pluginManager.configureAndroidModule(
                pluginId = "com.android.application",
                project = this,
                extensions = extensions,
                javaToolchainVersion = javaToolchainVersion
            ) {
                extensions.configure<ApplicationExtension> {
                    // for every Android module
                    configureCommonAndroid()

                    // only for Android applications
                    defaultConfig.targetSdk = 37    // Android 17

                    // take app version from dedicated object
                    defaultConfig.versionCode = AppVersion.CODE
                    defaultConfig.versionName = AppVersion.NAME
                }

                // Derive the DB account type from the (variant-specific) application ID, so that variants
                // and clones don't share the same account type. Overrides the library default.
                extensions.configure<ApplicationAndroidComponentsExtension> {
                    configureDbAccountTypeResource()
                }
            }

            // 2. Apply common Android library configuration
            pluginManager.configureAndroidModule(
                pluginId = "com.android.library",
                project = this,
                extensions = extensions,
                javaToolchainVersion = javaToolchainVersion
            ) {
                extensions.configure<LibraryExtension> {
                    // for every Android module
                    configureCommonAndroid()
                }

                // Default DB account type, so that libraries (core) can reference it. The application
                // module overrides them with values derived from the actual application ID.
                extensions.configure<LibraryAndroidComponentsExtension> {
                    configureDbAccountTypeResource()
                }
            }
        }
    }

    /**
     * Creates the string resource `account_type_db` as: `<package id>.account` on all variants.
     */
    private fun AndroidComponentsExtension<*, *, *>.configureDbAccountTypeResource() {
        onVariants { variant ->
            variant.resValues.put(
                variant.makeResValueKey("string", "account_type_db"),
                variant.namespace.map { ResValue("$it.account", "The account type used by DB accounts") }
            )
        }
    }

    private fun PluginManager.configureAndroidModule(
        pluginId: String,
        project: Project,
        extensions: ExtensionContainer,
        javaToolchainVersion: Int,
        configureAndroid: () -> Unit
    ) {
        withPlugin(pluginId) {
            configureAndroid()

            // Set Java toolchain version
            val javaExtension = extensions.findByType(JavaPluginExtension::class.java)
                ?: error("davx5.common-buildconfig requires Java toolchain support for $pluginId")
            javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion))
        }
    }

    /** Common configuration for all Android modules */
    private fun CommonExtension.configureCommonAndroid() {
        compileSdk {
            // Android 17
            version = release(37) {
                // QPR 2
                minorApiLevel = 2
            }
        }
        // see also synctools/robolectric.properties

        // Enable BuildConfig to generate the IS_BETA flag
        buildFeatures.buildConfig = true

        // For dynamically adding string resources from here
        buildFeatures.resValues = true

        defaultConfig.apply {
            minSdk = 24     // Android 7

            val isPreRelease = setOf("alpha", "beta", "rc").any { AppVersion.NAME.contains(it, ignoreCase = true) }
            buildConfigField("boolean", "PRE_RELEASE", isPreRelease.toString())
        }

        // enable desugaring for Java 8 Time API etc.
        compileOptions.apply {
            isCoreLibraryDesugaringEnabled = true
        }

        testOptions.unitTests.all { test ->
            // Robolectric on Java 17+ needs access to JDK internals [https://robolectric.org/getting-started/#running-with-java-17-and-higher]
            test.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )
        }
    }

}
