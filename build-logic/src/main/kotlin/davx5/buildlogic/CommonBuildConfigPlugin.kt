/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package davx5.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.PluginManager
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

class CommonBuildConfigPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.configureAndroidModule(
                pluginId = "com.android.application",
                project = this,
                extensions = extensions
            ) {
                extensions.configure<ApplicationExtension> {
                    configureCommonAndroid()
                    defaultConfig.targetSdk = 36
                }
            }
            pluginManager.configureAndroidModule(
                pluginId = "com.android.library",
                project = this,
                extensions = extensions
            ) {
                extensions.configure<LibraryExtension> {
                    configureCommonAndroid()
                }
            }
        }
    }

    private fun PluginManager.configureAndroidModule(
        pluginId: String,
        project: Project,
        extensions: ExtensionContainer,
        configureAndroid: () -> Unit
    ) {
        withPlugin(pluginId) {
            configureAndroid()

            val javaExtension = extensions.findByType(JavaPluginExtension::class.java)
                ?: error("davx5.common-buildconfig requires Java toolchain support for $pluginId")
            javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

            project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
                compilerOptions.freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }
    }

    private fun CommonExtension.configureCommonAndroid() {
        compileSdk = 37

        defaultConfig.apply {
            minSdk = 24
        }

        compileOptions.apply {
            isCoreLibraryDesugaringEnabled = true
        }
    }

}
