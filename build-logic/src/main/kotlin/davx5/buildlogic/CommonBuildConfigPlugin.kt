/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package davx5.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * This plugin can be applied to other modules (like core, synctools etc.) in order to
 * provide common configuration, like the Java toolchain version and Android configuration
 * like API levels and desugaring.
 */
@Suppress("unused")
class CommonBuildConfigPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val libsCatalog = target.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        val javaToolchainVersion = libsCatalog.findVersion("java-toolchain").get().requiredVersion.toInt()

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
                    defaultConfig.targetSdk = 36    // Android 16
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
            }
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

            // Add Kotlin compiler argument to specify the default annotation target
            project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
                compilerOptions.freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }
    }

    /** Common configuration for all Android modules */
    private fun CommonExtension.configureCommonAndroid() {
        compileSdk = 37     // Android 17
        // see also synctools/robolectric.properties

        defaultConfig.apply {
            minSdk = 24     // Android 7
        }

        // enable desugaring for Java 8 Time API etc.
        compileOptions.apply {
            isCoreLibraryDesugaringEnabled = true
        }
    }

}
